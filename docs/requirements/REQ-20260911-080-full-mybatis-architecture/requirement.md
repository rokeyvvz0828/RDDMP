# 全量 SQL 访问层迁移

## 目标

将服务端业务 SQL 统一收敛到 MyBatis Repository/Mapper 层，形成稳定的 `Controller -> Service -> Repository/Mapper -> Database` 分层，同时保持现有 API、权限、租户、事务、审计和数据结果不变。

## 必须需求

1. 所有生产代码 SQL 必须由 MyBatis Mapper/XML 或 Repository 实现承载；Controller、Service 不得直接调用 `JdbcTemplate`、`NamedParameterJdbcTemplate`、JPA native query 或拼接 SQL。
2. 每个迁移模块必须具备 Controller、Service、Repository/Mapper 的明确职责边界，公开 API 和 DTO 保持兼容。
3. 迁移后必须通过模块单测、集成测试、Flyway/MySQL 回归和接口功能验收；权限、租户、事务、审计和分页结果不得回归。
4. 迁移必须可分批回滚，任何模块出现行为偏差时可以独立恢复原实现。

## 不变量与约束

- 保留现有 `com.ccb.*` 包名、路由、响应结构和数据库表结构。
- Flyway 只允许追加，不修改已发布迁移。
- 不读取生产数据；`.env` 仅用于本地运行配置，不写入代码、日志或证据。
- 不绕过模块依赖、权限校验和审计要求。

## 非目标

- 不改变业务规则、页面交互或 API 语义。
- 不在本任务中引入新的 ORM 或数据库。
- 不通过自动脚本机械改写未经测试的 SQL。

## 验收

- 生产 Java 源码中无 Service/Controller 直接 SQL 访问。
- 每个模块的 Repository/Mapper 查询均有参数绑定、租户条件和权限调用链测试。
- 全量 Maven、MySQL/Flyway、接口和核心功能验收通过。
