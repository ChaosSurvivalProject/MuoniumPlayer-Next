package com.muonium.core.queue;

import com.muonium.core.TestPorts;
import com.muonium.core.model.Playlist;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayQueueTest {

    private PlayQueue seq(Playlist.RepeatMode repeat) {
        return new PlayQueue(List.of("a", "b", "c"), Playlist.PlayMode.SEQUENCE, repeat,
                1L, new TestPorts.SeededRandom(7), "playlist_favorites");
    }

    @Test
    void sequencePlaysInOrderThenStops() {
        var q = seq(Playlist.RepeatMode.NONE);
        assertEquals("a", q.next());
        assertEquals("b", q.next());
        assertEquals("c", q.next());
        assertNull(q.next(), "队列耗尽应停住，不擅自回到源歌单");
        assertEquals(List.of("a", "b", "c"), q.played());
        assertNull(q.current());
    }

    @Test
    void repeatOneHasPriorityOverListAndDoesNotAdvance() {
        var q = seq(Playlist.RepeatMode.ONE);
        assertEquals("a", q.next());
        assertEquals("a", q.next());
        assertEquals("a", q.next());
        assertTrue(q.played().isEmpty(), "单曲循环不应把当前曲目写入历史");
    }

    @Test
    void repeatListRefillsFromSourcePlaylist() {
        var q = seq(Playlist.RepeatMode.LIST);
        assertEquals("a", q.next());
        assertEquals("b", q.next());
        assertEquals("c", q.next());
        assertEquals("a", q.next(), "列表循环回到源歌单开头");
    }

    @Test
    void previousUsesHistory() {
        var q = seq(Playlist.RepeatMode.NONE);
        q.next(); q.next();
        assertEquals("a", q.previous());
        assertEquals(List.of("b", "c"), q.upcoming(), "当前曲目回到待播队列头部，后续顺序不重排");
    }

    @Test
    void enqueueNextAndRemove() {
        var q = seq(Playlist.RepeatMode.NONE);
        q.enqueueNext("x");
        assertEquals("x", q.next());
        q.enqueue("d");
        assertTrue(q.remove("d"));
        assertFalse(q.remove("missing"));
    }

    @Test
    void duplicateSongIdsAreDeduplicated() {
        var q = new PlayQueue(List.of("a", "a", "b"), Playlist.PlayMode.SEQUENCE,
                Playlist.RepeatMode.NONE, 1L, new TestPorts.SeededRandom(1), null);
        assertEquals("a", q.next());
        assertEquals("b", q.next());
        assertNull(q.next());
    }
}
