# 架构管理剩余业务按项目隔离工程设计

## 文档状态

- 修订：1
- 状态：已批准
- 需求：`REQ-20260905-065`
- 用户确认依据：2026-09-05 会话中逐段确认目标范围、数据库迁移、API 与权限、工作流与通知附件、前端项目切换以及测试发布设计，并批准落盘设计修订 1。

## 目标与成功信号

将网络专项工单、网络分区及网段、网络访问全链路和架构决策接入顶部当前项目，使四类业务从数据库、服务、接口、流程、通知、附件到前端状态形成完整项目隔离。

成功信号包括：不同项目可使用相同业务编号和分区名称；直接 ID、关联选择、审批回调、通知跳转和附件下载均不能跨项目；历史数据只属于 `RDDMP-PLATFORM`；共享的架构规范、计划模板、环境类型和平台字典行为不变。

## 范围与边界

### 项目私有

- 网络专项工单、历史、流程轮次和回执。
- 网络分区、网段和外部网络地址。
- 网络访问申请、历史、流程轮次、回执、访问关系和豁免规则。
- 架构决策事项、材料、评审、参与人、行动项、结论、发布意图、替代关系、编号序列、流程轮次和回执。

### 租户共享

- 架构规范及其版本、条款。
- 搭建计划模板及模板版本。
- 环境类型和平台字典。
- 工作流定义模板。

### 非目标

- 跨项目共享、复制、转移或合并业务数据。
- 新增项目级架构角色。
- 重新实现工作流项目权限或修改 Flowable `ACT_*` 表。
- 重命名 Java 包、数据库表或 API 根路径。
- 修改历史 Flyway 或访问生产数据。

## 方案比较与选择

采用“全链路显式项目归属”：主表和可独立查询、审计、回调的子表全部保存 `project_id`，API 显式传递 `projectRef`，服务端解析为可信项目，Store 使用 `tenant_id + project_id` 查询和写入。

未采用“仅根实体保存项目、子表通过 JOIN 推导”，因为直接访问子记录、生命周期回调和幂等处理容易遗漏项目条件。未采用“项目绑定关系表”，因为目标数据是单项目独占，额外绑定表会增加唯一性和关联复杂度。

## 架构边界与职责

- 前端项目上下文：提供当前 `projectRef`，无项目时阻止请求，切换时清理旧状态。
- Controller：所有目标接口要求 `projectRef`，保留原 RBAC 注解和输入校验。
- `ProjectAccessService`：校验租户、项目存在性、超级管理员或有效项目成员资格，返回可信 `ProjectAccess`。
- Service：组合业务状态、实体归属、关联项目和工作流项目校验。
- Store：所有目标 SQL 使用 `tenant_id + project_id`；直接 ID 查询不得只按 `tenant_id + id`。
- `WorkflowBusinessGateway`：复用平台现有项目流程匹配、实例固化、成员角色和待办隔离。
- 生命周期消费者：从业务实体恢复项目并校验事件项目、流程实例、轮次和回执。
- `ArchitectureProjectDeletionGuard`：检查新增项目化主表，存在数据时阻止项目删除。
- `LocalSeededWorkflowPublisher`：保持租户级流程定义发布；实施阶段审计网络和决策种子场景，只有缺少项目业务上下文时才调整。

参考实现采用当前已项目化的 `ArchitectureSubsystemSubmissionService`、`ResourceRequestSubmissionService` 及其 Controller、Store 和生命周期消费者模式。

## 数据模型与迁移

### 表范围

网络专项工单链：

- `arch_network_work_order`
- `arch_network_work_order_history`
- `arch_network_workflow_round`
- `arch_network_workflow_receipt`

网络分区和访问链：

- `arch_network_zone`
- `arch_network_zone_subnet`
- `arch_external_network_address`
- `arch_network_access_application`
- `arch_network_access_application_history`
- `arch_network_access_workflow_round`
- `arch_network_access_workflow_receipt`
- `arch_network_access_relation`
- `arch_network_access_exemption_rule`

架构决策链：

- `arch_decision_matter`
- `arch_decision_material`
- `arch_decision_review`
- `arch_decision_review_participant`
- `arch_decision_action_item`
- `arch_decision_conclusion`
- `arch_decision_publication_intent`
- `arch_decision_supersession`
- `arch_decision_number_sequence`
- `arch_decision_workflow_round`
- `arch_decision_workflow_receipt`

### 迁移顺序

1. 新增 `V158__scope_remaining_architecture_by_project.sql`，先为目标表增加可空 `project_id`。
2. 对存在目标数据的每个租户查找唯一活动 `RDDMP-PLATFORM`；零个或多个均失败。
3. 主表回填默认项目，从表按父链回填并校验不存在孤立记录。
4. 校验网络分区与已项目化部署单元、资源申请、环境部署实例的引用项目一致。
5. 校验决策替代关系、访问关系、豁免规则和流程记录不存在跨项目或无法确定归属的数据。
6. 将业务项目列改为非空，重建项目维度唯一索引和组合外键。

### 约束和唯一性

- 父子、关联和替代关系优先使用 `(tenant_id, project_id, id)` 组合约束。
- 架构决策编号、工单编号、访问申请编号、访问关系编号、分区编码、分区名称和外部地址按 `(tenant_id, project_id, ...)` 永久唯一。
- 决策编号序列主键或唯一键调整为 `(tenant_id, project_id, seq_year)`。
- 流程回执幂等键包含 `project_id`，相同业务编号或回执标识在不同项目间不冲突。
- 软删除不释放同项目编号和名称。

## API、服务与权限

```text
顶部 current projectRef
  -> 目标 API 必填 projectRef
  -> ProjectAccessService.requireAccessible
  -> ProjectAccess(projectId, projectRef, projectName)
  -> Service 业务与关联校验
  -> Store tenant_id + project_id 查询或写入
```

- 网络工单、网络访问和架构决策 Controller 的列表、详情、创建、修改、删除、提交、审批、发布及选项接口全部要求 `projectRef`。
- 创建和编辑 DTO 不接收项目主键，业务记录从可信 `ProjectAccess.id()` 写入项目。
- 普通用户必须同时满足项目有效成员资格和原有 `architecture:*` RBAC；超级管理员仍需明确项目上下文。
- 列表对其他项目数据不可见；详情、修改和删除以 `tenant_id + project_id + id` 查找，不属于当前项目时返回 `404`。
- 所有分区、网段、实例、关系、工单、决策、结论和替代目标在 Service 层校验同项目，数据库约束兜底。

## 工作流、通知、附件与事件

- `NetworkWorkOrderSubmissionService`、`NetworkAccessApplicationSubmissionService` 和 `ArchitectureDecisionService` 构造的 `WorkflowBusinessContext` 必须包含真实 `projectRef/projectName`。
- 启动结果必须与业务项目一致；上下文缺失或不一致时提交失败，业务状态不得伪装为已受理。
- 三类生命周期消费者按租户、项目、业务 ID、轮次和实例校验事件；不一致事件不更新业务事实。
- 流程轮次、回执、历史和幂等记录保存项目维度。
- 历史进行中流程随可证明关联的业务实体归入 `RDDMP-PLATFORM`，无法匹配时迁移失败并输出受限问题清单。
- 通知事件包含项目编号，跳转地址携带 `projectRef`；打开后仍执行项目访问和实体归属校验。
- 附件操作以业务实体项目为权威，附件 ID 不能绕过项目边界。
- 业务、工作流和通知事件的幂等身份包含租户、项目、业务类型和业务 ID。

## 前端交互与项目切换

- `web/src/modules/architecture/network.ts`、`api.ts` 中目标调用统一使用现有项目化请求约定并附带当前 `projectRef`。
- 页面复用顶部项目选择器，不新增局部项目控件。
- 无当前项目时显示统一选择项目状态，不发送网络或决策业务请求。
- 项目切换后取消或忽略旧项目响应，清空列表、详情、分页、筛选、选中项和缓存，关闭新增、编辑、审批及详情弹层，再加载新项目。
- 写操作记录发起时项目；期间项目发生变化时终止提交并要求重新执行。
- 通知和工作流链接携带 `projectRef`；无访问权时显示无权限，不自动切换到其他项目。
- 关联选择器只加载当前项目的分区、网段、部署单元、环境和实例。
- 桌面端沿用表格和详情模式；移动端使用现有卡片列表和受控弹层，按 `design-h5.md` 检查滚动边界、操作层级和文字溢出。

## 错误与恢复

- 缺少 `projectRef`：`400`，前端引导选择项目。
- 项目不存在或已删除：`400`，刷新项目列表。
- 非项目成员或缺少动作权限：`403`。
- 实体不属于当前项目：列表不可见，详情和写操作返回 `404`。
- 跨项目引用：`400` 并保留可恢复表单输入。
- 并发版本或唯一性冲突：`409`，刷新后重试。
- 工作流项目不一致：拒绝启动或消费，不改变业务状态。
- 默认项目缺失、不唯一或迁移存在孤立关系：Flyway 失败，修复数据后重新发布。
- 项目存在目标业务数据：删除返回 `409`。

## 验证策略

- 迁移：空库、既有库、默认项目缺失或不唯一、孤立子表、组合外键、项目内唯一和跨项目重复。
- 后端：两个项目、单项目成员、双项目成员和超级管理员的列表、详情、直接 ID、写入、审批和关联正负矩阵。
- 工作流：三条流程启动项目、流程实例项目、待办权限、轮次回执和生命周期不一致拒绝。
- 删除保护：四类主数据分别验证有引用阻止、无引用保持原行为。
- 前端：项目切换、旧请求失效、缓存清理、选择器隔离、通知和流程跳转。
- 视觉：`1280x800`、`375x812`、`390x844`、`430x932`，覆盖明暗主题和 `scrollWidth` 断言。
- 集成：architecture 聚焦测试、完整 Maven 测试、前端生产构建、真实 MySQL/Flyway、治理、范围及 `git diff --check`。

## UAT、上线与回退

- UAT 在两个项目创建相同编号和名称，验证列表、详情、流程、通知、附件和关联选择器互不可见。
- 验证网络分区不能关联其他项目部署单元、资源申请或环境实例，架构决策不能引用其他项目结论。
- 验证历史数据仅在 `RDDMP-PLATFORM` 可见，新增业务数据阻止项目删除。
- `projectRef` 强制化属于契约变更，数据库、后端和前端必须同批上线。
- 上线前执行数据库备份、默认项目检查、迁移预检查和目标数据量评估。
- Flyway 只追加，不提供反向脚本；失败时停止整批发布并按发布方案恢复备份，后续结构调整使用补偿迁移。

## 假设、风险与待确认

- 假设每个存在目标存量数据的租户具有唯一活动 `RDDMP-PLATFORM`；V158 前置检查负责证伪。
- 假设现有项目访问和工作流公开能力足以完成接入，不需要修改 platform/workflow。
- 风险集中在多表迁移顺序、Store 遗漏项目条件、历史流程缺少项目、通知附件间接泄露和当前工作区已有 `064` 未提交修改。
- 实施必须在 `064` 当前修改上增量工作，不回退、覆盖或批量格式化已有文件。
- 当前设计已获用户批准；下一步仅编写并复核实施计划，不直接编码。
