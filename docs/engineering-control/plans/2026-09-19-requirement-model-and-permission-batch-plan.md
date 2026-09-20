# 需求管理「数据模型重构 + 权限重建」同批实施计划

## 交付边界

- 模型：`docs/requirements/REQ-20260919-078-requirement-data-model-rebuild/requirement.md`
- 权限：`docs/requirements/REQ-20260919-077-requirement-permission-rebuild/requirement.md`
- 两个范围文件各自授权，实施顺序：**先建模，后权限**（数据范围、收回、流转、开发来源契约都建立在模型之上）。

## 任务序列

| 阶段 | 任务 | 产出 |
| --- | --- | --- |
| P1 | 数据模型迁移：建主表/系统关联表/详情表/统一流转日志/交付件表，清空并重建演示数据 | `V20260919210000__requirement_data_model_rebuild.sql` |
| P2 | 权限种子迁移：删旧 29 码与绑定、建 9 码、建三角色模板、补日志索引 | `V20260919200000__requirement_permission_rebuild.sql` |
| P3 | 需求后端改造：数据范围三档、收回、流转、系统关联、序号自动生成、开发来源契约实现 | requirement 服务与控制器、`Jdbc*Query` |
| P4 | 系统目录适配：requirement 侧接口 + boot 适配器委托架构物理子系统 | `RequirementSystemDirectoryAdapter` |
| P5 | 前端：新建差异单选系统、存量系统行去系统人员、页签重组、收回入口、按范围与 can_edit 显隐 | `RequirementsView.vue`、`api/requirements.ts` |
| P6 | 演示数据与验收：三个角色、两页样例、跨手流转后提出人收回场景 | 迁移内种子 |

## 固定契约

- 主表：`req_requirement(id, project_id, requirement_kind, requirement_no, name, created_by, current_handler_user_id, current_handler_user_name, review_status, current_stage, workflow_instance_id, source, import_batch_id, ...)`。
- 系统关联：`req_requirement_system(requirement_id, physical_subsystem_id, subsystem_code, subsystem_name, system_role, owner_user_id, owner_user_name, status, start_date, end_date, description, remark)`。
- 系统角色枚举：`LEAD`（主责）/`CHANGE`（改造）/`TEST`（测试）。
- 开发来源 `active`：新建差异 = 项目内无待评审/评审中差异 且 已纳入基线；存量 = 已到软需阶段 且 立项已完成。
- 权限码与角色：见 REQ-20260919-077 设计。

## 不做

- `business/development` 与 `business/architecture` 代码改动。
- `dev_task` 来源类型约束放开（后续需求）。

## 回退

代码与两条迁移整体回退，测试库重建模型与演示数据。
