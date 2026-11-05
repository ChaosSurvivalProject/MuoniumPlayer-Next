/**
 * 曲库：静态 JSON 在编译期由 esbuild 内联进 main.js。
 *
 * 官方基岩脚本没有通用桌面文件系统 API，不能承诺运行时读取包内任意文件，
 * 所以这里不做任何 fetch / fs / import() 动态加载。
 */
import rawLibrary from "../pack/behavior_pack/library.json";

export interface Track {
  readonly id: string;
  readonly title: string;
  readonly durationMs: number | null;
  readonly durationSource: string;
  readonly audioEvent: string;
  readonly license: string;
  readonly playableOffline: boolean;
}

export interface Library {
  readonly schemaVersion: number;
  readonly protocolVersion: number;
  readonly gameVersion: string;
  readonly tracks: readonly Track[];
}

const MAX_TRACKS = 100_000;
const SAFE_ID = /^[a-z0-9_]+$/;
const SAFE_REF = /^[a-z0-9_:./-]+$/;

function readString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

/** 校验并返回曲库；任何异常都退化为空曲库，不让游戏崩溃。 */
export function loadLibrary(): Library {
  const root = asRecord(rawLibrary);
  const rawTracks = Array.isArray(root["tracks"]) ? root["tracks"] : [];
  if (rawTracks.length > MAX_TRACKS) return emptyLibrary();

  const tracks: Track[] = [];
  const seen = new Set<string>();
  for (const item of rawTracks) {
    const t = asRecord(item);
    const id = readString(t["id"]);
    if (!SAFE_ID.test(id) || seen.has(id)) continue;
    const assets = asRecord(t["assets"]);
    const audio = asRecord(assets["audio"]);
    const audioEvent = readString(audio["ref"]);
    const source = asRecord(t["source"]);
    const license = readString(source["license"]);
    if (!SAFE_REF.test(audioEvent) || license.length === 0) continue;
    const flags = asRecord(t["flags"]);
    seen.add(id);
    tracks.push({
      id,
      title: readString(t["title"]).slice(0, 500),
      durationMs: readNumber(t["durationMs"]),
      durationSource: readString(t["durationSource"]),
      audioEvent,
      license,
      playableOffline: flags["playableOffline"] === true,
    });
  }
  return {
    schemaVersion: readNumber(root["schemaVersion"]) ?? 1,
    protocolVersion: readNumber(root["protocolVersion"]) ?? 1,
    gameVersion: readString(root["gameVersion"]),
    tracks,
  };
}

function emptyLibrary(): Library {
  return { schemaVersion: 1, protocolVersion: 1, gameVersion: "1.21.130", tracks: [] };
}

export function findTrack(library: Library, id: string): Track | undefined {
  return library.tracks.find((t) => t.id === id);
}

/** 进度条只允许显示导入时声明的估算时长，不得伪装为真实播放位置。 */
export function declaredDuration(track: Track): number | null {
  return track.durationMs !== null && track.durationMs >= 0 ? track.durationMs : null;
}
