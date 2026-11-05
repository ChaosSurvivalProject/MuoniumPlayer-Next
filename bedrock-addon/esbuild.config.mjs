// esbuild：把 TypeScript 打成单个 ESM 入口，游戏运行时提供 @minecraft/server* 模块。
// 曲库清单是静态 JSON，编译期内联进 main.js —— 基岩脚本没有通用文件系统 API，
// 不能承诺运行时读取包内任意文件。
import { build } from "esbuild";

await build({
  entryPoints: ["src/main.ts"],
  outfile: "pack/behavior_pack/scripts/main.js",
  bundle: true,
  format: "esm",
  target: "es2022",
  platform: "neutral",
  external: ["@minecraft/server", "@minecraft/server-ui", "@minecraft/common"],
  sourcemap: false,
  minify: false,
  legalComments: "none",
  logLevel: "info",
});

console.log("built pack/behavior_pack/scripts/main.js");
