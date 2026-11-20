package com.muonium.fabric;

import net.fabricmc.api.ClientModInitializer;
import com.muonium.fabric.autoupdate.ClientUpdateChecker;

/**
 * 唯一的 Minecraft 生命周期入口。
 *
 * <p>只使用 Fabric Loader 官方稳定 API {@link ClientModInitializer}：
 * 本模块不注册服务端命令、不发送自定义网络包、不读取服务端状态。
 * 其余代码全部是纯 Java 逻辑（common-core + 平台适配），
 * 因此可以在不启动 Minecraft 的情况下被单元测试覆盖。
 *
 * <p>HUD / 键位 / 屏幕属于阶段 2，需在目标 1.21.11 客户端真机核实
 * Fabric API 类名与方法签名后再实现，见 {@code ui/UiHooks.java} 与 docs/implementation-plan.md。
 * 本项目不凭记忆写 Minecraft/Fabric API（上游 PORTING 文档第 16 行亦明确要求）。
 */
public final class MuoniumClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientRuntime runtime = ClientRuntime.getInstance();
        runtime.start();
        // 内置更新：启动自检一次 + 每周四 12:00 定时检查（不改运行中的 jar，重启生效）。
        try {
            ClientUpdateChecker.fromMods(
                    "https://raw.githubusercontent.com/ChaosSurvivalProject/MuoniumPlayer-Next/main/updates/latest.json",
                    "0.1.0").start();
        } catch (Throwable t) {
            System.out.println("[Muonium] 更新模块初始化失败（不影响主功能）：" + t.getMessage());
        }

        // 退出游戏时释放音频设备、线程与临时文件；不留下后台进程。
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop, "muonium-shutdown"));
    }
}
