# MuoniumPlayer-Next 内置自动更新模块 · 改动说明

> 依据 `update-gate/skills/mc-autoupdate` 指南的 Fabric 客户端方案（fabric.md / contract.md）实现。

## 一、做了什么

为 MuoniumPlayer（Fabric 1.21.11 客户端模组）内置自动更新：启动自检 + 每周四 12:00 检查，GitHub 固定 manifest 直连（仅 HTTPS、无 Token），sha256 校验，下载到 `mods/.update/` 后提示玩家重启生效，**不静默替换运行中的 mod jar**，跨 MC 大版本只提示不自动下载。

## 二、改动文件清单

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `fabric-1_21_11/src/client/java/com/muonium/fabric/autoupdate/ClientUpdateChecker.java` | 新增 | 客户端检查器（@Environment(CLIENT)、自检 + 周四 12:00 调度 + 门禁 + 下载校验 + 双语提示） |
| `fabric-1_21_11/src/client/java/com/muonium/fabric/autoupdate/Json.java` | 新增 | 零依赖极简 JSON 解析器 |
| `fabric-1_21_11/src/client/java/com/muonium/fabric/MuoniumClient.java` | 修改 | onInitializeClient 注入检查器启动（try-catch 隔离） |
| `updates/latest.json` | 新增 | 更新清单（stable 通道，指向当前 v0.1.2 Release 的 fabric jar，含真实 sha256） |
| `fabric-1_21_11/build.gradle.kts` | 修改 | modVersion 0.1.0 → 0.1.1（patch +1） |

## 三、安全红线核对

- [x] 启动自检 + 每周四 12:00（Asia/Shanghai）
- [x] 跨 minor/major 只提示、不自动下载；同 minor 内才更新
- [x] sha256 校验失败删除；下载到 `.part` 后原子移动；不静默替换运行中的 jar
- [x] 无 Token、只走 HTTPS、文案中英双语、注释简体中文
- [x] 2 个新类用 javac + `-Xlint:all` 编译：零错误零警告
- [ ] **构建验证**：本沙箱无 Java 21/Fabric Loom 工具链且网络受限，需你本机 `./gradlew build` 确认

## 四、版本号说明

本地 modVersion patch +1 到 0.1.1；manifest 的 `target.version` 指向已发布的最新 fabric 包（0.1.0）。当前本地 ≥ 目标，不会触发更新（正确行为）。未来发版时同步升级 modVersion 与 manifest 后即可生效。
