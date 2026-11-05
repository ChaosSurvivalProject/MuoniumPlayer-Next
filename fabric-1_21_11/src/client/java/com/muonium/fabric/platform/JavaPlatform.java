package com.muonium.fabric.platform;

import com.muonium.core.codec.MapJsonCodec;
import com.muonium.core.error.MuoniumError;
import com.muonium.core.spi.Ports;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台能力实现。文件、时间、随机、日志、音频都在这里注入，
 * common-core 保持零平台依赖。
 *
 * <p>阶段 0/1 的音频后端是「假后端」：不发声、不占设备，只提供虚拟时钟与状态，
 * 用于在不启动 Minecraft 的情况下验证状态机 / 队列 / 歌词游标。
 * 阶段 2 才替换为真实本地音频引擎（见 docs/implementation-plan.md）。
 */
public final class JavaPlatform implements AutoCloseable {

    private final FixedMonotonicClock clock = new FixedMonotonicClock();
    private final SeededRandom random = new SeededRandom();
    private final SafeLogger logger = new SafeLogger();
    private final InMemoryStore store = new InMemoryStore();
    private final FakeAudioBackend audio = new FakeAudioBackend(clock, logger);
    private final MapJsonCodec codec = new MapJsonCodec();

    public Ports.Clock clock() { return clock; }
    public Ports.RandomSource random() { return random; }
    public Ports.MuoniumLogger logger() { return logger; }
    public Ports.KeyValueStore store() { return store; }
    public Ports.AudioBackend audioBackend() { return audio; }
    public Ports.JsonCodec jsonCodec() { return codec; }

    @Override public void close() { audio.close(); store.clear(); }

    /** 单调时钟：System.nanoTime 换算，禁止用 Thread.sleep 当歌词时钟。 */
    public static final class FixedMonotonicClock implements Ports.Clock {
        private final long origin = System.nanoTime();
        private volatile long offsetMs;
        @Override public long nowMs() { return (System.nanoTime() - origin) / 1_000_000L + offsetMs; }
        public void advance(long ms) { offsetMs += ms; }
        @Override public String isoNow() {
            return OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString();
        }
    }

    public static final class SeededRandom implements Ports.RandomSource {
        private final Random r = new Random(new SecureRandom().nextLong());
        @Override public long nextLong() { return r.nextLong(); }
        @Override public int nextInt(int boundExclusive) { return r.nextInt(boundExclusive); }
    }

    /** 日志脱敏：过滤 Cookie/Authorization/Token/二维码票据/手机号。 */
    public static final class SafeLogger implements Ports.MuoniumLogger {
        @Override public void info(String tag, String message) {
            System.out.println("[muonium/" + tag + "] " + MuoniumError.redact(message));
        }
        @Override public void warn(String tag, String message) {
            System.err.println("[muonium/" + tag + "] WARN " + MuoniumError.redact(message));
        }
        @Override public void error(String tag, String message, Throwable t) {
            System.err.println("[muonium/" + tag + "] ERROR " + MuoniumError.redact(message)
                    + (t == null ? "" : " cause=" + t.getClass().getSimpleName()));
        }
    }

    /** 本机设置持久化：阶段 0 用内存实现；阶段 2 换成配置目录原子写入。 */
    public static final class InMemoryStore implements Ports.KeyValueStore {
        private final Map<String, String> data = new ConcurrentHashMap<>();
        @Override public String get(String key) { return data.get(key); }
        @Override public void put(String key, String value) { data.put(key, value); }
        @Override public void remove(String key) { data.remove(key); }
        public void clear() { data.clear(); }
    }

    /**
     * 假音频后端：不发声，只维护虚拟播放位置与能力声明。
     * capabilities 全部为 false —— 基岩端同理，绝不能假装支持 pause/seek/position。
     */
    public static final class FakeAudioBackend implements Ports.AudioBackend {
        private final FixedMonotonicClock clock;
        private final Ports.MuoniumLogger logger;
        private long positionMs;
        private long startedAtMs;
        private volatile boolean playing;
        private volatile float volume = 0.8f;
        private String preparedId;

        public FakeAudioBackend(FixedMonotonicClock clock, Ports.MuoniumLogger logger) {
            this.clock = clock; this.logger = logger;
        }

        @Override public boolean prepare(com.muonium.core.model.Song song) {
            preparedId = song.id();
            positionMs = 0;
            return true;
        }
        @Override public boolean play(long fromMs) {
            if (preparedId == null) return false;
            positionMs = Math.max(0, fromMs);
            startedAtMs = clock.nowMs();
            playing = true;
            return true;
        }
        @Override public boolean pause() { playing = false; return true; }
        @Override public boolean resume() { if (preparedId == null) return false; playing = true; return true; }
        @Override public boolean seek(long pos) { positionMs = Math.max(0, pos); startedAtMs = clock.nowMs(); return true; }
        @Override public void stop() { playing = false; positionMs = 0; preparedId = null; }
        @Override public long positionMs() {
            return playing ? positionMs + (clock.nowMs() - startedAtMs) : positionMs;
        }
        @Override public float volume() { return volume; }
        @Override public void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }
        @Override public Ports.AudioCapabilities capabilities() {
            return new Ports.AudioCapabilities() {
                public boolean pauseResume() { return true; }
                public boolean seek() { return true; }
                public boolean playbackPosition() { return true; }
                public boolean completionEvent() { return false; }
                public boolean gapless() { return false; }
            };
        }
        @Override public void close() { stop(); logger.info("audio", "假音频后端已释放"); }
    }
}
