# 项目管理增强设计

## 设计结论

在现有 `platform/system` 项目域中追加 Flyway 迁移。项目创建继续保留负责人选择器，默认当前用户；服务端创建项目时初始化项目角色 `PM/项目负责人`，并在同一事务中将创建人加入项目并绑定该角色。项目负责人字段仍可在项目编辑时修改。

项目阶段和计划阶段使用参数管理维护：参数类别编码分别为 `PROJECT_PHASE` 和 `PLAN_PHASE`，参数键作为稳定值，参数值作为中文显示名。项目模块通过 `/api/project/options` 返回启用的阶段和组织树选项，避免前端直接依赖系统参数权限。

计划组织关系使用独立关联表，支持一个牵头组织和多个配合组织，避免把多值组织 ID 编码进单个业务字段。每次保存计划时替换该计划的组织关系，并按当前租户校验组织有效性。

项目和计划日期在服务端合并当前值后校验，前端在提交前即时校验；结束日期为空时不限制，存在时必须大于等于开始日期。

## 主要契约

- `GET /api/project/options`
  - `project_phases: [{ value, label }]`
  - `plan_phases: [{ value, label }]`
  - `organizations: [{ id, parent_id, org_name, children }]`
- `pm_project.phase`
- `pm_project_plan.phase`
- `pm_project_plan_org(plan_id, org_id, party_type)`，`party_type` 为 `LEAD` 或 `COOPERATING`

## 风险与回滚

- 旧项目阶段为空时由迁移填充默认参数键；旧计划组织关系为空，不影响已有数据。
- 若迁移失败，回滚新增迁移并停止后端发布；不修改已发布历史迁移。
- 服务端校验是最终约束，前端校验只负责即时反馈。
