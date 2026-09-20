# 需求管理权限重建实施计划

## 交付边界

需求：`docs/requirements/REQ-20260919-077-requirement-permission-rebuild/requirement.md`
范围：`docs/requirements/REQ-20260919-077-requirement-permission-rebuild/codex-task-scope.yaml`
控制前缀：`req-20260919-077-requirement-permission-rebuild`

## 任务包

| # | 任务 | 文件 | 依赖 | 采样点 |
| --- | --- | --- | --- | --- |
| T1 | 权限与角色种子迁移：删旧码/绑定、建 9 码、建三角色模板、补日志索引 | `V20260919200000__requirement_permission_rebuild.sql` | 无 | Flyway 迁移检查 + 本地迁移执行 |
| T2 | 数据范围服务重写：三档范围 + 可见/可操作判定 | `RequirementSecurityService.java` | T1 | 模块测试 |
| T3 | 差异域改造：列表/详情范围、收回接口、注解换码 | `RequirementDifferenceService.java`、`RequirementController.java` | T2 | 模块测试 |
| T4 | 存量域改造：列表/详情范围、流转收回、注解换码 | `RequirementLegacyService.java`、`RequirementLegacyController.java` | T2 | 模块测试 |
| T5 | 交付件/基线/导入/系统/参数域注解换码 | 相关 service/controller | T2 | 模块测试 |
| T6 | 前端：菜单/按钮/收回入口与范围展示 | `RequirementsView.vue`、`api/requirements.ts` | T3–T5 | 前端构建 |
| T7 | 演示数据重建：三角色 + 流转链样例（含 A→B 后提出人收回场景） | 迁移内种子 | T1 | 本地验证 |

## 固定契约

- 新权限码：`requirement:access`、`requirement:propose`、`requirement:edit`、`requirement:transfer`、`requirement:withdraw`、`requirement:review`、`requirement:read-own`、`requirement:read-project`、`requirement:manage`。
- 项目角色编码：`REQUIREMENT_PROPOSER`、`REQUIREMENT_ANALYST`、`REQUIREMENT_COORDINATOR`。
- 数据范围枚举：`ALL`（统筹/owner/PM）、`ASSIGNED`（分析员：当前处理人本人或流转日志曾与本人发生流转）、`OWN`（提出人：`created_by` 本人）。
- 收回日志动作：`WITHDRAW`，`to_user_id` = 提出人。

## 回滚

代码与迁移整体回退；测试库重新执行重建迁移并重建演示数据。

## 验证

按 `codex-task-scope.yaml` 的 `required_tests` 执行；浏览器验收由用户执行。
