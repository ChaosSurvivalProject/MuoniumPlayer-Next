#!/usr/bin/env node
// 生成跨端歌词时间轴测试向量（确定性输出，供 Java 与 TS 共用）。
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const OUT = path.join(ROOT, "shared-schemas", "vectors", "lyric-timeline.json");

const vectors = [
  {
    name: "lrc_basic_and_multiple_timestamps",
    format: "lrc",
    input: "[ti:示例]\n[00:10.00][00:20.00]第一句\n[00:30.50]第二句",
    durationMs: 60000,
    expectedLineCount: 3,
    expectedStartsMs: [10000, 20000, 30500],
  },
  {
    name: "fraction_digits_1_2_3",
    format: "lrc",
    input: "[00:01.5]a\n[00:02.50]b\n[00:03.500]c",
    durationMs: 10000,
    expectedLineCount: 3,
    expectedStartsMs: [1500, 2500, 3500],
  },
  {
    name: "qrc_real_word_timing",
    format: "qrc",
    input: "[10000,3500]第一句(10000,2800)歌词(12800,700)",
    durationMs: 214000,
    expectedLineCount: 1,
    expectedWordStartsMs: [10000, 12800],
  },
  {
    name: "out_of_order_timestamps",
    format: "lrc",
    input: "[00:20.00]第二句\n[00:10.00]第一句",
    durationMs: 40000,
    expectedLineCount: 2,
    expectedStartsMs: [10000, 20000],
  },
  {
    name: "html_entities_and_fullwidth_space",
    format: "lrc",
    input: "[00:01.00]A&amp;B　C",
    durationMs: 5000,
    expectedFirstLineText: "A&B C",
  },
  {
    name: "unknown_format_rejected",
    format: "unsupported",
    input: "这不是歌词格式",
    durationMs: null,
    expectedUnsupported: true,
    expectedLineCount: 0,
  },
];

const payload = {
  schemaVersion: 1,
  generatedBy: "tools/generate-test-vectors.mjs",
  note: "供 common-core 的 JVM 测试与基岩端共用；输入无版权，全部为程序化占位文本。",
  vectors,
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(payload, null, 2) + "\n", "utf8");
console.log(`GENERATE-TEST-VECTORS OK -> ${path.relative(ROOT, OUT)} (${vectors.length} vectors)`);
