package com.muonium.providers;

import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 降级可读性 + 临时文件清理测试（提示词 P0）。
 * 全部使用合成 fixture / Fake，不联网、不触碰任何真实版权内容。
 */
class DegradationTest {

    private static final String COOKIE = "uin=12345; qm_key=FAKE";

    // ---- HTTP 状态码分类 ----

    @Test
    void authFailureIsNotRetryable() {
        ProviderException e = JdkHttpPort.httpStatusError(403, "https://c.y.qq.com/x?cookie=SECRET");
        assertEquals(ProviderException.AUTH_EXPIRED, e.code());
        assertFalse(e.retryable());
        assertTrue(e.getMessage().contains("Cookie"));
        assertFalse(e.getMessage().contains("SECRET"), "错误消息不得回显查询串/Cookie");
    }

    @Test
    void rateLimitAndUpstreamAreRetryable() {
        ProviderException rl = JdkHttpPort.httpStatusError(429, "https://c.y.qq.com/x");
        assertEquals(ProviderException.RATE_LIMITED, rl.code());
        assertTrue(rl.retryable());

        ProviderException up = JdkHttpPort.httpStatusError(503, "https://c.y.qq.com/x");
        assertEquals(ProviderException.UPSTREAM, up.code());
        assertTrue(up.retryable());
    }

    @Test
    void other4xxIsNetworkRetryable() {
        ProviderException e = JdkHttpPort.httpStatusError(404, "https://c.y.qq.com/x");
        assertEquals(ProviderException.NETWORK, e.code());
        assertTrue(e.retryable());
    }

    @Test
    void signFailureIsNotRetryable() {
        ProviderException e = new ProviderException("SIGN/PLAY: 未能获取播放地址", null, ProviderException.SIGN);
        assertEquals(ProviderException.SIGN, e.code());
        assertFalse(e.retryable());
    }

    // ---- 临时文件清理 ----

    private static long binCount(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".bin")).count();
        }
    }

    @Test
    void streamFetchCleansTempOnTranscodeFailure() throws Exception {
        Path dir = Files.createTempDirectory("qq-clean-stream");
        Transcoder throwing = (in, outDir, base) -> { throw new ProviderException("TRANSCODE: fake failure"); };
        QQMusicMediaSource qq = new QQMusicMediaSource(
                new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture()),
                throwing, COOKIE, "12345", s -> "t", dir);

        assertThrows(ProviderException.class, () -> qq.fetchToLocalOgg("mid_abc", dir, "track_mid_abc"));
        assertEquals(0L, binCount(dir), "转码失败后不得残留中间 .bin");

        // 成功路径同样不留 .bin
        QQMusicMediaSource ok = new QQMusicMediaSource(
                new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture()),
                new Fakes.FakeTranscoder(), COOKIE, "12345", s -> "t", dir);
        Path ogg = ok.fetchToLocalOgg("mid_abc", dir, "track_ok");
        assertTrue(Files.isRegularFile(ogg));
        assertEquals(0L, binCount(dir), "成功后也不得残留中间 .bin");
    }

    @Test
    void prepareForImportCleansTempOnDownloadFailure() throws Exception {
        Path dir = Files.createTempDirectory("qq-clean-import");
        HttpPort failing = new HttpPort() {
            @Override public String getText(String url, java.util.Map<String, String> headers) {
                return Fakes.vkeyFixture();
            }
            @Override public void download(String url, Path target, java.util.Map<String, String> headers) {
                throw new ProviderException("HTTP 403 for c.y.qq.com: 凭据失效", null, ProviderException.AUTH_EXPIRED);
            }
        };
        QQMusicMediaSource qq = new QQMusicMediaSource(failing, new Fakes.FakeTranscoder(), COOKIE, "12345", s -> "t", dir);
        Ports.MediaResource track = new Ports.MediaResource("mid_abc", "t", List.of(), null, 1000L, null, null, null, "personal-use");
        ProviderException ex = assertThrows(ProviderException.class, () -> qq.prepareForImport(track));
        assertEquals(ProviderException.AUTH_EXPIRED, ex.code());
        assertFalse(ex.retryable());
        assertEquals(0L, binCount(dir), "下载失败后不得残留中间 .bin");
    }

    @Test
    void backendCleansTempWhenInnerRejects() throws Exception {
        Path dir = Files.createTempDirectory("qq-clean-backend");
        QQMusicMediaSource qq = new QQMusicMediaSource(
                new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture()),
                new Fakes.FakeTranscoder(), COOKIE, "12345", s -> "t", dir);
        Fakes.FakeAudioBackend rejecting = new Fakes.FakeAudioBackend();
        rejecting.rejectPrepare = true;
        QQMusicAudioBackend backend = new QQMusicAudioBackend(qq, rejecting, dir, null);
        Song song = new Song(1, "qq_mid_abc", "t", null, List.of(), null, 1000L,
                Song.DurationSource.UNKNOWN,
                new Song.SourceInfo("qq", "mid_abc", QQMusicMediaSource.LICENSE, null),
                new Song.Assets(new Song.AssetRef("audio", "x"), null, null),
                null, Song.Flags.DEFAULT, "1970-01-01T00:00:00Z");

        assertFalse(backend.prepare(song));
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(0L, s.filter(p -> p.getFileName().toString().endsWith(".ogg")).count(),
                    "真实后端拒绝后不得残留临时 OGG");
        }
    }
}
