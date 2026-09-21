# 需求管理流转/审批人员来源统一与审批接入审批流设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：2026-09-19 用户确认"只能读取项目管理里的项目组织架构里的人员来进行流转和审批、审批要接入审批流、要接入消息通知"，并明确"阶段推进不需要审批""按推荐方案开发"。

## 目标与成功信号

把两个需求页面的流转人、审批人来源收敛到项目管理项目组织架构的有效成员；把存量交付件评审接入平台审批流；把审批结果与流转事件接入站内消息。成功信号：候选人只来自当前项目组织架构；非成员提交被服务端拒绝；交付件评审在待办中完成并自动回写；审批与流转消息在通知中心可见且可跳转。

## 方案比较与选择

| 方案 | 做法 | 结论 |
| --- | --- | --- |
| A | 需求模块声明 `platform/system` 依赖，使用 `ProjectMemberReferenceQuery` + `ProjectAccessService` + `SystemNotificationPublisher` | 选择。语义匹配官方契约，服务端可强校验；通知接入契约本身即要求声明该依赖 |
| B | 借用工作流 `project-options` 与 `WorkflowProjectAccessGateway` | 不选。该契约要求调用者是项目成员，且语义属于流程节点解析，长期形成隐性耦合 |
| C | 仅前端收敛下拉，后端只校验用户启用 | 不选。可被直接调用接口绕过，不满足"只能"的强约束 |

## 架构边界与组件职责

- `RequirementProjectMemberService`（business/requirement 新增）：按 `projectRef` 解析项目并返回有效成员；提供 `requireActiveMembers` 强校验。
- `RequirementDifferenceService`：差异流转、差异提交评审改用成员目录并强校验；流程上下文不变。
- `RequirementLegacyService`：需求流转强校验接收人并发布流转/回传通知。
- `RequirementLegacyEnhanceService`：交付件提交评审启动 `requirement.legacy.deliverable.review`，删除直连评审写入。
- `RequirementWorkflowListener`：新增 `req-deliverable:{type}:{id}` 回写分支。
- `ProjectMemberReferenceQuery` / `ProjectAccessService` / `SystemNotificationPublisher`：platform/system 公开契约，只读或发布，不改平台实现。
- `RequirementsView.vue` / `api/requirements.ts`：选人来源、交付件审批入口、通知深链。

## 接口、数据与状态流

- `GET /api/requirements/project-members?projectRef=`：项目成员目录（`id/username/display_name`）。
- 提交类接口（差异提交评审、差异流转、需求流转、交付件提交评审）在事务内做成员资格校验，失败抛 `BAD_REQUEST` 且不落任何业务变更。
- 交付件评审状态机：`待评审/已退回` --提交评审--> `评审中`（生成流程实例）--工作流终态--> `已评审`/`已退回`，同时写 `req_review_record`。
- 流转不进入流程：只更新当前处理人、写 `req_flow_log`，并向接收人发布一条站内通知（`eventId = req-flow:{logId}`）。
- 迁移新增 `req_workload.workflow_instance_id`、`req_soft_doc.workflow_instance_id`，并追加全局流程定义 `requirement.legacy.deliverable.review`。

## 错误处理与质量属性

- 成员校验失败给出可操作提示（先到项目管理→项目组织架构添加成员）。
- 提交评审对已存在同 businessKey 的运行实例先终结再重建，避免退回后重提产生双实例。
- 通知失败不阻塞业务提交，异常按现有通知发布实现处理；通知不写入敏感字段。
- 前端保留加载、空、失败、提交中、防重复提交与移动端可用性。

## 非目标

- 阶段推进接入审批。
- 按项目角色或组织树选人。
- 短信/邮件/企微/浏览器推送与通知订阅偏好。
- 改造工作流定义的项目级模板模型。

## 验证策略

模块测试、全量后端测试、前端构建、治理检查与 `git diff --check`；浏览器验收按用户 2026-09-19 要求由用户执行，Codex 不声明浏览器验收证据。

## 主要风险

1. 存量在审交付件没有流程实例，回写逻辑需按"仅处理评审中且有实例"的幂等规则兼容。
2. 流转范围收窄可能挡住跨部门协作，需要在提示中引导先加入项目组织架构。
3. 新增 platform/system 依赖属公共能力依赖声明变更，需 Owner 复核。
