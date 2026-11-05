import { world } from "@minecraft/server";
import type { PlayMode, RepeatMode } from "./queue";

/** 本机设置持久化（world dynamic properties）。不存任何凭据。 */
export interface Settings {
  readonly volume: number;
  readonly mode: PlayMode;
  readonly repeat: RepeatMode;
}

const SETTINGS_KEY = "muonium:settings.v1";
const MAX_SAVED_CHARS = 4096;

export const DEFAULT_SETTINGS: Settings = {
  volume: 0.8,
  mode: "sequence",
  repeat: "none",
};

function isMode(v: unknown): v is PlayMode {
  return v === "sequence" || v === "shuffle";
}
function isRepeat(v: unknown): v is RepeatMode {
  return v === "none" || v === "one" || v === "list";
}
function clampVolume(v: unknown): number {
  return typeof v === "number" && Number.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0.8;
}

export function loadSettings(): Settings {
  try {
    const raw = world.getDynamicProperty(SETTINGS_KEY);
    if (typeof raw !== "string" || raw.length > MAX_SAVED_CHARS) return DEFAULT_SETTINGS;
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null) return DEFAULT_SETTINGS;
    const o = parsed as Record<string, unknown>;
    return {
      volume: clampVolume(o["volume"]),
      mode: isMode(o["mode"]) ? o["mode"] : "sequence",
      repeat: isRepeat(o["repeat"]) ? o["repeat"] : "none",
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

/** @return 是否写入成功；失败只记录，不让游戏崩溃。 */
export function saveSettings(s: Settings): boolean {
  try {
    const text = JSON.stringify(s);
    if (text.length > MAX_SAVED_CHARS) return false;
    world.setDynamicProperty(SETTINGS_KEY, text);
    return true;
  } catch {
    return false;
  }
}

/** 一键清除本机设置。 */
export function clearSettings(): void {
  try {
    world.setDynamicProperty(SETTINGS_KEY, undefined);
  } catch {
    // 清除失败不抛给游戏
  }
}
