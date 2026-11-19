package com.muonium.providers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** 测试用假实现：不联网、不触碰任何真实版权内容。 */
public final class Fakes {

    private Fakes() {}

    /** 按 URL 子串匹配返回固定响应体的假 HTTP 端口。 */
    public static final class FakeHttpPort implements HttpPort {
        private final Map<String, String> bodies = new LinkedHashMap<>();
        private String lastDownloadUrl;
        private Path lastDownloadTarget;

        public FakeHttpPort with(String urlSubstring, String body) {
            bodies.put(urlSubstring, body);
            return this;
        }

        public String lastDownloadUrl() { return lastDownloadUrl; }
        public Path lastDownloadTarget() { return lastDownloadTarget; }

        @Override
        public String getText(String url, Map<String, String> headers) throws ProviderException {
            for (Map.Entry<String, String> e : bodies.entrySet()) {
                if (url.contains(e.getKey())) return e.getValue();
            }
            throw new ProviderException("NETWORK: 无匹配 fixture: " + url);
        }

        @Override
        public void download(String url, Path target, Map<String, String> headers) throws ProviderException {
            this.lastDownloadUrl = url;
            this.lastDownloadTarget = target;
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, "FAKE_AUDIO_BYTES", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ProviderException("download failed", e);
            }
        }
    }

    /** 假转码器：把输入复制到 outDir/baseName.ogg（不调用 ffmpeg）。 */
    public static final class FakeTranscoder implements Transcoder {
        public Path produced;
        @Override
        public Path transcodeToOgg(Path in, Path outDir, String baseName) throws ProviderException {
            try {
                Files.createDirectories(outDir);
                Path out = outDir.resolve(baseName + ".ogg");
                Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                this.produced = out;
                return out;
            } catch (IOException e) {
                throw new ProviderException("transcode failed", e);
            }
        }
    }

    /** 把明文 LRC 压缩为 QQ 歌词接口返回的 base64(gzip) 形式。 */
    public static String gzipBase64Lrc(String lrc) throws IOException {
        byte[] raw = lrc.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(raw); }
        return java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    public static String searchFixture() {
        return """
                {"code":0,"data":{"song":{"list":[
                  {"songmid":"mid_abc","songname":"测试歌曲","singer":[{"name":"歌手A"},{"name":"歌手B"}],
                   "albumname":"测试专辑","albummid":"album_xyz","interval":222},
                  {"songmid":"mid_def","songname":"另一首","singer":[{"name":"歌手C"}],
                   "albumname":"专辑二","albummid":"album_qwe","interval":180}
                ]}}}
                """;
    }

    public static String playlistFixture() {
        return """
                {"code":0,"cdlist":[
                  {"songmid":"mid_pl1","songname":"歌单曲1","singer":[{"name":"歌手D"}],
                   "albumname":"专辑P","albummid":"album_p1","interval":200},
                  {"songmid":"mid_pl2","songname":"歌单曲2","singer":[{"name":"歌手E"}],
                   "albumname":"专辑P","albummid":"album_p2","interval":210}
                ]}
                """;
    }

    public static String vkeyFixture() {
        return """
                {"code":0,"req_0":{"code":0,"data":{
                  "midurlinfo":[{"purl":"C400mid_abc.m4a?vkey=TESTKEY123"}]}}}
                """;
    }

    /** 记录调用、全程可用的假音频后端（用于验证流式编排）。 */
    public static final class FakeAudioBackend implements com.muonium.core.spi.Ports.AudioBackend {
        public boolean prepared, played, paused, resumed, seeked, stopped, closed;
        public long lastSeekMs;
        public String preparedAudioRef;
        public boolean rejectPrepare;
        @Override public boolean prepare(com.muonium.core.model.Song song) {
            prepared = true; preparedAudioRef = song.assets() == null || song.assets().audio() == null ? null : song.assets().audio().ref();
            return !rejectPrepare;
        }
        @Override public boolean play(long fromMs) { played = true; return true; }
        @Override public boolean pause() { paused = true; return true; }
        @Override public boolean resume() { resumed = true; return true; }
        @Override public boolean seek(long positionMs) { seeked = true; lastSeekMs = positionMs; return true; }
        @Override public void stop() { stopped = true; }
        @Override public long positionMs() { return 0; }
        @Override public float volume() { return 0.8f; }
        @Override public void setVolume(float v) {}
        @Override public com.muonium.core.spi.Ports.AudioCapabilities capabilities() {
            return new com.muonium.core.spi.Ports.AudioCapabilities() {
                public boolean pauseResume() { return true; }
                public boolean seek() { return true; }
                public boolean playbackPosition() { return true; }
                public boolean completionEvent() { return true; }
                public boolean gapless() { return true; }
            };
        }
        @Override public void close() { closed = true; }
    }
}
