const assert = require("node:assert/strict");
const { createHash } = require("node:crypto");
const { readFileSync } = require("node:fs");
const path = require("node:path");

const frontendRoot = path.resolve(__dirname, "..");
const digestInputs = ["src/plugin.ts", "vite.config.ts", "package.json", "pnpm-lock.yaml"];
const digest = createHash("sha256");
for (const relativePath of digestInputs) {
  digest.update(relativePath).update("\0").update(readFileSync(path.join(frontendRoot, relativePath))).update("\0");
}
const expected = digest.digest("hex");
const bundle = readFileSync(path.join(frontendRoot, "../src/main/resources/META-INF/vireon/openapi/review-plugin.js"), "utf8");
const actual = bundle.match(/vireon-review-source-sha256:([a-f0-9]{64})/)?.[1];
assert.equal(actual, expected, "review-plugin.js 已陈旧，请在 frontend 运行 pnpm run build");
assert.doesNotMatch(bundle, /\bprocess\./, "浏览器 bundle 不得依赖 Node process 全局变量");
assert.doesNotMatch(bundle, /https?:\/\//, "评审插件不得包含外部 HTTP 请求地址");
console.log(`review-plugin.js inputs sha256 ${actual}`);

const scalarDigest = createHash("sha256");
for (const file of ["src/scalar.ts", "src/scalar-csp.ts", "vite.scalar.config.ts", "package.json", "pnpm-lock.yaml"]) {
  scalarDigest.update(file).update("\0").update(readFileSync(path.join(frontendRoot, file))).update("\0");
}
const scalarExpected = scalarDigest.digest("hex");
for (const file of ["scalar.js", "scalar.css"]) {
  const content = readFileSync(path.join(frontendRoot, "../src/main/resources/META-INF/vireon/openapi", file), "utf8");
  assert.equal(content.match(/vireon-scalar-source-sha256:([a-f0-9]{64})/)?.[1], scalarExpected, `${file} 已陈旧，请重建`);
}
console.log(`scalar inputs sha256 ${scalarExpected}`);
