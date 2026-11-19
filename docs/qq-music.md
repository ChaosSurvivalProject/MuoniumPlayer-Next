# QQ 音乐集成（非官方 · 仅供个人使用）

> **法律与边界声明（务必先读）**
>
> - QQ 音乐的歌曲、封面、歌词等内容受版权保护，腾讯**未提供**面向第三方的公开播放 API。
>   本项目对接的是社区维护的**非官方接口**，稳定性与合规性无法保证，且**与腾讯无任何关联、未获授权**。
> - 本集成**仅供个人使用**：你只能用**自己的账号 Cookie** 检索、试听、导入你有权访问的内容。
> - 本项目**绝不内置任何歌曲 / 封面 / 歌词等版权素材**，**绝不破解 DRM**，**绝不批量下载或分享**。
>   所有音频只落在你本机的曲库目录，作为本地文件播放。
> - 接口或签名算法一旦变动导致不可用属**预期内风险**，请按本文「排错」章节处理，不要以此为由要求内置凭据。
> - 是否使用本集成、使用哪个账号、下载哪些内容，由你自行决定并自行承担相应责任。

本文对应实现：`music-providers/`（纯 JVM 适配层）、`music-import-cli`（桌面导入器）、
`fabric-1_21_11`（Fabric 客户端播放器）、`bedrock-addon`（基岩表单导入，伴随流程）。

---

## 1. 两种使用方式

| 端 | 方式 | 触发 | 网络位置 |
|---|---|---|---|
| **Fabric（Java 1.21.11）** | 实时流式播放 | 游戏内点播 QQ 曲目 | 解析直链→下载→转码 OGG→本地临时文件→原生音频后端播放 |
| **桌面导入器** | 导入到本地曲库 | 命令行 `qqimport` | 下载→转码 OGG→写入曲库；之后断网可播 |
| **基岩（Bedrock 1.21.130）** | 仅导入表单（伴随流程） | 聊天 `/scriptevent muonium:qq` | 基岩端**只收集 songmid 并给出桌面命令**，不联网、不下载 |

> **为什么基岩端不能直接流式播放**：官方 Script API 没有通用桌面文件系统，也没有把任意远程音频流喂给
> 声音系统的能力（`@minecraft/server-net` 为 BDS/Beta 专用，本项目明确不用）。因此基岩端走
> 「**游戏内收集 songmid → 桌面端 `qqimport` 导入 → 重新加载曲库**」的伴随流程。
> 详见 [bedrock-capability-matrix.md](bedrock-capability-matrix.md)（两条 QQ 相关条目）。

---

## 2. 统一音乐源架构（四链路隔离）

`common-core` 早已预留 `Ports.MediaSource` 与 `Ports.AudioBackend` 两个注入式端口，`music-providers`
只是**实现已有扩展点**，没有改动核心，也没有把网络/文件依赖带进游戏运行时：

```text
网络(QQ 非官方接口)  →  解码(ffmpeg 转 OGG)  →  播放(本地 OGG→音频后端)  →  GUI(只读订阅播放状态)
   HttpPort              Transcoder              Ports.AudioBackend          EventBus 快照
   注入 Fake 可测          注入 Fake 可测          注入 Fake 可测              纯 Java
```

- 所有网络访问通过 `HttpPort`、转码通过 `Transcoder` 接口；单元测试注入 Fake，**不联网、不触碰版权内容**。
- 游戏内播放器**不主动发起平台请求**；一切搜索/导入/点播都由用户显式触发。
- 凭据 Cookie **仅存于本机内存 / 环境变量 / 你显式指定的文件**，**绝不写入仓库、曲库清单或日志**。
  日志经 `MuoniumError.redact()` 过滤 Cookie / Authorization / 二维码票据 / 手机号。

---

## 3. 获取你自己的 Cookie（用户自备）

1. 用浏览器登录 <https://y.qq.com/>（需你自己已登录的账号）。
2. 打开开发者工具（F12）→ Network（网络）→ 刷新页面 → 任选一个 `y.qq.com` 请求。
3. 在请求头里复制完整的 `Cookie` 值，确认其中含有 `uin=` 与 `qm_key=`（或 `skey=`）字段。
4. 把 Cookie **粘贴到一个本机文件**（例如 `~/.muonium/qq_cookie.txt`），或用环境变量传入。

> **不要把 Cookie 提交到 Git、放进曲库、贴进 Issue 或日志。** 它等同于你的账号凭据。
> 本项目不提供任何"内置账号"或扫码登录，也不保存 Cookie 到任何持久化配置。

---

## 4. 桌面导入器命令

前置：Java 21；`ffmpeg` 在 `PATH` 中（或用环境变量 `MUONIUM_FFMPEG` 指定路径）。
所有子命令都支持 `--fake`（内置合成响应，不联网、不触碰真实版权内容，**仅用于 CI/自测**）。

### 4.1 搜索

```bash
# 真实（需 Cookie）；Cookie 来自文件或环境变量
./gradlew :music-import-cli:run --args='qqsearch --query 关键词 --cookie ~/.muonium/qq_cookie.txt'
# 或
QQMUSIC_COOKIE='uin=...; qm_key=...' ./gradlew :music-import-cli:run --args='qqsearch --query 关键词'

# 自测（不联网）
./gradlew :music-import-cli:run --args='qqsearch --query 测试 --fake'
```

输出每条结果的 `songmid`（方括号内），供后面导入使用。

### 4.2 歌词

```bash
./gradlew :music-import-cli:run --args='qqlyric <songmid> --cookie ~/.muonium/qq_cookie.txt'
```

QQ 歌词接口返回 `base64(gzip(LRC))`，适配器解压后交给 `common-core` 的 `LyricParsers` 统一解析。

### 4.3 导入到本地曲库

```bash
./gradlew :music-import-cli:run --args='qqimport <songmid> --cookie ~/.muonium/qq_cookie.txt --library <曲库目录> [--name 标题]'
```

流程：解析播放直链（`musicu.fcg` 的 `vkey.GetVkeyServer`）→ 下载 → ffmpeg 转 OGG Vorbis
→ 生成带完整元数据（标题/歌手/专辑/时长/封面 URL/`personal-use` 许可声明）的曲目并追加到
`library.json`（同 id 去重）。导入后断网即可播放。

> 每次导入只处理你显式指定的单个 `songmid`，不做批量抓取、不做整歌单搬运。

### 4.4 凭据来源优先级

1. `--cookie <文件>`（读取并 `trim`）
2. 环境变量 `QQMUSIC_COOKIE`
3. `--fake` 模式使用合成值 `uin=0; qm_key=FAKE`

`uin` 可用 `--uin` 覆盖，否则从 Cookie 里提取 `uin=`，缺省 `0`。

---

## 5. Fabric 实时流式播放

`QQMusicIntegration.streamingBackend(nativeBackend, tempDir, cookie, uin, logger)` 用一个
`QQMusicAudioBackend` 包裹真实音频后端，实现"点播 → 解析直链 → 下载 → 转码 OGG → 委托播放"：

- `prepare(song)`：仅接受 `source.provider()=="qq"` 的曲目；下载/转码到 `tempDir`，把 OGG 本地路径
  注入内层后端；任一环节失败或内层拒绝都会**清理临时文件**。
- `stop()` / `close()`：停止内层并删除临时 OGG，**退出无残留**。
- `capabilities()`：`completionEvent` 与 `gapless` 恒为 `false`（**UNKNOWN**，未验证），
  其余能力透传内层但也需真机验证，未验证前 UI 按"不支持"处理。

> **现状（诚实）**：Fabric 模块目前是**空客户端 Mod**——本地曲库 + 假音频后端 + 控制器已就绪并通过
> Loom 编译，但真实音频引擎、HUD、键位仍为接口占位（`ui/UiHooks.java`），需在 **1.21.11 真机**核实
> API 后实现。因此"游戏内流式播放"目前提供的是**可编译的集成骨架**，实际发声依赖阶段 2 的原生音频后端。
> 详见 [test-report.md](test-report.md) §2 与 [implementation-plan.md](implementation-plan.md)。

---

## 6. 基岩伴随流程

1. 导入 `.mcaddon`，在世界里同时启用行为包与资源包。
2. 聊天输入 `/scriptevent muonium:qq` 打开导入表单。
3. 在表单里粘贴 QQ 分享链接或直接填 `songmid`。表单用 `parseSongmid()` 从链接/文本里提取形如
   `003XXXX` 的 mid；解析成功后用 `world.setDynamicProperty("muonium:pending_qq_import", mid)` 暂存，
   并提示你在**桌面端执行** `qqimport <songmid>`。
4. 桌面端导入到该世界的曲库目录后，重进世界/重新加载曲库即可播放。

> 基岩端**不发起任何 HTTP 请求、不下载音频、不解析签名**；它只做"收集 mid + 提示命令"。
> 详解见 [bedrock-capability-matrix.md](bedrock-capability-matrix.md)。

---

## 7. 失败降级与排错（对应 P0 验收）

错误通过 `ProviderException.code()` 暴露稳定错误码，`retryable()` 告诉上层是否值得重试：

| 错误码 | 触发 | `retryable` | 处理 |
|---|---|---|---|
| `QQ_MUSIC_REQUIRES_COOKIE` | 未提供 Cookie | 否 | 按本文 §3 配置自己的 Cookie |
| `AUTH_EXPIRED` | HTTP 401/403 | 否 | Cookie 失效或无权限，重新获取 Cookie |
| `RATE_LIMITED` | HTTP 429 | 是 | 平台限流，稍后重试（建议指数退避） |
| `UPSTREAM` | HTTP 5xx | 是 | 平台服务暂不可用，稍后重试 |
| `TIMEOUT` | 请求/下载超时 | 是 | 网络不可达或响应慢，检查网络后重试 |
| `NETWORK` | 其他 4xx / 网络错误 | 是 | 请求被拒绝，稍后重试 |
| `SIGN` | `vkey` 为空 / 签名失效 | 否 | 需同步最新 `QQMusicSign.MUSIC_KEY`（见下） |
| `LYRIC` | 歌词解压/解析失败 | 否 | 该曲目无可用歌词，按"无歌词"处理 |
| `TRANSCODE` | ffmpeg 失败 | 否 | 确认 ffmpeg 可用（`MUONIUM_FFMPEG`） |
| `PARSE` | 接口响应无法解析 | 否 | 接口结构可能已变更，查看脱敏日志 |

**签名占位值说明**：`QQMusicSign.MUSIC_KEY` 当前是占位符 `REPLACE_WITH_SYNCED_QQ_MUSIC_KEY`，
**不是可用密钥**，只用于让调用结构可编译。**单元测试用注入的假签名器，不依赖真实密钥**。
若你把真实接口跑起来并遇到 `SIGN` / `vkey` 为空，需自行从最新版 QQ 音乐 Web 端或社区版 API 同步该常量；
本项目**不内置、不分发**任何真实密钥。

**临时文件**：下载/转码失败、内层后端拒绝、流式 `stop()` 都会清理中间 `.bin` / 临时 `.ogg`，
不留半截文件（由 `DegradationTest` 覆盖）。

---

## 8. 测试状态

- `music-providers` 共 **25 个单元测试全部通过**（含 7 个降级/清理测试），全用 Fake，**不联网**。
- 全量 JVM `build test checkClientOnly` 在 Java 21 下 **BUILD SUCCESSFUL**，纯客户端闸门 PASS。
- CLI `--fake` 端到端已实测：`qqsearch`→结果、`qqlyric`→两行、`qqimport`→生成 OGG 与 `library.json`。
- **未验证**：真实 Cookie 下的在线检索/试听、Fabric 真机发声、基岩真机导入——这些需要你自己的账号与设备。

详见 [test-report.md](test-report.md) 与 [real-device-testing.md](real-device-testing.md)。
