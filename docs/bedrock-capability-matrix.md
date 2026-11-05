# 基岩能力矩阵

**规则**：阶段 4 真机验证前不得填「支持」。结论只能是
`SUPPORTED`（目标版本真机通过）/ `LIMITED`（可用但有条件）/
`EXPERIMENTAL`（实验开关可用）/ `UNSUPPORTED`（明确不可用）/ `UNKNOWN`（尚未验证）。

本表由 [bedrock-addon/src/capabilities.ts](../bedrock-addon/src/capabilities.ts) 同步维护。
**当前没有任何一项标为 SUPPORTED**：本沙箱无基岩客户端。

| 能力 | API / 版本 | 开关 | Windows | Android | 结论 | 依据 |
|---|---|---|---|---|---|---|
| 自定义 OGG 声音 | 资源包 `sound_definitions.json` | 无 | 待测 | 待测 | `UNKNOWN` | 需隔离世界 `/playsound` 验证 |
| 指定玩家播放 | `Player.playSound` @ 2.4.0 | 无 | 待测 | 待测 | `UNKNOWN` | `index.d.ts:14522` 确认签名 `(soundId, PlayerSoundOptions?): void`；仅本人可听未验证 |
| `playSound` 返回 `SoundInstance` | 同上 | — | — | — | `UNSUPPORTED` | 2.4.0 稳定类型明确返回 **void**，无实例句柄 |
| 音乐淡入 | `playMusic` / `queueMusic` | 待测 | 待测 | 待测 | `UNKNOWN` | 未验证 |
| 真实播放位置 | `SoundDurationInfo.getPlaybackPosition` | 实验 API | 待测 | 待测 | `UNKNOWN` | 微软文档列实验页，且依赖声音定义声明时长；不得默认可用 |
| 暂停 / 恢复 | 无稳定 API | 待测 | 待测 | 待测 | `UNKNOWN` | **不支持时显示「停止」，绝不假装暂停** |
| seek | 无稳定 API | 待测 | 待测 | 待测 | `UNKNOWN` | 不实现 |
| 完成事件 | 无稳定 API | 待测 | 待测 | 待测 | `UNKNOWN` | 不实现自动切歌 |
| 停止已发出的声音 | `/stopsound` + `Entity.runCommand` | 无 | 待测 | 待测 | `UNKNOWN` | `runCommand` 类型存在（`:6613`），但能否中断自定义事件未验证；失败时明确降级提示 |
| 同时声音上限 | 待测 | 待测 | 待测 | 待测 | `UNKNOWN` | 快速点击 50 次场景待测 |
| 动态属性持久化 | `world.setDynamicProperty` | 无 | 待测 | 待测 | `UNKNOWN` | 稳定 API 存在；重进世界是否保留待测 |
| 远程 HTTP | `@minecraft/server-net` | Beta / BDS 专用 | — | — | `UNSUPPORTED` | 本项目明确不使用；曲库断网可播 |
| 通用桌面文件系统 | 无 | — | — | — | `UNSUPPORTED` | 官方脚本无此类 API，基岩文件浏览器不做承诺 |
| 任意远程 MP3 直喂声音系统 | 无 | — | — | — | `UNSUPPORTED` | 必须先由导入器合法获取、转码 OGG、生成声音事件并装入曲库包 |

## 阶段 4 必须回答的 8 个问题（当前答案）

| 问题 | 当前回答 | 依据 |
|---|---|---|
| 目标版本 `@minecraft/server` 版本？ | `2.4.0` 稳定 | Minecraft Wiki《Bedrock Edition 1.21.130》技术条目「Lançado a versão 2.4.0」 |
| 是否需要实验开关？ | 稳定 API 路径**不需要**；`getPlaybackPosition` 等实验能力需要 | wiki.bedrock.dev 稳定/Beta 模块列表 |
| `playSound` 返回 `SoundInstance` 还是 void？ | **void** | 本地 `index.d.ts:14522`（类型层面确定；真机行为仍待测） |
| 是否能 pause / resume / seek？ | 未提供，标 `UNKNOWN`，不实现 | 稳定 API 无对应方法 |
| 是否有完成事件？ | 未提供，标 `UNKNOWN`，不实现 | 稳定 API 无对应事件 |
| 是否能读位置？ | 稳定 API 无；实验 API 标 `UNKNOWN` | 微软文档实验页 |
| 同时声音上限？ | `UNKNOWN` | 需真机快速点击 50 次 |
| 重新进入世界后实例是否保留？ | `UNKNOWN`（代码不持有实例；会话在 `playerLeave` 时清理） | 需真机验证 |

> **禁止用其他版本文档代替真机结果。** 上表「类型层面确定」仅指官方 `.d.ts` 的静态声明，
> 不等于游戏运行时行为已通过。所有运行时尚未验证的结论保持 `UNKNOWN`。

## 降级策略（已实现）

- 按钮标签：`PAUSE_RESUME_IMPLEMENTED = false` → 显示「停止」而非「暂停」。
- 进度条：`POSITION_BAR_IMPLEMENTED = false` → 只显示导入时声明的估算时长，不可拖动。
- `SEEK_IMPLEMENTED = false`、`COMPLETION_EVENT_IMPLEMENTED = false` → 不提供控件、不自动切歌。
- 切歌：普通切歌（先尝试 `/stopsound`，再 `playSound`），**不做无缝混音**。
- 停止失败：明确提示「当前版本无法中断已发出的声音，请等待音轨结束或重进世界」，不假装已停。
- 缺少资源、音量 0、包未启用、快速点击：表单与 `playSound` 均被 try/catch 包裹，不让游戏崩溃。
