import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";

const digest = createHash("sha256");
for (const file of ["src/scalar.ts", "src/scalar-csp.ts", "vite.scalar.config.ts", "package.json", "pnpm-lock.yaml"]) {
  digest.update(file).update("\0").update(readFileSync(fileURLToPath(new URL(`./${file}`, import.meta.url)))).update("\0");
}
const banner = `/* vireon-scalar-source-sha256:${digest.digest("hex")} */\n`;

export default defineConfig({
  plugins: [{
    name: "vireon-scalar-source-digest",
    enforce: "post",
    generateBundle: { order: "post", handler(_options, bundle) {
      for (const output of Object.values(bundle)) {
        if (output.type === "chunk") output.code = banner + output.code;
        else if (output.fileName.endsWith(".css")) output.source = banner + String(output.source);
      }
    } },
  }],
  define: { "process.env.NODE_ENV": JSON.stringify("production") },
  build: {
    emptyOutDir: false,
    target: "es2022",
    sourcemap: false,
    lib: { entry: fileURLToPath(new URL("./src/scalar.ts", import.meta.url)), name: "Scalar", formats: ["iife"], fileName: () => "scalar.js", cssFileName: "scalar" },
    rollupOptions: { output: { inlineDynamicImports: true } },
    outDir: "../src/main/resources/META-INF/vireon/openapi",
  },
});
