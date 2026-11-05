package com.muonium.core.queue;

import com.muonium.core.TestPorts;
import com.muonium.core.model.Playlist;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ShuffleDeterminismTest {

    private static PlayQueue shuffled(long seed) {
        return new PlayQueue(List.of("a", "b", "c", "d", "e", "f", "g", "h"),
                Playlist.PlayMode.SHUFFLE, Playlist.RepeatMode.NONE,
                seed, new TestPorts.SeededRandom(99), null);
    }

    @Test
    void sameSeedProducesSameOrder() {
        assertEquals(drain(shuffled(42L)), drain(shuffled(42L)));
        assertNotEquals(drain(shuffled(42L)), drain(shuffled(43L)));
    }

    @Test
    void snapshotRestoreDoesNotReshuffle() {
        var q = shuffled(42L);
        q.next(); q.next();
        var snap = q.snapshot();
        List<String> before = q.upcoming();

        var restored = PlayQueue.restore(snap, new TestPorts.SeededRandom(1));
        assertEquals(before, restored.upcoming(), "刷新 UI 不得重排队列");
        assertEquals(q.current(), restored.current());
        assertEquals(before.get(0), restored.next());
        assertEquals(snap.shuffleSeed(), restored.shuffleSeed());
    }

    @Test
    void snapshotRoundTripsThroughMap() {
        var q = shuffled(7L);
        q.next();
        var map = q.snapshot().toMap();
        var snap = Playlist.QueueSnapshot.fromMap(map);
        assertEquals(q.snapshot().toMap(), snap.toMap());
        assertEquals("shuffle", snap.mode().wire());
    }

    @Test
    void switchingModeKeepsCurrentTrack() {
        var q = shuffled(1L);
        String current = q.next();
        q.setMode(Playlist.PlayMode.SEQUENCE);
        assertEquals(current, q.current());
        q.setMode(Playlist.PlayMode.SHUFFLE);
        assertEquals(current, q.current());
    }

    @Test
    void concurrentEnqueueAndRemoveAreSafe() throws Exception {
        var q = shuffled(3L);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            final int id = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 200; i++) {
                    q.enqueue("x" + id + "_" + i);
                    q.remove("x" + id + "_" + (i - 1));
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertNotNull(q.snapshot());
        assertTrue(q.upcoming().size() >= 8, "并发操作不应丢失基础队列");
    }

    private static List<String> drain(PlayQueue q) {
        List<String> out = new ArrayList<>();
        String s;
        while ((s = q.next()) != null) out.add(s);
        return out;
    }
}
