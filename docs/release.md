# 发布、安装与回滚

## 1. Java / 基岩版本矩阵

| 端 | 游戏版本 | 运行要求 | 制品 |
|---|---|---|---|
| Java | **1.21.11** | Java 21、Fabric Loader ≥0.20.1、Fabric API `0.141.6+1.21.11` | `fabric-1_21_11` 构建出的 Mod jar |
| 基岩 | **1.21.130** | Windows / Android / iOS 基岩客户端，开启行为包与资源包 | `bedrock-addon/dist/muonium-player.mcaddon` |

两端版本号不同但属同一内容更新；**不要求版本字符串相等**。
不写服务端插件/Mod，不要求资源包被服务器下发，不适配 Geyser/Floodgate。

## 2. 安装

### Java 端

1. 安装 Minecraft Java 1.21.11 + Fabric Loader（Java 21）。
2. 把构建出的 Mod jar 放入 `mods/`。
3. 启动客户端；曲库放在 `游戏目录/config/muonium/library/library.json` 指定的目录。
4. 服务端**不需要安装任何东西**。

### 基岩端

1. 用 `node tools/pack-addon.mjs` 生成 `.mcaddon`（或取 CI 制品）。
2. 双击导入；在测试世界里**同时启用行为包与资源包**。
3. 聊天输入 `/scriptevent muonium:open` 打开表单播放器。
4. 先在隔离世界用 `/playsound muonium:track_0001` 验证自定义声音事件
   （声音文件路径、分隔符、事件命名空间与缓存重载是首要故障点）。

> 触发方式说明：经核实 `@minecraft/server 2.4.0` 类型中不存在 `chatSend`，
> 因此用官方稳定的 `scriptEventReceive`，不注册自定义命令、不改原版协议。

## 3. 权限说明

- 不需要任何服务端权限、OP 或 BDS 配置。
- 基岩行为包只声明 `@minecraft/server` 与 `@minecraft/server-ui` 稳定模块依赖；
  **不启用** `@minecraft/server-net` / `-admin` / `-gametest`（均为 BDS 专用或 Beta）。
- 不需要打开 Beta APIs 实验开关（若将来使用实验能力，会在能力矩阵显式标注）。
- 不收集听歌历史，遥测与崩溃上报默认关闭。

## 4. 示例包

- `test-fixtures/audio/test_tone.ogg`：ffmpeg 程序化生成的 5 秒和弦（CC0，无第三方录音）。
- `test-fixtures/covers/track_0001.png`：Pillow 生成的纯色占位封面。
- `test-fixtures/lyrics/track_0001.lrc`：程序化占位歌词。
- 包内不含任何商业音乐、封面、歌词、Cookie、Token 或账号数据。

## 5. 卸载与数据清除

| 端 | 操作 |
|---|---|
| Java | 删除 Mod jar；删除 `游戏目录/config/muonium/` 清除配置与缓存；曲库音频由用户自行管理 |
| 基岩 | 在世界设置中移除行为包与资源包，或直接卸载 `.mcaddon`；`state.clearSettings()` 清除动态属性 |

`ModConfig.clearAll()`（Java）与 `clearSettings()`（TS）提供一键清除入口。
卸载后不留后台进程、不留音频设备句柄。

## 6. 回滚方案

- 每个模块可独立回滚：`common-core` / `common-schema` / `music-import-cli` /
  `fabric-1_21_11` / `bedrock-addon` 互不二进制耦合。
- 数据格式带 `schemaVersion` 与 `configVersion`；`migration-rules.json` 记录逐版本规则，
  未来版本高于当前支持版本时**拒绝猜测**并抛错，可回退到上一版包。
- 基岩包 `version: [1,0,0]` 语义为「更高替换、相同或更低忽略」，回退请用更低版本号重新打包。
- CI 制品可用校验和比对；不发布测试账号与私有接口。

## 7. 发布前清单

- [x] 无版权示例包、安装说明、版本矩阵、权限说明、许可证与第三方清单
- [x] 崩溃与日志上报开关（默认关）、隐私边界、数据清除与回滚说明
- [x] `./gradlew clean build test checkClientOnly` 与 Add-On 构建在干净环境通过
- [x] 上游许可证风险已记录、无大段复制
- [ ] **真机兼容矩阵、测试日志与已知限制已提交** ← 需用户提供 Windows Java / Windows Bedrock / Android Bedrock
- [ ] Windows/Android 真机导入、第三方服务器联机、维度切换、2 小时连续播放
- [ ] 制品签名 / 校验和（阶段 8）

## 8. 隐私政策要点

- 不收集、不上传听歌历史；遥测与崩溃上报默认关闭且需明确同意。
- 平台凭据（若阶段 6 导入器使用）只存本机、不进世界存档与资源包、不上传任何服务器。
- 日志过滤 Cookie、Authorization、二维码票据与手机号。
- 不执行远程下发脚本，不自动更新可执行代码。

## 9. 已知限制（发布说明必须原样保留）

1. 基岩暂停 / 恢复 / seek / 真实播放位置 / 完成事件均为 `UNKNOWN`，**只提供停止**；
   进度条只显示导入时声明的估算时长，不可拖动。
2. 基岩切歌为普通切歌，无无缝混音 / Automix。
3. `/stopsound` 能否中断自定义声音未真机验证；失败时明确提示，不假装已停。
4. 基岩脚本没有通用桌面文件系统 API，不做本地文件浏览器；曲库随包预导入。
5. Fabric HUD / 键位属阶段 2，需在 1.21.11 真机核实 API 后实现。
6. 本仓库未执行任何 Minecraft 客户端真机测试；见 [test-report.md](test-report.md)。
