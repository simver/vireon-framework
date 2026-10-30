# OpenAPI 构建目录工具

`com.simxe.vireon:vireon-openapi-build` 是构建期 JAR，入口为
`com.simxe.vireon.openapi.build.ApiReviewGenerator`。运行期应用不依赖本工具。
使用 JDK 25 官方 [JavacTask.parse](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html)
解析 Java AST，不需要编译或加载消费服务，不执行注解处理器，不使用 Java 正则解析器。

## 调用及输入

使用 `exec-maven-plugin` 的 plugin dependency 消费正式坐标，`includePluginDependencies=true`、
`includeProjectDependencies=false`，`mainClass` 为上述入口。`generate-resources` 生成，
`prepare-package` 再以 `--verify true` 校验，避免生成后的源码变更留下陈旧目录。

```text
--service example-service
--source /service/src/main/java
--migrations /service/src/main/resources/db/migration
--notes /service/ops/docs/api-review-notes.json
--output /service/target/classes
--strict-history false
--verify false
```

所有参数成对传入，布尔仅接受 true/false。service 仅由 CLI 提供。
注记必须恰好具有以下三个数组，禁止重复 JSON 键或未知字段：

```json
{
  "relationships": [
    {"fromTable":"app.detail","fromColumn":"fact_id","toTable":"app.fact","toColumn":"id","description":"应用层关系"}
  ],
  "permissions": [{"code":"example:read","description":"读取示例"}],
  "runtimeNotes": [{"title":"运行约束","description":"这里填写允许披露的设计注记"}]
}
```

关系引用必须存在于提取的模型；权限码和运行注记标题唯一。不得填入凭据、连接地址或业务数据。
每份输入/输出上限 2 MiB，注记嵌套最多 12 层，单项文本最多 16000 字符且禁止 NUL，数组最多 2000 项。
权限码仅接受 `[A-Za-z0-9:._-]{1,128}`；operationId 为 `[A-Za-z0-9._-]{1,160}`，
handler 为 `[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+`，与运行时协议一致。
源码、迁移不跟随符号链接。输出不可位于输入根中或覆盖注记；输出目录、祖先和已存在目标
拒绝符号链接、junction/reparse point，并核验真实路径。

Java 首期支持显式 `Controller`/`RestController`、标准 Spring MVC Mapping、单路径与单 HTTP 方法，
路径和 `Operation` 字段为字面量或字符串拼接。每个 handler 必须显式 `operationId`，
禁止重复 operationId、同名 handler 重载和重复 method/path。继承控制器、路径常量、多路由及
源码中声明的组合 Controller/Mapping 注解明确失败。
公开 HTTP 和 DTO 仅由 springdoc 提供；目录仅记录 Hidden 内部端点及处理器历史。
消费运行组件还须将目录与实际 HandlerMethod 比对，以发现自定义组合注解等未覆盖的动态映射。

SQL 首期为明确的 PostgreSQL 子集：整数 `V1__name.sql` 迁移按版本数值排序；
支持 schema 限定 CREATE TABLE、bigint/smallint/integer/uuid/boolean/jsonb/text/varchar/timestamptz、
NOT NULL、PRIMARY KEY、UNIQUE、GENERATED ALWAYS AS IDENTITY、本项目毫秒时钟默认表达式、
普通/唯一列索引及 ASC/DESC、表列 COMMENT。另支持整数/布尔/单引号文本字面量默认值（解析但不输出），
以及单个已知列与字面量等值的部分索引条件；任意函数、复合条件仍拒绝。
隐式索引名称使用 PostgreSQL 常规命名，超过 63 字符失败。
引号、逗号、分号与 SQL 注释按词法边界处理。
INSERT/UPDATE/DELETE/GRANT/REVOKE 不改变该目录模型，忽略其内容且不输出真实值；
受限支持 `ALTER TABLE schema.table ADD COLUMN <上述列定义>` 与
`ALTER TABLE schema.table ALTER COLUMN column SET/DROP NOT NULL`，并核验表、列存在及重复列；
ALTER TYPE、其他 ALTER/DROP、表达式索引、未知类型/约束和其他 SQL 语句明确失败，扩展前须补充语法和回归用例。
不连接数据库，关系仅来自经引用校验的项目注记。

## schemaVersion 1

两个输出均位于 `META-INF/vireon/`，公共头为
`schemaVersion:1, service, revision, inputDigest`。revision 为 Git HEAD 或 `unavailable`；
inputDigest 是按逻辑相对路径、长度和原始字节绑定全部 Java、迁移、注记的 SHA-256，无绝对路径。

- `api-review.json`：internalEndpoints（operationId/method/path/summary）、
  tables（name/comment/columns/indexes）、relationships、permissions、runtimeNotes。
  columns 为 name/type/nullable/comment；indexes 为 name/unique/columns（保留排序方向），部分索引另有predicate纯文本。
- `api-git-history.json`：operations，每项为 operationId、handler（完整类名#方法名）、
  state（committed/dirty/unavailable）、commit、timestamp；后两项无可靠历史时为 null。

Git 使用 [git log -L](https://git-scm.com/docs/git-log) 追踪处理方法及其声明注解范围；
committed 时间使用该范围最后提交的 committer timestamp（ISO UTC，显示层转 Asia/Shanghai），
不代表业务调用链变更时间。`git diff HEAD --unified=0` 与方法范围求交；
无关方法变更不会标记 dirty，前方增删行换算到 HEAD 范围。未跟踪/新增源文件 dirty 优先。
浅历史、缺 Git、重命名或追踪失败明确 unavailable；不以文件 mtime/构建时间回退。
`--strict-history true` 要求每个 handler 已提交且有完整可追踪历史，并核验全部 Java、迁移及
注记受 Git 跟踪且干净，并检查源码和迁移目录的完整状态，包含已删除的跟踪文件。
生成过程前后校验输入摘要与 revision；输出无构建时间，
完全相同输入与 Git 状态产生完全相同字节。
`--verify true` 重新生成并逐字节比较两份目录，缺失/重复/陈旧映射失败。

## 本地验证

2026-09-11：JDK 25 Tree API 编译探针成功；初始 11 项测试通过，审查修复后外置独立源码快照
`verify` 为 20 项通过、0 失败/错误/跳过；快照完成二进制/source/javadoc JAR 与 Spotless 检查。
覆盖确定性、公开契约与数据不输出、陈旧目录、operationId 遗漏/重复、未知 SQL/引用、
注记重复键/结构、源码边界、Git 缺失/方法范围/dirty/rename/shallow/strict 和输入大小；
新增协议文本边界、组合注解遗漏、删除首注解、非 handler 输入 strict 检查、identity 非空以及
真实 Windows junction/注记覆盖拒绝，以及删除仅 DML 迁移/普通 Java 文件的 strict 反例。
revision 并发保护已实现，未单独执行并发提交竞态测试。
构建输出外置；正式仓库发布、空缓存 plugin 消费及完整项目门禁由所属计划统一记录。
