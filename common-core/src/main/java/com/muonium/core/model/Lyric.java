package com.muonium.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一歌词模型：支持逐行、逐字、翻译、对唱、空行、重复时间与未知结束时间。
 * 缺失 endMs 用 {@code null} 表示「未知」，不要用 0 或猜测值填充。
 */
public final class Lyric {

    private Lyric() {}

    /** 说话人：对唱分列渲染用。 */
    public enum Speaker { LEAD, BACKING, DUET_A, DUET_B, UNKNOWN }

    public record Word(String text, long startMs, Long endMs) {
        public Word {
            if (text == null) text = "";
            if (startMs < 0) startMs = 0;
        }
    }

    public record Line(int index, long startMs, Long endMs, Speaker speaker,
                       List<Word> words, String text, String translation) {
        public Line {
            words = words == null ? List.of() : List.copyOf(words);
            speaker = speaker == null ? Speaker.UNKNOWN : speaker;
            if (startMs < 0) startMs = 0;
        }
    }

    /** 原始文件无法识别格式时保留原文并标记 unsupported，拒绝猜测。 */
    public record Document(int schemaVersion, String songId, Long durationMs,
                           List<Line> lines, String format, boolean unsupported) {
        public Document {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
        public static final int CURRENT_SCHEMA = 1;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("schemaVersion", schemaVersion);
            m.put("songId", songId);
            m.put("durationMs", durationMs);
            m.put("format", format);
            m.put("unsupported", unsupported);
            List<Map<String, Object>> arr = new ArrayList<>();
            for (Line l : lines) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("index", l.index());
                e.put("startMs", l.startMs());
                e.put("endMs", l.endMs());
                e.put("speaker", l.speaker().name().toLowerCase(java.util.Locale.ROOT));
                e.put("text", l.text());
                e.put("translation", l.translation());
                List<Map<String, Object>> ws = new ArrayList<>();
                for (Word w : l.words()) {
                    Map<String, Object> we = new LinkedHashMap<>();
                    we.put("text", w.text());
                    we.put("startMs", w.startMs());
                    we.put("endMs", w.endMs());
                    ws.add(we);
                }
                e.put("words", ws);
                arr.add(e);
            }
            m.put("lines", arr);
            return m;
        }

        @SuppressWarnings("unchecked")
        public static Document fromMap(Map<String, Object> m) {
            List<Line> lines = new ArrayList<>();
            if (m.get("lines") instanceof List<?> raw) {
                for (Object o : raw) {
                    if (!(o instanceof Map<?, ?> mm)) continue;
                    Map<String, Object> e = (Map<String, Object>) mm;
                    List<Word> words = new ArrayList<>();
                    if (e.get("words") instanceof List<?> wr) {
                        for (Object wo : wr) {
                            if (!(wo instanceof Map<?, ?> wmm)) continue;
                            Map<String, Object> w = (Map<String, Object>) wmm;
                            long s = w.get("startMs") instanceof Number n ? n.longValue() : 0L;
                            Long en = w.get("endMs") instanceof Number n2 ? n2.longValue() : null;
                            words.add(new Word(String.valueOf(w.get("text")), s, en));
                        }
                    }
                    long s = e.get("startMs") instanceof Number n ? n.longValue() : 0L;
                    Long en = e.get("endMs") instanceof Number n2 ? n2.longValue() : null;
                    int idx = e.get("index") instanceof Number n3 ? n3.intValue() : lines.size();
                    Speaker sp = Speaker.UNKNOWN;
                    if (e.get("speaker") != null) {
                        sp = switch (e.get("speaker").toString().toUpperCase(java.util.Locale.ROOT)) {
                            case "LEAD" -> Speaker.LEAD;
                            case "BACKING" -> Speaker.BACKING;
                            case "DUET_A" -> Speaker.DUET_A;
                            case "DUET_B" -> Speaker.DUET_B;
                            default -> Speaker.UNKNOWN;
                        };
                    }
                    lines.add(new Line(idx, s, en, sp, words,
                            e.get("text") == null ? "" : e.get("text").toString(),
                            e.get("translation") == null ? null : e.get("translation").toString()));
                }
            }
            Long dur = m.get("durationMs") instanceof Number n ? n.longValue() : null;
            int sv = m.get("schemaVersion") instanceof Number n2 ? n2.intValue() : 1;
            return new Document(sv, String.valueOf(m.get("songId")), dur, lines,
                    String.valueOf(m.get("format")), Boolean.TRUE.equals(m.get("unsupported")));
        }
    }
}
