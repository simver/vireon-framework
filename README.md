# Vireon Framework

面向业务宿主的通用 Java／Spring 基础框架，独立仓库、独立构建，通过正式 Maven 坐标被 IAM 和业务使用。

## 初始化状态

2026-10-29 建立独立 Git 历史，目标主分支为 `main`。本次仅包含仓库说明、协作规则和来源记录；尚未迁入源码、POM、Wrapper 或运行配置，暂无可执行构建／启动命令，不代表框架功能已经交付。

来源：[simver/vireon-framework](https://github.com/simver/vireon-framework)。在 QBC Vireon 父仓中以 `repos/framework` 子模块接入，由父仓 gitlink 固定版本。

## 一期边界

- 延续 core、web、security、observability、openapi、openapi-build、test-support 和 starter-service 的职责；具体模块按冻结源码和已确认设计迁入，不创建空制品。
- 提供身份／权限契约及通用 HTTP、CORS、CSRF 机制；不依赖 IAM 的账号、角色或数据库模型。
- 默认不自动连接数据库、Redis 或外部服务；运行日志不承担系统审计业务。
- 一期支持模块化单体，不提前建设分布式平台、RPC 或消息总线。

下一步在父仓任务工作树中，依据一期设计与实施计划开展 T0 来源映射及 T1 契约工作。规则见 [AGENTS.md](AGENTS.md)，来源见 [源码来源](docs/source-origin.md)。
