# 架构管理模块集成契约

## 范围与状态

本契约归属 `business/architecture`，约束逻辑子系统、物理子系统及其安全选项接口。当前只完成模块边界登记；具体 HTTP DTO 与行为将随对应实现任务按已批准需求补齐并由自动化测试锁定。

## 固定边界

- 资源根为 `/api/architecture/logical-subsystems` 与 `/api/architecture/physical-subsystems`。
- 前端代码归 `web/src/modules/architecture/**`，后端代码归 `server/src/modules/architecture/**`。
- 组织数据通过 `com.ccb.system.org.OrganizationService` 读取；用户、参数和操作审计只通过 `com.ccb.system.capability` 公开契约访问。
- HTTP 请求和响应不得包含 `tenantId`；租户只从服务端认证上下文注入。
- 当前不提供启停用接口，也不提供动态表单 schema 接口。
- 业务模块只直接读写 `arch_` 表，不直连 platform/system 数据表。
