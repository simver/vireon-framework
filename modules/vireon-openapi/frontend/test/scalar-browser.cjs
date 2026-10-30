const {assertBrowserExecutionAllowed,launchChromium}=require('./browser-safety.cjs');
assertBrowserExecutionAllowed(); // before output, servers, fixtures or browser loading
const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");

const playwrightRoot = process.env.PLAYWRIGHT_ROOT || "C:/Users/simver/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright";
const chromePath = process.env.CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe";
const outputRoot = process.env.SCALAR_BROWSER_OUTPUT || path.join(os.tmpdir(), "vireon-artifacts", "framework-scalar-d2-frontend");
const moduleRoot = path.resolve(__dirname, "../..");
const pluginPath = path.join(moduleRoot, "src/main/resources/META-INF/vireon/openapi/review-plugin.js");
const stylesPath = path.join(outputRoot, "styles.css");
const scalarPath = path.join(moduleRoot, "src/main/resources/META-INF/vireon/openapi/scalar.js");
const { chromium } = require(playwrightRoot);

fs.mkdirSync(outputRoot, { recursive: true });
fs.writeFileSync(stylesPath, Buffer.concat([fs.readFileSync(path.join(moduleRoot, "src/main/resources/META-INF/vireon/openapi/scalar.css")), Buffer.from("\n"), fs.readFileSync(path.join(moduleRoot, "src/main/resources/META-INF/vireon/openapi/styles.css"))]));
const malicious = '<img src=x onerror="window.__xss=1">危险文本';
const review = { schemaVersion: 1, service: "browser-probe", revision: "abc123", inputDigest: "sha256:123",
  internalEndpoints: [{ operationId: "probe", method: "GET", path: "/internal/probe", summary: malicious }],
  tables: [{ name: "probe_table", comment: "探针表", columns: [{ name: "id", type: "uuid", nullable: false, comment: malicious }], indexes: [] }],
  relationships: [{ fromTable: "probe_child", fromColumn: "parent_id", toTable: "probe_table", toColumn: "id", description: "父级" }],
  permissions: [{ code: "probe:read", description: malicious }], runtimeNotes: [{ title: "同源", description: malicious }] };
const openapi = { openapi: "3.1.0", info: { title: "Vireon browser probe", version: "1" }, paths: { "/probe": { get: { operationId: "probePublic", summary: "Public probe", "x-badges": [{ name: "固定版", position: "before", color: "#2563eb" }], responses: { 200: { description: "OK" } } } } } };
const bootstrap = `const script=document.querySelector('script[data-review-url]');document.body.dataset.layout=new URL(location.href).searchParams.get('layout')||'balanced';window.Scalar.createApiReference('#app',{url:'/openapi.json',telemetry:false,withDefaultFonts:false,persistAuth:false,agent:{disabled:true},pluginUrls:[new URL('/review-plugin.js',location.href).href]});window.__reviewUrl=script.dataset.reviewUrl;`;
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'self';script-src 'self';connect-src 'self';style-src 'self' 'unsafe-inline';img-src 'self' data:;font-src 'self';object-src 'none';base-uri 'none'"><link rel="stylesheet" href="/styles.css"><link rel="icon" href="data:,"><title>D2</title></head><body data-layout="balanced"><div id="app"></div><script src="/scalar.js"></script><script src="/bootstrap.js" data-review-url="/review.json"></script></body></html>`;
const resources = new Map([["/", ["text/html", html]], ["/bootstrap.js", ["text/javascript", bootstrap]], ["/openapi.json", ["application/json", JSON.stringify(openapi)]], ["/review.json", ["application/json", JSON.stringify(review)]], ["/scalar.js", ["text/javascript", scalarPath]], ["/review-plugin.js", ["text/javascript", pluginPath]], ["/styles.css", ["text/css", stylesPath]]]);
const requests = [];
const server = http.createServer((request, response) => { const pathname = new URL(request.url, "http://localhost").pathname; requests.push(pathname); const resource = resources.get(pathname); if (!resource) return response.writeHead(404).end(); response.writeHead(200, { "Content-Type": resource[0], "Cache-Control": "no-store" }); fs.existsSync(resource[1]) ? fs.createReadStream(resource[1]).pipe(response) : response.end(resource[1]); });

(async () => {
  let browser;
  try {
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    browser = await launchChromium(chromium,require.resolve(playwrightRoot+"/package.json"),{ executablePath: chromePath, headless: true });
    const page = await browser.newPage({ viewport: { width: 1800, height: 1000 } });
    const consoleErrors = []; const pageErrors = []; const externalRequests = [];
    page.on("console", (message) => { if (message.type() === "error") consoleErrors.push(message.text()); });
    page.on("pageerror", (error) => pageErrors.push(error.message));
    page.on("request", (request) => { if (new URL(request.url()).origin !== `http://127.0.0.1:${server.address().port}`) externalRequests.push(request.url()); });
    await page.goto(`http://127.0.0.1:${server.address().port}/?layout=request-expanded`, { waitUntil: "domcontentloaded" });
    await page.getByText("架构与实现评审").waitFor();
    await page.getByText("内部端点", { exact: true }).waitFor();
    await page.getByRole("button", { name: "接口审查" }).click();
    const maliciousNode = page.getByText(malicious).first(); await maliciousNode.waitFor();
    const escaped = await maliciousNode.evaluate((node) => ({ text: node.textContent, children: node.children.length }));
    await page.getByRole("button", { name: "数据建模" }).click(); await page.getByText("probe_table", { exact: true }).waitFor();
    await page.getByRole("button", { name: "权限码" }).click(); await page.getByText("probe:read", { exact: true }).waitFor();
    await page.getByRole("button", { name: "运行约束" }).click(); await page.getByText("同源", { exact: true }).waitFor();
    const search = page.getByRole("searchbox"); await search.fill("不存在"); await page.getByText("没有匹配的运行注记").waitFor(); await search.fill("同源"); await page.getByText("同源", { exact: true }).waitFor();
    const theme = await page.locator(".vireon-review").evaluate((node) => {
      const colors = () => ({ background: getComputedStyle(node).backgroundColor, color: getComputedStyle(node).color });
      const light = colors(); document.body.classList.remove("light-mode"); document.body.classList.add("dark-mode"); const dark = colors(); return { light, dark };
    });
    const layout = await page.locator(".operation-layout").first().evaluate((node) => {
      const measure = (mode) => { document.body.dataset.layout = mode; const columns = getComputedStyle(node).gridTemplateColumns.split(" ").map(Number.parseFloat); return { mode, columns }; };
      return [measure("balanced"), measure("request-focused"), measure("request-expanded")];
    });
    assert.equal(escaped.children, 0); assert.equal(await page.evaluate(() => window.__xss || 0), 0);
    const ratios = layout.map((entry) => entry.columns[0] / entry.columns[1]);
    assert.ok(Math.abs(ratios[0] - 1) < 0.02); assert.ok(Math.abs(ratios[1] - (2 / 3)) < 0.02); assert.ok(Math.abs(ratios[2] - (3 / 7)) < 0.02);
    assert.ok(layout[2].columns[0] >= 360);
    assert.ok(await page.getByText("固定版").count());
    assert.notDeepEqual(theme.light, theme.dark);
    assert.deepEqual(consoleErrors, []); assert.deepEqual(pageErrors, []); assert.deepEqual(externalRequests, []);
    assert.ok(requests.includes("/review-plugin.js") && requests.includes("/review.json"));
    const bundle = fs.readFileSync(pluginPath, "utf8"); assert.doesNotMatch(bundle, /\bprocess\./); assert.match(bundle, /vireon-review-source-sha256:[a-f0-9]{64}/);
    const result = { scalarVersion: "1.66.1", scalarSha256: require('node:crypto').createHash('sha256').update(fs.readFileSync(scalarPath)).digest('hex'), views: 5, search: true, pluginLoaded: true, reviewLoaded: true, layout, theme, escaped, xss: 0, consoleErrors, pageErrors, externalRequests, requests };
    fs.writeFileSync(path.join(outputRoot, "result.json"), JSON.stringify(result, null, 2));
    await page.screenshot({ path: path.join(outputRoot, "review.png"), fullPage: true });
    console.log(JSON.stringify(result, null, 2));
  } finally {
    if (browser) await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
