package com.muonium.core.cache;

import com.muonium.core.spi.Ports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 缓存键、LRU 淘汰与重试退避。纯逻辑，不做任何文件/网络 I/O。 */
public final class CachePolicies {

    private CachePolicies() {}

    /** 确定性缓存键：对规范化输入做 SHA-256，避免把临时 URL 当永久键。 */
    public static String key(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p == null ? "<null>" : p.trim().toLowerCase(java.util.Locale.ROOT)).append('|');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 有界 LRU：超出容量时淘汰最久未使用项。 */
    public static final class LruCache<K, V> {
        private final int maxSize;
        private final LinkedHashMap<K, V> map;

        public LruCache(int maxSize) {
            if (maxSize < 1) throw new IllegalArgumentException("maxSize must be >= 1");
            this.maxSize = maxSize;
            this.map = new LinkedHashMap<>(16, 0.75f, true);
        }

        public synchronized V get(K key) { return map.get(key); }
        public synchronized void put(K key, V value) {
            map.put(key, value);
            while (map.size() > maxSize) {
                K eldest = map.keySet().iterator().next();
                map.remove(eldest);
            }
        }
        public synchronized V remove(K key) { return map.remove(key); }
        public synchronized int size() { return map.size(); }
        public synchronized boolean contains(K key) { return map.containsKey(key); }
        public synchronized void clear() { map.clear(); }
    }

    /** 指数退避 + 抖动 + 重试上限；取消与失败分开，由调用方决定是否重试。 */
    public static final class RetryPolicy {
        private final int maxAttempts;
        private final long baseDelayMs;
        private final long maxDelayMs;
        private final double multiplier;
        private final Ports.RandomSource random;

        public RetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs,
                           double multiplier, Ports.RandomSource random) {
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            this.maxAttempts = maxAttempts;
            this.baseDelayMs = baseDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.multiplier = multiplier;
            this.random = random;
        }

        public static RetryPolicy defaults(Ports.RandomSource random) {
            return new RetryPolicy(3, 200L, 5_000L, 2.0, random);
        }

        /** @param attempt 已失败次数，从 1 开始 */
        public long delayFor(int attempt) {
            double raw = baseDelayMs * Math.pow(multiplier, Math.max(0, attempt - 1));
            long capped = (long) Math.min(maxDelayMs, raw);
            long jitter = random == null ? 0 : Math.floorMod(random.nextLong(), Math.max(1, capped / 4));
            return Math.min(maxDelayMs, capped + jitter);
        }

        public boolean shouldRetry(int attempt, boolean retryable) {
            return retryable && attempt < maxAttempts;
        }

        public int maxAttempts() { return maxAttempts; }
    }

    /** 原子写入语义由平台实现；core 只定义两阶段提交键。 */
    public static Map<String, Object> atomicWriteEnvelope(String key, String payload, String checksum) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("payload", payload);
        m.put("checksum", checksum);
        m.put("phase", "pending");
        return m;
    }
}
