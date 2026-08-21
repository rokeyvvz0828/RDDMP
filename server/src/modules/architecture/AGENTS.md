# 架构管理业务模块规则

适用于 `server/src/modules/architecture`，并继承仓库根目录和 `server/src/modules` 的规则。

- 本模块只负责逻辑子系统、物理子系统及其关联关系；不承载组织、用户、参数等平台数据。
- 只允许直接读写本模块拥有的 `arch_` 表。组织、用户、参数等平台数据必须通过 `com.ccb.system.capability` 公开契约访问，不得直连平台表或调用其私有 Repository。
- 租户标识只能从服务端认证上下文 `AuthUser` 注入，HTTP DTO 不得接收或返回 `tenantId`；本地 mock 初始化例外必须受 local profile、显式租户和租户存在性校验约束。
- 写接口必须执行服务端认证、RBAC、租户隔离、实体授权和操作审计；跨逻辑/物理记录写入按需求约定处理并发锁和冲突。
- 当前需求不实现系统启停用，也不引入动态表单元数据；前端使用固定、受控的字段结构。
- Flyway 迁移只能新增当前需求 scope 明确授权的版本，不得修改既有迁移。
- 新增或变更跨模块能力前，必须先维护 `governance/modules.yaml` 和对应集成契约。
