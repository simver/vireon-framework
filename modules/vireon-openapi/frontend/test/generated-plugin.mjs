import assert from "node:assert/strict";
import { createRenderer, nextTick } from "vue";
import createPlugin from "../../src/main/resources/META-INF/vireon/openapi/review-plugin.js";

const node = (type, text = "") => ({ type, text, children: [], parent: null, props: {} });
const renderer = createRenderer({
  patchProp(element, key, _previous, next) { element.props[key] = next; },
  insert(child, parent, anchor) { child.parent = parent; const index = anchor ? parent.children.indexOf(anchor) : -1; index < 0 ? parent.children.push(child) : parent.children.splice(index, 0, child); },
  remove(child) { const index = child.parent?.children.indexOf(child) ?? -1; if (index >= 0) child.parent.children.splice(index, 1); },
  createElement: (type) => node(type), createText: (text) => node("#text", text), createComment: (text) => node("#comment", text),
  setText(target, text) { target.text = text; }, setElementText(target, text) { target.text = text; target.children = []; },
  parentNode: (target) => target.parent, nextSibling: (target) => target.parent?.children[(target.parent.children.indexOf(target) + 1)] ?? null,
  querySelector: () => null, setScopeId() {},
  insertStaticContent(content, parent, anchor) { const target = node("#static", content); target.parent = parent; const index = anchor ? parent.children.indexOf(anchor) : -1; index < 0 ? parent.children.push(target) : parent.children.splice(index, 0, target); return [target, target]; },
});

const malicious = '<img src=x onerror="globalThis.__xss=1">危险文本';
const script = { dataset: { reviewUrl: "/api/test/docs/review.json" } };
globalThis.document = { querySelector: () => script };
globalThis.window = { location: { origin: "http://localhost" } };
let requested;
let fetchCalls = 0;
globalThis.fetch = async (url, options) => {
  fetchCalls += 1;
  requested = { url: String(url), options };
  return { ok: true, async json() { return {
    schemaVersion: 1, service: "test-service", revision: "abc123", inputDigest: "sha256:123",
    internalEndpoints: [{ operationId: "internalProbe", method: "GET", path: "/internal/probe", summary: malicious }],
    tables: [{ name: "iam_account", comment: "账号", columns: [{ name: "id", type: "uuid", nullable: false, comment: malicious }], indexes: [{ name: "uk_account", unique: true, columns: ["id"], predicate: "id = 'Special'" }] }],
    relationships: [{ fromTable: "iam_session", fromColumn: "account_id", toTable: "iam_account", toColumn: "id", description: "会话所属账号" }],
    permissions: [{ code: "iam:docs:read", description: malicious }], runtimeNotes: [{ title: "代理边界", description: malicious }],
  }; } };
};

const root = node("#root");
const plugin = createPlugin();
assert.equal(plugin.name, "vireon-api-review");
assert.deepEqual(Object.keys(plugin.views), ["content.end"]);
const app = renderer.createApp(plugin.views["content.end"][0].component);
app.mount(root);
const text = (target) => target.text + target.children.map(text).join("");
const find = (target, predicate) => predicate(target) ? target : target.children.map((child) => find(child, predicate)).find(Boolean);
for (let attempt = 0; attempt < 20 && text(root).includes("正在加载"); attempt += 1) { await new Promise((resolve) => setImmediate(resolve)); await nextTick(); }
assert.equal(requested.url, "http://localhost/api/test/docs/review.json");
assert.equal(requested.options.credentials, "same-origin");
assert.match(text(root), /内部端点1数据表1表关系1权限码1运行注记/);

for (const label of ["接口审查", "数据建模", "权限码", "运行约束"]) {
  const button = find(root, (target) => target.type === "button" && target.text === label);
  assert.ok(button, `缺少 ${label} 标签`);
  button.props.onClick(); await nextTick();
  assert.match(text(root), new RegExp(label === "接口审查" ? "internal/probe" : label === "数据建模" ? "iam_account" : label === "权限码" ? "iam:docs:read" : "代理边界"));
  if(label === "数据建模") assert.ok(text(root).includes("条件：id = 'Special'"));
}
const elementsWithMaliciousText = [];
const collect = (target) => { if (target.text.includes("<img")) elementsWithMaliciousText.push(target); target.children.forEach(collect); };
collect(root);
assert.ok(elementsWithMaliciousText.length > 0);
assert.ok(elementsWithMaliciousText.every((target) => target.children.length === 0), "恶意文本不得形成子元素");
app.unmount();

script.dataset.reviewUrl = "//evil.example/review.json";
const rejectedRoot = node("#root");
const rejectedApp = renderer.createApp(plugin.views["content.end"][0].component);
rejectedApp.mount(rejectedRoot);
await nextTick();
assert.match(text(rejectedRoot), /评审数据路径必须是绝对同源路径/);
assert.equal(fetchCalls, 1, "拒绝跨源路径后不得发起 fetch");
rejectedApp.unmount();
