# 测试报告

报告时间：2026-11-05（CST）
环境：Ubuntu 24.04 / Linux amd64，**无 Minecraft 客户端、无 GUI、无 Windows/Android 设备**

> 本报告只记录**真实执行过**的命令与结果。
> 未执行的真机测试一律标注「未验证」，不写成通过。

## 1. JVM 侧（真实通过）

命令（JDK `openjdk 21.0.12.1`；本机 Gradle 用 `/opt/gradle-9.8.0/bin/gradle`，
并设 `GRADLE_USER_HOME=/root/.gradle`；**必须 Java 21**，否则 Loom 配置阶段失败）：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export GRADLE_USER_HOME=/root/.gradle
/opt/gradle-9.8.0/bin/gradle -PincludeFabric=true build test checkClientOnly --console=plain
```

| Gradle | 结果 |
|---|---|
| 8.14.3 | `BUILD SUCCESSFUL`（中间修过 2 个真实错误，见 §6） |
| **9.8.0（最终锁定）** | `BUILD SUCCESSFUL in 31s`，`checkClientOnly PASS` |

**单元测试：96 个，0 失败，0 跳过**（从 `build/test-results/test/TEST-*.xml` 精确统计）：

| 模块 | 数量 | 说明 |
|---|---|---|
| `common-core` | 60 | 见下表 |
| `music-import-cli` | 11 | `ImportCliTest` |
| `music-providers` | 25 | QQ 音乐源 + 降级/清理（见 §1.1） |

其中 `common-core` 的 60 个：

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `PlaybackStateMachineTest` | 6 | 9 态转换表、非法转换、缓冲恢复、失败与停止分离 |
| `PlayQueueTest` | 6 | 顺序、单曲循环优先、列表循环、历史、队列耗尽停住 |
| `ShuffleDeterminismTest` | 5 | 种子可复现、snapshot 不重排、并发入队/移除 |
| `LyricParsersTest` | 8 | LRC 多时间戳、毫秒/百分秒、QRC/YRC/增强 LRC、TTML、未知格式、巨型文件 |
| `LyricNormalizerTest` | 6 | 乱序、倒退/负数、缺失结束时间、重复时间戳、翻译对齐、超长行 |
| `LyricCursorTest` | 4 | 二分查找边界、逐字游标、20,000 行性能 |
| `JsonSafetyTest` | 6 | 原型污染、深度、巨型输入、转义往返、非有限数 |
| `ConfigMigratorTest` | 5 | v1→v3 迁移、默认 clientOnly、未来版本拒绝 |
| `CachePoliciesTest` | 4 | LRU 淘汰、缓存键确定性、重试退避上限 |
| `SchemaCompatibilityTest` | 6 | Song/Playlist/Lyric JSON 往返、null 语义、资源名规则 |
| `EventBusTest` | 4 | 订阅/取消/关闭、类型过滤、日志脱敏 |
| `ImportCliTest` | 11 | 重复 ID/声音事件、路径穿越、凭据、临时 URL、许可证、CLI 真实文件 |

### 1.1 QQ 音乐适配层测试（`music-providers`，25 个全绿）

全用 Fake（合成 fixture），**不联网、不触碰任何真实版权内容**：

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `QQMusicMediaSourceTest` | 6 | 搜索/歌单解析、vkey 直链、歌词 gzip+base64、导入、缺凭据 |
| `LocalMediaSourceTest` | 3 | 本地曲库只读查询 |
| `MediaSourceRegistryTest` | 3 | 注册/延迟构造/缺省源 |
| `QQMusicAudioBackendTest` | 3 | 流式编排、能力 UNKNOWN 纪律、拒绝非 QQ 曲目 |
| `QQMusicIntegrationTest` | 3 | 注册表/后端装配、uin 提取 |
| `DegradationTest` | 7 | 401/403/429/5xx/404 错误码与 retryable、签名失败、下载/转码/后端拒绝三处临时文件清理 |

`common-schema` 产物 jar 已核验包含 6 个 schema（song/playlist/queue/lyric/error-codes/migration-rules）。

## 2. Fabric Loom 编译（真实通过）

版本组合经多轮真实探测后确定（详见 [version-lock.md](version-lock.md) §1.1）：

```bash
./gradlew -PincludeFabric=true :fabric-1_21_11:build --no-daemon
```

`Fabric Loom: 1.17.21` + `Gradle 9.8.0` + `Java 21`，**BUILD SUCCESSFUL in 4m10s**，
执行了 `compileClientJava` → `jar` → `remapJar` → `assemble` → `build`。

> 编译通过 ≠ 功能通过：本环境**没有启动 Minecraft 客户端**，
> 「进入 1.21.11 世界、HUD、键位、真实音频」仍为 **未验证**。

Loom 输出一条警告：`Cannot remap modifiers because it does not exist in any of the targets [] or their parents.`
与 1.21.11 混淆/未混淆过渡有关，构建仍成功；已记入 [risk-register.md](risk-register.md) R5。

## 3. Bedrock Add-On（静态验证真实通过）

```bash
cd bedrock-addon && npm ci && npm run check && cd ..
```

| 检查 | 结果 |
|---|---|
| `npm install` | 7 packages，5 s |
| `tsc --noEmit`（strict / noImplicitAny / noUncheckedIndexedAccess / exactOptionalPropertyTypes） | **PASS** |
| `esbuild` 打包 | `pack/behavior_pack/scripts/main.js` 24,230 bytes（新增 QQ 导入表单） |
| `node --check` | `NODE_CHECK_OK` |

严格类型检查过程中发现并修复的真实问题：
`chatSend` 在 `@minecraft/server 2.4.0` 类型中不存在（TS2339）、`console` 未声明。
改用官方稳定的 `system.afterEvents.scriptEventReceive` 并补 `globals.d.ts`。

## 4. 校验工具与制品（真实通过）

| 工具 | 结果 |
|---|---|
| `tools/check-manifests.mjs` | PASS（UUID 唯一、`[1,21,130]`、脚本入口存在、模块版本与 package.json 一致） |
| `tools/check-sound-events.mjs` | PASS（`muonium:track_0001` → 19,844 bytes OGG，`OggS` 魔数校验） |
| `tools/validate-library.mjs` | PASS（1 首曲目） |
| `tools/generate-test-vectors.mjs` | OK（6 个歌词时间轴向量） |
| `tools/pack-addon.mjs` | OK → `bedrock-addon/dist/muonium-player.mcaddon` **27,756 bytes / 21 files** |

## 5. 本机导入器 CLI（真实通过，含真实外部进程）

| 命令 | 结果 |
|---|---|
| `music-import-cli validate bedrock-addon/pack/behavior_pack/library.json` | `VALIDATE PASS: 1 首曲目` |
| `music-import-cli probe test-fixtures/audio/test_tone.ogg` | `PROBE OK durationMs=5000` |
| `music-import-cli transcode ... /tmp/muonium_transcoded.ogg` | `TRANSCODE OK`，ffprobe 复核 `5.000000` |
| `qqsearch --query 测试 --fake` | 1 条合成结果（不联网） |
| `qqlyric mid_abc --fake` | 2 行合成歌词 |
| `qqimport mid_abc --library /tmp/lib --fake` | 生成 `track_mid_abc.ogg` + `library.json`（标题/歌手/专辑/时长/封面/`personal-use` 元数据齐全） |

素材生成：`ffmpeg` 程序化生成 5 秒 440/554.37/659.25 Hz 和弦 OGG Vorbis 44.1 kHz 双声道
（`codec_name=vorbis, sample_rate=44100, channels=2, duration=5.000000`）；
`Pillow` 生成 256×256 程序化封面。全部无版权。

## 6. 过程中真实修复的错误（不隐藏）

| # | 错误 | 修复 |
|---|---|---|
| 1 | 环境全局 `/root/.gradle/init.gradle` 语法错误（URL 未加引号、`mavelCentral()` 拼写），所有 Gradle 构建在 startup 失败 | 重写为合法 Groovy |
| 2 | `LyricCursor.java` 缺 `import java.util.List` | 补 import |
| 3 | `PlayQueueTest.previousUsesHistory` 断言写成 `[b]`，实际应为 `[b,c]` | 修正测试预期（实现行为正确） |
| 4 | `checkClientOnly` 把 `capabilities.ts` 文档文字里的 `server-net` 误判为服务端代码 | 闸门改为只检查代码符号与 import 行，跳过注释/文档字符串 |
| 5 | `chatSend` 在 2.4.0 不存在；`console` 未声明 | 改用 `scriptEventReceive` + `globals.d.ts` |
| 6 | `check-sound-events.mjs` 检查「不合并」但文件写「禁止合并」 | 修正断言字符串 |
| 7 | `fabric-loom 1.19.3` 要求 Gradle ≥9.8.0 且 JVM ≥25 | 锁定 `fabric-loom 1.17.21`（JVM 21）+ Gradle 9.8.0 |
| 8 | Loom Kotlin DSL `sourceSet(sourceSets.main)` 类型不匹配（Provider vs SourceSet） | 改用 `sourceSet("main")` / `sourceSet("client")` 字符串重载；去掉不兼容的 `removeIf` |
| 9 | 完整构建默认用 Java 20 JVM，Loom 1.17.21 要求 ≥21 而失败 | 显式 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` 后 BUILD SUCCESSFUL |
| 10 | 默认 `java`/`cd` 在后台命令中不跨调用保持 | 每条命令内显式 `cd` 与 `export JAVA_HOME/PATH` |
| 11 | `DegradationTest` 缺 `Song` import、`FakeAudioBackend` 为 final 无法匿名继承 | 补 import；给 Fake 加 `rejectPrepare` 标志而非继承 |

## 7. 未验证项（真实清单，禁止写成通过）

| 类别 | 场景 |
|---|---|
| Windows Java 1.21.11 + Fabric | `runClient` 启动、进入世界、HUD、键位、真实音频引擎、3/10/30 分钟音频、快速切歌 100 次、内存、退出无残留进程 |
| Windows / Android Bedrock 1.21.130 | `.mcaddon` 导入、`/playsound` 自定义事件、表单 UI、`stopsound` 效果、动态属性重进保留、音量 0、包未启用、歌曲缺失、快速点击 50 次、维度切换 |
| 联机 | 第三方服务器联机时听歌、Java 与基岩玩家同时在线（验证不依赖服务端） |
| 稳定性 | 连续播放 2 小时、低内存与慢存储、资源包下载失败、包卸载/重载 |
| 本地化 | 中文/日文/emoji/超长标题、非法 UTF-8、路径穿越真机表现 |
| 性能 | 解码延迟、掉帧、线程/句柄计数、GC 长暂停影响 |

以上全部需要用户提供的真机环境，见 [implementation-plan.md](implementation-plan.md)。

## 8. 已知限制

1. 基岩暂停/恢复/seek/进度/完成事件均为 `UNKNOWN`，只实现停止；进度条只显示估算时长且不可拖动。
2. `playSound` 在 2.4.0 返回 `void`，无 SoundInstance；切歌为普通切歌，无 Automix。
3. `/stopsound` 能否中断自定义声音未真机验证，失败时明确提示而非假装已停。
4. Fabric HUD/键位为接口占位（`ui/UiHooks.java`），需 1.21.11 真机核实 API 后实现。
5. QQ 音乐为**非官方、仅供个人使用**集成：`QQMusicSign.MUSIC_KEY` 为占位符，真实在线播放需用户自备 Cookie 并同步最新密钥并自行验证；真实在线检索/试听、Fabric 真机发声、基岩真机导入均未验证，见 [qq-music.md](qq-music.md)。
6. Loom 那条 `Cannot remap modifiers` 警告未消除，需真机确认是否影响运行。

## 9. 回滚

各模块独立，无二进制耦合；配置带 `configVersion`，未来版本拒绝猜测；
基岩包 `version: [1,0,0]`，回退用更低版本号重打包。详见 [release.md](release.md)。
