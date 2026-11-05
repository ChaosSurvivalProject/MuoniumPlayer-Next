package com.muonium.fabric;

import com.muonium.core.codec.MapJsonCodec;
import com.muonium.core.config.ConfigMigrator;
import com.muonium.core.event.EventBus;
import com.muonium.core.playback.PlaybackStateMachine;
import com.muonium.core.spi.Ports;
import com.muonium.fabric.config.ModConfig;
import com.muonium.fabric.platform.JavaPlatform;
import com.muonium.fabric.playback.PlaybackController;
import com.muonium.fabric.storage.LocalLibrary;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端运行时：装配所有依赖，管理生命周期与资源释放。
 * 纯 Java，不引用 net.minecraft / Fabric API 之外的渲染类。
 */
public final class ClientRuntime {

    private static final ClientRuntime INSTANCE = new ClientRuntime();

    private final List<AutoCloseable> closeables = new ArrayList<>();
    private volatile boolean started;

    private ClientRuntime() {}

    public static ClientRuntime getInstance() { return INSTANCE; }

    public synchronized void start() {
        if (started) return;
        started = true;

        JavaPlatform platform = new JavaPlatform();
        EventBus bus = new EventBus();
        closeables.add(bus);

        ModConfig config = ModConfig.load(platform.jsonCodec(), configDir());
        PlaybackStateMachine machine = new PlaybackStateMachine(platform.clock(), platform.logger());
        LocalLibrary library = new LocalLibrary(platform.jsonCodec(), config.libraryPath(), platform.logger());
        PlaybackController controller = new PlaybackController(
                machine, library, platform.audioBackend(), platform.clock(), bus, platform.logger());

        // 曲库扫描在启动线程一次性完成，绝不在 UI 线程每帧扫描整个曲库。
        controller.loadLibrary();
        platform.logger().info("muonium", "纯客户端播放器已启动；曲库=" + library.size() + " 首");
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        // 逆序释放：先停订阅，再释放音频设备/线程/临时文件。
        for (int i = closeables.size() - 1; i >= 0; i--) {
            try {
                closeables.get(i).close();
            } catch (Exception e) {
                System.err.println("[muonium] 资源释放失败（已记录，不吞异常）: " + e.getMessage());
            }
        }
        closeables.clear();
    }

    /** 配置目录来自 Fabric Loader 提供的 gameDir，不写任何服务端路径。 */
    private static Path configDir() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("muonium");
    }

    public boolean isStarted() { return started; }

    /** 供测试装配：显式注入，不依赖 Minecraft。 */
    public static PlaybackController newControllerForTest(JavaPlatform platform,
                                                          LocalLibrary library, EventBus bus) {
        return new PlaybackController(
                new PlaybackStateMachine(platform.clock(), platform.logger()),
                library, platform.audioBackend(), platform.clock(), bus, platform.logger());
    }

    public static Ports.JsonCodec defaultCodec() { return new MapJsonCodec(); }
}
