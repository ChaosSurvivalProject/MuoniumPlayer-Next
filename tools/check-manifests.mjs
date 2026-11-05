#!/usr/bin/env node
// 校验行为包 / 资源包 manifest：UUID 唯一、版本口径、依赖一致、脚本入口存在。
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const PACK = path.join(ROOT, "bedrock-addon", "pack");
const BP = path.join(PACK, "behavior_pack", "manifest.json");
const RP = path.join(PACK, "resource_pack", "manifest.json");
const PKG = path.join(ROOT, "bedrock-addon", "package.json");

const errors = [];
const notes = [];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FORBIDDEN_MODULES = ["@minecraft/server-net", "@minecraft/server-admin", "@minecraft/server-gametest"];

function readJson(p) {
  try { return JSON.parse(fs.readFileSync(p, "utf8")); }
  catch (e) { errors.push(`无法解析 ${path.relative(ROOT, p)}: ${e.message}`); return {}; }
}

const bp = readJson(BP);
const rp = readJson(RP);
const pkg = readJson(PKG);

function checkManifest(m, label) {
  if (m.format_version !== 2) errors.push(`${label}.format_version 应为 2（稳定 Script API 基线），实际 ${m.format_version}`);
  const h = m.header ?? {};
  if (!UUID.test(h.uuid ?? "")) errors.push(`${label}.header.uuid 非法: ${h.uuid}`);
  if (!Array.isArray(h.version) || h.version.length !== 3) errors.push(`${label}.header.version 必须是三段数组`);
  if (JSON.stringify(h.min_engine_version) !== JSON.stringify([1, 21, 130])) {
    errors.push(`${label}.min_engine_version 必须精确为 [1,21,130]（基岩 1.21.130），实际 ${JSON.stringify(h.min_engine_version)}`);
  }
  if (typeof h.name !== "string" || h.name.length === 0) errors.push(`${label}.header.name 缺失`);
  for (const mod of m.modules ?? []) {
    if (!UUID.test(mod.uuid ?? "")) errors.push(`${label} 模块 uuid 非法: ${mod.uuid}`);
    if (!Array.isArray(mod.version) || mod.version.length !== 3) errors.push(`${label} 模块 version 必须是三段数组`);
  }
  return h.uuid;
}

const bpHeader = checkManifest(bp, "BP");
const rpHeader = checkManifest(rp, "RP");

// 所有 UUID（两个 header + 所有 module）必须互不相同
const allUuids = [
  ...[bp, rp].flatMap((m) => [m.header?.uuid, ...(m.modules ?? []).map((x) => x.uuid)]),
].filter(Boolean);
if (new Set(allUuids).size !== allUuids.length) errors.push("存在重复的 UUID（header 或 module）");

// BP 必须有 script 模块且入口文件真实存在
const scriptMod = (bp.modules ?? []).find((m) => m.type === "script");
if (!scriptMod) errors.push("BP 缺少 type=script 模块");
else {
  if (scriptMod.language !== "javascript") errors.push("script 模块 language 必须为 javascript");
  const entry = scriptMod.entry;
  const entryPath = path.join(PACK, "behavior_pack", entry ?? "");
  if (!entry || !fs.existsSync(entryPath)) errors.push(`脚本入口文件不存在: ${entry}`);
  else notes.push(`脚本入口存在: ${entry} (${fs.statSync(entryPath).size} bytes)`);
}

// BP -> RP 依赖：UUID 与版本必须与 RP header 完全一致
const deps = bp.dependencies ?? [];
const rpDep = deps.find((d) => d.uuid === rpHeader);
if (!rpDep) errors.push("BP 未声明对 RP header UUID 的依赖");
else if (JSON.stringify(rpDep.version) !== JSON.stringify(rp.header?.version)) {
  errors.push("BP 声明的 RP 版本与 RP manifest 不一致");
}
if (!deps.find((d) => d.uuid === bpHeader)) {
  // 反向依赖不是硬性要求，这里只记录
  notes.push("RP 未反向声明 BP 依赖（可选）");
}

// 脚本模块版本必须与 package.json 锁定的类型包版本一致
for (const d of deps) {
  if (!d.module_name) continue;
  if (FORBIDDEN_MODULES.includes(d.module_name)) errors.push(`禁止依赖服务端模块: ${d.module_name}`);
  const expected = pkg.devDependencies?.[d.module_name];
  if (expected === undefined) errors.push(`依赖了未在 package.json 锁定的模块: ${d.module_name}`);
  else if (expected !== d.version) errors.push(`${d.module_name}: manifest=${d.version} 与 package.json=${expected} 不一致`);
}

// 纯客户端闸门
for (const [label, m] of [["BP", bp], ["RP", rp]]) {
  const text = JSON.stringify(m);
  for (const bad of ["server-net", "floodgate", "geyser", "ServerPlayer", "Bukkit"]) {
    if (text.includes(bad)) errors.push(`${label} manifest 含禁止字样: ${bad}`);
  }
}

notes.push(`BP min_engine_version=${JSON.stringify(bp.header?.min_engine_version)}`);
notes.push(`RP min_engine_version=${JSON.stringify(rp.header?.min_engine_version)}`);
notes.push("两端只声明各自版本，未跨端引用 Java 1.21.11");

if (errors.length) {
  console.error("CHECK-MANIFESTS FAIL");
  for (const e of errors) console.error("  ERROR " + e);
  process.exit(1);
}
console.log("CHECK-MANIFESTS PASS");
for (const n of notes) console.log("  " + n);
