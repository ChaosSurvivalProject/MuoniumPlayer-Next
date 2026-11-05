# 贡献与工程规范

## 提交必须包含

每次提交的说明里写清楚：**目的、行为变化、平台、测试、风险、回滚**。
UI 或 API 改动附截图/视频与内容日志片段。

## 编码规范

- Java 21；`common-core` 禁止出现 `net.minecraft.*`、Fabric API、Script API、
  Java Sound、FFmpeg 进程、AWT/Swing/Skija、直接文件/网络/线程实现。
- 文件、时间、随机、HTTP、日志、音频全部通过 `com.muonium.core.spi.Ports` 注入。
- TypeScript `strict: true`，`noImplicitAny`，禁止 `any` 逃逸；`tsconfig.json` 里
  `noUnusedLocals / noUnusedParameters / noUncheckedIndexedAccess / exactOptionalPropertyTypes` 均开启。
- 不可变模型优先；所有 public API 写明 null 安全约定（`null`=未知，空集合=明确没有）。
- 不吞异常：取消 / 失败 / 未知状态分开处理。
- 资源实现 `Closeable` 或等价释放；关闭播放器后释放实例与事件订阅。
- UI 不直接持有音频解码对象；网络层有超时、取消、重试上限与指数退避。
- 不用 `Thread.sleep` 当歌词时钟或音频同步机制。
- 所有跨端 ID、文件名、枚举做兼容测试。

## 纯客户端闸门

`./gradlew checkClientOnly` 会扫描源码，发现以下任一符号即失败：

`ServerPlayer`、`ServerPlayNetworking`、`ServerPlayNetworkHandler`、`MinecraftServer`、
`Bukkit`、`org.bukkit`、`@Environment(SERVER)`、`geyser`、`floodgate`；
以及 import 了 `@minecraft/server-net` / `@minecraft/server-admin` / `@minecraft/server-gametest`。

注释与文档字符串会被排除，避免误报。发现即删除，不「留着以后用」。

## 本地验收命令

```bash
# JVM 侧
./gradlew clean build test checkClientOnly

# Bedrock 侧
cd bedrock-addon && npm ci && npm run check && cd ..
node tools/check-manifests.mjs
node tools/check-sound-events.mjs
node tools/validate-library.mjs
node tools/generate-test-vectors.mjs
node tools/pack-addon.mjs

# Fabric 侧（需可访问 Mojang / Fabric Maven，且真机才能验证启动）
./gradlew -PincludeFabric=true :fabric-1_21_11:build
```

## 红线

- 不把 Java Mod 声称「编译成 Bedrock」；
- 不把 Fabric API 说成 Bedrock 可用；
- 不把实验 API 写成稳定功能；
- 不编造 GitHub 文件路径、类名、许可证或 API；
- 没运行就写「构建通过 / 真机通过」；
- 远程任意 MP3 直接喂给基岩声音系统；
- 内置网易云 / QQ 音乐破解、解灰或 Cookie；
- 把音乐、歌词、封面和账号凭据提交进仓库；
- 因「先做出效果」跳过资源释放、取消和安全校验。

## Issue 模板

见 `.github/ISSUE_TEMPLATE/`。提交真机问题时请附：游戏版本、设备、
内容日志、复现步骤、包版本、是否开启实验开关。
