# Vireon Framework

面向业务宿主的通用 Java／Spring 基础框架，独立仓库、独立构建，通过正式 Maven 坐标被 IAM 和业务使用。

## 初始化状态

2026-10-29 建立独立 Git 历史，目标主分支为 `main`。T1 已迁入独立 POM、Wrapper、基础模块及原测试，并重构身份／权限契约。使用 JDK 25，在本仓执行 `./mvnw.cmd verify`；Maven 固定为 3.9.16。当前不提供应用启动入口。

来源：[simver/vireon-framework](https://github.com/simver/vireon-framework)。在 QBC Vireon 父仓中以 `repos/framework` 子模块接入，由父仓 gitlink 固定版本。

## 一期边界

- 延续 core、web、security、observability、openapi、openapi-build、test-support 和 starter-service 的职责；具体模块按冻结源码和已确认设计迁入，不创建空制品。
- 提供身份／权限契约及通用 HTTP、CORS、CSRF 机制；不依赖 IAM 的账号、角色或数据库模型。
- 默认不自动连接数据库、Redis 或外部服务；运行日志不承担系统审计业务。
- 一期支持模块化单体，不提前建设分布式平台、RPC 或消息总线。

身份通过 CurrentIdentity 读取；权限请求仅含权限码与 ALL／ANY，不携带任意账号或全局权限快照。无身份／权限后端时拒绝，依赖异常向调用方传播。旧 Principal 的组织与权限字段已移除，调用方须迁移。通用 HTTP 方法／CORS／CSRF 的新宿主约束在 T6 实施，本次构建通过不代表这些 HTTP 行为已经完成。规则见 [AGENTS.md](AGENTS.md)，来源见 [源码来源](docs/source-origin.md)。
