import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";

const entry = fileURLToPath(new URL("./src/plugin.ts", import.meta.url));
const digestInputs = ["src/plugin.ts", "vite.config.ts", "package.json", "pnpm-lock.yaml"];
const digest = createHash("sha256");
for (const relativePath of digestInputs) {
  digest.update(relativePath).update("\0").update(readFileSync(fileURLToPath(new URL(`./${relativePath}`, import.meta.url)))).update("\0");
}
const sourceDigest = digest.digest("hex");

export default defineConfig({
  plugins: [{
    name: "vireon-review-source-digest",
    generateBundle(_options, bundle) {
      for (const output of Object.values(bundle)) {
        if (output.type === "chunk") output.code = `/* vireon-review-source-sha256:${sourceDigest} */\n${output.code}`;
      }
    },
  }],
  define: {
    "process.env.NODE_ENV": JSON.stringify("production"),
  },
  build: {
    target: "es2022",
    emptyOutDir: false,
    minify: "esbuild",
    sourcemap: false,
    lib: {
      entry,
      formats: ["es"],
      fileName: () => "review-plugin.js",
    },
    outDir: "../src/main/resources/META-INF/vireon/openapi",
  },
});
