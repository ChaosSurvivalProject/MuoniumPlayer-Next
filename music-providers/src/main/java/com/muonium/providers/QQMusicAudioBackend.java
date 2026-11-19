package com.muonium.providers;

import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QQ 音乐流式播放后端：实现统一的 {@link Ports.AudioBackend}，
 * 把"解析直链 → 下载 → 转码 OGG → 委托真实音频后端播放"串起来。
 *
 * <p>本类只做<b>编排</b>，不含任何 Minecraft / Fabric 渲染代码，因此可在纯 JVM 下用假后端完整测试。
 * 最终的"真实发声"由注入的 {@code inner} 后端负责（阶段 2 接入原生 Minecraft 声音系统）。
 *
 * <p><b>能力声明纪律</b>：按项目规范，未经验证的项一律标 false。
 * 流式播放下 {@code completionEvent} 与 {@code gapless} 为 UNKNOWN（永远 false）；
 * {@code pauseResume}/{@code seek}/{@code playbackPosition} 透传自 inner 后端，但同样需真机验证，
 * 未验证前请按"不支持"处理（UI 据此隐藏对应控件）。
 */
public final class QQMusicAudioBackend implements Ports.AudioBackend {

    private final Ports.MediaSource qq;
    private final Ports.AudioBackend inner;
    private final Path tempDir;
    private final Ports.MuoniumLogger logger;

    private Path tempFile;
    private Song prepared;

    public QQMusicAudioBackend(Ports.MediaSource qq, Ports.AudioBackend inner,
                              Path tempDir, Ports.MuoniumLogger logger) {
        this.qq = qq;
        this.inner = inner;
        this.tempDir = tempDir;
        this.logger = logger;
    }

    @Override
    public boolean prepare(Song song) {
        if (song == null || song.source() == null || !"qq".equals(song.source().provider())) {
            return false;
        }
        if (!(qq instanceof QQMusicMediaSource q)) return false;
        String mid = song.source().providerTrackId();
        if (mid == null) return false;
        try {
            Path ogg = q.fetchToLocalOgg(mid, tempDir, "track_" + sanitize(mid));
            this.tempFile = ogg;
            this.prepared = withLocalAudio(song, ogg.toAbsolutePath().toString());
            if (!inner.prepare(this.prepared)) {
                // P0：真实后端拒绝时清理已下载的临时 OGG，不留残留。
                deleteTemp();
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // P0：下载/转码/装配任一环节失败都清理临时文件。
            deleteTemp();
            if (logger != null) logger.warn("qq-audio", "准备流式播放失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean play(long fromMs) {
        return inner.play(fromMs);
    }

    @Override
    public boolean pause() { return inner.pause(); }

    @Override
    public boolean resume() { return inner.resume(); }

    @Override
    public boolean seek(long positionMs) { return inner.seek(positionMs); }

    @Override
    public void stop() {
        inner.stop();
        deleteTemp();
    }

    @Override
    public long positionMs() { return inner.positionMs(); }

    @Override
    public float volume() { return inner.volume(); }

    @Override
    public void setVolume(float v) { inner.setVolume(v); }

    @Override
    public Ports.AudioCapabilities capabilities() {
        Ports.AudioCapabilities c = inner.capabilities();
        return new Ports.AudioCapabilities() {
            public boolean pauseResume() { return c.pauseResume(); }
            public boolean seek() { return c.seek(); }
            public boolean playbackPosition() { return c.playbackPosition(); }
            public boolean completionEvent() { return false; } // UNKNOWN：流式下完成事件语义未验证
            public boolean gapless() { return false; }          // UNKNOWN：流式下无法保证无缝
        };
    }

    @Override
    public void close() {
        stop();
    }

    private void deleteTemp() {
        if (tempFile != null) {
            try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            tempFile = null;
        }
    }

    private static Song withLocalAudio(Song s, String path) {
        Map<String, Object> m = s.toMap();
        Object raw = m.get("assets");
        if (raw instanceof Map<?, ?> rm) {
            Map<String, Object> a = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rm.entrySet()) a.put(String.valueOf(e.getKey()), e.getValue());
            Object ra = a.get("audio");
            if (ra instanceof Map<?, ?> ram) {
                Map<String, Object> audio = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : ram.entrySet()) audio.put(String.valueOf(e.getKey()), e.getValue());
                audio.put("ref", path);
                a.put("audio", audio);
            }
            m.put("assets", a);
        }
        return Song.fromMap(m);
    }

    private static String sanitize(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
