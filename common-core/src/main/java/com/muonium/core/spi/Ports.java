package com.muonium.core.spi;

import com.muonium.core.model.Lyric;
import com.muonium.core.model.Song;

import java.util.List;
import java.util.Map;

/**
 * 所有平台能力通过接口注入。common-core 不碰文件、网络、线程、音频设备、时间、随机源。
 * 这样共享核心能被纯 JVM 单元测试完整覆盖，不启动 Minecraft。
 */
public final class Ports {

    private Ports() {}

    public interface Clock {
        /** 单调毫秒，用于歌词游标与状态机；禁止用 Thread.sleep 当时钟。 */
        long nowMs();
        /** 墙钟 ISO-8601，用于 updatedAt。 */
        String isoNow();
    }

    public interface RandomSource {
        long nextLong();
        int nextInt(int boundExclusive);
    }

    public interface MuoniumLogger {
        void info(String tag, String message);
        void warn(String tag, String message);
        void error(String tag, String message, Throwable t);
    }

    /** JSON 编解码由平台提供实现，core 只依赖这个抽象。 */
    public interface JsonCodec {
        String stringify(Map<String, Object> value);
        Map<String, Object> parse(String text);
    }

    /** 键值存储（Fabric: 配置目录；Bedrock: world dynamic properties）。 */
    public interface KeyValueStore {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    public interface AudioCapabilities {
        boolean pauseResume();
        boolean seek();
        boolean playbackPosition();
        boolean completionEvent();
        boolean gapless();
    }

    /**
     * 音频后端。所有方法在调用方的线程语义下执行；后端自己管理解码线程。
     * 返回 false 表示「此后端不支持」，调用方必须降级为停止，不得假装暂停。
     */
    public interface AudioBackend extends AutoCloseable {
        boolean prepare(Song song);
        boolean play(long fromMs);
        /** 不支持时返回 false。 */
        boolean pause();
        boolean resume();
        boolean seek(long positionMs);
        void stop();
        /** 真实播放位置；不支持返回 -1。 */
        long positionMs();
        float volume();
        void setVolume(float v);
        AudioCapabilities capabilities();
        @Override void close();
    }

    /** 统一媒体资源，禁止平台 JSON 渗进播放器 UI。 */
    public record MediaResource(String sourceTrackId, String title, List<String> artists,
                                String album, Long durationMs, String audioRef,
                                String coverRef, Lyric.Document lyric, String license) {}

    /**
     * 音乐来源统一接口。第一阶段只有本地曲库；平台适配器放独立模块，
     * 且只由用户主动触发的本机导入器调用，游戏内播放器不发起平台请求。
     */
    public interface MediaSource extends AutoCloseable {
        List<MediaResource> search(String query, int page);
        MediaResource resolve(String sourceTrackId);
        List<MediaResource> getPlaylist(String id, int page);
        Lyric.Document getLyric(MediaResource track);
        String getCover(MediaResource track);
        MediaResource prepareForImport(MediaResource track);
        @Override void close();
    }
}
