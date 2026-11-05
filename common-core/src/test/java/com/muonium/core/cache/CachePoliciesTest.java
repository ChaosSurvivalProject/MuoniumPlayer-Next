package com.muonium.core.cache;

import com.muonium.core.TestPorts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CachePoliciesTest {

    @Test
    void lruEvictsLeastRecentlyUsed() {
        var c = new CachePolicies.LruCache<String, String>(2);
        c.put("a", "1"); c.put("b", "2");
        c.get("a");                 // a 变为最近使用
        c.put("c", "3");
        assertFalse(c.contains("b"));
        assertTrue(c.contains("a"));
        assertTrue(c.contains("c"));
        assertEquals(2, c.size());
    }

    @Test
    void cacheKeyIsDeterministicAndNormalized() {
        assertEquals(CachePolicies.key("Track", "ID"),
                CachePolicies.key("  track ", " id "));
        assertNotEquals(CachePolicies.key("a", "b"), CachePolicies.key("b", "a"));
        assertEquals(64, CachePolicies.key("a").length());
    }

    @Test
    void retryBackoffIsCappedAndRespectsAttemptLimit() {
        var p = CachePolicies.RetryPolicy.defaults(new TestPorts.SeededRandom(1));
        assertTrue(p.delayFor(1) >= 200L);
        assertTrue(p.delayFor(10) <= 5_000L);
        assertTrue(p.shouldRetry(1, true));
        assertFalse(p.shouldRetry(3, true));
        assertFalse(p.shouldRetry(1, false), "不可重试错误立即停止");
    }

    @Test
    void atomicWriteEnvelopeStartsPending() {
        var e = CachePolicies.atomicWriteEnvelope("k", "payload", "sha");
        assertEquals("pending", e.get("phase"));
        assertEquals("sha", e.get("checksum"));
    }
}
