package com.muonium.providers;

import com.muonium.core.model.Lyric;
import com.muonium.core.spi.Ports;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QQMusicMediaSourceTest {

    private static final String COOKIE = "uin=12345; qm_key=FAKE";

    private QQMusicMediaSource source(Fakes.FakeHttpPort http) {
        return new QQMusicMediaSource(http, new Fakes.FakeTranscoder(),
                COOKIE, "12345", s -> "testsig", null);
    }

    @Test
    void searchParsesList() {
        Fakes.FakeHttpPort http = new Fakes.FakeHttpPort().with("client_search_cp", Fakes.searchFixture());
        List<Ports.MediaResource> r = source(http).search("测试", 1);
        assertEquals(2, r.size());
        assertEquals("mid_abc", r.get(0).sourceTrackId());
        assertEquals("测试歌曲", r.get(0).title());
        assertEquals(List.of("歌手A", "歌手B"), r.get(0).artists());
        assertEquals("测试专辑", r.get(0).album());
        assertEquals(222_000L, r.get(0).durationMs());
        assertNull(r.get(0).audioRef(), "搜索阶段不应带直链");
        assertTrue(r.get(0).license().startsWith("personal-use"));
    }

    @Test
    void resolveReturnsPlayUrl() {
        Fakes.FakeHttpPort http = new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture());
        Ports.MediaResource r = source(http).resolve("mid_abc");
        assertNotNull(r.audioRef());
        assertTrue(r.audioRef().contains("C400mid_abc.m4a?vkey=TESTKEY123"), r.audioRef());
    }

    @Test
    void getPlaylistParsesCdlist() {
        Fakes.FakeHttpPort http = new Fakes.FakeHttpPort().with("fcg_v8_create_playlist", Fakes.playlistFixture());
        List<Ports.MediaResource> r = source(http).getPlaylist("diss_1", 1);
        assertEquals(2, r.size());
        assertEquals("mid_pl1", r.get(0).sourceTrackId());
        assertEquals("歌单曲1", r.get(0).title());
    }

    @Test
    void getLyricDecodesGzipBase64() throws Exception {
        String lrc = "[00:01.00]第一行\n[00:04.50]第二行";
        String b64 = Fakes.gzipBase64Lrc(lrc);
        Fakes.FakeHttpPort http = new Fakes.FakeHttpPort().with("fcg_query_lyric", "{\"code\":0,\"lyric\":\"" + b64 + "\"}");
        Ports.MediaResource track = new Ports.MediaResource("mid_abc", "测试歌曲", List.of(), null, 6000L, null, null, null, "personal-use");
        Lyric.Document doc = source(http).getLyric(track);
        assertNotNull(doc);
        assertEquals(2, doc.lines().size());
        assertEquals("第一行", doc.lines().get(0).text());
        assertEquals(1000L, doc.lines().get(0).startMs());
        assertEquals(4500L, doc.lines().get(1).startMs());
    }

    @Test
    void prepareForImportDownloadsAndTranscodes() throws Exception {
        Path dir = Files.createTempDirectory("qq-import-test");
        Fakes.FakeTranscoder transcoder = new Fakes.FakeTranscoder();
        Fakes.FakeHttpPort http = new Fakes.FakeHttpPort().with("musicu.fcg", Fakes.vkeyFixture());
        QQMusicMediaSource src = new QQMusicMediaSource(http, transcoder, COOKIE, "12345", s -> "testsig", dir);
        Ports.MediaResource track = new Ports.MediaResource("mid_abc", "测试歌曲", List.of(), null, 6000L, null, null, null, "personal-use");
        Ports.MediaResource imported = src.prepareForImport(track);
        assertNotNull(imported.audioRef());
        assertTrue(imported.audioRef().endsWith(".ogg"), imported.audioRef());
        assertNotNull(transcoder.produced);
        assertTrue(transcoder.produced.toString().endsWith(".ogg"));
        assertNotNull(http.lastDownloadUrl());
        assertTrue(http.lastDownloadUrl().contains("vkey=TESTKEY123"));
    }

    @Test
    void requiresCookie() {
        QQMusicMediaSource noCred = new QQMusicMediaSource(new Fakes.FakeHttpPort(), new Fakes.FakeTranscoder(),
                null, "0", s -> "x", null);
        ProviderException ex = assertThrows(ProviderException.class, () -> noCred.search("x", 1));
        assertTrue(ex.getMessage().contains("COOKIE"));
    }
}
