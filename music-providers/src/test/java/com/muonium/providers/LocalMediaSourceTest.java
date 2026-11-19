package com.muonium.providers;

import com.muonium.core.codec.MapJsonCodec;
import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalMediaSourceTest {

    private Path writeLibrary() throws Exception {
        Path dir = Files.createTempDirectory("local-src-test");
        Song s = new Song(1, "local_1", "晴天", "qing tian",
                List.of(new Song.ArtistRef(null, "周杰伦")),
                new Song.AlbumRef(null, "叶惠美"), 269_000L, Song.DurationSource.PROVIDER_METADATA,
                new Song.SourceInfo("local", null, "local", null),
                new Song.Assets(new Song.AssetRef("audio", "sounds/muonium/track_local_1.ogg"),
                        new Song.AssetRef("cover", "textures/muonium/covers/track_local_1.png"), null),
                null, Song.Flags.DEFAULT, "2026-01-01T00:00:00Z");
        Map<String, Object> doc = new java.util.LinkedHashMap<>();
        doc.put("tracks", List.of(s.toMap()));
        Files.writeString(dir.resolve("library.json"), new MapJsonCodec().stringify(doc), StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    void searchAndResolveWork() throws Exception {
        Path dir = writeLibrary();
        LocalMediaSource src = new LocalMediaSource(new MapJsonCodec(), dir);
        List<Ports.MediaResource> byTitle = src.search("晴天", 1);
        assertEquals(1, byTitle.size());
        assertEquals("local_1", byTitle.get(0).sourceTrackId());
        assertEquals("周杰伦", byTitle.get(0).artists().get(0));
        assertEquals("sounds/muonium/track_local_1.ogg", byTitle.get(0).audioRef());

        List<Ports.MediaResource> byArtist = src.search("周杰伦", 1);
        assertEquals(1, byArtist.size());

        Ports.MediaResource resolved = src.resolve("local_1");
        assertNotNull(resolved);
        assertEquals("晴天", resolved.title());

        assertNull(src.resolve("missing"));
    }

    @Test
    void emptyLibraryIsSafe() {
        LocalMediaSource src = new LocalMediaSource(new MapJsonCodec(), Path.of("no-such-dir"));
        assertTrue(src.search("anything", 1).isEmpty());
    }
}
