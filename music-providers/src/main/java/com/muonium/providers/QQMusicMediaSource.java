package com.muonium.providers;

import com.muonium.core.json.Json;
import com.muonium.core.lyric.LyricParsers;
import com.muonium.core.model.Lyric;
import com.muonium.core.spi.Ports;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/**
 * QQ 音乐非官方音乐源适配器，实现统一的 {@link Ports.MediaSource}。
 *
 * <p><b>法律边界（务必阅读 docs/qq-music.md）</b>：
 * <ul>
 *   <li>QQ 音乐内容受版权保护，腾讯未提供面向第三方的公开播放 API；本适配器使用社区版非官方接口。</li>
 *   <li>仅供<b>个人使用</b>：使用<b>你自己的</b>账号 Cookie 检索 / 试听你有权访问的内容；
 *       绝不内置任何歌曲 / 封面 / 歌词等版权素材，绝不破解 DRM，绝不批量下载或分享。</li>
 *   <li>Cookie 仅存于本机内存 / 环境变量 / 显式指定的文件，<b>绝不写入仓库、曲库清单或日志</b>。</li>
 *   <li>本项目与腾讯无任何关联，未获官方授权；如腾讯调整接口或签名算法导致不可用，属预期内风险。</li>
 * </ul>
 *
 * <p>所有网络 / 转码均通过注入的 {@link HttpPort} 与 {@link Transcoder}，因此纯 JVM 下可用假实现完整测试。
 */
public final class QQMusicMediaSource implements Ports.MediaSource {

    /** 非官方集成许可声明，随每条资源携带，便于审计与合规。 */
    public static final String LICENSE = "personal-use; unofficial QQ Music integration; "
            + "user-provided credentials; not affiliated with Tencent";

    private static final String SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String PLAYLIST_URL = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_create_playlist.fcg";
    private static final String LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String VKEY_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String COVER_URL = "https://y.gtimg.cn/music/photo_new/T002R300x300M000%s.jpg";
    private static final List<String> PLAY_HOSTS = List.of(
            "https://isure.stream.qqmusic.qq.com/", "https://dl.stream.qqmusic.qq.com/");

    private final HttpPort http;
    private final Transcoder transcoder;
    private final String cookie;
    private final String uin;
    private final Function<String, String> signer;
    private final Path importDir; // 仅 prepareForImport 使用；流式播放模式可为 null

    public QQMusicMediaSource(HttpPort http, Transcoder transcoder, String cookie, String uin) {
        this(http, transcoder, cookie, uin, QQMusicSign::sign, null);
    }

    public QQMusicMediaSource(HttpPort http, Transcoder transcoder, String cookie, String uin,
                              Function<String, String> signer, Path importDir) {
        this.http = http;
        this.transcoder = transcoder;
        this.cookie = cookie;
        this.uin = (uin == null || uin.isBlank()) ? "0" : uin;
        this.signer = signer == null ? QQMusicSign::sign : signer;
        this.importDir = importDir;
    }

    private void requireCredential() {
        if (cookie == null || cookie.isBlank()) {
            throw new ProviderException("QQ_MUSIC_REQUIRES_COOKIE: 需要你自己的 QQ 音乐 Cookie（含 uin/cookie），"
                    + "请见 docs/qq-music.md 的获取步骤；本项目绝不内置任何凭据。");
        }
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Cookie", cookie);
        h.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        h.put("Referer", "https://y.qq.com/");
        return h;
    }

    @Override
    public List<Ports.MediaResource> search(String query, int page) {
        requireCredential();
        int p = Math.max(1, page);
        String url = SEARCH_URL + "?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song"
                + "&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0"
                + "&p=" + p + "&n=20&w=" + enc(query)
                + "&g_tk=0&loginUin=" + enc(uin) + "&hostUin=0&format=json"
                + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0";
        Map<String, Object> root = getJson(url);
        Map<String, Object> data = J.map(J.map(root.get("data")).get("song"));
        List<Object> list = J.list(data.get("list"));
        List<Ports.MediaResource> out = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> s = J.map(o);
            String mid = J.str(s.get("songmid"));
            String name = J.str(s.get("songname"));
            String album = J.str(s.get("albumname"));
            String albumMid = J.str(s.get("albummid"));
            long dur = J.lng(s.get("interval")) * 1000L;
            List<String> artists = new ArrayList<>();
            for (Object sg : J.list(s.get("singer"))) {
                String nm = J.str(J.map(sg).get("name"));
                if (nm != null) artists.add(nm);
            }
            String cover = albumMid == null ? null : String.format(COVER_URL, albumMid);
            out.add(new Ports.MediaResource(mid, name, artists, album, dur,
                    null, cover, null, LICENSE));
        }
        return out;
    }

    @Override
    public Ports.MediaResource resolve(String sourceTrackId) {
        requireCredential();
        String songmid = sourceTrackId;
        String url = playUrl(songmid);
        Ports.MediaResource meta = fetchMeta(songmid);
        String title = meta != null ? meta.title() : null;
        List<String> artists = meta != null ? meta.artists() : List.of();
        String album = meta != null ? meta.album() : null;
        Long dur = meta != null ? meta.durationMs() : null;
        String cover = (meta != null && meta.coverRef() != null)
                ? meta.coverRef() : String.format(COVER_URL, fetchAlbumMid(songmid));
        return new Ports.MediaResource(songmid, title, artists, album, dur,
                url, cover, null, LICENSE);
    }

    /** 取单曲元数据（标题/歌手/专辑/时长/封面），失败返回 null，不影响播放。 */
    private Ports.MediaResource fetchMeta(String songmid) {
        try {
            String url = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?songmid="
                    + enc(songmid) + "&platform=yqq.json&format=json"
                    + "&g_tk=0&loginUin=" + enc(uin) + "&hostUin=0&inCharset=utf8"
                    + "&outCharset=utf-8&notice=0&needNewCode=0";
            Map<String, Object> root = getJson(url);
            List<Object> data = J.list(root.get("data"));
            if (data.isEmpty()) return null;
            Map<String, Object> s = J.map(data.get(0));
            String name = J.str(s.get("songname"));
            String album = J.str(J.map(s.get("album")).get("name"));
            String albumMid = J.str(J.map(s.get("album")).get("mid"));
            long dur = J.lng(s.get("interval")) * 1000L;
            List<String> artists = new ArrayList<>();
            for (Object sg : J.list(s.get("singer"))) {
                String nm = J.str(J.map(sg).get("name"));
                if (nm != null) artists.add(nm);
            }
            String cover = albumMid == null ? null : String.format(COVER_URL, albumMid);
            return new Ports.MediaResource(songmid, name, artists, album, dur, null, cover, null, LICENSE);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<Ports.MediaResource> getPlaylist(String id, int page) {
        requireCredential();
        String url = PLAYLIST_URL + "?type=1&json=1&utf8=1&onlysong=0&new_json=1"
                + "&disstid=" + enc(id) + "&g_tk=0&loginUin=" + enc(uin) + "&hostUin=0"
                + "&format=json&platform=yqq.json&needNewCode=0";
        Map<String, Object> root = getJson(url);
        List<Object> cdlist = J.list(J.map(root.get("cdlist")).get("songlist"));
        if (cdlist.isEmpty()) cdlist = J.list(root.get("cdlist"));
        List<Ports.MediaResource> out = new ArrayList<>();
        for (Object o : cdlist) {
            Map<String, Object> s = J.map(o);
            String mid = J.str(s.get("songmid"));
            String name = J.str(s.get("songname"));
            String album = J.str(s.get("albumname"));
            String albumMid = J.str(s.get("albummid"));
            long dur = J.lng(s.get("interval")) * 1000L;
            List<String> artists = new ArrayList<>();
            for (Object sg : J.list(s.get("singer"))) {
                String nm = J.str(J.map(sg).get("name"));
                if (nm != null) artists.add(nm);
            }
            String cover = albumMid == null ? null : String.format(COVER_URL, albumMid);
            out.add(new Ports.MediaResource(mid, name, artists, album, dur,
                    null, cover, null, LICENSE));
        }
        return out;
    }

    @Override
    public Lyric.Document getLyric(Ports.MediaResource track) {
        requireCredential();
        String songmid = track.sourceTrackId();
        if (songmid == null) return LyricParsers.unsupported(track.title(), null, "no songmid");
        String url = LYRIC_URL + "?songmid=" + enc(songmid) + "&g_tk=0&loginUin=" + enc(uin)
                + "&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0"
                + "&platform=yqq.json&needNewCode=0&nobase64=0&songtype=0";
        Map<String, Object> root = getJson(url);
        String raw = J.str(root.get("lyric"));
        if (raw == null || raw.isBlank()) return LyricParsers.unsupported(track.title(), null, "empty lyric");
        try {
            String text = gunzipBase64(raw);
            return LyricParsers.parse(text, track.sourceTrackId(), track.durationMs());
        } catch (RuntimeException e) {
            throw new ProviderException("LYRIC: 解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getCover(Ports.MediaResource track) {
        return track.coverRef();
    }

    /**
     * 导入准备：解析播放直链 → 下载到 importDir → 转码 OGG → 返回带本地路径的资源。
     * 流式播放（Fabric）不使用此方法；importDir 为 null 时抛错提示。
     */
    @Override
    public Ports.MediaResource prepareForImport(Ports.MediaResource track) {
        requireCredential();
        if (importDir == null) {
            throw new ProviderException("PREPARE_IMPORT: 该实例为流式模式（importDir=null），"
                    + "请改用 import 流程或直接流式播放");
        }
        String mid = track.sourceTrackId();
        if (mid == null) throw new ProviderException("PREPARE_IMPORT: 缺少 songmid");
        String url = track.audioRef() != null ? track.audioRef() : playUrl(mid);
        Path tmp = null;
        try {
            Files.createDirectories(importDir);
            tmp = Files.createTempFile(importDir, "qq-" + sanitize(mid), ".bin");
            http.download(url, tmp, headers());
            Path ogg = transcoder.transcodeToOgg(tmp, importDir, "track_" + sanitize(mid));
            return new Ports.MediaResource(mid, track.title(), track.artists(), track.album(),
                    track.durationMs(), ogg.toAbsolutePath().toString(), track.coverRef(),
                    track.lyric(), LICENSE);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("PREPARE_IMPORT: 下载/转码失败: " + e.getMessage(), e);
        } finally {
            // P0：成功或失败都清理中间 .bin，绝不留半截临时文件（成功后 ogg 已生成，tmp 可删）。
            safeDelete(tmp);
        }
    }

    /** 取播放直链（C400{mid}.m4a + vkey），供 Fabric 流式后端使用。 */
    public String playUrl(String songmid) {
        requireCredential();
        String filename = "C400" + songmid + ".m4a";
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("filename", List.of(filename));
        param.put("guid", guid());
        param.put("songmid", List.of(songmid));
        param.put("songtype", List.of(0));
        param.put("uin", uin);
        param.put("loginflag", 1);
        param.put("platform", "20");
        Map<String, Object> req0 = new LinkedHashMap<>();
        req0.put("module", "vkey.GetVkeyServer");
        req0.put("method", "CgiGetVkey");
        req0.put("param", param);
        Map<String, Object> comm = new LinkedHashMap<>();
        comm.put("uin", uin);
        comm.put("format", "json");
        comm.put("ct", 24);
        comm.put("cv", 0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_0", req0);
        data.put("comm", comm);
        String dataJson = Json.stringify(data);
        String sign = signer.apply(dataJson);
        String url = VKEY_URL + "?-=getplaysongvkey&g_tk=0&loginUin=" + enc(uin)
                + "&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0"
                + "&platform=yqq.json&needNewCode=0&data=" + enc(dataJson)
                + "&sign=" + enc(sign);
        Map<String, Object> root = getJson(url);
        Map<String, Object> respReq0 = J.map(root.get("req_0"));
        Map<String, Object> respData = J.map(respReq0.get("data"));
        List<Object> midurlinfo = J.list(respData.get("midurlinfo"));
        if (midurlinfo.isEmpty()) {
            throw new ProviderException("SIGN/PLAY: 未能获取播放地址（vkey 为空，可能签名失效或需更新密钥，见 docs/qq-music.md）");
        }
        String purl = J.str(J.map(midurlinfo.get(0)).get("purl"));
        if (purl == null || purl.isBlank()) {
            throw new ProviderException("SIGN/PLAY: 未能获取播放地址（vkey 为空，可能签名失效或需更新密钥，见 docs/qq-music.md）");
        }
        for (String host : PLAY_HOSTS) {
            if (purl.startsWith(host)) return purl;
        }
        return PLAY_HOSTS.get(0) + purl;
    }

    /**
     * 流式播放专用：解析直链 → 下载到 dir/baseName.bin → 转码 OGG → 返回本地路径。
     * 与 {@link #prepareForImport} 不同，此处用于不落盘到曲库的即时播放（临时文件由调用方管理）。
     */
    public Path fetchToLocalOgg(String songmid, Path dir, String baseName) {
        requireCredential();
        String url = playUrl(songmid);
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "qq-stream-" + sanitize(songmid), ".bin");
            http.download(url, tmp, headers());
            return transcoder.transcodeToOgg(tmp, dir, baseName);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("FETCH: 下载/转码失败: " + e.getMessage(), e);
        } finally {
            safeDelete(tmp); // 流式播放同样不留中间 .bin
        }
    }

    private String fetchAlbumMid(String songmid) {
        // 仅用于补全封面；失败返回 null，不影响播放
        try {
            String url = SEARCH_URL + "?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song"
                    + "&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0"
                    + "&p=1&n=1&w=" + enc(songmid)
                    + "&g_tk=0&loginUin=" + enc(uin) + "&hostUin=0&format=json"
                    + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0";
            Map<String, Object> data = J.map(getJson(url).get("data"));
            Map<String, Object> song = J.map(data.get("song"));
            List<Object> list = J.list(song.get("list"));
            if (!list.isEmpty()) return J.str(J.map(list.get(0)).get("albummid"));
        } catch (Exception ignored) {
            // 封面补全失败不阻断
        }
        return null;
    }

    private Map<String, Object> getJson(String url) {
        try {
            String text = http.getText(url, headers());
            return Json.parseObject(text);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("PARSE: 无法解析 QQ 音乐响应: " + e.getMessage(), e);
        }
    }

    private static String gunzipBase64(String b64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            try (GZIPInputStream gz = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
                return new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new ProviderException("LYRIC: 解压失败: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }

    private static String guid() {
        // 非加密随机 guid，仅作为请求参数，无安全意义
        return Long.toHexString(System.nanoTime() + 0x1000000000L);
    }

    private static String sanitize(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /** 尽力删除临时文件；清理失败不影响主流程，也不吞掉原始异常。 */
    private static void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        // 无底层资源需释放；凭据不持有引用
    }

    /** 安全访问 JSON 的极小工具。 */
    private static final class J {
        static String str(Object o) { return o == null ? null : String.valueOf(o); }
        static long lng(Object o) { return o instanceof Number n ? n.longValue() : 0L; }
        @SuppressWarnings("unchecked")
        static List<Object> list(Object o) { return o instanceof List<?> l ? (List<Object>) l : List.of(); }
        @SuppressWarnings("unchecked")
        static Map<String, Object> map(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    }
}
