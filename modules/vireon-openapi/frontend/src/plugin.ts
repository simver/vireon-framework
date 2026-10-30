import { defineComponent, h, type VNodeChild } from "vue";

type InternalEndpoint = { operationId?: string; method?: string; path?: string; summary?: string };
type Column = { name?: string; type?: string; nullable?: boolean; comment?: string };
type Index = { name?: string; unique?: boolean; columns?: string[]; predicate?: string };
type Table = { name?: string; comment?: string; columns?: Column[]; indexes?: Index[] };
type Relationship = { fromTable?: string; fromColumn?: string; toTable?: string; toColumn?: string; description?: string };
type Permission = { code?: string; description?: string };
type RuntimeNote = { title?: string; description?: string };
type ReviewDocument = {
  schemaVersion?: number;
  service?: string;
  revision?: string;
  inputDigest?: string;
  internalEndpoints?: InternalEndpoint[];
  tables?: Table[];
  relationships?: Relationship[];
  permissions?: Permission[];
  runtimeNotes?: RuntimeNote[];
};

const tabs = [
  ["overview", "总览"],
  ["endpoints", "接口审查"],
  ["data", "数据建模"],
  ["permissions", "权限码"],
  ["runtime", "运行约束"],
] as const;
type Tab = (typeof tabs)[number][0];

const safeText = (value: unknown, fallback = "—"): string =>
  typeof value === "string" || typeof value === "number" || typeof value === "boolean" ? String(value) : fallback;
const normalize = (value: unknown): string => safeText(value, "").toLocaleLowerCase("zh-CN");
const matches = (query: string, values: unknown[]): boolean => !query || normalize(values.map((value) => safeText(value, "")).join(" ")).includes(normalize(query));
const list = <T>(value: T[] | undefined): T[] => Array.isArray(value) ? value : [];
const reviewUrl = (): URL => {
  const script = document.querySelector<HTMLScriptElement>("script[data-review-url]");
  const configured = script?.dataset.reviewUrl ?? "";
  if (!configured.startsWith("/")) throw new Error("评审数据路径必须是绝对同源路径");
  const url = new URL(configured, window.location.origin);
  if (url.origin !== window.location.origin || url.username || url.password || url.search || url.hash) {
    throw new Error("评审数据路径必须是绝对同源路径");
  }
  return url;
};

const Empty = (message: string): VNodeChild => h("p", { class: "vireon-review-empty" }, message);
const ReviewPanel = defineComponent({
  name: "VireonApiReviewPanel",
  data() {
    return { document: null as ReviewDocument | null, error: "", active: "overview" as Tab, query: "" };
  },
  async mounted() {
    try {
      const response = await fetch(reviewUrl(), { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const document = await response.json() as ReviewDocument;
      if (document.schemaVersion !== 1) throw new Error(`不支持的 schemaVersion：${safeText(document.schemaVersion)}`);
      this.document = document;
    } catch (cause) {
      this.error = cause instanceof Error ? cause.message : "未知错误";
    }
  },
  methods: {
    renderOverview(): VNodeChild {
      const document = this.document;
      if (!document) return null;
      const metrics = [
        [list(document.internalEndpoints).length, "内部端点"],
        [list(document.tables).length, "数据表"],
        [list(document.relationships).length, "表关系"],
        [list(document.permissions).length, "权限码"],
        [list(document.runtimeNotes).length, "运行注记"],
      ];
      return h("div", { class: "vireon-review-overview" }, [
        h("div", { class: "vireon-review-metrics" }, metrics.map(([value, label]) =>
          h("article", { class: "vireon-review-metric" }, [h("strong", safeText(value)), h("span", label)]))),
        h("dl", { class: "vireon-review-provenance" }, [
          h("div", [h("dt", "服务"), h("dd", safeText(document.service))]),
          h("div", [h("dt", "源码版本"), h("dd", [h("code", safeText(document.revision))])]),
          h("div", [h("dt", "输入摘要"), h("dd", [h("code", safeText(document.inputDigest))])]),
        ]),
      ]);
    },
    renderEndpoints(): VNodeChild {
      const endpoints = list(this.document?.internalEndpoints).filter((item) => matches(this.query, Object.values(item)));
      if (!endpoints.length) return Empty(this.query ? "没有匹配的内部端点" : "没有内部端点");
      return h("div", { class: "vireon-review-list" }, endpoints.map((endpoint) => h("article", { class: "vireon-review-card" }, [
        h("div", { class: "vireon-review-card-title" }, [h("span", { class: "vireon-review-method" }, safeText(endpoint.method)), h("code", safeText(endpoint.path))]),
        h("h3", safeText(endpoint.summary)),
        h("p", { class: "vireon-review-meta" }, ["operationId ", h("code", safeText(endpoint.operationId))]),
        h("p", { class: "vireon-review-disclosure" }, "内部实现审查信息，不属于公开 OpenAPI 契约"),
      ])));
    },
    renderData(): VNodeChild {
      const tables = list(this.document?.tables).filter((table) => matches(this.query, [table.name, table.comment,
        ...list(table.columns).flatMap((column) => Object.values(column)), ...list(table.indexes).flatMap((index) => [index.name, index.predicate, ...list(index.columns)])]));
      const relationships = list(this.document?.relationships).filter((relation) => matches(this.query, Object.values(relation)));
      if (!tables.length && !relationships.length) return Empty(this.query ? "没有匹配的数据模型" : "没有数据模型");
      return h("div", { class: "vireon-review-data" }, [
        ...tables.map((table) => h("article", { class: "vireon-review-card" }, [
          h("h3", [h("code", safeText(table.name)), h("span", safeText(table.comment, ""))]),
          list(table.columns).length ? h("div", { class: "vireon-review-table-wrap" }, [h("table", [
            h("thead", [h("tr", ["字段", "类型", "可空", "说明"].map((label) => h("th", label)))]),
            h("tbody", list(table.columns).map((column) => h("tr", [
              h("td", [h("code", safeText(column.name))]), h("td", safeText(column.type)), h("td", column.nullable ? "是" : "否"), h("td", safeText(column.comment)),
            ]))),
          ])]) : Empty("没有字段"),
          list(table.indexes).length ? h("ul", { class: "vireon-review-indexes" }, list(table.indexes).map((index) =>
            h("li", [h("code", safeText(index.name)), ` · ${index.unique ? "唯一" : "普通"} · ${list(index.columns).map((column) => safeText(column)).join(", ")}`, index.predicate ? ` · 条件：${safeText(index.predicate)}` : ""]))) : null,
        ])),
        relationships.length ? h("section", { class: "vireon-review-relations", "aria-label": "表关系" }, [
          h("h3", "关系路径"),
          ...relationships.map((relation) => h("article", { class: "vireon-review-relation" }, [
            h("code", `${safeText(relation.fromTable)}.${safeText(relation.fromColumn)}`),
            h("span", { "aria-hidden": "true" }, "→"),
            h("code", `${safeText(relation.toTable)}.${safeText(relation.toColumn)}`),
            h("p", safeText(relation.description)),
          ])),
        ]) : null,
      ]);
    },
    renderPermissions(): VNodeChild {
      const permissions = list(this.document?.permissions).filter((item) => matches(this.query, Object.values(item)));
      if (!permissions.length) return Empty(this.query ? "没有匹配的权限码" : "没有权限码");
      return h("div", { class: "vireon-review-list" }, permissions.map((permission) => h("article", { class: "vireon-review-card vireon-review-permission" }, [
        h("code", safeText(permission.code)), h("p", safeText(permission.description)),
      ])));
    },
    renderRuntime(): VNodeChild {
      const notes = list(this.document?.runtimeNotes).filter((item) => matches(this.query, Object.values(item)));
      if (!notes.length) return Empty(this.query ? "没有匹配的运行注记" : "没有运行注记");
      return h("div", { class: "vireon-review-list" }, notes.map((note) => h("article", { class: "vireon-review-card vireon-review-note" }, [
        h("h3", safeText(note.title)), h("p", safeText(note.description)),
      ])));
    },
    renderActive(): VNodeChild {
      return ({ overview: this.renderOverview, endpoints: this.renderEndpoints, data: this.renderData,
        permissions: this.renderPermissions, runtime: this.renderRuntime } as Record<Tab, () => VNodeChild>)[this.active].call(this);
    },
  },
  render() {
    return h("section", { class: "vireon-review", "aria-labelledby": "vireon-review-title" }, [
      h("header", { class: "vireon-review-header" }, [
        h("p", { class: "vireon-review-kicker" }, "VIREON REVIEW"),
        h("h2", { id: "vireon-review-title" }, "架构与实现评审"),
        h("p", "Scalar 展示公开接口契约；这里集中呈现同服务内部端点、数据模型、权限与运行约束。"),
      ]),
      !this.document && !this.error ? h("p", { class: "vireon-review-status", role: "status" }, "正在加载评审数据…") : null,
      this.error ? h("p", { class: "vireon-review-error", role: "alert" }, `评审数据加载失败：${this.error}`) : null,
      this.document ? h("nav", { class: "vireon-review-tabs", "aria-label": "评审视图" }, tabs.map(([key, label]) => h("button", {
        type: "button", class: this.active === key ? "is-active" : "", "aria-pressed": this.active === key,
        onClick: () => { this.active = key; },
      }, label))) : null,
      this.document ? h("label", { class: "vireon-review-search" }, [
        h("span", "搜索当前视图"),
        h("input", { type: "search", value: this.query, placeholder: "输入路径、表名、权限码或运行条件",
          onInput: (event: Event) => { this.query = (event.target as HTMLInputElement).value; } }),
      ]) : null,
      this.document ? h("div", { class: "vireon-review-content" }, [this.renderActive()]) : null,
    ]);
  },
});

export default () => ({
  name: "vireon-api-review",
  extensions: [],
  views: { "content.end": [{ component: ReviewPanel, sidebar: { show: true, label: "架构评审" } }] },
});
