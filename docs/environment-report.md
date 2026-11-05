# 环境配置报告

> 用户要求「开始前要先配置好环境，和技能」。本报告记录实际配置与修复。

## 1. 技能加载

已加载工程技能 **「编程大神」**（`dev-god`）并遵循其执行原则：
继承现场、按目标行动、只问关键缺口、用证据判断、区分观察/推断/未验证、
不虚构执行结果、构建通过不等于功能通过。
另按需读取其《下载与备用源》参考以处理 GitHub 不可达问题。

## 2. 工具链（配置前 → 配置后）

| 工具 | 配置前 | 配置后 |
|---|---|---|
| Java | `openjdk 20` | **`openjdk 21.0.12.1`**（`apt install openjdk-21-jdk-headless`） |
| Gradle | 未安装 | **8.14.3**（腾讯云镜像下载 137 MB，`/opt/gradle-8.14.3`；仓库内 wrapper 已生成） |
| Node / npm | v22.13.1 / 11.9.0 | 保持不变 |
| ffmpeg / ffprobe | 已装 | 保持不变（真实生成与探测 OGG） |
| Pillow / Python | 3.11.1 + Pillow | 保持不变（生成封面） |
| git / curl / unzip / zip | 已装 | 保持不变 |

`sdk install java 21.0.9-tem` 曾因 `Recv failure: Connection reset by peer` 失败，
改用系统 apt 源安装 OpenJDK 21 成功。

## 3. 环境故障与修复

| 故障 | 现象 | 修复 |
|---|---|---|
| 全局 Gradle 初始化脚本损坏 | `/root/.gradle/init.gradle` 第 3 行 URL 未加引号、第 4 行 `mavelCentral()` 拼写错误 → 所有 Gradle 构建在 startup 阶段失败 | 重写为合法 Groovy（腾讯云 Maven 镜像 + `mavenCentral()`），未改项目配置、未关闭 TLS 校验 |
| `github.com:443` 不可达 | `git clone` 132 s 超时 | 改用 `api.github.com`（元数据 / tree / blobs）+ `codeload` 归档；归档限速中断，改以 API 为准 |
| `services.gradle.org` 307 到不可达的 GitHub releases | wrapper 下载会失败 | wrapper 属性保留官方 URL（供用户干净环境），本地用 `/opt/gradle-8.14.3/bin/gradle` 执行同等任务 |
| GitHub 匿名 API 限流 | 23 个关键文件只成功 9 个（403） | 以已取得的 9 个文件做行号级审计，其余按 tree 路径分类，不臆测内容 |
| npm registry metadata 首次超时 | 15.9 MB 元数据下到 13.4 MB 超时 | `npm install` 拉具体 tarball，5 s 完成（7 packages） |

## 4. 网络连通性实测（2026-11-04 UTC）

| 域 | 结果 |
|---|---|
| `api.github.com` | 200 |
| `codeload.github.com` | 301 / 200（限速） |
| `github.com` / `raw.githubusercontent.com` | 超时 |
| `meta.fabricmc.net` / `maven.fabricmc.net` | 200 |
| `repo1.maven.org` / `plugins.gradle.org` / `registry.npmjs.org` | 200 |
| `services.gradle.org` | 200（307 到不可达域） |
| `mirrors.cloud.tencent.com/gradle` | 200 |
| `mirrors.tencent.com/nexus` (Maven 镜像) | 200 |
| `piston-meta.mojang.com` | 200 |
| `learn.microsoft.com` | 302/200 |
| `minecraft.wiki` | 403（改用搜索结果中的镜像条目） |

## 5. 沙箱限制（决定验收边界）

- 无 Minecraft Java / Bedrock 客户端，无 GUI，无 Windows / Android 设备；
- 无法执行任何真机测试（导入 `.mcaddon`、`runClient`、联机、维度切换、连续播放 2 小时）；
- 因此所有真机项标 `UNKNOWN`，**未在报告中写成通过**。

## 6. 复现命令

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
cd muonium-player-next          # 进入仓库根目录
./gradlew clean build test checkClientOnly
cd bedrock-addon && npm ci && npm run check
cd .. && node tools/check-manifests.mjs && node tools/check-sound-events.mjs \
  && node tools/validate-library.mjs && node tools/generate-test-vectors.mjs \
  && node tools/pack-addon.mjs
```
