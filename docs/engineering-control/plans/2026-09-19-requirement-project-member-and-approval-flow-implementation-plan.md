# 需求管理流转/审批人员来源统一实施计划

## 交付边界

需求：`docs/requirements/REQ-20260919-076-requirement-project-member-and-approval-flow/requirement.md`
范围：`docs/requirements/REQ-20260919-076-requirement-project-member-and-approval-flow/codex-task-scope.yaml`
控制前缀：`req-20260919-076-requirement-member-approval`

## 任务包

| # | 任务 | 文件 | 依赖 | 采样点 |
| --- | --- | --- | --- | --- |
| T1 | 声明 platform/system 依赖 | `governance/modules.yaml`、`server/src/modules/requirement/pom.xml` | 无 | `node scripts/check-all-governance.mjs` |
| T2 | 项目成员目录服务与接口 | `RequirementProjectMemberService.java`、`RequirementController.java` | T1 | `mvn -pl :ccb-requirement -am test` |
| T3 | 差异流转/提交评审改成员来源并强校验 | `RequirementDifferenceService.java`、`RequirementController.java` | T2 | 同上 |
| T4 | 存量需求流转强校验 + 流转/回传通知 | `RequirementLegacyService.java` | T2 | 同上 |
| T5 | 交付件评审接入审批流并回写 | `RequirementLegacyEnhanceService.java`、`RequirementWorkflowListener.java`、`RequirementLegacyController.java` | T2 | 同上 |
| T6 | 迁移：交付件流程实例列 + 流程定义 | `V20260919180000__requirement_member_approval_and_flow_notification.sql` | 无 | Flyway 迁移检查 |
| T7 | 前端选人来源、审批入口与通知深链 | `web/src/api/requirements.ts`、`web/src/views/RequirementsView.vue` | T2–T5 | `npm --prefix web run build` |

## 固定契约

- 成员目录响应：`{ id, username, display_name }`，其中 `id` 为用户 ID（与 `wf_task.assignee_id`、`current_flow_user_id` 一致）。
- 交付件评审 businessKey：`req-deliverable:{WORKLOAD|SOFT}:{deliverableId}`，businessType：`requirement_deliverable_review`。
- 通知板块：`moduleCode=requirement`、`moduleName=需求管理`；流转 `eventId=req-flow:{reqFlowLogId}`，回传 `eventId=req-flow-return:{reqFlowLogId}`。
- 交付件评审动作路径：`/requirements/legacy`；差异评审：`/requirements/new-project`。

## 回滚

回退代码、pom 与 modules.yaml；数据库保留新增列与流程定义。

## 验证

按 `codex-task-scope.yaml` 的 `required_tests` 执行；浏览器验收由用户执行。
