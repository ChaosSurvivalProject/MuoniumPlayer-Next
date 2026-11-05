# MuoniumPlayer Bedrock Add-On

目标：**Minecraft 基岩版 1.21.130** 官方 Add-On（纯客户端）。

## 结构

```text
pack/behavior_pack/
  manifest.json              format_version 2, min_engine_version [1,21,130]
  scripts/main.js            esbuild 产物（由 src/*.ts 构建，勿手改）
  sounds/sound_definitions.json   脚本声音元数据（时长/类别），与资源包定义分离
  library.json               预导入曲库清单（编译期内联进 main.js）
pack/resource_pack/
  manifest.json
  sounds/sound_definitions.json   事件 -> 实际 OGG 文件
  sounds/muonium/track_0001.ogg   ffmpeg 程序化生成的无版权测试音轨
  textures/muonium/covers/        程序化占位封面
  lang/                          语言文件
src/*.ts                    TypeScript strict 源码
```

## 构建

```bash
npm ci
npm run check        # tsc --noEmit + esbuild + node --check
cd ..
node tools/check-manifests.mjs
node tools/check-sound-events.mjs
node tools/validate-library.mjs
node tools/pack-addon.mjs     # -> dist/muonium-player.mcaddon
```

## 使用

1. 导入 `.mcaddon`，在测试世界同时启用行为包与资源包。
2. 先用 `/playsound muonium:track_0001` 在隔离世界验证自定义声音事件。
3. 聊天输入 `/scriptevent muonium:open` 打开表单播放器。

> 为什么用 `scriptevent`：经核实 `@minecraft/server 2.4.0` 的类型定义中
> `WorldBeforeEvents` / `WorldAfterEvents` **都没有 `chatSend`**，
> 官方文档注释里的示例与实际类型不一致。因此使用官方稳定的
> `system.afterEvents.scriptEventReceive`，不凭记忆写 API。

## 能力边界

- `Player.playSound(soundId, PlayerSoundOptions?): void` —— **返回 void，无 SoundInstance**。
- 暂停 / 恢复 / seek / 真实播放位置 / 完成事件：**UNKNOWN，未实现假功能**；
  按钮显示「停止」，进度只显示导入时声明的估算时长且不可拖动。
- 切歌为普通切歌，无无缝混音 / Automix。
- 停止用 `/stopsound` + `runCommand`，能否中断自定义声音未真机验证；
  失败时明确提示，不假装已停。
- 不做远程 HTTP（`@minecraft/server-net` 为 BDS 专用 Beta）、不做通用文件浏览器。
- 曲库随包预导入，**断网可播**；不写服务端代码、不做跨玩家同步。

详见 `docs/bedrock-capability-matrix.md` 与 `docs/version-lock.md`（仓库根目录）。
