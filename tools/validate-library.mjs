#!/usr/bin/env node
// 校验曲库清单（与 Java LibraryValidator 相同规则，跨端一致）。
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const files = process.argv.slice(2);
const targets = files.length ? files : [
  path.join(ROOT, "bedrock-addon", "pack", "behavior_pack", "library.json"),
];

const ALLOWED_LICENSES = new Set(["user_provided", "cc0", "cc-by", "cc-by-sa", "public_domain", "own_recording"]);
const SAFE_ID = /^[a-z0-9_]+$/;
let failed = false;

function safePath(ref) {
  if (typeof ref !== "string" || ref.length === 0 || ref.length > 512) return false;
  if (ref.includes("..") || ref.startsWith("/") || ref.startsWith("\\")) return false;
  if (/^[a-zA-Z]:/.test(ref) || ref.includes("://") || ref.includes("\0")) return false;
  return true;
}

for (const file of targets) {
  const errors = [];
  let doc;
  try { doc = JSON.parse(fs.readFileSync(file, "utf8")); }
  catch (e) { errors.push(`无法解析: ${e.message}`); doc = {}; }

  if (doc.schemaVersion !== 1) errors.push("schemaVersion 必须为 1");
  if (!Array.isArray(doc.tracks)) errors.push("缺少 tracks 数组");
  const ids = new Set();
  const events = new Set();
  for (const [i, t] of (doc.tracks ?? []).entries()) {
    const p = `tracks[${i}]`;
    if (!SAFE_ID.test(t.id ?? "")) errors.push(`${p}.id 非法: ${t.id}`);
    else if (ids.has(t.id)) errors.push(`${p}.id 重复: ${t.id}`);
    ids.add(t.id);
    const license = t.source?.license;
    if (!ALLOWED_LICENSES.has(license)) errors.push(`${p}.source.license 缺少可导入授权标记`);
    if (t.tempUrl || t.streamUrl) errors.push(`${p} 不得持久化临时音源 URL`);
    for (const k of ["cookie", "token", "password", "phone"]) {
      if (t[k] !== undefined) errors.push(`${p} 含敏感字段 ${k}`);
    }
    const ref = t.assets?.audio?.ref;
    if (!safePath(ref)) errors.push(`${p}.assets.audio.ref 路径不安全: ${ref}`);
    if (typeof ref === "string" && ref.startsWith("muonium:")) {
      if (events.has(ref)) errors.push(`${p} 声音事件重复: ${ref}`);
      events.add(ref);
    }
    if (t.durationMs !== null && t.durationMs !== undefined && t.durationMs < 0) {
      errors.push(`${p}.durationMs 不能为负数`);
    }
    if (!t.durationSource) errors.push(`${p}.durationSource 必须记录来源`);
  }

  const name = path.relative(ROOT, file);
  if (errors.length) {
    failed = true;
    console.error(`VALIDATE-LIBRARY FAIL ${name}`);
    for (const e of errors) console.error("  ERROR " + e);
  } else {
    console.log(`VALIDATE-LIBRARY PASS ${name} (${doc.tracks.length} 首)`);
  }
}
process.exit(failed ? 1 : 0);
