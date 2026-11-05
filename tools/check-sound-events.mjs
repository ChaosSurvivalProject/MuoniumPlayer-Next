#!/usr/bin/env node
// 校验声音事件：两份 sound_definitions 不合并、事件与曲库映射一致、OGG 文件真实存在。
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const PACK = path.join(ROOT, "bedrock-addon", "pack");
const RP_DEFS = path.join(PACK, "resource_pack", "sounds", "sound_definitions.json");
const BP_DEFS = path.join(PACK, "behavior_pack", "sounds", "sound_definitions.json");
const LIBRARY = path.join(PACK, "behavior_pack", "library.json");
const errors = [];
const notes = [];

function readJson(p) {
  try { return JSON.parse(fs.readFileSync(p, "utf8")); }
  catch (e) { errors.push(`无法解析 ${path.relative(ROOT, p)}: ${e.message}`); return {}; }
}

const rp = readJson(RP_DEFS);
const bpMeta = readJson(BP_DEFS);
const lib = readJson(LIBRARY);

// 资源包：把声音事件映射到实际音频文件
const rpEvents = rp.sound_definitions ?? {};
if (Object.keys(rpEvents).length === 0) errors.push("资源包 sound_definitions 为空");

for (const [event, def] of Object.entries(rpEvents)) {
  if (def.category !== "music") errors.push(`${event} category 建议为 music，实际 ${def.category}`);
  if (!Array.isArray(def.sounds) || def.sounds.length === 0) { errors.push(`${event} 缺少 sounds`); continue; }
  for (const s of def.sounds) {
    if (typeof s.name !== "string" || !s.name.startsWith("sounds/muonium/")) {
      errors.push(`${event} 声音路径异常: ${s.name}`);
      continue;
    }
    const ogg = path.join(PACK, "resource_pack", s.name + ".ogg");
    if (!fs.existsSync(ogg)) { errors.push(`${event} 音频文件缺失: ${s.name}.ogg`); continue; }
    const fd = fs.openSync(ogg, "r");
    const buf = Buffer.alloc(4);
    fs.readSync(fd, buf, 0, 4, 0);
    fs.closeSync(fd);
    if (buf.toString("ascii") !== "OggS") errors.push(`${event} 不是 OGG 容器: ${s.name}.ogg`);
    else notes.push(`${event} -> ${s.name}.ogg (${fs.statSync(ogg).size} bytes, OggS OK)`);
  }
}

// 行为包：脚本声音元数据（时长/类别），与 RP 定义分离
const bpMetaEvents = bpMeta.script_sound_metadata ?? {};
if (!bpMeta._comment || !bpMeta._comment.includes("禁止合并")) {
  errors.push("行为包声音元数据缺少「禁止合并」说明");
}

// 曲库引用必须都能在 RP 定义中找到；时长必须与导入探针一致
const eventsSeen = new Set();
for (const t of lib.tracks ?? []) {
  const ref = t.assets?.audio?.ref;
  if (!ref) { errors.push(`${t.id} 缺少 audio.ref`); continue; }
  if (eventsSeen.has(ref)) errors.push(`声音事件重复: ${ref}`);
  eventsSeen.add(ref);
  if (!rpEvents[ref]) errors.push(`${t.id} 引用了未注册的声音事件: ${ref}`);
  const decl = bpMetaEvents[ref]?.declared_duration_ms;
  if (decl !== undefined && decl !== t.durationMs) {
    errors.push(`${t.id} 元数据声明时长 ${decl} 与曲库 ${t.durationMs} 不一致`);
  }
}
for (const event of Object.keys(rpEvents)) {
  if (!eventsSeen.has(event)) errors.push(`声音事件未被任何曲目引用: ${event}`);
}

notes.push(`曲库 ${lib.tracks?.length ?? 0} 首；资源包事件 ${Object.keys(rpEvents).length} 个；行为包元数据 ${Object.keys(bpMetaEvents).length} 条`);
notes.push("资源包定义与行为包元数据为两份独立文件，未合并");

if (errors.length) {
  console.error("CHECK-SOUND-EVENTS FAIL");
  for (const e of errors) console.error("  ERROR " + e);
  process.exit(1);
}
console.log("CHECK-SOUND-EVENTS PASS");
for (const n of notes) console.log("  " + n);
