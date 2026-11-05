#!/usr/bin/env node
// 打包 .mcaddon：先跑全部校验，再用系统 zip 生成（不在仓库内提交二进制）。
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const PACK = path.join(ROOT, "bedrock-addon", "pack");
const DIST = path.join(ROOT, "bedrock-addon", "dist");
const OUT = path.join(DIST, "muonium-player.mcaddon");

for (const tool of ["check-manifests.mjs", "check-sound-events.mjs", "validate-library.mjs"]) {
  execFileSync(process.execPath, [path.join(ROOT, "tools", tool)], { stdio: "inherit", cwd: ROOT });
}

fs.mkdirSync(DIST, { recursive: true });
if (fs.existsSync(OUT)) fs.rmSync(OUT);
execFileSync("zip", ["-r", "-q", "-X", OUT, "behavior_pack", "resource_pack", "-x", "*.DS_Store"], {
  cwd: PACK, stdio: "inherit",
});
const size = fs.statSync(OUT).size;
console.log(`PACK OK -> ${path.relative(ROOT, OUT)} (${size} bytes)`);
