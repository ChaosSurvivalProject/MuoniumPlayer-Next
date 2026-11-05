/**
 * 基岩能力声明（阶段 4 之前的快照）。
 *
 * 未经目标版本 1.21.130 真机验证的项一律 UNKNOWN；
 * 代码不得把任何 UNKNOWN 当成 SUPPORTED 来实现功能。
 * 真机矩阵见 docs/bedrock-capability-matrix.md。
 */
export type Verdict = "SUPPORTED" | "LIMITED" | "EXPERIMENTAL" | "UNSUPPORTED" | "UNKNOWN";

export interface CapabilityRow {
  readonly api: string;
  readonly version: string;
  readonly experiment: string;
  readonly verdict: Verdict;
  readonly note: string;
}

export const CAPABILITIES: readonly CapabilityRow[] = [
  { api: "自定义 OGG 声音事件", version: "资源包 sound_definitions.json", experiment: "无", verdict: "UNKNOWN", note: "需在隔离世界用 /playsound 验证" },
  { api: "指定玩家播放", version: "Player.playSound (@minecraft/server 2.4.0)", experiment: "无", verdict: "UNKNOWN", note: "官方 index.d.ts:14522 确认签名 playSound(soundId, PlayerSoundOptions?): void；仅本人可听需真机确认" },
  { api: "音乐淡入", version: "Player.playMusic / queueMusic", experiment: "无", verdict: "UNKNOWN", note: "未验证" },
  { api: "获取声音实例", version: "SoundInstance", experiment: "待测", verdict: "UNSUPPORTED", note: "2.4.0 稳定类型中 playSound 明确返回 void，无实例句柄；实验 API 不计入" },
  { api: "真实播放位置", version: "SoundDurationInfo.getPlaybackPosition", experiment: "实验 API", verdict: "UNKNOWN", note: "微软文档列为实验；不得默认可用" },
  { api: "暂停/恢复", version: "无稳定 API", experiment: "待测", verdict: "UNKNOWN", note: "不支持时显示停止，绝不假装暂停" },
  { api: "seek", version: "无稳定 API", experiment: "待测", verdict: "UNKNOWN", note: "不实现" },
  { api: "完成事件", version: "无稳定 API", experiment: "待测", verdict: "UNKNOWN", note: "不实现自动切歌" },
  { api: "同时声音上限", version: "待测", experiment: "待测", verdict: "UNKNOWN", note: "快速点击 50 次场景待测" },
  { api: "动态属性持久化", version: "world.setDynamicProperty", experiment: "无", verdict: "UNKNOWN", note: "稳定 API 存在，重进世界是否保留待测" },
  { api: "远程 HTTP", version: "@minecraft/server-net", experiment: "Beta / BDS 专用", verdict: "UNSUPPORTED", note: "本项目明确不使用；曲库断网可播" },
] as const;

/** 在真机矩阵填 SUPPORTED 之前，播放器不提供暂停/进度/seek 控件。 */
export const PAUSE_RESUME_IMPLEMENTED = false;
export const SEEK_IMPLEMENTED = false;
export const POSITION_BAR_IMPLEMENTED = false;
export const COMPLETION_EVENT_IMPLEMENTED = false;

/** 降级说明：能力 UNKNOWN 时只提供「停止」，不提供「暂停」标签。 */
export const STOP_ONLY_LABEL = "停止";
