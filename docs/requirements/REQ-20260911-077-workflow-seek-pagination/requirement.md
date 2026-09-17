---
id: REQ-20260911-077
status: ready
owner: rokeyvvz0828
module: platform/workflow
---

# 工作流高增长列表游标分页

## 业务目标

为待办、已办审计记录和流程实例历史提供稳定的 seek pagination，避免数据增长后深页 `OFFSET` 扫描，同时保持现有后台管理列表的页码分页和权限边界不变。

## 范围

### 本次实施

- 新增受认证和既有数据范围约束保护的工作流游标列表接口：待办、已办和实例历史。
- 游标按 `(created_at, id)` 降序编码；请求游标不合法时返回既有统一参数错误。
- 游标响应返回记录、`next_cursor` 与 `has_more`，不执行 `COUNT(*)`。
- 为三条读取路径追加 MySQL 8.4 复合索引。
- 覆盖首屏、续页、时间相同的 ID 边界、租户与项目可见范围、非法游标的聚焦测试。

### 本次不实施

- 不移除或改变 `/api/workflows/instances`、`/inbox`、`/done` 的现有页码分页响应。
- 不改变流程实例详情时间线、定义版本历史、Flowable 引擎表或业务模块接口。
- 不改前端页面；当前前端未消费上述高增长列表接口，后续独立 UI 任务再切换调用方。

## 现状与规则

- `WorkflowService.inbox`、`done` 与 `WorkflowMonitorService.instances` 均使用 `LIMIT/OFFSET` 和 `COUNT(*)`。
- 项目与租户范围由现有 `InstanceVisibility` 和 `WorkflowProjectAccessGateway` 在服务层确定，游标不得绕过。
- `(created_at, id)` 是稳定排序键；游标仅携带时间和 ID，不含租户、用户、业务编号或权限信息。

## 接口与数据

- API 契约：新增 `GET /api/workflows/inbox/seek`、`/done/seek`、`/instances/seek`；保留原接口及响应不变。
- 数据 Owner：工作流实例、任务和任务动作均属于 platform/workflow；Flyway 属于 platform/infrastructure。
- 数据库迁移与存量兼容：追加复合索引；应用回退时索引可保留，旧页码查询仍可使用。
- 脱敏输入输出示例：`cursor` 是 Base64URL 编码的时间与 ID；日志、指标和错误不输出原始游标载荷。

## 验收标准

1. 三个 seek 接口使用 `created_at < ? OR (created_at = ? AND id < ?)`，每次最多读取请求大小加一条，且没有 `OFFSET` 或 `COUNT(*)`。
2. 首屏和使用 `next_cursor` 的续页无重复、无跳过；同一时间戳的记录按 ID 降序连续返回。
3. 游标请求与原接口一致地执行认证、租户、项目和实例可见范围限制；非法游标被拒绝。
4. V204 增加与三条排序及固定过滤条件匹配的复合索引，不修改已发布迁移。
5. Workflow 聚焦测试、模块回归、迁移检查、范围检查与本地受保护接口验证通过，或如实记录环境限制。

## 测试与发布

- 必须执行的测试：`mvn -pl :ccb-workflow -am test`、`mvn test`、游标 SQL/权限聚焦测试、Flyway 迁移检查。
- 上线验证：观察三条新接口的 p95、查询计划索引命中、空游标首屏与连续翻页结果。
- 回退或补偿：回退应用提交即可停止新接口调用；V204 的附加索引保留，不需要数据补偿。
- 风险与人工复核人：platform/workflow、platform/infrastructure Owner `rokeyvvz0828`；重点复核游标签名/解析、排序一致性和项目范围条件。
