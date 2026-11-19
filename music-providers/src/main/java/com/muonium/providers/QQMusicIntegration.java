package com.muonium.providers;

import com.muonium.core.codec.MapJsonCodec;
import com.muonium.core.spi.Ports;

import java.nio.file.Path;

/**
 * QQ 音乐集成入口（纯 JVM，可单元测试，不含任何 Minecraft / Fabric 渲染代码）。
 *
 * <p>把"音乐源注册表"与"流式播放后端"的构造集中在这里，Fabric / 桌面端只需提供
 * 真实音频后端（阶段 2 接入原生声音系统）与临时目录即可。游戏内播放器不主动发起网络请求；
 * 一切都在用户显式触发（搜索 / 导入 / 点播）时发生。
 *
 * <p>凭据来自调用方显式传入（环境变量 / 配置文件 / 用户输入），本类不读取、不持久化、不回显。
 */
public final class QQMusicIntegration {

    private QQMusicIntegration() {}

    /**
     * 构造流式播放后端：用 QQ 音乐源包裹真实音频后端，实现"解析直链→下载→转码→播放"。
     * 真实音频后端（nativeBackend）在阶段 2 接入 Minecraft 声音系统后注入。
     */
    public static QQMusicAudioBackend streamingBackend(Ports.AudioBackend nativeBackend,
                                                       Path tempDir, String cookie, String uin,
                                                       Ports.MuoniumLogger logger) {
        HttpPort http = new JdkHttpPort();
        Transcoder transcoder = FfmpegTranscoder.create();
        QQMusicMediaSource qq = new QQMusicMediaSource(http, transcoder, cookie, uin);
        return new QQMusicAudioBackend(qq, nativeBackend, tempDir, logger);
    }

    /** 构造统一音乐源注册表：始终含 local；仅当提供了 Cookie 时才注册 qq。 */
    public static MediaSourceRegistry registry(Path libraryDir, String cookie, String uin) {
        MediaSourceRegistry reg = new MediaSourceRegistry();
        reg.register("local", () -> new LocalMediaSource(new MapJsonCodec(), libraryDir));
        if (cookie != null && !cookie.isBlank()) {
            reg.register("qq", () -> new QQMusicMediaSource(
                    new JdkHttpPort(), FfmpegTranscoder.create(), cookie, uin));
        }
        return reg;
    }

    /** 从 Cookie 串里提取 uin（缺省 0）。 */
    public static String uinFromCookie(String cookie) {
        if (cookie == null) return "0";
        int i = cookie.indexOf("uin=");
        if (i < 0) return "0";
        int j = cookie.indexOf(';', i);
        String v = (j < 0 ? cookie.substring(i + 4) : cookie.substring(i + 4, j)).trim();
        return v.isBlank() ? "0" : v;
    }
}
