# 安全与隐私

逐条对应提示词第 10 节，标注实现位置与验证方式。

| # | 要求 | 实现 | 验证 |
|---|---|---|---|
| 1 | 配置默认 `client_only` | `ConfigMigrator` v3 强制 `clientOnly=true`；`fabric.mod.json` `environment: client` | `ConfigMigratorTest.defaultConfigIsClientOnly` |
| 2 | 网络请求显示目标域名与用途 | 游戏内播放器**零网络请求**；仅 `music-import-cli` 有外部进程/平台访问，README 明示 | `checkClientOnly` 闸门 |
| 3 | 平台凭据只存本机，不进世界存档和资源包 | `LibraryValidator` 拒绝 `cookie/token/password/phone/tempUrl/streamUrl`；BP manifest 无凭据 | `ImportCliTest.credentialsAndTempUrlsAreRejected` |
| 4 | Token 加密并支持一键清除 | 阶段 6 导入器实现；当前仓库不存任何 Token；`clearSettings()` / `ModConfig.clearAll()` | `docs/release.md` 数据清除章节 |
| 5 | 日志过滤 Cookie/Authorization/二维码票据/手机号 | Java `MuoniumError.redact()`；TS `errors.redact()`；`JavaPlatform.SafeLogger` | `EventBusTest.errorDetailsNeverLeakCredentials` |
| 6 | 不执行远程下发脚本 | 无动态 `import()` / `eval` / `Function()`；`main.js` 由本地 esbuild 构建 | `bedrock-addon/src/main.ts` 只有静态 import |
| 7 | 不自动更新可执行代码 | 无更新器、无远程代码拉取 | 源码审阅 |
| 8 | 曲库包只被当作内容，不授予任意文件权限 | 曲库 JSON 编译期内联；无文件系统 API 调用；路径校验 | `LibraryValidator.isSafePath` |
| 9 | JSON 解析限制深度、长度、数量 | `Json`：`MAX_INPUT_CHARS=1,000,000`、`MAX_DEPTH=64`、`MAX_ELEMENTS=100,000`，拒绝 `__proto__` / `constructor` / `prototype` | `JsonSafetyTest.*` |
| 10 | ZIP 防路径穿越 / Zip Slip / 压缩炸弹 | `Packer.addTree()` 逐条 `isSafePath`，拒绝 `..` / 绝对路径 / 盘符 / URL / NUL | `ImportCliTest.pathTraversalAndUrlsAreRejected` |
| 11 | FFmpeg 固定版本/受控参数，不拼 shell | `Packer` 用 `List<String>` 固定参数 + `ProcessBuilder`，无 shell 字符串 | CLI 真实 `probe` / `transcode` |
| 12 | 外部进程默认关闭，需用户显式启用 | 游戏内播放器不调用外部进程；只有显式运行 CLI 子命令才启动 | `ImportCli` 帮助文本 |
| 13 | 不收集听歌历史，遥测默认关闭 | `ConfigMigrator` 强制 `telemetryEnabled=false`、`crashReportingEnabled=false`；无上报代码 | `ConfigMigratorTest` |
| 14 | 崩溃上报需明确同意且可关闭 | 同上 | 同上 |
| 15 | 不隐藏 Mod、不伪装原版、不作弊 | `fabric.mod.json` 明示名称与 `environment: client`；无协议修改 | 源码审阅 |

## 代码审查闸门（第 6.3 节）

`./gradlew checkClientOnly` 精确扫描 **代码符号** 与 **import 路径**，
跳过注释与文档字符串，避免误报。发现以下任一即构建失败：

```
ServerPlayer / ServerPlayNetworking / ServerPlayNetworkHandler / MinecraftServer
Bukkit / org.bukkit / @Environment(SERVER) / geyser / floodgate
import "@minecraft/server-net" / "@minecraft/server-admin" / "@minecraft/server-gametest"
```

> 一次真实误报与修复：初版闸门把 `capabilities.ts` 里说明文字中的 `server-net`
> 判为违规。改为「只检查符号与 import 行」后，文档文本被排除，
> 真实 import 仍会被拦截。记录于此以免后续又退回粗暴全文匹配。

## 法律红线

明确禁止且未实现：解灰、绕过会员或地域限制、破解 DRM、逆向私有加密、
模拟官方客户端绕过风控、共享账号、批量抓取、公开分发付费内容、
用 Minecraft 网络包代理大音频流、在仓库提交音乐/封面/歌词/Cookie/Token/手机号。

上游许可证 `NOASSERTION`，本项目独立实现，不复制源码与素材，详见
[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 与 [source-audit.md](source-audit.md)。
