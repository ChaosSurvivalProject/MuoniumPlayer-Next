# 真机测试指南（Real-Device Testing）

> 本仓库的 CI 与单元测试只覆盖**静态构建、类型检查、领域核心逻辑**；
> 凡是「在真实 Minecraft 客户端里能不能用」都属于**真机项**，在有人提供设备前一律标记为 `UNKNOWN`，**不写成通过**。

本文给出双端在真实设备上的验证步骤、可勾选清单、结果上报方式，以及如何把某个 `UNKNOWN` 安全地翻转为 `SUPPORTED`。

---

## 0. 测试前须知（诚实边界）

- **两端都纯客户端、可断网播放**，不依赖任何服务端、代理或媒体服务器。
- 基岩端部分能力（暂停/恢复/seek/真实播放位置/完成事件/同时声音上限/音乐淡入）按纪律**一律 `UNKNOWN`，代码未实现假功能**；详见 [bedrock-capability-matrix.md](bedrock-capability-matrix.md)。
- 报告问题时请用 [`.github/ISSUE_TEMPLATE/device-issue.md`](../.github/ISSUE_TEMPLATE/device-issue.md) 模板，必填项见 §5。
- 本仓库 CI 出的是**制品（mcaddon / Fabric jar）**，不会替你做真机验收。

---

## 1. Java / Fabric 真机测试

### 1.1 准备

| 项 | 要求 |
|---|---|
| 游戏 | Minecraft Java **1.21.11**（驯兽之灾） |
| Java | **21**（Fabric Loader 需要） |
| 加载器 | Fabric Loader ≥ **0.20.1** |
| API | Fabric API **0.141.6+1.21.11** |
| 启动器 | 官方启动器或合规第三方（建议官方，确保纯客户端无注入） |

### 1.2 构建与安装

```bash
# 本地构建（需联网下载 Minecraft 客户端与映射，约数分钟）
./gradlew -PincludeFabric=true :fabric-1_21_11:build
# 取成品：fabric-1_21_11/build/libs/fabric-1_21_11-0.1.0.jar
# 放入 .minecraft/mods/
```

或直接取 **GitHub Release** 里的 Fabric Mod jar（见仓库 Releases 页）。

### 1.3 验证清单（请逐项勾选）

- [ ] 启动器出现 Fabric 配置，可进入 1.21.11 单人世界
- [ ] 进入世界无崩溃，日志中无 `MuoniumPlayer` 报错（日志路径见 `logs/latest.log`）
- [ ] 曲库 JSON 被读取（无 `library.json` 解析错误）
- [ ] HUD / 键位占位接口不报错（注：HUD/键位为接口占位，需在 1.21.11 真机核实 API 后实现，见 [risk-register.md](risk-register.md)）
- [ ] 中文 / emoji / 超长标题显示正常
- [ ] 退出客户端后无残留 Java 进程、无占用音频设备句柄

### 1.4 首要故障点

- `Cannot remap modifiers` 警告（Loom 与 1.21.11 混淆过渡相关，构建仍成功）——确认是否影响运行，记入风险清单 R5。
- 加载器 / Fabric API 版本不匹配导致 `ClassNotFound` 或 `Mixin` 失败。

---

## 2. Bedrock 真机测试

### 2.1 准备

| 项 | 要求 |
|---|---|
| 游戏 | 基岩版 **1.21.130**（Windows 10/11 版、Android 版或 iOS 版） |
| 实验开关 | **不需要**开启 Beta APIs（本包只用稳定模块 `@minecraft/server` 2.4.0 与 `@minecraft/server-ui` 2.0.0） |
| 测试世界 | 建议先用一个**隔离的本地世界**，避免影响存档 |

### 2.2 构建与导入

```bash
# 本地构建 -> bedrock-addon/dist/muonium-player.mcaddon
node tools/pack-addon.mjs
```

或直接取 **CI 制品 / Release 附件** 里的 `.mcaddon`。
双击导入，在测试世界里**同时启用「行为包」与「资源包」**（两个都要开，少一个声音或 UI 会缺失）。

### 2.3 触发

- 进入世界后会收到欢迎消息：`[MuoniumPlayer] 输入 /scriptevent muonium:open 打开音乐播放器（纯客户端，断网可播）`。
- 在聊天输入 **`/scriptevent muonium:open`** 打开官方表单播放器。
- 先用隔离世界验证自定义声音事件：`/playsound muonium:track_0001`（命名空间 `muonium:`、事件名 `track_0001` 需在资源包的 `sound_definitions.json` 中声明）。

> 触发方式说明：经核实 `@minecraft/server 2.4.0` 类型中**不存在 `chatSend`**（官方文档示例与类型定义不一致），
> 因此改用官方稳定的 `system.afterEvents.scriptEventReceive`，不注册自定义命令、不改原版协议。

### 2.4 验证清单（请逐项勾选）

- [ ] `.mcaddon` 导入成功，包列表里**同时出现行为包与资源包**
- [ ] 世界启用后无内容日志告警
- [ ] `/playsound muonium:track_0001` **能听到声音**（首要故障点：事件命名空间、分隔符、缓存重载）
- [ ] `/scriptevent muonium:open` 弹出官方表单 UI
- [ ] 选择曲目播放，**仅触发者本人可听**（他人不应听到——验证不加他人）
- [ ] 「停止」生效；`/stopsound` 不能中断时明确提示而非假装已停
- [ ] 重进世界后设置保留（动态属性持久化）
- [ ] 降级场景正常：音量 0、包未启用、曲目缺失
- [ ] 快速点击 50 次不崩溃

### 2.5 已知 `UNKNOWN`（未实现，绝不假装）

| 能力 | 现状 |
|---|---|
| 暂停 / 恢复 | 只提供「停止」标签，不提供「暂停」 |
| seek | 不实现 |
| 真实播放位置 / 进度条 | 只显示导入时声明的估算时长，不可拖动 |
| 完成事件 / 自动切歌 | 不实现 |
| 同时声音上限 | 快速点击场景待测 |
| 音乐淡入 / Automix | 未验证，普通切歌 |

验证结果请回填 [bedrock-capability-matrix.md](bedrock-capability-matrix.md) 与 `src/capabilities.ts` 的 `verdict`。

---

## 3. 联机与跨端

- **第三方服务器联机时本地听歌**：验证不依赖服务端（无服务端插件 / Mod / 适配）。
- **Java 与基岩玩家同时在线**：各自本机出声，互不干扰。
- 不适配 Geyser / Floodgate（纯客户端，无跨端同步需求）。

---

## 4. 压力与稳定性

- [ ] 连续播放 **2 小时**无崩溃 / 无内存泄漏
- [ ] 快速切歌 **100 次**无音频设备句柄泄漏
- [ ] 低内存 / 慢存储下仍可启动
- [ ] 维度切换（主世界 ↔ 下界 ↔ 末地）后播放状态正常

---

## 5. 上报问题

使用 [`.github/ISSUE_TEMPLATE/device-issue.md`](../.github/ISSUE_TEMPLATE/device-issue.md)，必填：

1. 设备 / 操作系统 / 游戏版本（Java 1.21.11 或 基岩 1.21.130 + 平台）
2. 加载器与 API 版本（Fabric Loader / Fabric API，或基岩平台）
3. 复现步骤（含触发命令）
4. 日志片段（`logs/latest.log` 或内容日志；**脱敏已内置**：Cookie、Authorization、二维码票据、手机号会被过滤）
5. 期望行为 vs 实际行为

不要提交音乐文件、封面、歌词、Cookie、Token 或账号数据。

---

## 6. 如何把 `UNKNOWN` 翻转为 `SUPPORTED`

1. 在真实设备完成 §1–§4 对应清单，保留测试日志。
2. 更新 [bedrock-capability-matrix.md](bedrock-capability-matrix.md) 的 `verdict` 与 `note`，并同步 `src/capabilities.ts` 的 `CAPABILITIES` 与 `PAUSE_RESUME_IMPLEMENTED` 等开关。
3. 提交 PR 并附上设备信息 / 日志（或关联一条 `device-issue`）。
4. **不靠 CI 推断**——以真机证据为准；CI 只保证构建与静态校验。

---

## 7. 可勾选测试日志模板

```text
设备/系统：____________________
游戏版本：Java 1.21.11 / 基岩 1.21.130（平台：____）
加载器/API：Fabric Loader ____ + Fabric API ____   （基岩无需）
构建来源：本地构建 / Release v____

Java 端清单：
  [ ] 进入世界    [ ] 无崩溃    [ ] 曲库读取    [ ] HUD/键位    [ ] 退出无残留
基岩端清单：
  [ ] 双包启用    [ ] /playsound 有声    [ ] 表单弹出    [ ] 仅本人可听
  [ ] 停止生效    [ ] 设置保留    [ ] 降级正常    [ ] 快速点击不崩
联机/压力：
  [ ] 联机本地听歌    [ ] 2 小时连续    [ ] 切歌 100 次    [ ] 维度切换
结论：PASS / FAIL（附日志路径与现象）
```
