# Song 数据模型

Schema：[song.schema.json](../shared-schemas/schema/song.schema.json)
实现：Java `common-core/model/Song.java`；TS `bedrock-addon/src/library.ts`（读取子集）。
示例：[shared-schemas/examples/song.json](../shared-schemas/examples/song.json)

## 字段

| 字段 | 类型 | 规则 |
|---|---|---|
| `schemaVersion` | int | 当前 `1`，未来版本拒绝猜测 |
| `id` | string | `[a-z0-9_]+`，永久稳定，不随文件名/平台/音质变化 |
| `title` / `sortTitle` | string / null | 最长 500 字符 |
| `artists[]` | array | `{id, name}`，最多 100 |
| `album` | object / null | `{id, name}` |
| `durationMs` | int / null | **null = 未知，禁止写 0** |
| `durationSource` | enum | `import_probe` / `provider_metadata` / `user_declared` / `unknown` |
| `source` | object / null | `provider` / `providerTrackId` / `license` / `region` |
| `assets.audio` | AssetRef | `kind` + `ref`；基岩用 `resource_sound_event` |
| `assets.cover` | AssetRef | `pack_texture` 等 |
| `assets.lyric` | AssetRef | `pack_file` 等 |
| `analysis` | object / null | `loudnessLufs` / `peakDbfs` / `automix{enabled,score}` |
| `flags` | object | `hasLyric` / `hasTranslation` / `explicit` / `playableOffline` |
| `updatedAt` | string / null | ISO-8601 |

`AssetRef.kind`: `resource_sound_event | pack_file | pack_texture | local_file`

## 硬性规则

1. **id 永久稳定**：不把第三方平台内部 ID 当全局 ID，不把临时音源 URL 持久化当永久资源。
2. **文件名小写 ASCII + 下划线 + 稳定编号**，不用中文歌名作文件名；
   `Song.isValidResourceName()` 与 `LibraryValidator` 双重校验。
3. **`durationMs` 必须记录来源**：四个 `durationSource` 枚举，缺省 `unknown`。
4. **缺失字段区分「未知」与「没有」**：`null` vs 空集合/空串。
5. **许可证标记**：`source.license` 必须是可导入的授权标记
   （`user_provided` / `cc0` / `cc-by` / `cc-by-sa` / `public_domain` / `own_recording`），
   否则 `LICENSE_NOT_PERMITTED`。
6. **凭据不入包**：曲库对象不得出现 `cookie` / `token` / `password` / `phone` / `tempUrl` / `streamUrl`。
7. **路径安全**：`ref` 拒绝 `..`、绝对路径、Windows 盘符、`://`、NUL，防 Zip Slip。

## Playlist / Queue

- 歌单 `revision` 用于乐观并发：`PlaylistStore.merge()` 检测到双方都基于同一 revision 变更时
  返回冲突，不静默覆盖。
- 队列是**运行时状态**，不直接修改源歌单；`QueueSnapshot` 可序列化恢复。
- 随机播放使用可恢复的 `shuffleSeed` 与 `played` 集合；**刷新 UI 不得重排队列**
  （`PlayQueue.restore()` 直接使用保存的 `upcoming`）。
- 队列耗尽：`repeat=list` 回源歌单开头，否则 **停住**并返回 null，
  由上层依据 `autoplaySourcePlaylistId` 决定是否续播。
- `repeat=one` 优先级高于 `repeat=list`。

## 验收

`SchemaCompatibilityTest`（JVM）覆盖 Song / Playlist / Lyric 的 JSON 往返、
null 语义、资源名规则；`ImportCliTest` 覆盖重复 ID、路径穿越、凭据、临时 URL、许可证。
