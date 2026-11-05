# 风险清单

| # | 风险 | 等级 | 现状 | 缓解 / 待办 |
|---|---|---|---|---|
| R1 | 上游许可证 `NOASSERTION`、无项目自身 LICENSE | **高** | 已识别 | 全部 `REFERENCE_ONLY`；独立实现；不复制源码/素材/二进制；`THIRD_PARTY_NOTICES.md`；不宣称官方续作 |
| R2 | 参考仓库是 1.8.9 / Forge / Java 8 旧线，与目标平台不兼容 | 高 | 已识别 | `docs/source-audit.md` §2.1；所有平台代码重写 |
| R3 | 提示词点名的 `net.fabricmc.fabric-loom-remap` 插件实际不存在 | 中 | 已核实并绕开 | 改用标准 `fabric-loom 1.19.3` + `splitEnvironmentSourceSets()`；`version-lock.md` 记录差异 |
| R4 | Fabric Loom 编译与「1.21.11 客户端可启动」未验证 | **高** | 未验证 | 模块默认不纳入 `./gradlew build`，避免伪装通过；需 Windows 真机 + Mojang 资源；`test-report.md` 如实记录 |
| R5 | 1.21.11 存在混淆/未混淆变体，映射与 remap 行为可能不同 | 中 | 未验证 | 不使用未混淆专属 API；`version-lock.md` 列为待测 |
| R6 | 基岩 `playSound` 返回 void、无实例/位置/完成事件 | **高** | 已从官方 `.d.ts` 确认 | 暂停/进度/seek/完成事件标 UNKNOWN 且不实现假功能；按钮显示「停止」 |
| R7 | 基岩 `chatSend` 事件在 2.4.0 类型中不存在（文档注释仍引用） | 中 | 已核实并绕开 | 改用 `system.afterEvents.scriptEventReceive`；`version-lock.md` §3 |
| R8 | `/stopsound` 能否中断自定义声音未真机验证 | 中 | UNKNOWN | try/catch 降级为「标记为停止 + 明确提示」，不假装已停 |
| R9 | 自定义声音事件命名空间、`stream` 策略、缓存重载 | 中 | UNKNOWN | 先用 `/playsound` 在隔离世界测试；`check-sound-events.mjs` 做静态一致性校验 |
| R10 | 行为包 `sounds/sound_definitions.json` 是否被目标 API 读取 | 低 | 未验证 | 与资源包定义分离维护；能力矩阵标注「取决于目标版本 API」 |
| R11 | 真机矩阵完全缺失（Windows Java / Windows Bedrock / Android / 联机） | **高** | 未验证 | `implementation-plan.md` 列出所需环境；CI 不伪造真机结果 |
| R12 | GitHub 直连不可达，审计依赖 API + 部分 blobs | 中 | 已记录 | 完整 tree（809 blobs）、SHA、许可证状态实测；未取得源码的文件不臆测内容；`REFERENCE_COMMIT.txt` 记录获取方式 |
| R13 | 匿名 GitHub API 限流，9/23 关键源码下载成功 | 低 | 已记录 | 行为契约全部基于已下载文件的真实行号；其余按路径分类 |
| R14 | 字体 / jar / DLL 许可与来源不可确认 | 中 | 已规避 | 全部 `DO_NOT_PORT`；测试素材程序化生成 |
| R15 | 平台导入器涉及凭据与版权合规 | 中 | 阶段 6 未开始 | 仅本机、用户主动触发、逐首许可证确认、Token 加密可清除；禁止解灰/破解 |
| R16 | 恶意输入（巨型 JSON、路径穿越、Zip Slip、原型污染） | 中 | 已覆盖 | `Json` 限制、`LibraryValidator.isSafePath`、`Packer.addTree`、对应单元测试 |
| R17 | 外部进程挂死（ffmpeg stderr 管道写满） | 中 | 已覆盖 | `Packer.run()` 异步排空 stdout/stderr，固定参数不拼 shell |
| R18 | 音频线程受 GC 长暂停、UI 线程做解码/扫描 | 中 | 架构已约束 | 性能预算见提示词 9.3；真实引擎属阶段 2，需真机测量 |
| R19 | Gradle 全局 `init.gradle` 语法错误导致所有构建失败 | 低 | 已修复 | 修正为合法 Groovy（引号 + `mavenCentral` 拼写），`environment-report.md` 记录 |
| R20 | 提交二进制大文件 / 凭据入库 | 低 | 已规避 | `.gitignore` 排除 `.mcaddon` / `node_modules` / `build` / 凭据文件；CI 制品走 artifact |

## 未解决的外部阻塞

1. **Minecraft 客户端真机**：本沙箱为 Ubuntu 24.04 无 GUI，无法启动 Java/基岩客户端。
2. **Fabric Loom 上游资源**：需下载 MC 1.21.11 jar / 映射 / assets，本次尝试结果见 `test-report.md`。
3. **github.com 直连**：只影响完整 `git clone`，不影响审计与构建（已用 API 绕开）。
