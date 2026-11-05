package com.muonium.core.model;

import com.muonium.core.codec.MapJsonCodec;
import com.muonium.core.json.Json;
import com.muonium.core.playlist.PlaylistStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaCompatibilityTest {

    @Test
    void songRoundTripsThroughJson() {
        var song = new Song(1, "song_01JZ9Q7V3PC8N2M4A5B6C7D8E9", "示例歌曲", "示例歌曲",
                List.of(new Song.ArtistRef("artist_0001", "示例歌手")),
                new Song.AlbumRef("album_0001", "示例专辑"),
                214000L, Song.DurationSource.IMPORT_PROBE,
                new Song.SourceInfo("user_library", "stable-id", "user_provided", "unknown"),
                new Song.Assets(new Song.AssetRef("resource_sound_event", "muonium:track_0001"),
                        new Song.AssetRef("pack_texture", "textures/muonium/covers/track_0001"),
                        new Song.AssetRef("pack_file", "lyrics/track_0001.muoni.json")),
                new Song.Analysis(-14.2, -1.3, new Song.Automix(false, null)),
                new Song.Flags(true, false, false, true), "2026-11-04T00:00:00+08:00");
        String json = new MapJsonCodec().stringify(song.toMap());
        Map<String, Object> round = Json.parseObject(json);
        var back = Song.fromMap(round);
        assertEquals(song.id(), back.id());
        assertEquals(song.durationMs(), back.durationMs());
        assertEquals(Song.DurationSource.IMPORT_PROBE, back.durationSource());
        assertEquals(1, back.artists().size());
        assertEquals("muonium:track_0001", back.assets().audio().ref());
        assertNull(back.analysis().automix().score(), "未知与没有必须可区分：score 保持 null");
    }

    @Test
    void unknownVersusAbsentFieldsRemainDistinguishable() {
        var song = new Song(1, "song_1", "T", "t", List.of(), null, null,
                Song.DurationSource.UNKNOWN, null, null, null, null, null);
        var back = Song.fromMap(Json.parseObject(Json.stringify(song.toMap())));
        assertNull(back.durationMs(), "未知时长保持 null，不能写成 0");
        assertNull(back.album(), "没有专辑保持 null");
        assertEquals("unknown", back.durationSource().wire());
    }

    @Test
    void playlistRevisionAndItemsRoundTrip() {
        var doc = new Playlist.Document(1, "playlist_favorites", "我喜欢",
                List.of(new Playlist.Item("song_1", 1790000000000L)), 12, "2026-11-04T00:00:00+08:00");
        var back = PlaylistStore.importDocument(Json.parseObject(Json.stringify(PlaylistStore.exportDocument(doc))));
        assertEquals(12, back.revision());
        assertEquals(1, back.items().size());
        assertEquals(1790000000000L, back.items().get(0).addedAt());
    }

    @Test
    void playlistMergeDetectsConflict() {
        var base = new Playlist.Document(1, "p", "P", List.of(), 1, null);
        var local = PlaylistStore.addItem(base, "a", 1L);
        var remote = PlaylistStore.addItem(base, "b", 2L);
        assertTrue(PlaylistStore.merge(local, remote, 1).conflict());
        assertFalse(PlaylistStore.merge(local, base, 1).conflict());
    }

    @Test
    void lyricDocumentRoundTrips() {
        var doc = new Lyric.Document(1, "song_1", 214000L,
                List.of(new Lyric.Line(0, 10000L, 13500L, Lyric.Speaker.LEAD,
                        List.of(new Lyric.Word("第一句", 10000L, 12800L)),
                        "第一句", "First lyric line")), "qrc", false);
        var back = Lyric.Document.fromMap(Json.parseObject(Json.stringify(doc.toMap())));
        assertEquals(1, back.lines().size());
        assertEquals(Lyric.Speaker.LEAD, back.lines().get(0).speaker());
        assertEquals("First lyric line", back.lines().get(0).translation());
        assertEquals(10000L, back.lines().get(0).words().get(0).startMs());
    }

    @Test
    void resourceNameRulesRejectChineseAndUpperCase() {
        assertTrue(Song.isValidResourceName("track_0001"));
        assertFalse(Song.isValidResourceName("示例歌曲.mp3"));
        assertFalse(Song.isValidResourceName("Track-01"));
        assertFalse(Song.isValidResourceName(""));
    }
}
