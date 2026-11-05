# THIRD_PARTY_NOTICES

本文件记录本项目引用、参考或链接到的第三方来源、许可状态与处理方式。
结论优先遵循「不复制、不再分发、不声称授权」原则。

## 1. 产品与算法参考（仅行为参考）

| 项目 | 来源 | 许可状态 | 本项目处理 |
|---|---|---|---|
| MuoniumPlayer（旧线） | <https://github.com/zihe114514/MuoniumPlayer> @ `bb38b4720b75899c46fe08459f37a9df8be3505a` | GitHub `license.spdx_id = NOASSERTION`；根目录只有 Forge/JAADEC 等第三方许可文件，**无项目自身 LICENSE** | `REFERENCE_ONLY`。只提取行为契约写入 `docs/source-audit.md`，**不复制任何源码、字体、贴图、音频、歌词、jar** |
| MuoniumPlayer-multiversion | README.md:8 指向的新版本仓库 | 未审计 | 未使用，仅记录存在；不引用其代码 |

因许可不可识别且缺少再授权条款，本项目采用独立实现，不批量复制大段原代码，
不为代码/素材/歌词/音频作未经许可的再分发，在取得作者明确许可前不宣称官方续作或已获授权。

## 2. 上游构建中出现的第三方依赖（仅记录，不引入本项目）

以下来自上游 `build.gradle` 的真实行（行号见 `docs/source-audit.md`）。
本项目 **不引入** 这些依赖，也不分发其二进制；仅用于理解上游行为边界。

| 组件 | 上游出处 | 备注 |
|---|---|---|
| ForgeGradle 2.1-SNAPSHOT / MinecraftForge | `build.gradle:19,24,61` | 旧线 1.8.9 构建链，本项目用 Fabric |
| Shadow 2.0.4 | `build.gradle:21,25`；`maven-repo/.../shadow-2.0.4.jar` | 上游自带本地 jar，不拷贝 |
| Cadence（网易云 / QQ 音乐 API，Kotlin） | `README.md:159` | 平台协议参考，未引入 |
| qq-music-api | `README.md:160` | 微信扫码协议参考，未引入 |
| KuGouMusicApi | `README.md:161` | 酷狗协议参考，未引入 |
| api-enhanced（网易云 API / 一起听） | `README.md:162` | 「一起听」为本项目明确禁止功能 |
| AMLL TTML DB | `README.md:163` | 逐字歌词词库格式参考，未引入数据 |
| ZXing / Gson 2.11 / JNA 5.14 / Zstd-jni 1.5.6 | `README.md:125,143-144,127` | 未引入 |
| JSyn / JAADEC / FLAC encoder / 内置 MP3·AAC·FLAC 解码器 | `src/main/java/repackage/**` | 上游内嵌第三方解码器（`repackage/`），未拷贝 |
| kotlin-stdlib 2.4.0 / closure-compiler | `build.gradle:133,135` | 未引入 |
| 字体 `pf_normal.ttf`(10.7MB) / `pf_middleblack.ttf`(10.6MB) / `arial*.ttf` / `sf*.otf` | `src/main/resources/muonium/fonts/**` | **未拷贝**，字体许可未知 |

## 3. 本项目实际引入的依赖

| 依赖 | 版本 | 许可 | 用途 |
|---|---|---|---|
| JUnit 5 (Jupiter / Platform) | 5.11.4 | Eclipse Public License 2.0 | 仅 `common-core` 测试域 |
| Fabric Loader / Fabric API / Yarn / Fabric Loom | 见 `gradle/libs.versions.toml` | 各自上游许可 | 仅 `fabric-1_21_11` 模块，运行时由用户自行安装 |
| TypeScript / esbuild / `@minecraft/server` / `@minecraft/server-ui` | 见 `bedrock-addon/package.json` | 各自上游许可 | 仅 `bedrock-addon` 构建期与类型声明；不打包进 `.mcaddon` 之外的任何位置 |

## 4. 素材

- `test-fixtures/audio/*.ogg`：由 ffmpeg 在本仓库内**程序化生成的无版权正弦波/和弦测试音**，
  不含任何第三方录音，非上游素材。
- `test-fixtures/covers/*.png`：程序化生成的纯色占位图。
- `bedrock-addon/pack/resource_pack/sounds/muonium/*.ogg`：同上，无版权示例音轨。
- 仓库中**不包含**任何商业音乐、封面、歌词、Cookie、Token、二维码票据或账号数据。

## 5. 官方资料引用（仅文档，不是代码依赖）

- Minecraft Wiki：Bedrock Edition 1.21.130（`@minecraft/server` 稳定 2.4.0）
- Microsoft Learn：Script API / Player.playSound / Latest Platform Version Guidance
- FabricMC Wiki：Fabric Loom（`splitEnvironmentSourceSets()`）
- Fabric Meta / Maven：`1.21.11`、`yarn 1.21.11+build.6`、`loader 0.20.1`、`loom 1.19.3`

来源与查询时间统一记录在 `docs/version-lock.md`。
