package com.muonium.fabric.ui;

import com.muonium.core.model.PlaybackState;
import com.muonium.core.model.Song;

/**
 * UI 集成契约（阶段 2 待实现）。
 *
 * <p><b>为什么这里只有接口没有实现</b>：
 * 上游 PORTING 文档第 16 行明确要求「不许猜 API」，本提示词也禁止编造
 * Minecraft / Fabric API 类名与方法签名。1.21.11 的
 * {@code HudRenderCallback} / {@code KeyBindingHelper} / {@code DrawContext}
 * 等签名必须在目标客户端真机解包 jar、查 Loom 生成源码后逐项核实，
 * 因此阶段 0/1 只用接口占位，不写一帧渲染代码。
 *
 * <p>实现约束：
 * <ul>
 *   <li>UI 不直接持有音频解码对象，只订阅 EventBus；</li>
 *   <li>键位通过注册表注册，不允许硬编码键吞掉正常输入；</li>
 *   <li>中文、长歌曲名、高 DPI 缩放、无障碍对比度；</li>
 *   <li>后端不支持 pause 时按钮显示「停止」而非「暂停」。</li>
 * </ul>
 */
public interface UiHooks extends AutoCloseable {

    void renderNowPlaying(Song song, PlaybackState state, long estimatedPositionMs);

    void showNotification(String chineseMessage);

    /** @return false 表示该能力在当前后端不可用，调用方必须隐藏对应控件。 */
    boolean supportsPause();

    boolean supportsSeek();

    /** @return 已注册的键位 id 列表，供配置界面展示。 */
    String[] registeredKeyBindingIds();

    @Override
    void close();
}
