# 版本锁定与来源记录

查询时间：**2026-11-04 UTC / 2026-11-05 CST**
所有版本均来自对官方元数据的实际请求，未凭记忆填写。
锁定文件：[gradle/libs.versions.toml](../gradle/libs.versions.toml)、
[bedrock-addon/package.json](../bedrock-addon/package.json)、两个 `manifest.json`。

## 1. Java 端（1.21.11）

| 组件 | 锁定值 | 来源（实际请求） | 结果 |
|---|---|---|---|
| Minecraft | `1.21.11` | `GET https://meta.fabricmc.net/v2/versions/game` | `{'version': '1.21.11', 'stable': True}` |
| Yarn 映射 | `1.21.11+build.6` | `GET .../v2/versions/yarn/1.21.11` | `build: 6, stable: true, maven: net.fabricmc:yarn:1.21.11+build.6` |
| Fabric Loader | `0.20.1` | `GET .../v2/versions/loader` | 首个 `stable: True` 为 `0.20.1` |
| Fabric API | `0.141.6+1.21.11` | `GET api.modrinth.com/v2/project/fabric-api/version?...` | `0.141.6+1.21.11 release 2026-07-28` |
| Fabric Loom | `1.19.3` | `GET maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml` | `<release>1.19.3</release>` |
| Java | `21` | `fabric.mod.json` `java: >=21`；Gradle toolchain 21 | 本机 `openjdk 21.0.12.1` |
| Gradle | `8.14.3` | wrapper 生成时指定 | 本机验证通过 |

`fabric.mod.json` 声明 `minecraft: ~1.21.11`、`java: >=21`、
`fabricloader: >=0.20.1`、`environment: client`，不声明未使用的服务端模块。

### 1.1 重要核实差异：`net.fabricmc.fabric-loom-remap`

提示词 3.2 提到「Fabric Loom 对 1.21.11 应使用适用于混淆版本的
`net.fabricmc.fabric-loom-remap` 插件」。**本次实际核实结果：该插件不存在或不可确认**：

| 检查 | 结果 |
|---|---|
| `GET https://plugins.gradle.org/plugin/net.fabricmc.fabric-loom-remap` | **Plugin Not Found** |
| `GET https://maven.fabricmc.net/net/fabricmc/fabric-loom-remap/maven-metadata.xml` | **404 Not Found** |
| `GET https://fabricmc.net/wiki/documentation:fabric_loom` | 文档使用标准 `fabric-loom` 插件，并给出 `splitEnvironmentSourceSets()` 用法（本项目已采用） |

按「禁止编造 API / 不凭记忆写」的纪律，本项目使用**实际存在**的 `fabric-loom 1.19.3`
（`alias(libs.plugins.fabric.loom)`），并在 `fabric-1_21_11/build.gradle.kts` 中启用
`splitEnvironmentSourceSets()` 隔离客户端代码。
若未来官方发布 `fabric-loom-remap`，需重新评估后替换，并在本文件追加记录。

> 备注：Fabric Meta 同时列出 `1.21.11_unobfuscated` 实验项，说明该版本存在混淆/未混淆变体。
> 本项目不使用任何未混淆专属 API，也不对此做未经真机的承诺。

## 2. 基岩端（1.21.130）

| 项 | 锁定值 | 来源 | 结果 |
|---|---|---|---|
| 游戏版本 | `1.21.130` | Microsoft Learn《Latest Platform Version Guidance》 | 文中明确「target ... 1.21.130」，并说明 `Major.Minor.Patch = 1.21.130` |
| manifest `format_version` | `2` | 同上 + Bedrock Wiki manifest 指南 | 稳定 Script API 基线为 2；v3 属 preview（含 pack settings） |
| `min_engine_version` | `[1, 21, 130]` | 同上 + wiki.bedrock.dev（「A min_engine_version of at least 1.21.130」） | 两端 manifest 各自声明，不跨端引用 |
| `@minecraft/server` | `2.4.0`（稳定） | Minecraft Wiki 葡语版《Edição Bedrock 1.21.130》技术条目 | 「Lançado a versão 2.4.0 do @minecraft/server」，同时新增 `2.5.0-beta` |
| `@minecraft/server-ui` | `2.0.0`（稳定） | wiki.bedrock.dev《Intro to Scripting》稳定模块列表 | `2.0.0 (Latest version, requires @minecraft/server 2.0.0+)` |
| 事件触发 | `system.afterEvents.scriptEventReceive` | 本地安装的 `node_modules/@minecraft/server/index.d.ts:18157` | 见下节 |
| npm `latest` | `2.11.0` | `GET registry.npmjs.org/@minecraft/server` | **未使用**：`latest` 对应更新的游戏版本，不能冒充 1.21.130 |

> 版本口径：Java 1.21.11 与基岩 1.21.130 属同一内容更新，两端各用各的官方版本号；
> 代码中不写「基岩 1.21.11」，也不要求两端版本字符串相等。

## 3. Script API 2.4.0 类型实测（本地 `.d.ts`，非猜测）

对 `bedrock-addon/node_modules/@minecraft/server/index.d.ts`（649 KB）实际检索：

| API | 实测签名 / 结论 | 行号 |
|---|---|---|
| `Player.playSound` | `playSound(soundId: string, soundOptions?: PlayerSoundOptions): void` → **返回 void，不是 SoundInstance** | 14522 |
| `Player.runCommand` | `runCommand(commandString: string): CommandResult` | 6613 / 8164 |
| `PlayerSoundOptions` | `location? / pitch? / volume?` | 21186 |
| `WorldBeforeEvents` | **不含 `chatSend`**（只有 effectAdd / entityRemove / explosion / itemUse / playerBreakBlock / playerGameModeChange / playerInteract* / playerLeave / weatherChange） | 19427–19507 |
| `WorldAfterEvents` | 同样**不含 `chatSend`**；含 `playerJoin`、`playerLeave`、`worldLoad` | 19326–19417 |
| `system.afterEvents.scriptEventReceive` | 存在，`ScriptEventCommandMessageAfterEventSignal` | 18157 |
| `ScriptEventCommandMessageAfterEvent` | `id / message / sourceType / sourceEntity? / initiator? / sourceBlock?` | 17116 |
| `PlayerLeaveAfterEvent` | `playerId / playerName` | 15886 |

**重要发现**：`index.d.ts:18540` 的文档注释里仍有
`world.beforeEvents.chatSend.subscribe(...)` 示例，但 **2.4.0 的类型定义中该属性不存在**。
本项目因此不用聊天事件，改用官方稳定的 `/scriptevent muonium:open` →
`system.afterEvents.scriptEventReceive`。这是「不许猜 API」的直接收益：
若凭记忆写 `chatSend`，`tsc --strict` 会明确报
`TS2339: Property 'chatSend' does not exist on type 'WorldBeforeEvents'`。

`@minecraft/server-net` 为 BDS 专用且需 Beta，本项目明确 `UNSUPPORTED`，不做远程 HTTP。

## 4. 网络与工具链环境

| 源 | 结果 | 用途 |
|---|---|---|
| `github.com:443` git clone | 超时失败 | 改用 `api.github.com` + `codeload` 归档 |
| `api.github.com` | 200 | 元数据 / tree / blobs |
| `codeload.github.com` | 200 但限速中断 | 未完成，不作为基线依据 |
| `meta.fabricmc.net` | 200 | 版本查询 |
| `maven.fabricmc.net` | 200 | Loom / 依赖 |
| `repo1.maven.org` | 200 | JUnit |
| `services.gradle.org` | 200（但 307 到不可达的 `github.com/gradle/...`） | wrapper 属性保留官方 URL；本地用腾讯云镜像的 Gradle 8.14.3 二进制执行 |
| `mirrors.cloud.tencent.com/gradle` | 200（137 MB，12.6 s） | 本地 Gradle 发行版 |
| `registry.npmjs.org` | 200 | npm 依赖 |
| `piston-meta.mojang.com` | 200 | Loom 后续下载 MC 所需（本沙箱未执行） |
| `learn.microsoft.com` | 302/200 | Script API 文档 |
| `minecraft.wiki` | 403（改用搜索结果中的镜像条目） | 版本条目 |

## 5. 待真机确认项（不得提前写成已验证）

- Fabric 1.21.11 客户端能否用 Loom 1.19.3 + Yarn `build.6` 正常 remap 并启动；
- `splitEnvironmentSourceSets()` 在 1.21.11 的具体行为；
- 基岩 1.21.130 是否读取行为包 `sounds/sound_definitions.json`；
- `stopsound` 命令与 `Entity.runCommand` 在目标版本是否可中断自定义声音；
- 动态属性在重进世界后是否保留；
- 自定义 OGG 声音事件的命名空间（`muonium:track_0001`）、`stream` 策略与缓存重载。
