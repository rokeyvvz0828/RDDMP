---
id: REQ-20260919-076
status: ready
owner: rokeyvvz0828
module: business/requirement
---

# 需求管理流转/审批人员来源统一与审批接入审批流

> 跨模块说明：本需求涉及 `business/requirement`（主）、`platform/system`（只读项目成员契约与站内通知发布契约，仅新增依赖声明，不改平台代码）、`platform/workflow`（复用已发布流程能力）、`platform/infrastructure`（追加 Flyway 迁移）、`frontend/application`（需求页面）。

## 业务目标

需求管理平台（新建项目页与存量项目页）当前的流转人、审批人候选取自"全租户启用用户"或需求模块私有的 `req_project_member`，与项目管理里的项目组织架构脱节：用户看到的人不是他所在项目的人，选到的人也无法被项目权限约束。同时存量项目的交付件评审没有走审批流，流转全程没有消息提醒。

本需求把两个页面的全部选人入口收敛到"项目管理 → 项目组织架构"的当前项目有效成员，把存量交付件评审接入平台审批流，并把审批与流转结果接入站内消息通知。

可观察的成功结果：

- 新建项目页的差异流转、差异提交评审，存量项目页的需求流转、交付件提交评审，四个选人下拉都只出现当前项目组织架构中的有效成员；切换顶部项目后候选随之变化。
- 直接用接口提交非当前项目成员作为流转人或审批人，服务端返回明确错误且不产生任何状态变更。
- 存量工作量表/软需文档提交评审后生成流程实例，审批人在"我的待办/任务中心"处理，通过或退回后交付件状态与评审记录自动回写。
- 审批通知与流转通知都出现在消息中心，业务板块显示"需求管理"，点击可进入需求管理对应页面。
- 存量项目"阶段推进"保持现有直连状态流转，不新增审批。

## 范围

### 本次实施

- 新增需求模块项目成员目录服务与只读接口 `GET /api/requirements/project-members?projectRef=`，数据来自 `com.ccb.system.capability.ProjectMemberReferenceQuery`，项目主键由 `com.ccb.system.capability.ProjectAccessService.requireAccessible(projectRef, actor)` 解析。
- 差异流转、差异提交评审、存量需求流转、交付件提交评审四个入口的候选人与服务端强校验统一走该目录；非当前项目有效成员一律拒绝。
- 存量交付件评审接入新流程定义 `requirement.legacy.deliverable.review`（动态审批人），新增 `req_workload`/`req_soft_doc` 的 `workflow_instance_id` 列。
- `RequirementWorkflowListener` 增加交付件评审回写分支（通过→已评审、退回/驳回→已退回，并写 `req_review_record`）。
- 流转成功通知接收人、回传通知被回传人，使用 `SystemNotificationPublisher`，板块 `requirement / 需求管理`。
- `governance/modules.yaml` 与 `server/src/modules/requirement/pom.xml` 声明对 `platform/system` 的依赖（站内通知接入契约的强制要求）。
- 前端：`web/src/api/requirements.ts` 与 `web/src/views/RequirementsView.vue` 的选人来源、交付件评审入口、通知跳转落地。

### 本次不实施

- 存量项目"阶段推进"接入审批（用户 2026-09-19 明确不接入）。
- 审批人按项目角色（`PROJECT_ROLE`）选择、按组织树/业务组选择。
- 短信、邮件、企业微信、浏览器推送；通知模板、订阅偏好与合并节流。
- 删除或迁移 `req_project_member`、`req_legacy_member` 历史数据（仅停止作为候选人来源）。
- 工作流定义的项目级模板改造（继续使用全局模板 `GLOBAL`）。

## 业务规则

1. 候选人集合 = `pm_project_member` 中该项目 `status=1`、`deleted=0` 且 `sys_user` 启用未删除的成员；这是流转人、审批人的唯一来源。
2. 项目归属由业务记录反查，不信任前端传入：差异按 `req_difference.project_id → req_project.project_code`，存量需求按 `req_legacy_requirement.project_id → req_project.project_code`。
3. 服务端在提交时对每个被选用户执行成员资格校验，任一人不是当前项目有效成员即整单拒绝，错误信息指明"不是当前项目的有效成员"。
4. 存量交付件评审只允许从"待评审/已退回"提交；提交后状态为"评审中"，审批只能通过工作流任务完成，业务接口不再直接写评审结论。
5. 流转不产生审批任务，仅变更当前处理人并写流转记录；流转通知为定向单条消息，不进入待办。
6. 通知动作路径只允许站内路由；幂等键使用业务事件 ID，重复提交不产生重复消息。
7. 阶段推进、基线、导入、协同事项等既有行为不变。

## 接口与数据

- `GET /api/requirements/project-members?projectRef=<projectCode>&keyword=`：返回 `[{id, username, display_name}]`，需 `requirement:access`。
- `POST /api/requirements/differences/{id}/submit-review`：新增审批人成员资格校验。
- `POST /api/requirements/differences/{id}/transfer`：新增接收人成员资格校验。
- `POST /api/requirements/legacy/{id}/flow`：新增接收人成员资格校验并发布流转通知。
- `POST /api/requirements/legacy/{id}/flow/return`：发布回传通知。
- `POST /api/requirements/deliverables/{id}/submit-review`：新增成员资格校验并启动审批流。
- 删除 `POST /api/requirements/deliverables/{id}/review`（评审改为工作流待办处理）。
- 迁移 `V20260919180000__requirement_member_approval_and_flow_notification.sql`：新增 `req_workload.workflow_instance_id`、`req_soft_doc.workflow_instance_id`，追加流程定义 `requirement.legacy.deliverable.review`。

## 权限、审计与数据边界

- 全部接口保留既有 `@PreAuthorize`；成员目录接口额外要求请求项目可访问。
- 资质校验失败返回 400/403，不写业务表、不启动流程、不发通知。
- 流转继续写 `req_flow_log` 与统一改动记录；交付件评审继续写 `req_review_record` 与改动记录。
- 通知不携带敏感字段，接收人限定同租户启用用户。

## 验收标准

1. 四个选人场景只出现当前项目组织架构成员；切项目后候选变化。
2. 伪造非项目成员 ID 提交流转或审批，接口报错且无状态变更。
3. 存量交付件提交评审生成流程实例，待办中可审批，通过/退回后交付件状态与评审记录正确。
4. 审批与流转消息在通知中心可见，板块"需求管理"，点击可达需求页面。
5. 阶段推进仍为直接流转，无审批任务产生。
6. `mvn -pl :ccb-requirement -am test`、`mvn test`、`npm --prefix web run build` 与治理检查通过。

## 回退

回退本次 Java/TS/Vue 与依赖声明；数据库新增列与流程定义保留（旧应用忽略新列，流程定义未使用时不影响存量）。不修改历史 Flyway 文件。
