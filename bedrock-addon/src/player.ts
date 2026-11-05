import type { Player } from "@minecraft/server";
import type { Track } from "./library";
import { declaredDuration } from "./library";
import { makeError, type MuoniumError } from "./errors";
import { POSITION_BAR_IMPLEMENTED, SEEK_IMPLEMENTED } from "./capabilities";

export interface PlayResult {
  readonly ok: boolean;
  readonly error?: MuoniumError;
  /** true 表示引擎无法中断已发出的声音，只能标记状态为停止。 */
  readonly limitation?: boolean;
}

function clamp(v: number): number {
  return Number.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0.8;
}

/**
 * 只向目标玩家播放预先声明的资源包声音事件。
 * 绝不把任意远程 URL / MP3 喂给基岩声音系统。
 */
export function playTrack(player: Player, track: Track, volume: number): PlayResult {
  if (!track.playableOffline) {
    return { ok: false, error: makeError("LIBRARY_AUDIO_MISSING", `track=${track.id}`) };
  }
  try {
    player.playSound(track.audioEvent, { volume: clamp(volume) });
    return { ok: true };
  } catch (e) {
    return { ok: false, error: makeError("AUDIO_BACKEND_LIMITATION", String(e)) };
  }
}

/**
 * 停止。`/stopsound` 与 Entity.runCommand 需在 1.21.130 真机核实：
 * 若命令不可用或抛错，降级为「标记为停止、不中断已发声」，并明确返回 limitation=true。
 * 绝不假装已暂停。
 */
export function stopTrack(player: Player, audioEvent: string | null): PlayResult {
  if (audioEvent === null) return { ok: true };
  try {
    player.runCommand(`stopsound @s ${audioEvent}`);
    return { ok: true };
  } catch (e) {
    return {
      ok: false,
      limitation: true,
      error: makeError("AUDIO_BACKEND_LIMITATION", `stopsound 不可用: ${String(e)}`),
    };
  }
}

export interface TrackProgressView {
  readonly label: string;
  readonly declaredMs: number | null;
  readonly showBar: boolean;
  readonly seekable: boolean;
}

/**
 * 进度展示：只有导入时声明的估算时长，没有真实位置就不画可拖动进度条。
 */
export function progressView(track: Track): TrackProgressView {
  const declared = declaredDuration(track);
  return {
    label: declared === null ? "时长未知" : `声明时长 ${Math.round(declared / 1000)} 秒（估算，非实时位置）`,
    declaredMs: declared,
    showBar: POSITION_BAR_IMPLEMENTED,
    seekable: SEEK_IMPLEMENTED,
  };
}
