/* vireon-review-source-sha256:12821c268b3762315fd9acd6730ff222db678a588a962e951e12dd3358e57311 */
/**
* @vue/shared v3.5.42
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
const x = (e) => e.charCodeAt(0) === 111 && e.charCodeAt(1) === 110 && // uppercase letter
(e.charCodeAt(2) > 122 || e.charCodeAt(2) < 97), U = (e) => e.startsWith("onUpdate:"), A = Object.assign, d = Array.isArray, h = (e) => typeof e == "function", m = (e) => typeof e == "string", D = (e) => typeof e == "symbol", v = (e) => e !== null && typeof e == "object";
let V;
const N = () => V || (V = typeof globalThis < "u" ? globalThis : typeof self < "u" ? self : typeof window < "u" ? window : typeof global < "u" ? global : {});
function F(e) {
  if (d(e)) {
    const t = {};
    for (let n = 0; n < e.length; n++) {
      const i = e[n], r = m(i) ? K(i) : F(i);
      if (r)
        for (const o in r)
          t[o] = r[o];
    }
    return t;
  } else if (m(e) || v(e))
    return e;
}
const L = /;(?![^(]*\))/g, M = /:([^]+)/, B = /\/\*[^]*?\*\//g;
function K(e) {
  const t = {};
  return e.replace(B, "").split(L).forEach((n) => {
    if (n) {
      const i = n.split(M);
      i.length > 1 && (t[i[0].trim()] = i[1].trim());
    }
  }), t;
}
function I(e) {
  let t = "";
  if (m(e))
    t = e;
  else if (d(e))
    for (let n = 0; n < e.length; n++) {
      const i = I(e[n]);
      i && (t += i + " ");
    }
  else if (v(e))
    for (const n in e)
      e[n] && (t += n + " ");
  return t.trim();
}
/**
* @vue/reactivity v3.5.42
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
new Set(
  /* @__PURE__ */ Object.getOwnPropertyNames(Symbol).filter((e) => e !== "arguments" && e !== "caller").map((e) => Symbol[e]).filter(D)
);
// @__NO_SIDE_EFFECTS__
function O(e) {
  return e ? !!e.__v_raw : !1;
}
// @__NO_SIDE_EFFECTS__
function W(e) {
  return e ? e.__v_isRef === !0 : !1;
}
/**
* @vue/runtime-core v3.5.42
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
let b = null, G = null;
const R = (e) => e.__isTeleport;
function H(e) {
  let t = e[0];
  if (e.length > 1) {
    for (const n of e)
      if (n.type !== z) {
        t = n;
        break;
      }
  }
  return t;
}
function Y(e) {
  if (!Q(e))
    return R(e.type) && e.children ? H(e.children) : e;
  if (e.component)
    return e.component.subTree;
  const { shapeFlag: t, children: n } = e;
  if (n) {
    if (t & 16)
      return n[0];
    if (t & 32 && h(n.default))
      return n.default();
  }
}
function q(e, t) {
  if (e.shapeFlag & 6 && e.component) {
    e.transition = t;
    const n = e.component.subTree;
    q(
      R(n.type) && Y(n) || n,
      t
    );
  } else e.shapeFlag & 128 ? (e.ssContent.transition = t.clone(e.ssContent), e.ssFallback.transition = t.clone(e.ssFallback)) : e.transition = t;
}
// @__NO_SIDE_EFFECTS__
function J(e, t) {
  return h(e) ? (
    // #8236: extend call and options.name access are considered side-effects
    // by Rollup, so we have to wrap it in a pure-annotated IIFE.
    A({ name: e.name }, t, { setup: e })
  ) : e;
}
N().requestIdleCallback;
N().cancelIdleCallback;
const Q = (e) => e.type.__isKeepAlive, X = /* @__PURE__ */ Symbol.for("v-ndc"), Z = {}, k = (e) => Object.getPrototypeOf(e) === Z, ee = (e) => e.__isSuspense, $ = /* @__PURE__ */ Symbol.for("v-fgt"), te = /* @__PURE__ */ Symbol.for("v-txt"), z = /* @__PURE__ */ Symbol.for("v-cmt");
function E(e) {
  return e ? e.__v_isVNode === !0 : !1;
}
const P = ({ key: e }) => e ?? null, y = ({
  ref: e,
  ref_key: t,
  ref_for: n
}) => (typeof e == "number" && (e = "" + e), e != null ? m(e) || /* @__PURE__ */ W(e) || h(e) ? { i: b, r: e, k: t, f: !!n } : e : null);
function ne(e, t = null, n = null, i = 0, r = null, o = e === $ ? 0 : 1, a = !1, f = !1) {
  const u = {
    __v_isVNode: !0,
    __v_skip: !0,
    type: e,
    props: t,
    key: t && P(t),
    ref: t && y(t),
    scopeId: G,
    slotScopeIds: null,
    children: n,
    component: null,
    suspense: null,
    ssContent: null,
    ssFallback: null,
    dirs: null,
    transition: null,
    el: null,
    anchor: null,
    target: null,
    targetStart: null,
    targetAnchor: null,
    staticCount: 0,
    shapeFlag: o,
    patchFlag: i,
    dynamicProps: r,
    dynamicChildren: null,
    appContext: null,
    ctx: b
  };
  return f ? (_(u, n), o & 128 && e.normalize(u)) : n && (u.shapeFlag |= m(n) ? 8 : 16), u;
}
const w = se;
function se(e, t = null, n = null, i = 0, r = null, o = !1) {
  if ((!e || e === X) && (e = z), E(e)) {
    const f = T(
      e,
      t,
      !0
      /* mergeRef: true */
    );
    return n && _(f, n), f.patchFlag = -2, f;
  }
  if (oe(e) && (e = e.__vccOpts), t) {
    t = ie(t);
    let { class: f, style: u } = t;
    f && !m(f) && (t.class = I(f)), v(u) && (/* @__PURE__ */ O(u) && !d(u) && (u = A({}, u)), t.style = F(u));
  }
  const a = m(e) ? 1 : ee(e) ? 128 : R(e) ? 64 : v(e) ? 4 : h(e) ? 2 : 0;
  return ne(
    e,
    t,
    n,
    i,
    r,
    a,
    o,
    !0
  );
}
function ie(e) {
  return e ? /* @__PURE__ */ O(e) || k(e) ? A({}, e) : e : null;
}
function T(e, t, n = !1, i = !1) {
  const { props: r, ref: o, patchFlag: a, children: f, transition: u } = e, C = t ? le(r || {}, t) : r, S = {
    __v_isVNode: !0,
    __v_skip: !0,
    type: e.type,
    props: C,
    key: C && P(C),
    ref: t && t.ref ? (
      // #2078 in the case of <component :is="vnode" ref="extra"/>
      // if the vnode itself already has a ref, cloneVNode will need to merge
      // the refs so the single vnode can be set on multiple refs
      n && o ? d(o) ? o.concat(y(t)) : [o, y(t)] : y(t)
    ) : o,
    scopeId: e.scopeId,
    slotScopeIds: e.slotScopeIds,
    children: f,
    target: e.target,
    targetStart: e.targetStart,
    targetAnchor: e.targetAnchor,
    staticCount: e.staticCount,
    shapeFlag: e.shapeFlag,
    // if the vnode is cloned with extra props, we can no longer assume its
    // existing patch flag to be reliable and need to add the FULL_PROPS flag.
    // note: preserve flag for fragments since they use the flag for children
    // fast paths only.
    patchFlag: t && e.type !== $ ? a === -1 ? 16 : a | 16 : a,
    dynamicProps: e.dynamicProps,
    dynamicChildren: e.dynamicChildren,
    appContext: e.appContext,
    dirs: e.dirs,
    transition: u,
    // These should technically only be non-null on mounted VNodes. However,
    // they *should* be copied for kept-alive vnodes. So we just always copy
    // them since them being non-null during a mount doesn't affect the logic as
    // they will simply be overwritten.
    component: e.component,
    suspense: e.suspense,
    ssContent: e.ssContent && T(e.ssContent),
    ssFallback: e.ssFallback && T(e.ssFallback),
    placeholder: e.placeholder,
    el: e.el,
    anchor: e.anchor,
    ctx: e.ctx,
    ce: e.ce
  };
  return u && i && q(
    S,
    u.clone(S)
  ), S;
}
function re(e = " ", t = 0) {
  return w(te, null, e, t);
}
function _(e, t) {
  let n = 0;
  const { shapeFlag: i } = e;
  if (t == null)
    t = null;
  else if (d(t))
    n = 16;
  else if (typeof t == "object")
    if (i & 65) {
      const r = t.default;
      r && (r._c && (r._d = !1), _(e, r()), r._c && (r._d = !0));
      return;
    } else
      n = 32, !t._ && !k(t) && (t._ctx = b);
  else if (h(t)) {
    if (i & 65) {
      _(e, { default: t });
      return;
    }
    t = { default: t, _ctx: b }, n = 32;
  } else
    t = String(t), i & 64 ? (n = 16, t = [re(t)]) : n = 8;
  e.children = t, e.shapeFlag |= n;
}
function le(...e) {
  const t = {};
  for (let n = 0; n < e.length; n++) {
    const i = e[n];
    for (const r in i)
      if (r === "class")
        t.class !== i.class && (t.class = I([t.class, i.class]));
      else if (r === "style")
        t.style = F([t.style, i.style]);
      else if (x(r)) {
        const o = t[r], a = i[r];
        a && o !== a && !(d(o) && o.includes(a)) ? t[r] = o ? [].concat(o, a) : a : a == null && o == null && // mergeProps({ 'onUpdate:modelValue': undefined }) should not retain
        // the model listener.
        !U(r) && (t[r] = a);
      } else r !== "" && (t[r] = i[r]);
  }
  return t;
}
{
  const e = N(), t = (n, i) => {
    let r;
    return (r = e[n]) || (r = e[n] = []), r.push(i), (o) => {
      r.length > 1 ? r.forEach((a) => a(o)) : r[0](o);
    };
  };
  t(
    "__VUE_INSTANCE_SETTERS__",
    (n) => n
  ), t(
    "__VUE_SSR_SETTERS__",
    (n) => n
  );
}
function oe(e) {
  return h(e) && "__vccOpts" in e;
}
function s(e, t, n) {
  try {
    const i = arguments.length;
    return i === 2 ? v(t) && !d(t) ? E(t) ? w(e, null, [t]) : w(e, t) : w(e, null, t) : (i > 3 ? n = Array.prototype.slice.call(arguments, 2) : i === 3 && E(n) && (n = [n]), w(e, t, n));
  } finally {
  }
}
const ce = [
  ["overview", "总览"],
  ["endpoints", "接口审查"],
  ["data", "数据建模"],
  ["permissions", "权限码"],
  ["runtime", "运行约束"]
], l = (e, t = "—") => typeof e == "string" || typeof e == "number" || typeof e == "boolean" ? String(e) : t, j = (e) => l(e, "").toLocaleLowerCase("zh-CN"), p = (e, t) => !e || j(t.map((n) => l(n, "")).join(" ")).includes(j(e)), c = (e) => Array.isArray(e) ? e : [], ae = () => {
  const t = document.querySelector("script[data-review-url]")?.dataset.reviewUrl ?? "";
  if (!t.startsWith("/")) throw new Error("评审数据路径必须是绝对同源路径");
  const n = new URL(t, window.location.origin);
  if (n.origin !== window.location.origin || n.username || n.password || n.search || n.hash)
    throw new Error("评审数据路径必须是绝对同源路径");
  return n;
}, g = (e) => s("p", { class: "vireon-review-empty" }, e), ue = /* @__PURE__ */ J({
  name: "VireonApiReviewPanel",
  data() {
    return { document: null, error: "", active: "overview", query: "" };
  },
  async mounted() {
    try {
      const e = await fetch(ae(), { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (!e.ok) throw new Error(`HTTP ${e.status}`);
      const t = await e.json();
      if (t.schemaVersion !== 1) throw new Error(`不支持的 schemaVersion：${l(t.schemaVersion)}`);
      this.document = t;
    } catch (e) {
      this.error = e instanceof Error ? e.message : "未知错误";
    }
  },
  methods: {
    renderOverview() {
      const e = this.document;
      if (!e) return null;
      const t = [
        [c(e.internalEndpoints).length, "内部端点"],
        [c(e.tables).length, "数据表"],
        [c(e.relationships).length, "表关系"],
        [c(e.permissions).length, "权限码"],
        [c(e.runtimeNotes).length, "运行注记"]
      ];
      return s("div", { class: "vireon-review-overview" }, [
        s("div", { class: "vireon-review-metrics" }, t.map(([n, i]) => s("article", { class: "vireon-review-metric" }, [s("strong", l(n)), s("span", i)]))),
        s("dl", { class: "vireon-review-provenance" }, [
          s("div", [s("dt", "服务"), s("dd", l(e.service))]),
          s("div", [s("dt", "源码版本"), s("dd", [s("code", l(e.revision))])]),
          s("div", [s("dt", "输入摘要"), s("dd", [s("code", l(e.inputDigest))])])
        ])
      ]);
    },
    renderEndpoints() {
      const e = c(this.document?.internalEndpoints).filter((t) => p(this.query, Object.values(t)));
      return e.length ? s("div", { class: "vireon-review-list" }, e.map((t) => s("article", { class: "vireon-review-card" }, [
        s("div", { class: "vireon-review-card-title" }, [s("span", { class: "vireon-review-method" }, l(t.method)), s("code", l(t.path))]),
        s("h3", l(t.summary)),
        s("p", { class: "vireon-review-meta" }, ["operationId ", s("code", l(t.operationId))]),
        s("p", { class: "vireon-review-disclosure" }, "内部实现审查信息，不属于公开 OpenAPI 契约")
      ]))) : g(this.query ? "没有匹配的内部端点" : "没有内部端点");
    },
    renderData() {
      const e = c(this.document?.tables).filter((n) => p(this.query, [
        n.name,
        n.comment,
        ...c(n.columns).flatMap((i) => Object.values(i)),
        ...c(n.indexes).flatMap((i) => [i.name, i.predicate, ...c(i.columns)])
      ])), t = c(this.document?.relationships).filter((n) => p(this.query, Object.values(n)));
      return !e.length && !t.length ? g(this.query ? "没有匹配的数据模型" : "没有数据模型") : s("div", { class: "vireon-review-data" }, [
        ...e.map((n) => s("article", { class: "vireon-review-card" }, [
          s("h3", [s("code", l(n.name)), s("span", l(n.comment, ""))]),
          c(n.columns).length ? s("div", { class: "vireon-review-table-wrap" }, [s("table", [
            s("thead", [s("tr", ["字段", "类型", "可空", "说明"].map((i) => s("th", i)))]),
            s("tbody", c(n.columns).map((i) => s("tr", [
              s("td", [s("code", l(i.name))]),
              s("td", l(i.type)),
              s("td", i.nullable ? "是" : "否"),
              s("td", l(i.comment))
            ])))
          ])]) : g("没有字段"),
          c(n.indexes).length ? s("ul", { class: "vireon-review-indexes" }, c(n.indexes).map((i) => s("li", [s("code", l(i.name)), ` · ${i.unique ? "唯一" : "普通"} · ${c(i.columns).map((r) => l(r)).join(", ")}`, i.predicate ? ` · 条件：${l(i.predicate)}` : ""]))) : null
        ])),
        t.length ? s("section", { class: "vireon-review-relations", "aria-label": "表关系" }, [
          s("h3", "关系路径"),
          ...t.map((n) => s("article", { class: "vireon-review-relation" }, [
            s("code", `${l(n.fromTable)}.${l(n.fromColumn)}`),
            s("span", { "aria-hidden": "true" }, "→"),
            s("code", `${l(n.toTable)}.${l(n.toColumn)}`),
            s("p", l(n.description))
          ]))
        ]) : null
      ]);
    },
    renderPermissions() {
      const e = c(this.document?.permissions).filter((t) => p(this.query, Object.values(t)));
      return e.length ? s("div", { class: "vireon-review-list" }, e.map((t) => s("article", { class: "vireon-review-card vireon-review-permission" }, [
        s("code", l(t.code)),
        s("p", l(t.description))
      ]))) : g(this.query ? "没有匹配的权限码" : "没有权限码");
    },
    renderRuntime() {
      const e = c(this.document?.runtimeNotes).filter((t) => p(this.query, Object.values(t)));
      return e.length ? s("div", { class: "vireon-review-list" }, e.map((t) => s("article", { class: "vireon-review-card vireon-review-note" }, [
        s("h3", l(t.title)),
        s("p", l(t.description))
      ]))) : g(this.query ? "没有匹配的运行注记" : "没有运行注记");
    },
    renderActive() {
      return {
        overview: this.renderOverview,
        endpoints: this.renderEndpoints,
        data: this.renderData,
        permissions: this.renderPermissions,
        runtime: this.renderRuntime
      }[this.active].call(this);
    }
  },
  render() {
    return s("section", { class: "vireon-review", "aria-labelledby": "vireon-review-title" }, [
      s("header", { class: "vireon-review-header" }, [
        s("p", { class: "vireon-review-kicker" }, "VIREON REVIEW"),
        s("h2", { id: "vireon-review-title" }, "架构与实现评审"),
        s("p", "Scalar 展示公开接口契约；这里集中呈现同服务内部端点、数据模型、权限与运行约束。")
      ]),
      !this.document && !this.error ? s("p", { class: "vireon-review-status", role: "status" }, "正在加载评审数据…") : null,
      this.error ? s("p", { class: "vireon-review-error", role: "alert" }, `评审数据加载失败：${this.error}`) : null,
      this.document ? s("nav", { class: "vireon-review-tabs", "aria-label": "评审视图" }, ce.map(([e, t]) => s("button", {
        type: "button",
        class: this.active === e ? "is-active" : "",
        "aria-pressed": this.active === e,
        onClick: () => {
          this.active = e;
        }
      }, t))) : null,
      this.document ? s("label", { class: "vireon-review-search" }, [
        s("span", "搜索当前视图"),
        s("input", {
          type: "search",
          value: this.query,
          placeholder: "输入路径、表名、权限码或运行条件",
          onInput: (e) => {
            this.query = e.target.value;
          }
        })
      ]) : null,
      this.document ? s("div", { class: "vireon-review-content" }, [this.renderActive()]) : null
    ]);
  }
}), fe = () => ({
  name: "vireon-api-review",
  extensions: [],
  views: { "content.end": [{ component: ue, sidebar: { show: !0, label: "架构评审" } }] }
});
export {
  fe as default
};
