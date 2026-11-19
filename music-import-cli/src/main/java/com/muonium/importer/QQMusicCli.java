package com.muonium.importer;

import com.muonium.core.codec.MapJsonCodec;
import com.muonium.core.json.Json;
import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;
import com.muonium.providers.FfmpegTranscoder;
import com.muonium.providers.HttpPort;
import com.muonium.providers.ProviderException;
import com.muonium.providers.QQMusicMediaSource;
import com.muonium.providers.Transcoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QQ 音乐子命令（非官方、个人使用）。
 *
 * <p>凭据只来自显式传入的 {@code --cookie <文件>} 或环境变量 {@code QQMUSIC_COOKIE}；
 * <b>绝不写入曲库清单、日志或仓库</b>。{@code --fake} 走内置合成响应，仅供 CI/自测，不联网、
 * 不触碰任何真实版权内容。详见 docs/qq-music.md。
 */
public final class QQMusicCli {

    private QQMusicCli() {}

    static int search(String[] args) throws IOException {
        Map<String, String> opt = opts(args);
        String query = opt.get("query");
        if (query == null || query.isBlank()) { System.err.println("用法: qqsearch --query 关键词 [--page N] [--cookie FILE] [--fake]"); return 2; }
        int page = parseInt(opt.get("page"), 1);
        QQMusicMediaSource src = buildSource(opt);
        List<Ports.MediaResource> list = src.search(query, page);
        System.out.println("SEARCH '" + query + "' -> " + list.size() + " 条（个人使用，非官方集成）");
        for (int i = 0; i < list.size(); i++) {
            Ports.MediaResource r = list.get(i);
            System.out.printf("%d. [%s] %s - %s (%s)%n", i + 1, r.sourceTrackId(), r.title(),
                    String.join("/", r.artists()), r.durationMs() == null ? "?" : (r.durationMs() / 1000) + "s");
        }
        return 0;
    }

    static int lyric(String[] args) throws IOException {
        Map<String, String> opt = opts(args);
        String mid = opt.get("songmid");
        if (mid == null || mid.isBlank()) { System.err.println("用法: qqlyric <songmid> [--cookie FILE] [--fake]"); return 2; }
        QQMusicMediaSource src = buildSource(opt);
        Ports.MediaResource track = new Ports.MediaResource(mid, opt.get("name"), List.of(), null,
                null, null, null, null, "personal-use");
        com.muonium.core.model.Lyric.Document doc = src.getLyric(track);
        if (doc == null || doc.lines().isEmpty()) { System.out.println("(无歌词)"); return 0; }
        for (com.muonium.core.model.Lyric.Line l : doc.lines()) {
            System.out.printf("[%s] %s%n", fmt(l.startMs()), l.text());
        }
        return 0;
    }

    static int importTrack(String[] args) throws IOException {
        Map<String, String> opt = opts(args);
        String mid = opt.get("songmid");
        if (mid == null || mid.isBlank()) { System.err.println("用法: qqimport <songmid> [--cookie FILE] [--library DIR] [--name 标题] [--fake]"); return 2; }
        Path libraryDir = Path.of(opt.getOrDefault("library", "."));
        Files.createDirectories(libraryDir);
        QQMusicMediaSource src = buildSource(opt, libraryDir);
        Ports.MediaResource resolved = src.resolve(mid);
        Ports.MediaResource imported = src.prepareForImport(resolved);
        Path audioPath = Path.of(imported.audioRef());
        String audioRef = libraryDir.toAbsolutePath().relativize(audioPath.toAbsolutePath()).toString().replace('\\', '/');
        Song song = toSong(imported, audioRef, opt.get("name"));
        appendTrack(libraryDir, song);
        System.out.println("IMPORT OK -> " + song.id() + " 音频=" + audioRef);
        System.out.println("提示：在基岩/Java 端重新加载曲库即可播放；仅限个人使用。");
        return 0;
    }

    private static QQMusicMediaSource buildSource(Map<String, String> opt) throws IOException {
        return buildSource(opt, null);
    }

    private static QQMusicMediaSource buildSource(Map<String, String> opt, Path importDir) throws IOException {
        String cookie = resolveCookie(opt);
        String uin = opt.getOrDefault("uin", extractUin(cookie));
        HttpPort http = opt.containsKey("fake") ? new FakeHttpPort() : new com.muonium.providers.JdkHttpPort();
        Transcoder transcoder = opt.containsKey("fake")
                ? (in, dir, base) -> {
                    try {
                        Path o = dir.resolve(base + ".ogg");
                        Files.createDirectories(dir);
                        Files.copy(in, o, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return o;
                    } catch (IOException e) {
                        throw new ProviderException("transcode failed", e);
                    }
                }
                : FfmpegTranscoder.create();
        return new QQMusicMediaSource(http, transcoder, cookie, uin, s -> "testsig", importDir);
    }

    private static String resolveCookie(Map<String, String> opt) throws IOException {
        if (opt.containsKey("fake")) return "uin=0; qm_key=FAKE";
        String file = opt.get("cookie");
        if (file != null && !file.isBlank()) return Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
        String env = System.getenv("QQMUSIC_COOKIE");
        if (env != null && !env.isBlank()) return env.trim();
        throw new IOException("缺少 QQ 音乐 Cookie：请传 --cookie <文件> 或设置环境变量 QQMUSIC_COOKIE（个人使用）。");
    }

    private static String extractUin(String cookie) {
        if (cookie == null) return "0";
        int i = cookie.indexOf("uin=");
        if (i < 0) return "0";
        int j = cookie.indexOf(';', i);
        String v = (j < 0 ? cookie.substring(i + 4) : cookie.substring(i + 4, j)).trim();
        return v.isBlank() ? "0" : v;
    }

    private static Song toSong(Ports.MediaResource r, String audioRef, String overrideName) {
        String id = "qq_" + r.sourceTrackId();
        List<Song.ArtistRef> artists = r.artists().stream().map(n -> new Song.ArtistRef(null, n)).toList();
        Song.AlbumRef album = r.album() == null ? null : new Song.AlbumRef(null, r.album());
        Song.SourceInfo source = new Song.SourceInfo("qq", r.sourceTrackId(), QQMusicMediaSource.LICENSE, null);
        Song.Assets assets = new Song.Assets(
                new Song.AssetRef("audio", audioRef),
                r.coverRef() == null ? null : new Song.AssetRef("cover", r.coverRef()),
                null);
        return new Song(1, id, overrideName != null ? overrideName : r.title(), null, artists, album,
                r.durationMs(), Song.DurationSource.PROVIDER_METADATA, source, assets,
                null, new Song.Flags(false, false, false, true), "1970-01-01T00:00:00Z");
    }

    @SuppressWarnings("unchecked")
    private static void appendTrack(Path libraryDir, Song song) throws IOException {
        Path file = libraryDir.resolve("library.json");
        Map<String, Object> doc = new LinkedHashMap<>();
        List<Map<String, Object>> tracks = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.isBlank()) {
                Map<String, Object> existing = Json.parseObject(text);
                if (existing.get("tracks") instanceof List<?> list) {
                    for (Object o : list) if (o instanceof Map<?, ?> m) tracks.add((Map<String, Object>) m);
                }
            }
        }
        // 同 id 去重
        tracks.removeIf(t -> song.id().equals(String.valueOf(t.get("id"))));
        tracks.add(song.toMap());
        doc.put("tracks", tracks);
        MapJsonCodec codec = new MapJsonCodec();
        Files.writeString(file, codec.stringify(doc), StandardCharsets.UTF_8);
    }

    private static String fmt(long ms) {
        long s = ms / 1000, m = s / 60;
        return String.format("%02d:%02d", m, s % 60);
    }

    private static int parseInt(String v, int d) {
        try { return v == null ? d : Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return d; }
    }

    private static Map<String, String> opts(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--fake") || a.equals("--dry-run")) { m.put("fake", "1"); continue; }
            if (a.startsWith("--") && i + 1 < args.length) {
                String key = a.substring(2);
                String val = args[++i];
                if (key.equals("query")) { m.put("query", val); continue; }
                m.put(key, val);
            } else if (m.get("songmid") == null && !a.startsWith("--")) {
                m.put("songmid", a); // 位置参数：songmid
            }
        }
        return m;
    }

    /** --fake 模式用的合成 HTTP 端口（不联网、不触碰真实内容）。 */
    private static final class FakeHttpPort implements HttpPort {
        private static final String SEARCH = "{\"code\":0,\"data\":{\"song\":{\"list\":["
                + "{\"songmid\":\"mid_abc\",\"songname\":\"测试歌曲\",\"singer\":[{\"name\":\"歌手A\"}],"
                + "\"albumname\":\"测试专辑\",\"albummid\":\"album_xyz\",\"interval\":222}]}}}";
        private static final String PLAYLIST = "{\"code\":0,\"cdlist\":["
                + "{\"songmid\":\"mid_pl1\",\"songname\":\"歌单曲1\",\"singer\":[{\"name\":\"歌手D\"}],"
                + "\"albumname\":\"专辑P\",\"albummid\":\"album_p1\",\"interval\":200}]}";
        private static final String VKEY = "{\"code\":0,\"req_0\":{\"code\":0,\"data\":{"
                + "\"midurlinfo\":[{\"purl\":\"C400mid_abc.m4a?vkey=TESTKEY123\"}]}}}";
        private static final String SINGLE = "{\"code\":0,\"data\":[{\"songmid\":\"mid_abc\","
                + "\"songname\":\"测试歌曲\",\"singer\":[{\"name\":\"歌手A\"},{\"name\":\"歌手B\"}],"
                + "\"album\":{\"name\":\"测试专辑\",\"mid\":\"album_xyz\"},\"interval\":222}]}";
        @Override
        public String getText(String url, Map<String, String> headers) {
            if (url.contains("client_search_cp")) return SEARCH;
            if (url.contains("musicu.fcg")) return VKEY;
            if (url.contains("fcg_play_single_song")) return SINGLE;
            if (url.contains("fcg_query_lyric")) return "{\"code\":0,\"lyric\":\"" + fakeLrc() + "\"}";
            if (url.contains("fcg_v8_create_playlist")) return PLAYLIST;
            return "{\"code\":0}";
        }
        @Override
        public void download(String url, Path target, Map<String, String> headers) {
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, "FAKE_AUDIO", StandardCharsets.UTF_8);
            } catch (IOException e) { throw new RuntimeException(e); }
        }
        private static String fakeLrc() {
            try {
                String lrc = "[00:01.00]Fake line one\n[00:04.00]Fake line two";
                byte[] raw = lrc.getBytes(StandardCharsets.UTF_8);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) { gz.write(raw); }
                return java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
            } catch (IOException e) { return ""; }
        }
    }
}
