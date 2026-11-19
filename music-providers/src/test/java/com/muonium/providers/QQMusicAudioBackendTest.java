package com.muonium.providers;

import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QQMusicAudioBackendTest {

    private static final String COOKIE = "uin=12345; qm_key=FAKE";

    private Song qqSong() {
        return new Song(1, "qq_mid_abc", "测试歌曲", null, List.of(new Song.ArtistRef(null, "歌手")),
                null, 222_000L, Song.DurationSource.PROVIDER_METADATA,
                new Song.SourceInfo("qq", "mid_abc", QQMusicMediaSource.LICENSE, null),
                new Song.Assets(new Song.AssetRef("audio", "PLACEHOLDER"), null, null),
                null, Song.Flags.DEFAULT, "1970-01-01T00:00:00Z");
    }

    @Test
    void orchestratesResolveDownloadTranscodeAndDelegates() throws Exception {
        Path dir = Files.createTempDirectory("qq-backend-test");
        Fakes.FakeHttpPort http = new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture());
        Fakes.FakeTranscoder transcoder = new Fakes.FakeTranscoder();
        QQMusicMediaSource qq = new QQMusicMediaSource(http, transcoder, COOKIE, "12345", s -> "t", dir);
        Fakes.FakeAudioBackend inner = new Fakes.FakeAudioBackend();
        QQMusicAudioBackend backend = new QQMusicAudioBackend(qq, inner, dir, null);

        assertTrue(backend.prepare(qqSong()));
        assertTrue(inner.prepared);
        // 内层后端拿到的应是本地 OGG 路径，而非原始占位符
        assertTrue(inner.preparedAudioRef != null && inner.preparedAudioRef.endsWith(".ogg"), inner.preparedAudioRef);

        backend.play(0);
        assertTrue(inner.played);
        backend.pause();
        backend.seek(5000);
        assertTrue(inner.seeked);
        assertEquals(5000, inner.lastSeekMs);
    }

    @Test
    void refusesNonQqSong() {
        QQMusicAudioBackend backend = new QQMusicAudioBackend(
                new QQMusicMediaSource(new Fakes.FakeHttpPort(), new Fakes.FakeTranscoder(), COOKIE, "12345", s -> "t", null),
                new Fakes.FakeAudioBackend(), Path.of("."), null);
        Song local = new Song(1, "local_1", "x", null, List.of(), null, 1000L, Song.DurationSource.UNKNOWN,
                null, null, null, Song.Flags.DEFAULT, null);
        assertFalse(backend.prepare(local));
    }

    @Test
    void streamingCapabilitiesMarkCompletionAndGaplessUnknown() throws Exception {
        Path dir = Files.createTempDirectory("qq-cap-test");
        QQMusicMediaSource qq = new QQMusicMediaSource(
                new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture()),
                new Fakes.FakeTranscoder(), COOKIE, "12345", s -> "t", dir);
        QQMusicAudioBackend backend = new QQMusicAudioBackend(qq, new Fakes.FakeAudioBackend(), dir, null);
        Ports.AudioCapabilities c = backend.capabilities();
        assertTrue(c.pauseResume());   // 透传内层（需真机验证）
        assertTrue(c.seek());
        assertTrue(c.playbackPosition());
        assertFalse(c.completionEvent(), "流式下完成事件为 UNKNOWN，必须 false");
        assertFalse(c.gapless(), "流式下无缝为 UNKNOWN，必须 false");
    }
}
