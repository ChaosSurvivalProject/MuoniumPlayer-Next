# 实施计划与阶段状态

> 提示词要求「先提交计划让我确认，再进入阶段 1」。本文件即为提交给用户确认的计划。
> 本次已在无需真机的范围内完成阶段 0 与阶段 1，并把阶段 2+ 的代码骨架与
> 可静态验证部分尽量前置；**所有需要真机的验收一律未声称通过**。

## 阶段 0：审计与可编译空壳 — ✅ 已完成（本环境可验证部分）

| 项 | 状态 | 证据 |
|---|---|---|
| 上游 SHA、许可证、依赖清单 | ✅ | `REFERENCE_COMMIT.txt`、`docs/source-audit.md`、`THIRD_PARTY_NOTICES.md` |
| Gradle 多模块、Java 21 工具链 | ✅ | `./gradlew clean build test checkClientOnly` BUILD SUCCESSFUL |
| Fabric 1.21.11 空客户端 Mod 源码 | ✅ 源码 / ⚠️ Loom 编译待验证 | `fabric-1_21_11/`；见 [test-report.md](test-report.md) |
| 基岩空 BP/RP 可导入 | ✅ 静态校验 / ⚠️ 真机导入待测 | `check-manifests` / `check-sound-events` / `.mcaddon` 制品 |
| CI 可执行 | ✅ | `.github/workflows/ci.yml` |
| 编码规范、提交规范、Issue 模板 | ✅ | `CONTRIBUTING.md`、`.github/ISSUE_TEMPLATE/` |
| 上游代码映射表 | ✅ | `docs/source-audit.md` §3 |

## 阶段 1：共享核心和假音频后端 — ✅ 已完成

Song/Playlist/Queue/Lyric 模型、JSON Schema 与迁移、9 态状态机、
顺序/随机/循环/历史、歌词游标（O(log n)）、虚拟时钟、假音频后端、
安全 JSON、缓存淘汰、重试退避、事件总线、错误模型。
**71 个 JVM 测试全部通过，不启动 Minecraft。**

## 阶段 2：Fabric 本地音频 MVP — ⚠️ 部分（代码骨架，真机未验证）

已做：纯客户端入口、本地曲库扫描（不扫全盘）、假音频后端、
播放控制器（暂停不支持时降级为停止）、配置原子写入、shutdown hook 资源释放、
`fabric.mod.json` 纯客户端声明。
待做（需真机）：真实本地音频引擎（OGG/MP3/OPUS/FLAC/WAV）、音量/seek/淡入淡出/Automix、
游戏内 HUD、键位注册、中文与高 DPI 适配、3/10/30 分钟音频、快速切歌 100 次、
切世界与重进、内存与残留进程检查。

## 阶段 3：Fabric 歌词、封面和主题 — 📋 计划

LRC/QRC/TTML 解析已在 `common-core` 完成并有测试；UI 侧（逐字与翻译、
歌曲卡、通知、简约 HUD、主题、中文字体回退、高 DPI、无障碍对比度）待阶段 2 真机后实现。

## 阶段 4：Bedrock 最小闭环 — ⚠️ 静态部分完成，真机 UNKNOWN

已做：一个无版权 OGG、一个声音事件、表单 UI、`Player.playSound()` 调用、
只本玩家可听、停止（带明确降级）、内容日志诊断、两份声音配置分离。
**8 个阶段 4 问题的当前答案见 [bedrock-capability-matrix.md](bedrock-capability-matrix.md)**；
所有运行时尚未验证的结论为 `UNKNOWN`。

## 阶段 5：Bedrock 曲库 MVP — ⚠️ 静态部分完成，真机未验证

已做：表单主页、曲库/歌曲/队列、播放/停止/下一首/上一首、随机/循环、
音量、本机设置（动态属性）、元数据与声音 ID 映射、缺少资源时禁用而不是崩溃、
`.mcaddon` 打包。
待做（真机）：Windows/Android 导入、第三方服务器联机听歌、音量 0、包未启用、
歌曲文件缺失、快速点击 50 次、切换维度。

## 阶段 6：平台导入器 — 📋 计划（仅骨架）

`music-import-cli` 已完成本地文件导入、ffprobe 探测、OGG 转码、校验、打包。
待做：网易云/QQ 音乐适配器（独立模块）、用户主动授权、逐首许可证确认、
Token 本机加密与一键清除、FFmpeg 缺失时只启用可处理格式。
**不做**解灰、绕会员、破解、Cookie 上传。

## 阶段 8：发布准备 — ⚠️ 文档完成，真机矩阵缺失

见 [release.md](release.md) 的发布前清单。

---

## 需要用户确认 / 提供的测试环境

按提示词第 12 节「必须先输出」第 8 项，以下为继续阶段 2+ 的必要条件：

| 需要 | 用途 |
|---|---|
| Windows Java 版 1.21.11 + Fabric（Java 21） | 验证 Loom 构建、`runClient` 启动、HUD/键位 API 真实签名、音频引擎 |
| Windows 基岩版 1.21.130 | 验证 `.mcaddon` 导入、`/playsound`、表单 UI、`stopsound`、动态属性 |
| Android 基岩版 1.21.130 | 验证移动端导入、性能、音量 0、快速点击 |
| 任意第三方服务器（联机时听歌） | 验证「不依赖服务端」：两端各自本机播放 |
| Java 与基岩玩家同时在线 | 验证无跨玩家干扰 |
| 是否开启 Beta APIs 实验开关 | 决定是否评估实验音频能力（当前一律不依赖实验 API） |
| 是否允许评估 `MuoniumPlayer-multiversion` 仓库 | 若需更贴近新版本行为，需另行审计该仓库许可 |

## 待用户确认的选择

1. **是否继续进入阶段 2+**：本次已按「先审计、再计划、后编码」完成阶段 0/1
   与可静态验证部分；继续需要上述真机环境。
2. **Fabric 音频引擎选型**：自研 Java Sound / 经许可证审查的第三方解码库 /
   可选 FFmpeg 外部进程（默认关闭）。
3. **平台导入器范围**：仅本地文件，或含用户自有内容的网易云/QQ 授权导入。

## 每阶段输出格式（后续沿用）

改了什么 · 为何这样设计 · 运行了什么命令 · 测试结果 · 已知限制 · 回滚方式 · 下一阶段依赖。
