package com.muonium.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 歌单 / 队列 / 播放模式。队列是运行时状态，不直接修改源歌单。 */
public final class Playlist {

    private Playlist() {}

    public enum PlayMode { SEQUENCE, SHUFFLE;
        public String wire() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static PlayMode fromWire(String s) {
            return "shuffle".equalsIgnoreCase(s) ? SHUFFLE : SEQUENCE;
        }
    }

    public enum RepeatMode { NONE, ONE, LIST;
        public String wire() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static RepeatMode fromWire(String s) {
            if (s == null) return NONE;
            return switch (s.toLowerCase(java.util.Locale.ROOT)) {
                case "one" -> ONE;
                case "list" -> LIST;
                default -> NONE;
            };
        }
    }

    public record Item(String songId, Long addedAt) {}

    /** 源歌单：revision 用于乐观并发控制。 */
    public record Document(int schemaVersion, String id, String name, List<Item> items,
                           int revision, String updatedAt) {
        public Document {
            Objects.requireNonNull(id, "playlist id");
            items = items == null ? List.of() : List.copyOf(items);
        }
        public static final int CURRENT_SCHEMA = 1;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("schemaVersion", schemaVersion);
            m.put("id", id);
            m.put("name", name);
            List<Map<String, Object>> arr = new ArrayList<>();
            for (Item it : items) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("songId", it.songId());
                e.put("addedAt", it.addedAt());
                arr.add(e);
            }
            m.put("items", arr);
            m.put("revision", revision);
            m.put("updatedAt", updatedAt);
            return m;
        }

        @SuppressWarnings("unchecked")
        public static Document fromMap(Map<String, Object> m) {
            List<Item> items = new ArrayList<>();
            if (m.get("items") instanceof List<?> raw) {
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> mm) {
                        Map<String, Object> e = (Map<String, Object>) mm;
                        Long added = e.get("addedAt") instanceof Number n ? n.longValue() : null;
                        items.add(new Item(String.valueOf(e.get("songId")), added));
                    }
                }
            }
            int rev = m.get("revision") instanceof Number n ? n.intValue() : 0;
            int sv = m.get("schemaVersion") instanceof Number n2 ? n2.intValue() : 1;
            return new Document(sv, String.valueOf(m.get("id")), String.valueOf(m.get("name")),
                    items, rev, m.get("updatedAt") == null ? null : m.get("updatedAt").toString());
        }
    }

    /** 队列快照：可序列化、可恢复；刷新 UI 不得重排队列。 */
    public record QueueSnapshot(
            String playingSongId,
            List<String> played,
            List<String> upcoming,
            PlayMode mode,
            Long shuffleSeed,
            RepeatMode repeat,
            String autoplaySourcePlaylistId
    ) {
        public QueueSnapshot {
            played = played == null ? List.of() : List.copyOf(played);
            upcoming = upcoming == null ? List.of() : List.copyOf(upcoming);
            mode = mode == null ? PlayMode.SEQUENCE : mode;
            repeat = repeat == null ? RepeatMode.NONE : repeat;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("playingSongId", playingSongId);
            m.put("played", played);
            m.put("upcoming", upcoming);
            m.put("mode", mode.wire());
            m.put("shuffleSeed", shuffleSeed);
            m.put("repeat", repeat.wire());
            m.put("autoplaySourcePlaylistId", autoplaySourcePlaylistId);
            return m;
        }

        @SuppressWarnings("unchecked")
        public static QueueSnapshot fromMap(Map<String, Object> m) {
            return new QueueSnapshot(
                    m.get("playingSongId") == null ? null : m.get("playingSongId").toString(),
                    (List<String>) m.getOrDefault("played", List.of()),
                    (List<String>) m.getOrDefault("upcoming", List.of()),
                    PlayMode.fromWire(String.valueOf(m.get("mode"))),
                    m.get("shuffleSeed") instanceof Number n ? n.longValue() : null,
                    RepeatMode.fromWire(String.valueOf(m.get("repeat"))),
                    m.get("autoplaySourcePlaylistId") == null ? null : m.get("autoplaySourcePlaylistId").toString());
        }
    }
}
