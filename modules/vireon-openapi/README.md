# Vireon OpenAPI

可选Servlet/Spring MVC组件。正式坐标为`com.simxe.vireon:vireon-openapi:0.1.0-SNAPSHOT`；
通用service starter不默认引入。当前组合与验收以父仓[一期设计](../../../../docs/superpowers/specs/2026-10-29-phase1-modular-monolith-design.md)为准。

## 消费配置

```yaml
vireon:
  openapi:
    enabled: true
    path: /api/example/docs
    title: 示例接口与架构评审
    layout: balanced
    review:
      enabled: false
    git-history:
      enabled: false
    try-it:
      enabled: false
springdoc:
  api-docs:
    enabled: true
    path: /api/example/v3/api-docs
```

默认全部关闭。springdoc和组件总开关必须一致；Swagger UI、额外Scalar入口、管理端口文档及分组不支持。
路径必须是规范绝对路径，标题最多160字符，布局仅balanced/request-focused/request-expanded。
布局在宽容器下调整请求区域，窄屏保留Scalar原生布局。基础文档无需Git、数据库、评审资源或Node运行时。

消费项目必须提供`OpenApiSecurityAdapter`，传入实际文档权限码、Framework `PermissionEvaluator` 及既有401/403处理器；
其既有SecurityFilterChain通过`OpenApiRoutes.matches(request)`选择、认证并授权完整精确资源集合。
适配器不解析凭据、不建立会话。框架过滤器在既有安全链后再次检查认证，并通过 ALL 模式实时判断该权限，
不读取 Authentication authorities 快照，也不默认放行管理员。缺少适配器启动失败；显式 DENY 才进入403处理器。权限端口异常原样传播到宿主已有错误边界，保留依赖故障503及失效会话401等分类，不伪装为正常拒绝。null属于端口契约错误，不能当作DENY；这些情况均不渲染文档。

```java
new OpenApiSecurityAdapter("service:docs:read", permissionEvaluator, entryPoint, deniedHandler);
```

IAM 宿主注入其提供的 Framework PermissionEvaluator，不向 Framework 引入 IAM 依赖。Cookie 的 Access Path 为 /api 时，文档和 schema 也应配置在 /api 内，保证真实凭据到达。

`OpenApiRoutes.paths()`包含HTML、scalar.js、bootstrap.js、styles.css、schema JSON及YAML；
启用评审后增加review-plugin.js及review.json。组件读取资源支持 GET/HEAD；本项目宿主统一入口进一步只允许 GET/POST 和必要预检，因此实际 HEAD 返回405。HTML和所有资源受相同权限保护。
拒绝转发/include、编码/尾斜杠/分组别名和第三方webjar旁路；关闭时仍拒绝依赖包中的Scalar静态资源。
不新增SecurityFilterChain、不读取Forwarded头、不启用遥测、外部字体、Agent/MCP或凭据持久化。
请求测试按钮默认隐藏；显式`try-it.enabled=true`仅显示原生操作试调，独立Client入口仍隐藏。
试调沿用当前浏览器会话，写操作须手工提供服务CSRF，所有权限及Origin校验仍由原服务执行。
同源CSP限制请求目的地；框架不代理请求、不代填凭据。文档关闭时禁止启用该增强。
初始化脚本外置；CSP只允许同源脚本与连接，不允许unsafe-eval。Scalar动态样式需要style-src的unsafe-inline，
该例外已在固定版本浏览器探针验证；不允许任意脚本来源。

## 评审与Git数据

[vireon-openapi-build](../vireon-openapi-build/README.md)在构建时生成两份schemaVersion=1目录：
`META-INF/vireon/api-review.json`和`META-INF/vireon/api-git-history.json`。
评审模式读取成对目录以校验相同输入和全部运行handler，即使不显示Git标记也检查映射完整性。
`git-history.enabled`控制Git标记展示；仅此功能开启时可以不提供review目录。
每份最大2MiB、最多12层、文本最多16000字符；拒绝重复键、未知字段/版本、无效引用及重复classpath目录。
所有消费方MVC handler必须具有稳定、显式的`@Operation(operationId=...)`且与构建目录双向对应。
未支持的组合注解不能导致运行接口被静默遗漏；运行映射不一致直接启动失败。

公开HTTP/DTO契约仍由springdoc生成；插件仅展示内部接口、表列索引/关系、权限说明、运行约束和搜索。
Git标记只指处理方法及其声明注解；dirty显示“未提交变更”，committed显示Asia/Shanghai提交时间，
无Git/浅历史/无法可靠追踪则显示“历史不可用”。不得解释为整个业务调用链的更新时间。
运行进程不调用Git、不读取源码或数据库。只接受固定classpath资源，不接受文件路径/URL配置。

## 开发与验证

固定组合为Scalar API Reference1.66.1、Zod4.4.3、springdoc2.9.0、Vue3.5.42、Node24.19.0、pnpm11.19.0。
官方模块薄构建在Scalar初始化前配置Zod的`jitless`；JS及CSS从固定锁文件生成，替代Java0.6.65内嵌资源。
变更依据和严格CSP反例见[ADR-0006](../../docs/adr/ADR-0006-scalar-csp-and-opt-in-try-it.md)。
普通Framework Maven verify用`node frontend/test/verify-bundle.cjs`检测源/配置/锁文件与bundle是否陈旧。
完整`ops/ci/verify.py`另外以锁文件重建前端、执行组件测试并比较评审JS、Scalar JS/CSS三个产物的字节摘要。
生成物必须与源一起提交；不使用开发服务器，也不要求消费项目安装Node。

手动前端重建在外置源码快照的frontend目录执行，安装时显式设置仓库外store：
`pnpm --store-dir <任务缓存> install --frozen-lockfile --ignore-scripts`，然后`pnpm run build`及`pnpm test`。
`frontend/test/scalar-browser.cjs`通过PLAYWRIGHT_ROOT、CHROME_PATH、SCALAR_BROWSER_OUTPUT指定浏览器和输出，使用本模块受测资源。
`frontend/test/scalar-csp-browser.cjs`使用相同浏览器位置、PLAYWRIGHT_BROWSERS_PATH及新绝对SCALAR_CSP_OUTPUT，
在Firefox/Chrome核验默认隐藏、真实GET/PUT、零CSP事件/脚本错误/外联。该匿名探针不能替代服务身份验收。
正式验收还必须执行服务真实JAR浏览器联验，不能用插件静态探针替代服务身份验收。
