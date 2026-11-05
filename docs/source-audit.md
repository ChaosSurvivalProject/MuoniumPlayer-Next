# 源码审计（阶段 0 产出）

审计时间：2026-11-05（CST）
基线：`bb38b4720b75899c46fe08459f37a9df8be3505a`，见 [REFERENCE_COMMIT.txt](../REFERENCE_COMMIT.txt)

## 1. 基线获取方式（真实，不虚构）

| 步骤 | 命令 / 接口 | 结果 |
|---|---|---|
| `git clone` | `git clone https://github.com/zihe114514/MuoniumPlayer.git` | **失败**：`Failed to connect to github.com port 443 after 132957 ms` |
| 仓库元数据 | `GET api.github.com/repos/zihe114514/MuoniumPlayer` | 200；`default_branch=main`，`license.spdx_id=NOASSERTION`，`size=59728` KB |
| commit | `GET .../commits?per_page=3` | `bb38b472… 2026-10-30T08:44:54Z xinshibaobao / Update README.md` |
| 文件清单 | `GET .../git/trees/main?recursive=1` | 973 entries，**809 blobs**，`truncated=false` |
| 源码内容 | `GET .../git/blobs/{sha}` | 下载 9 个关键文件成功；其余触发匿名限流（403 rate limit exceeded） |
| 归档 | `codeload.github.com/.../tar.gz/refs/heads/main` | HTTP 200 但限速中断（仅 1.5 MB），gzip 校验失败，未使用 |

> 因此：SHA、文件清单、许可证状态与已下载文件行号均为**实测**；
> 未取得源码的文件只按 tree 中的路径分类，不臆测其内容。

## 2. 决定性审计结论

### 2.1 这不是目标平台的代码

`README.md:4`（实测）：

> `> ## 这条线（Minecraft 1.8.9 / Forge / Java 8）已经停止更新`

`README.md:8` 指向新仓库 `MuoniumPlayer-multiversion`，说明支持
1.20.1 / 1.21.1 / 1.21.11 / 26.2（Fabric + Forge/NeoForge）。
`README.md:112` 明确「唯一支持的版本组合 = Minecraft 1.8.9 + Forge 11.15.1.2318」。
`build.gradle:39` 为 `sourceCompatibility = targetCompatibility = '1.8'`，
`build.gradle:61` 为 `version = "1.8.9-11.15.1.2318-1.8.9"`。

**结论**：参考仓库是旧线 Forge/Java 8 代码，与本项目目标（Fabric 1.21.11 / Java 21、
Bedrock 1.21.130 Add-On）二进制与 API 均不兼容，全部按 `REFERENCE_ONLY` / `REWRITE` 处理。

### 2.2 许可证风险：NOASSERTION

- GitHub API `license.spdx_id = NOASSERTION`。
- 根目录（tree 实测）**没有项目自身的 LICENSE**，只有第三方许可文件：
  `LICENSE-fml.txt`、`MinecraftForge-License.txt`、`MinecraftForge-Credits.txt`、
  `CREDITS-fml.txt`、`Paulscode IBXM Library License.txt`、
  `Paulscode SoundSystem CodecIBXM License.txt`、`THIRD-PARTY-NOTICES-JAADEC.txt`。
- `README.md:167-169`「## 许可」只说「Forge 与第三方许可声明保留在仓库根目录与 `docs/licenses/`」。

按提示词 1.2，本项目因此：
**默认只把源码作为理解产品行为的参考；采用独立实现；不批量复制大段原代码；
不为代码、素材、歌词或音频作未经许可的再分发；记录到 THIRD_PARTY_NOTICES.md；
在取得作者明确许可前不宣称官方续作或已获授权。**

### 2.3 上游明确禁止在本项目复刻的功能

| 功能 | 实测出处 | 处理 |
|---|---|---|
| 「一起听」房间 / 邀请码 / 房主播放状态同步 | `README.md:42`、`:66` | `DO_NOT_PORT`：违反「不做跨玩家同步」硬边界 |
| 网易云听歌记录实时上报 | `README.md:41`、`:62` | `DO_NOT_PORT`：不收集听歌历史，遥测默认关闭 |
| 扫码登录 / Cookie / VIP 音质 / 网盘 | `README.md:40`、`:54`、`:121` | `REFERENCE_ONLY`：仅本机导入器、用户主动授权、凭据不入包 |
| 在线聚合源（GD 音乐台）兜底 | `README.md:55` | `DO_NOT_PORT`：不做解灰 / 绕过地域或会员 |
| 动态封面（MP4 + ffmpeg） | `README.md:81`、`:120` | `REFERENCE_ONLY`：初版不做 |
| 全局快捷键 / 桌面 OSD / 灵动岛 | `README.md:45`、`:68`、`:76` | `REWRITE`：按目标平台分阶段实现 |

## 3. 代码资产清单（逐类标记）

标记含义：`REUSE_AS_IS` / `ADAPT` / `REWRITE` / `REFERENCE_ONLY` / `DO_NOT_PORT`。
本次审计后 **没有任何文件被标为 REUSE_AS_IS**（许可证不可识别 + 平台不兼容）。

| # | 审计项 | 上游真实路径（tree / 已下载文件） | 标记 | 本项目对应 |
|---|---|---|---|---|
| 1 | Mod 入口与生命周期 | `src/main/java/com/muoniumplayer/MuoniumPlayerMod.java`；`build.gradle:24` ForgeGradle | `REWRITE` | `fabric-1_21_11/.../MuoniumClient.java`（仅 `ClientModInitializer`） |
| 2 | 命令、按键、全局快捷键 | `README.md:68`、`:94`；`src/main/java/com/muoniumplayer/core/settings/**` | `REWRITE` | `ui/UiHooks.java` 接口占位（阶段 2） |
| 3 | GUI / HUD / 桌面歌词 / 通知 | `src/main/java/com/muoniumplayer/core/screens/ncm/**`（大量 renderer）、`core/rendering/**` | `REWRITE` | 基岩用官方表单 UI；Fabric HUD 留待真机核实 API |
| 4 | 播放状态机 | 上游 `AudioPlayer.java:100-218`（`play/playFrom/prepareStart/startPrepared/isPrepared`） | `ADAPT` | `common-core/playback/PlaybackStateMachine.java`（9 态转换表） |
| 5 | 音频设备 / 解码 / 缓冲 / 音量 / 淡入淡出 / Automix | `core/ncm/music/AudioPlayer.java`（662 行）、`repackage/com/jsyn/**`、`AacAudioDecoder.java` | `REWRITE` / `DO_NOT_PORT` | 阶段 1 假后端 `JavaPlatform.FakeAudioBackend`；真实引擎属阶段 2 |
| 6 | 网易云 / QQ / 酷狗适配器 | `core/ncm/music/CloudMusic.java`、`KuGouMusicService.java`、`core/ncm/qq/**`、`core/ncm/kugou/**` | `DO_NOT_PORT` | 仅 `Ports.MediaSource` 接口；平台导入器独立模块（阶段 6） |
| 7 | 登录 / 二维码 / Cookie / Token / 刷新 | `core/ncm/qq/QQWechatLogin.java`、`README.md:40` | `DO_NOT_PORT` | 不存凭据；`MuoniumError` 只保留 `PROVIDER_LOGIN_EXPIRED` 提示 |
| 8 | 歌曲 / 歌单 / 收藏 / 搜索 / 缓存 | `core/ncm/music/CloudMusic.java`、`MusicAudioCache.java`、`AudioCacheFiles.java` | `ADAPT` | `common-core/model`、`queue`、`playlist`、`cache` |
| 9 | 音源候选 / 质量选择 / 失败回退 | `AnalysisAudioSource.java`、`AudioContainerSupport.java` | `REFERENCE_ONLY` | 统一 `MuoniumError`；不做解灰与破解候选 |
| 10 | LRC / QRC / KRC / TTML 解析 | `core/screens/ncm/LyricParser.java`（769 行）、`QQQrcLyricService.java`、`AmllTtmlLyricService.java`（445 行）、`KrcLyrics.java`（258 行） | `ADAPT` | `common-core/lyric/LyricParsers.java`（独立重写）+ `LyricNormalizer` + `LyricCursor` |
| 11 | 封面下载 / 缩放 / 模糊 / 动态封面 | `core/rendering/DownloadDynamicIsland.java` 等 | `REWRITE` | 阶段 3；测试素材为程序化 PNG |
| 12 | FFmpeg / ffprobe 调用 | `core/ncm/music/FfmpegAnalysisAudio.java`（214 行） | `ADAPT` | `music-import-cli/Packer.java`（固定参数、异步排空 stderr） |
| 13 | 配置 / 迁移 / 日志 / 崩溃恢复 | `core/settings/JsonConfigStorage.java`、`ConfigPaths.java`、`core/screens/ncm/NCMPlayerConfig.java` | `REWRITE` | `common-core/config/ConfigMigrator.java`、`fabric/config/ModConfig.java` |
| 14 | 网络超时 / 重试 / 断线 / 限流 / 并发 | 上游各 HTTP 客户端 | `REWRITE` | `common-core/cache/CachePolicies.RetryPolicy`、`Json` 输入限制 |
| 15 | 许可证 / 二进制依赖 / 素材来源 | `libs/*.jar`、`maven-repo/**/shadow-2.0.4.jar`、`src/main/resources/muonium/fonts/*.ttf` (10.7 MB / 10.6 MB)、`IngameIME.dll` | `DO_NOT_PORT` | 见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)，一律不拷贝 |

### 3.1 明确不拷贝的二进制与素材

`libs/kotlin-stdlib-2.4.0.jar`、`libs/music-api-0.1.1.jar`、
`libs/zstd-jni-1.5.6-9-forge189.jar`、`maven-repo/.../shadow-2.0.4.jar`、
`src/main/resources/muonium/fonts/pf_normal.ttf` / `pf_middleblack.ttf` / `arial*.ttf` / `sf*.otf`、
`src/main/resources/muonium/IngameIME.dll`、`src/main/java/repackage/**`（内嵌第三方解码器）。
字体与 DLL 许可与来源不可确认，全部 `DO_NOT_PORT`。

## 4. 行为契约（规格化，均附实测行号与验收）

| # | 契约 | 上游依据 | 本项目规格 | 验收 |
|---|---|---|---|---|
| C1 | 点击下一首时当前曲目如何结束 | `PlayQueuePanel.java:31-32`（列表是 `CloudMusic.getQueuedNextSongs()` 的每帧快照，顺序调整只走 `moveQueuedNext`） | 当前曲目进入 `played`，从 `upcoming` 头部取下一首；UI 只订阅事件 | `PlayQueueTest.sequencePlaysInOrderThenStops` |
| C2 | 队列耗尽后停住还是回到歌单 | `PlayQueuePanel.java:161`（`clearQueuedNextSongs()`） | `repeat=LIST` 回源歌单开头；否则返回 `null` 并停住，由上层用 `autoplaySourcePlaylistId` 决定 | `PlayQueueTest.repeatListRefillsFromSourcePlaylist` / `sequencePlaysInOrderThenStops` |
| C3 | 随机如何避免重复 | 上游未见统一规则 | 用可恢复 `shuffleSeed` + 一次性确定性洗牌；刷新 UI 用 snapshot 恢复，绝不重新洗牌 | `ShuffleDeterminismTest.sameSeedProducesSameOrder` / `snapshotRestoreDoesNotReshuffle` |
| C4 | 单曲循环与列表循环优先级 | 上游未见统一规则 | `repeat=ONE` 优先，不推进队列、不写历史 | `PlayQueueTest.repeatOneHasPriorityOverListAndDoesNotAdvance` |
| C5 | 平台歌曲失效后跳下一候选 | `README.md:55`（GD 音乐台兜底） | **不做解灰/破解候选**；返回 `PROVIDER_TRACK_UNAVAILABLE`，提示「该来源暂不可用」，不崩溃 | `MuoniumError` 错误码 + `EventBusTest.errorDetailsNeverLeakCredentials` |
| C6 | 歌词缺行 / 重叠 / 负数 / 乱序 | `LyricParser.java:117`、`:200`（`result.sort(comparingLong(timestamp))`）、`:171`、`:178`（`findNearbySecondary(..., 80L)` 翻译容差） | 排序、钳制倒退时间、负数归零、缺失 `endMs` 用下一行起点/歌曲时长、±80ms 翻译对齐、超长行截断、巨型文件拒绝 | `LyricNormalizerTest.*`、`LyricParsersTest.*` |
| C7 | 逐字歌词必须真的逐字 | `LyricParser.java:46`（「真实逐字时间轴必须先于普通 LRC 解析」）、`:57`、`:125`（`hasRealWordTiming`）、`:121`（「普通 LRC 不再按字数平均切分，避免假逐字歌词」） | YRC/QRC/增强 LRC/TTML 才产生 `words`；LRC 只行级；不伪造字符时间轴 | `LyricParsersTest.qrcProvidesRealWordTiming` / `lrcSupportsMultipleTimestampsPerLine` |
| C8 | 登录过期提示与 Token 清理 | `README.md:121`（不登录也能播放公开曲目） | 本项目不存游戏内 Token；导入器凭据只在本机，`PROVIDER_LOGIN_EXPIRED` 提示重新授权 | `MuoniumError` + `docs/security.md` |
| C9 | FFmpeg 缺失时哪些功能降级 | `README.md:120`（「没装只会丢掉动态封面与少数容器的播放」）、`FfmpegAnalysisAudio.java:26`（「ffmpeg 不存在、解码失败、超时，全部返回空窗口——分析退回不可分析，播放不受影响」） | `probe` 返回 -1、`transcode` 明确失败；曲库播放不受影响；外部进程默认关闭、调用即视为显式启用 | `ImportCliTest` + CLI 真实 `probe/transcode` |
| C10 | 外部进程不能挂死 | `FfmpegAnalysisAudio.java:186`（「ffmpeg 的 stderr 必须一直被读走：管道满了子进程会停在写调用上」）、`:149-179`（固定参数列表） | `Packer.run()` 用 `List<String>` 固定参数、不拼 shell，异步排空 stdout/stderr | CLI 真实转码 5 秒 OGG 通过 |
| C11 | 切世界 / 退出 / 失焦 | 上游未形成可测规格 | shutdown hook 释放后端与订阅；EventBus `close()` 清空订阅；失焦策略留待真机 | `EventBusTest.closeReleasesAllSubscriptions`；真机项 UNKNOWN |

## 5. 明确不能 / 不会实现的部分

1. **真机验证**：Windows Java 1.21.11、Windows/Android Bedrock、第三方服务器联机、
   维度切换、2 小时连续播放等，本 Linux 沙箱无 Minecraft 客户端与 GUI，全部 `UNKNOWN`。
2. **基岩精确音频控制**：`playSound` 在 2.4.0 稳定类型里返回 `void`，
   没有 SoundInstance / 完成事件 / 真实位置；暂停、恢复、seek、进度条、无缝切歌一律不实现假功能。
3. **任意远程音频**：基岩脚本不能把任意 URL 的 MP3 喂给声音系统；必须走导入器转 OGG + 声音事件。
4. **通用桌面文件浏览器（基岩）**：Script API 无通用文件系统 API。
5. **平台破解 / 解灰 / 绕过会员 / DRM**：提示词明令禁止。
6. **联机同步**：不做房间、DJ、跨玩家点歌、全服播放、Geyser/Floodgate 适配。
7. **上游素材**：字体、jar、DLL、音频、封面、歌词一律不拷贝。
8. **Fabric HUD / 键位**：1.21.11 的 Fabric API 类名与签名需在真机解包核实后才写，
   上游 `docs/PORTING-1.20.1-FABRIC.md:16` 也明确「不许猜 API」。
