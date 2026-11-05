# 跨端协议与数据格式

协议版本：**1**（`shared-schemas/schema/migration-rules.json` 中 `protocolVersion: 1`）

## 1. 唯一允许的「连接」

- 曲库是**本机内容**，Java 端从用户显式选择的目录扫描，基岩端随包预导入。
- 游戏内播放器**不发起任何平台网络请求**。
- 只有 `music-import-cli`（本机导入器）会访问音乐平台官方接口，且由用户主动触发。
- 曲库导入后断网可完整播放。

## 2. 纯客户端边界（代码闸门）

`./gradlew checkClientOnly` 会拒绝以下任一符号出现在源码中：

`ServerPlayer`、`ServerPlayNetworking`、`ServerPlayNetworkHandler`、`MinecraftServer`、
`Bukkit`、`org.bukkit`、`@Environment(SERVER)`、`geyser`、`floodgate`；
以及 import `@minecraft/server-net` / `@minecraft/server-admin` / `@minecraft/server-gametest`。

注释与文档字符串被排除，避免把「说明文字」误判成代码引用。

**不写**：服务端插件/Mod、BDS 插件、独立媒体服务器、房间/DJ/跨玩家点歌、
全服播放、现在播放广播、自定义网络包、原版协议修改、服务端命令。
Java 玩家与基岩玩家在什么服务端听歌，结果都只是「自己本机播放自己的曲库」。

## 3. 统一数据模型

| 对象 | Schema | 说明 |
|---|---|---|
| `Song` | [song.schema.json](../shared-schemas/schema/song.schema.json) | 见 [song-schema.md](song-schema.md) |
| `Playlist.Document` | [playlist.schema.json](../shared-schemas/schema/playlist.schema.json) | 源歌单，`revision` 乐观并发 |
| `QueueSnapshot` | [queue.schema.json](../shared-schemas/schema/queue.schema.json) | 运行时队列，不写回源歌单 |
| `Lyric.Document` | [lyric.schema.json](../shared-schemas/schema/lyric.schema.json) | 见 [lyric-schema.md](lyric-schema.md) |
| 错误码 | [error-codes.json](../shared-schemas/schema/error-codes.json) | 16 个统一错误码 |
| 迁移规则 | [migration-rules.json](../shared-schemas/schema/migration-rules.json) | 版本与迁移策略 |

### 3.1 跨端一致性

Java 与基岩使用**同一份 JSON 键名与枚举值**：

- `mode`: `sequence | shuffle`
- `repeat`: `none | one | list`
- `durationSource`: `import_probe | provider_metadata | user_declared | unknown`
- `speaker`: `lead | backing | duet_a | duet_b | unknown`
- `format`: `lrc | qrc | yrc | enhanced_lrc | ttml | krc | unsupported`
- `PlaybackState`: `Idle/Loading/Ready/Buffering/Playing/Paused/Stopped/Ended/Failed`

Java 侧由 `common-core/model/*` 的 `toMap()/fromMap()` 保证；
TypeScript 侧由 `bedrock-addon/src/library.ts`、`queue.ts`、`errors.ts` 的类型与校验保证。
`SchemaCompatibilityTest` 覆盖 Java 侧往返；`tools/validate-library.mjs` 覆盖曲库清单。

### 3.2 null 语义（重要）

> **`null` = 未知 / 未提供；空集合或空字符串 = 明确没有。二者必须可区分。**

因此 `durationMs` 未知时保持 `null`，**禁止写成 0**；
`analysis.automix.score` 未知保持 `null`；
歌词 `endMs` 未知保持 `null`，规范化时才用下一行起点或歌曲时长推断并显式写出。

## 4. 错误模型

每个错误包含：`code`、中文 `userMessage`、`technicalDetail`、`retryable`、
`suggestedAction`、`sensitiveDataLeaked`、`platform`、`gameVersion`、
`packVersion`、`protocolVersion`。

日志脱敏过滤：`cookie`、`authorization`、`token`、`qrkey`、`unikey`、`phone`、`password`
（Java `MuoniumError.redact`，TS `errors.redact`）。

平台接口变化或登录失效时优雅提示「该来源暂不可用」，不让游戏崩溃。

## 5. 文件与版本迁移

| 对象 | 当前版本 |
|---|---|
| song / playlist / queue / lyric | 1 |
| config | 3 |
| protocol | 1 |

迁移策略（`ConfigMigrator`）：

- v1→v2：`volume` 由 0..100 改为 0..1，`volumePercent` 重命名；
- v2→v3：删除所有 `server*` / `sync*` / `room*` / `remote*` / `broadcast*` 键，
  强制 `clientOnly=true`、`telemetryEnabled=false`、`externalProcessesEnabled=false`、
  `crashReportingEnabled=false`；
- 未来版本号高于当前支持版本 → **抛异常拒绝猜测**，不静默降级；
- 未知歌词格式 → 标记 `unsupported` 并保留原始文件交调用方保管，不伪造时间轴；
- 重复 ID / 临时 URL 持久化 / 包内凭据 → 一律拒绝。

## 6. 音频资产约定

| 端 | 音频引用 | 说明 |
|---|---|---|
| Java | `local_file` / `pack_file` | 阶段 2 起本地 OGG/MP3/OPUS/FLAC/WAV 能力分级 |
| 基岩 | `resource_sound_event` → `muonium:track_0001` | 资源包 `sound_definitions.json` 映射到 `sounds/muonium/*.ogg` |

两份同名声音配置**用途不同、禁止合并**：

- 资源包 `sound_definitions.json`：事件 → 实际音频文件；
- 行为包 `sounds/sound_definitions.json`：脚本声音的声明时长、音乐类别等元数据
  （目标 API 是否读取需真机验证，见 [bedrock-capability-matrix.md](bedrock-capability-matrix.md)）。

文件名规则：小写 ASCII、下划线、稳定编号；**不使用中文歌名作文件名**。

## 7. 导入器输出前检查单

`music-import-cli` / `tools/validate-library.mjs` 执行：

采样率与声道规范化、OGG Vorbis 转码、时长探针与清单一致、SHA-256 可用性、
重复 ID/路径检查、包名/UUID/manifest 依赖检查、文本 JSON 合法性、
文件体积与分包建议、许可证确认、**不嵌入凭据**、
ZIP 防路径穿越 / Zip Slip / 压缩炸弹、FFmpeg 固定参数不拼 shell。

## 8. 生命周期

- 启动：只扫描一次曲库，不每 tick 扫描；
- 运行中：歌词查找每帧 O(log n)（`LyricCursor` 二分），封面按需加载；
- 关闭：逆序释放音频后端、线程、临时文件与事件订阅，不留下后台进程；
- 重进世界（基岩）：`playerLeave` 清理会话，不持有失效 `Player` 引用。
