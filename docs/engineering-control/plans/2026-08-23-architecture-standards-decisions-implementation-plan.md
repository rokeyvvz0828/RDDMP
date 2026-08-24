# 架构规范与架构决策实施计划

## 状态与来源

- 计划修订：1
- 状态：本任务内执行
- 需求：`REQ-20260823-050`
- 设计：`docs/engineering-control/designs/2026-08-23-architecture-standards-decisions-design.md`
- 分支：`feat/REQ-20260823-050-architecture-standards-decisions`（worktree 隔离）

## 任务包

### T1：需求与治理准入

- 建立 `docs/requirements/REQ-20260823-050-architecture-standards-decisions/`（requirement.md、codex-task-scope.yaml）与设计/计划文档。
- 记录 `baseline.json`、`model.json`、`control-plan.json` 到 `.ai-control/requirements/req-20260823-050-architecture-standards-decisions/`。
- 验证：`node scripts/check-development-entry.mjs --require-plugin`、`node scripts/check-codex-scope.mjs`（--base 1f61cb8）。

### T2：数据库迁移 V87/V88

- V87：`arch_standard_document`、`arch_standard_document_version`、`arch_decision_matter`、`arch_decision_material`、`arch_decision_review`、`arch_decision_review_participant`、`arch_decision_action_item`、`arch_decision_conclusion`、`arch_decision_supersession`、`arch_decision_number_sequence`、`arch_decision_workflow_receipt`、`arch_decision_workflow_round`。
- V88：字典 `ARCH_STANDARD_CATEGORY`（360007）/`ARCH_MATTER_TYPE`（360008）与种子参数（360201-360305）；菜单 806/807；权限 8061/8062/8071-8074；角色 112 `ARCHITECTURE_GROUP` 与授权；角色 110/1 补授权；工作流草稿 `architecture.decision.review`（definition 900000000000040/version 900000000000041）。
- 验证：`node scripts/check-flyway-migrations.mjs`、MySQL 集成测试。

### T3：后端架构规范子域

- `com.ccb.architecture.standard`：模型（StandardModels）、存储（StandardStore）、服务（ArchitectureStandardService）、控制器（ArchitectureStandardController）、附件策略（StandardAttachmentAccessPolicy）。
- 权限 `architecture:standard:view/manage`；状态 DRAFT/PUBLISHED/OFFLINE；发布版本快照；附件经 AttachmentPort 绑定 `architecture-standard`。
- 测试：`ArchitectureStandardServiceTest`、`ArchitectureStandardControllerTest`。

### T4：后端架构决策子域

- `com.ccb.architecture.decision`：模型（DecisionModels）、存储（DecisionStore）、服务（ArchitectureDecisionService：编号、期限、状态机、材料、评审、行动项、发布准备、替代链）、控制器（ArchitectureDecisionController）、工作流消费者（ArchitectureDecisionWorkflowLifecycleConsumer）、附件策略（DecisionAttachmentAccessPolicy）。
- 权限 `architecture:decision:view/propose/review/manage`；工作流 `architecture.decision.review` 发布门禁；结论不可变；替代链推导。
- 测试：`ArchitectureDecisionServiceTest`、`ArchitectureDecisionControllerTest`、`ArchitectureDecisionWorkflowLifecycleConsumerTest`、`ArchitectureStandardsDecisionsMySqlTest`。

### T5：前端页面与路由

- `web/src/modules/architecture`：`StandardDocumentListPage.vue`（列表/详情抽屉/创建编辑/发布/下线/版本历史/附件）、`DecisionMatterListPage.vue`、`DecisionMatterFormPage.vue`、`DecisionMatterDetailPage.vue`（材料、首次处理、评审、行动项、结论发布、替代链）；`types.ts`、`api.ts` 扩展；路由注册。
- 复用 `components/ui`、delivery-showcase 模式、`web/src/api/attachments.ts` 附件能力；桌面/移动全状态。
- 验证：`npm --prefix web run build`。

### T6：集成验证与账本

- `mvn -pl :ccb-architecture -am test`、`mvn -pl :ccb-workflow -am test`、`mvn test`、`npm --prefix web run build`。
- `node scripts/check-all-governance.mjs`、`check-codex-scope.mjs`、`git diff --check`。
- 记录 execution/observation/convergence 账本；未执行项（真实浏览器 UAT、手工 API/权限矩阵）明确标为未验证并给出剩余风险。

## 依赖与顺序

T1 → T2 → T3/T4（可并行）→ T5 → T6。迁移必须先于存储测试；工作流消费者依赖 V88 流程草稿（测试环境通过平台发布入口部署后启动，遵循 048 先例）。

## 采样点

- T2 后：迁移检查 + MySQL 空库/增量迁移测试。
- T3/T4 后：模块聚焦测试。
- T5 后：前端构建。
- T6：全量检查与收敛判定。
