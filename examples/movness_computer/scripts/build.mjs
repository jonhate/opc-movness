import { build } from "esbuild";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
await mkdir(resolve(root, "dist/packages"), { recursive: true });
await build({ entryPoints: [resolve(root, "src/main.ts")], outfile: resolve(root, "dist/main.js"),
  bundle: true, format: "esm", platform: "neutral", target: "es2020", minify: false });
const tool = resolve(root, "src/packages/computer.ts");
const source = await readFile(tool, "utf8");
const match = source.match(/\/\* METADATA\s*([\s\S]*?)\*\//);
if (!match) throw new Error("Tool metadata missing");
const metadata = JSON.parse(match[1]);
const result = await build({ entryPoints: [tool], bundle: true, format: "cjs", platform: "neutral",
  target: "es2020", minify: false, write: false });
await writeFile(resolve(root, "dist/packages/computer.js"), `/* METADATA\n${JSON.stringify(metadata, null, 2)}\n*/\n${result.outputFiles[0].text}`);
await readFile(resolve(root, "LICENSE"), "utf8");
console.log("Built ToolPkg UI and eight computer tools");
