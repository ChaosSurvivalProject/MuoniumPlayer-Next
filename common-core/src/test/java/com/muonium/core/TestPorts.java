package com.muonium.core;

import com.muonium.core.spi.Ports;

import java.util.Random;

/** 测试用的注入式实现：固定时钟、可复现随机、静默日志。 */
public final class TestPorts {

    private TestPorts() {}

    public static final class FixedClock implements Ports.Clock {
        private long now;
        public FixedClock(long start) { this.now = start; }
        public void advance(long ms) { now += ms; }
        @Override public long nowMs() { return now; }
        @Override public String isoNow() { return "2026-11-04T00:00:00+08:00"; }
    }

    public static final class SeededRandom implements Ports.RandomSource {
        private final Random r;
        public SeededRandom(long seed) { this.r = new Random(seed); }
        @Override public long nextLong() { return r.nextLong(); }
        @Override public int nextInt(int boundExclusive) { return r.nextInt(boundExclusive); }
    }

    public static final class SilentLogger implements Ports.MuoniumLogger {
        @Override public void info(String tag, String message) {}
        @Override public void warn(String tag, String message) {}
        @Override public void error(String tag, String message, Throwable t) {}
    }
}
