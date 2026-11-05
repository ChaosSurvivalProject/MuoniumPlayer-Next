package com.muonium.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨端统一歌曲模型（schemaVersion = 1，键名与 docs/song-schema.md 一致，snake_case）。
 *
 * <p><b>null 安全约定</b>：{@code null} 表示「未知 / 未提供」，空集合或空字符串表示「明确没有」。
 * 两者在数据中必须可区分，导入器不得把「未知」写成 0 或空串。
 *
 * <p><b>禁止包含</b> Minecraft、Fabric、Script API、AWT、文件/网络/线程实现。
 * 全部为不可变值对象，跨 Java 端与基岩端共用同一份 JSON 表达。
 */
public record Song(
        int schemaVersion,
        String id,
        String title,
        String sortTitle,
        List<ArtistRef> artists,
        AlbumRef album,
        Long durationMs,
        DurationSource durationSource,
        SourceInfo source,
        Assets assets,
        Analysis analysis,
        Flags flags,
        String updatedAt
) {
    public Song {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
        artists = artists == null ? List.of() : List.copyOf(artists);
        if (durationMs != null && durationMs < 0) throw new IllegalArgumentException("durationMs must be >= 0");
        if (durationSource == null) durationSource = DurationSource.UNKNOWN;
        if (flags == null) flags = Flags.DEFAULT;
    }

    /** id 永久稳定，不随文件名、平台或音质变化；不得使用第三方平台内部 ID 作为全局 ID。 */
    public static final int CURRENT_SCHEMA = 1;

    public enum DurationSource {
        IMPORT_PROBE, PROVIDER_METADATA, USER_DECLARED, UNKNOWN;
        public String wire() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static DurationSource fromWire(String s) {
            if (s == null) return UNKNOWN;
            return switch (s) {
                case "import_probe" -> IMPORT_PROBE;
                case "provider_metadata" -> PROVIDER_METADATA;
                case "user_declared" -> USER_DECLARED;
                default -> UNKNOWN;
            };
        }
    }

    public record ArtistRef(String id, String name) {}
    public record AlbumRef(String id, String name) {}
    public record SourceInfo(String provider, String providerTrackId, String license, String region) {}
    public record AssetRef(String kind, String ref) {}
    public record Assets(AssetRef audio, AssetRef cover, AssetRef lyric) {}
    public record Automix(boolean enabled, Double score) {}
    public record Analysis(Double loudnessLufs, Double peakDbfs, Automix automix) {}
    public record Flags(boolean hasLyric, boolean hasTranslation, boolean explicit, boolean playableOffline) {
        public static final Flags DEFAULT = new Flags(false, false, false, false);
    }

    /** 文件名规则：小写 ASCII、下划线、稳定编号；不允许中文歌名作文件名。 */
    public static boolean isValidResourceName(String name) {
        return name != null && name.matches("[a-z0-9_]+");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("schemaVersion", schemaVersion);
        m.put("id", id);
        m.put("title", title);
        m.put("sortTitle", sortTitle);
        m.put("artists", artists.stream().map(a -> nullableMap("id", a.id(), "name", a.name())).toList());
        m.put("album", album == null ? null : nullableMap("id", album.id(), "name", album.name()));
        m.put("durationMs", durationMs);
        m.put("durationSource", durationSource.wire());
        m.put("source", source == null ? null : nullableMap(
                "provider", source.provider(), "providerTrackId", source.providerTrackId(),
                "license", source.license(), "region", source.region()));
        m.put("assets", assets == null ? null : assetMap(assets));
        m.put("analysis", analysis == null ? null : nullableMap(
                "loudnessLufs", analysis.loudnessLufs(), "peakDbfs", analysis.peakDbfs(),
                "automix", nullableMap("enabled", analysis.automix().enabled(),
                        "score", analysis.automix().score())));
        m.put("flags", Map.of(
                "hasLyric", flags.hasLyric(), "hasTranslation", flags.hasTranslation(),
                "explicit", flags.explicit(), "playableOffline", flags.playableOffline()));
        m.put("updatedAt", updatedAt);
        return m;
    }

    private static Map<String, Object> assetMap(Assets a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("audio", ref(a.audio()));
        m.put("cover", ref(a.cover()));
        m.put("lyric", ref(a.lyric()));
        return m;
    }

    private static Map<String, Object> ref(AssetRef r) {
        return r == null ? null : nullableMap("kind", r.kind(), "ref", r.ref());
    }

    /** Map.of 不接受 null；这里用允许 null 值的 map 保持「未知」语义。 */
    private static Map<String, Object> nullableMap(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Song fromMap(Map<String, Object> m) {
        Objects.requireNonNull(m, "song map");
        int v = ((Number) m.getOrDefault("schemaVersion", 1)).intValue();
        List<ArtistRef> artists = List.of();
        Object rawArtists = m.get("artists");
        if (rawArtists instanceof List<?> list) {
            artists = list.stream()
                    .filter(Map.class::isInstance).map(x -> (Map<String, Object>) x)
                    .map(a -> new ArtistRef(str(a.get("id")), str(a.get("name"))))
                    .toList();
        }
        AlbumRef album = null;
        if (m.get("album") instanceof Map<?, ?> a) {
            album = new AlbumRef(str(a.get("id")), str(a.get("name")));
        }
        Long dur = m.get("durationMs") instanceof Number n ? n.longValue() : null;
        return new Song(
                v, str(m.get("id")), str(m.get("title")), str(m.get("sortTitle")),
                artists, album, dur,
                DurationSource.fromWire(str(m.get("durationSource"))),
                readSource(m.get("source")), readAssets(m.get("assets")),
                readAnalysis(m.get("analysis")), readFlags(m.get("flags")),
                str(m.get("updatedAt")));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    @SuppressWarnings("unchecked")
    private static SourceInfo readSource(Object o) {
        if (!(o instanceof Map<?, ?> mm)) return null;
        Map<String, Object> m = (Map<String, Object>) mm;
        return new SourceInfo(str(m.get("provider")), str(m.get("providerTrackId")),
                str(m.get("license")), str(m.get("region")));
    }

    @SuppressWarnings("unchecked")
    private static Assets readAssets(Object o) {
        if (!(o instanceof Map<?, ?> mm)) return null;
        Map<String, Object> m = (Map<String, Object>) mm;
        return new Assets(readRef(m.get("audio")), readRef(m.get("cover")), readRef(m.get("lyric")));
    }

    @SuppressWarnings("unchecked")
    private static AssetRef readRef(Object o) {
        if (!(o instanceof Map<?, ?> mm)) return null;
        Map<String, Object> m = (Map<String, Object>) mm;
        return new AssetRef(str(m.get("kind")), str(m.get("ref")));
    }

    @SuppressWarnings("unchecked")
    private static Analysis readAnalysis(Object o) {
        if (!(o instanceof Map<?, ?> mm)) return null;
        Map<String, Object> m = (Map<String, Object>) mm;
        Double lufs = m.get("loudnessLufs") instanceof Number n ? n.doubleValue() : null;
        Double peak = m.get("peakDbfs") instanceof Number n2 ? n2.doubleValue() : null;
        Automix am = new Automix(false, null);
        if (m.get("automix") instanceof Map<?, ?> amm) {
            Map<String, Object> a = (Map<String, Object>) amm;
            am = new Automix(Boolean.TRUE.equals(a.get("enabled")),
                    a.get("score") instanceof Number n3 ? n3.doubleValue() : null);
        }
        return new Analysis(lufs, peak, am);
    }

    @SuppressWarnings("unchecked")
    private static Flags readFlags(Object o) {
        if (!(o instanceof Map<?, ?> mm)) return Flags.DEFAULT;
        Map<String, Object> m = (Map<String, Object>) mm;
        return new Flags(Boolean.TRUE.equals(m.get("hasLyric")), Boolean.TRUE.equals(m.get("hasTranslation")),
                Boolean.TRUE.equals(m.get("explicit")), Boolean.TRUE.equals(m.get("playableOffline")));
    }
}
