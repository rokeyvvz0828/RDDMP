# 项目管理设计

> 设计状态：已获用户确认。项目入口为单一“项目管理”菜单，项目内功能使用详情页签承载。

## 目标与边界

项目管理属于现有 `platform/system` 平台管理边界，新增独立 `/api/project` 接口和 `com.ccb.system.project` 包，不新增四个菜单，也不复制用户、角色和权限基础数据。前端使用现有 Vue、Element Plus、通用 UI 组件和动态路由契约。

## 用户流程

1. 用户点击“项目管理”，进入项目卡片工作台。
2. 卡片显示项目名称、编号、状态、负责人、计划日期、进度和成员头像。
3. 点击卡片进入项目详情，页签为“概览、项目计划、项目成员、项目角色”。
4. 项目计划使用父子任务表格，成员可关联多个项目角色，角色是项目内职责与授权标识。
5. 服务端根据当前用户和租户过滤所有项目域查询，并在写操作前检查项目范围与系统操作权限。

## 数据模型

- `pm_project`：项目主数据。
- `pm_project_plan`：项目计划任务，`parent_id` 建立任务树。
- `pm_project_role`：项目内角色，独立于 `sys_role`。
- `pm_project_member`：项目与用户、项目角色的关联，同一成员可有多个角色。

项目删除采用逻辑删除；关联成员、角色和计划同时逻辑删除，避免残留可见数据。所有表和字段使用中文注释，所有查询带 `tenant_id` 和 `deleted` 条件。

## API 契约

- `GET /api/project/workbench`：返回当前用户可见项目卡片。
- `POST /api/project`、`PUT /api/project/{id}`、`DELETE /api/project/{id}`：项目维护。
- `GET /api/project/{id}`：项目详情和统计摘要。
- `GET/POST/PUT/DELETE /api/project/{id}/plans[/{planId}]`：计划维护。
- `GET/POST/PUT/DELETE /api/project/{id}/members[/{memberId}]`：成员维护。
- `GET/POST/PUT/DELETE /api/project/{id}/roles[/{roleId}]`：项目角色维护。

所有响应使用现有 `ApiResponse`；日期接口返回 `yyyy-MM-dd HH:mm:ss`，前端展示按现有约定显示年月日。

## 权限

项目菜单使用 `project:access`，操作权限使用 `project:project:*`、`project:plan:*`、`project:member:*`、`project:role:*`。菜单权限种子与 `SUPER_ADMIN` 角色绑定；服务端 `ProjectService` 同时校验系统操作权限和项目数据范围。

## 风险与验证

主要风险是前端隐藏造成越权、关联数据越租户、项目角色与系统角色混淆，以及新增路由未被动态菜单加载。验证覆盖服务层的管理员全量可见、普通成员范围、非成员拒绝、跨租户拒绝和权限拒绝，并执行 Maven、Vue 构建、治理检查和真实接口冒烟。
