# MuoniumPlayer-Next

![MuoniumPlayer · 星穹旅驿官方出品](docs/assets/banner.png)

**纯客户端双端音乐播放器 · 星穹旅驿官方出品**：Java 版用 Fabric 客户端 Mod，基岩版用官方 Add-On。
两端各自读取本机曲库、各自在本机出声，**与服务器完全无关**。

> 本项目是从零独立实现，仅以 [zihe114514/MuoniumPlayer](https://github.com/zihe114514/MuoniumPlayer)
> 作为产品行为参考。上游许可证 `NOASSERTION`，本仓库不复制其源码与素材，
> 也不宣称是官方续作或已获授权。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 版本口径（别搞错）

| 端 | 目标版本 | 内容更新 |
|---|---|---|
| Java 版 | **1.21.11** | 驯兽之灾（Mounts of Mayhem），Java 旧版号体系最后一版 |
| 基岩版 | **1.21.130** | 同一批内容的基岩对应版本，仍用自己的编号体系 |

两端 manifest 只声明各自版本，**不跨端引用**，也不要求两端版本号字符串相等。

## 仓库结构

```text
common-core/        纯 Java 21 领域核心（零 Minecraft / 零第三方运行时依赖）
common-schema/      跨端 JSON Schema（单一来源指向 shared-schemas/schema/）
music-import-cli/   本机导入 / 校验 / ffprobe / OGG 转码 / .mcaddon 打包
fabric-1_21_11/     Java 1.21.11 Fabric 纯客户端 Mod（可选构建）
bedrock-addon/      1.21.130 行为包 + 资源包 + TypeScript 严格模式源码
shared-schemas/     JSON Schema、示例、测试向量
test-fixtures/      程序化生成的无版权音频 / 封面 / 歌词
tools/              清单、声音事件、曲库校验与打包脚本
docs/               审计、版本锁定、能力矩阵、协议、安全、测试报告、计划、风险
```

## 快速开始

```bash
# 1) JVM：构建 + 60 个单元测试 + 纯客户端闸门
./gradlew clean build test checkClientOnly

# 2) Bedrock：类型检查 + 打包 + 语法校验
cd bedrock-addon && npm ci && npm run check && cd ..

# 3) 全量校验与制品
node tools/check-manifests.mjs
node tools/check-sound-events.mjs
node tools/validate-library.mjs
node tools/generate-test-vectors.mjs
node tools/pack-addon.mjs       # -> bedrock-addon/dist/muonium-player.mcaddon
```

导入基岩包后，在聊天输入 `/scriptevent muonium:open` 打开表单播放器。

Fabric 模块默认不纳入 `./gradlew build`——Loom 需下载 Minecraft 客户端与映射，
且「可启动」只能在真机验证。显式启用：

```bash
./gradlew -PincludeFabric=true :fabric-1_21_11:build
```

## 已完成边界（诚实）

| 项 | 状态 |
|---|---|
| 上游审计 / 许可证风险 / commit 基线 | ✅ 真实完成，见 [docs/source-audit.md](docs/source-audit.md) |
| 多模块仓库、依赖版本锁定 | ✅ 真实完成，见 [docs/version-lock.md](docs/version-lock.md) |
| `common-core` 模型 / 状态机 / 队列 / 歌词 / 缓存 / 配置迁移 | ✅ 实现且 **60 个 JVM 测试全部通过** |
| Fabric 空客户端 Mod + 本地曲库 + 假音频后端 + 控制器 | ✅ 源码完整，**Loom 未编译验证**（需真机/上游资源） |
| Bedrock 空 BP/RP + 声音事件 + 表单 UI + 曲库 MVP | ✅ `tsc --strict` / esbuild / `node --check` / 清单校验全部通过 |
| 基岩暂停 / 进度 / seek / 完成事件 | ⛔ **一律 UNKNOWN，未实现假功能**，见 [docs/bedrock-capability-matrix.md](docs/bedrock-capability-matrix.md) |
| Windows Java / Windows Bedrock / Android Bedrock 真机 | ⛔ **本沙箱无法验证**，需用户提供，见 [docs/implementation-plan.md](docs/implementation-plan.md) |

## 硬性边界

- 没有服务端插件 / Mod / 代理 / 媒体服务器，没有房间同步、跨玩家控制、Geyser/Floodgate 适配。
- 只有本机导入器会访问音乐平台接口，且由用户主动触发；游戏内播放器不发平台请求。
- 曲库一旦导入，断网也能完整播放。
- 仓库不含任何音乐、封面、歌词、Cookie、Token 或账号数据；测试素材全部程序化生成。

## 数据清除

- Java 端：删除 `游戏目录/config/muonium/` 即可清除配置与缓存；
  `ModConfig.clearAll()` 提供一键清除入口。
- 基岩端：卸载行为包与资源包；`state.clearSettings()` 清除动态属性设置。
- 详细步骤见 [docs/release.md](docs/release.md)。

## 文档索引

[审计](docs/source-audit.md) · [版本锁定](docs/version-lock.md) · [基岩能力矩阵](docs/bedrock-capability-matrix.md) ·
[协议](docs/protocol.md) · [Song Schema](docs/song-schema.md) · [歌词 Schema](docs/lyric-schema.md) ·
[安全](docs/security.md) · [发布](docs/release.md) · [测试报告](docs/test-report.md) ·
[实施计划](docs/implementation-plan.md) · [风险清单](docs/risk-register.md) · [环境报告](docs/environment-report.md)
