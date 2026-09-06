# 架构管理剩余业务按项目隔离实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将网络专项工单、网络分区及网段、网络访问全链路和架构决策接入顶部当前项目，在数据库、服务端、工作流、通知、附件和前端状态中阻断跨项目访问与关联。

**架构：** HTTP 接口显式接收 `projectRef`，Controller 通过 `ProjectAccessService.requireAccessible` 解析可信 `ProjectAccess`，Service 与 Store 使用 `tenant_id + project_id` 读写。主表和可独立查询、审计、回调的子表保存项目归属；流程定义、架构规范、搭建计划模板、环境类型和平台字典保持租户共享。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring JDBC、MySQL 8.4、Flyway、JUnit 5、Mockito、Vue 3、Pinia、TypeScript、Element Plus、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-05-architecture-remaining-project-scope-design.md`
- 需求文档：`docs/requirements/REQ-20260905-065-architecture-remaining-project-scope/requirement.md`
- 计划状态：已批准，可移交
- handoff：`approved`

## 全局约束

- `tenant_id` 与 `project_id` 只来自认证用户和 `ProjectAccessService`，业务写 DTO 不接收两者。
- 目标列表、详情、选项、写入、删除、唯一性、关联、附件策略和工作流回调均限定 `tenant_id + project_id`。
- 项目成员资格与原有 `architecture:*` RBAC 叠加；超级管理员仍提供明确 `projectRef`。
- 实体不属于当前项目时列表不可见，详情和写操作按不存在处理；跨项目关联返回 `400`，并发或唯一性冲突返回 `409`。
- 工作流定义保持租户共享；业务实例、轮次、回执和生命周期回调项目必须一致。
- 架构规范、搭建计划模板、环境类型和平台字典不增加 `project_id`，共享接口不强制 `projectRef`。
- 不修改 Flowable `ACT_*`、历史 Flyway、Java 包名、数据库表名和 API 根路径。
- 迁移固定为 `V158__scope_remaining_architecture_by_project.sql`；执行前重新扫描版本，若被占用则停止并修订需求范围和计划，不自行换号。
- 存量数据只归入同租户唯一活动 `RDDMP-PLATFORM`；缺失、不唯一或关系无法确定时失败关闭。
- 当前工作区包含 `REQ-20260905-064` 未提交实现；所有编辑必须保留其差异，不得回退、覆盖或批量格式化。
- 提交检查点只允许暂存本任务可独立识别的差异；同一文件无法与 `064` 安全拆分时不提交，以 execution 证据代替。
- 不访问生产、真实凭据或敏感数据。

---

## 文件职责地图

### 数据库与模型

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V158__scope_remaining_architecture_by_project.sql`，负责项目列、历史回填、校验、唯一索引和组合约束。
- 修改：`NetworkWorkOrderModels.java`、`NetworkAccessModels.java`、`DecisionModels.java`，为根实体、流程轮次和回执增加 `projectId`。
- 新建测试：`ArchitectureRemainingProjectScopeMigrationMySqlTest.java`，覆盖空库、既有库、默认项目异常、项目内唯一和跨项目重复。

### 网络专项工单

- 修改：`NetworkWorkOrderController.java`、`NetworkWorkOrderService.java`、`NetworkWorkOrderSubmissionService.java`、`NetworkWorkOrderStore.java`、`NetworkWorkflowLifecycleConsumer.java`。
- 修改：`NetworkAttachmentAccessPolicy.java`，从附件业务键解析工单后校验项目成员和工单项目。
- 修改：`PlanStore.java`、`PlanWorkOrderService.java`，由搭建计划生成网络工单时沿用计划项目。

### 网络分区与访问

- 修改：`NetworkAccessController.java`、`NetworkAccessService.java`、`NetworkAccessApplicationSubmissionService.java`、`NetworkAccessStore.java`、`NetworkAccessCoverage.java`、`NetworkAccessWorkflowLifecycleConsumer.java`。
- 修改：`DeploymentUnitStore.java`、`EnvironmentResourceStore.java` 及对应 Service 测试，保证分区、部署单元、资源申请和环境实例引用同项目。

### 架构决策

- 修改：`ArchitectureDecisionController.java`、`ArchitectureDecisionService.java`、`DecisionStore.java`、`ArchitectureDecisionWorkflowLifecycleConsumer.java`。
- 修改：`DecisionAttachmentAccessPolicy.java`，附件读取和删除必须先通过项目访问与事项归属校验。

### 删除保护、种子流程与前端

- 修改：`ArchitectureProjectDeletionGuard.java` 及测试，追加网络工单、网络分区、网络访问申请和架构决策主表。
- 修改：`LocalSeededWorkflowPublisher.java` 及测试，将 `architecture.network.work-order`、`architecture.network-access-application`、`architecture.decision.review` 纳入本地租户级定义发布清单，不按项目复制定义。
- 修改：`web/src/modules/architecture/api.ts`、`network.ts`、网络和决策页面，使目标请求使用当前项目并覆盖无项目、加载、空、失败、无权限、提交中和切换状态。
- 修改：`mock/mock-data.json`，增加两个项目的重复编号网络和决策数据。

## 依赖与覆盖

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6 -> T7
```

全部任务串行。T1 固定数据库与模型契约；T2、T3、T4 分别完成三条业务链；T5 集成删除保护和租户级种子定义；T6 在 API 稳定后改前端；T7 完成全量验证。

| 需求 | 任务 |
|---|---|
| R1 | T1、T2、T3、T4 |
| R2 | T2、T3、T4、T6 |
| R3 | T1、T2、T3、T4 |
| R4 | T1、T2、T3、T4 |
| R5 | T2、T3、T4、T5 |
| R6 | T2、T4、T6、T7 |
| R7 | T1、T7 |
| R8 | T5、T7 |
| R9 | T6、T7 |
| R10 | T1、T5、T6、T7 |
| R11 | T5、T7 |
| R12 | T7 |

### T1：数据库迁移与模型项目契约

**需求映射：** R1、R3、R4、R7、R10

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V158__scope_remaining_architecture_by_project.sql`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/model/NetworkWorkOrderModels.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/model/NetworkAccessModels.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/model/DecisionModels.java`
- 新建测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureRemainingProjectScopeMigrationMySqlTest.java`

**接口：**
- 消费：`pm_project(tenant_id, id, project_ref, status, deleted)` 和 V156 已项目化的部署单元、环境、资源申请、实例、计划表。
- 产出：目标表非空 `project_id`、项目维度唯一键、组合引用键，以及模型中的 `long projectId`。

- [ ] **步骤 1：重新确认迁移版本和当前结构**

运行：`node scripts/check-flyway-migrations.mjs`

预期：除仓库已知基线问题外，最高连续业务版本仍为 `V157`，`V158` 未被占用；若占用则停止任务。

证据：记录命令退出码、最高版本和已知基线问题。

- [ ] **步骤 2：先建立真实 MySQL 失败测试**

测试必须断言：默认项目缺失或不唯一时迁移失败；历史根表和子表全部回填；目标列无空值；同项目重复失败；跨项目重复成功；决策序列按项目独立；共享表无新增项目列。

运行：`mvn -pl :ccb-architecture -am -Dtest=ArchitectureRemainingProjectScopeMigrationMySqlTest test`

预期：新增测试在 V158 缺失时失败，失败原因指向项目列或索引不存在。

证据：保存失败测试名和关键断言。

- [ ] **步骤 3：实现分阶段迁移和模型字段**

按“可空列 -> 默认项目临时表 -> 根表回填 -> 子表父链回填 -> 孤立/跨项目校验 -> 非空 -> 项目唯一索引 -> 组合键”的顺序实现。目标表清单以批准设计为准，不修改 V98、V100、V111-V116、V156 或 V157。

预期：所有目标根实体、流程轮次和回执模型均能携带 `projectId`，共享模型不变。

证据：迁移和模型 diff、`information_schema` 断言。

- [ ] **步骤 4：复验迁移和架构编译**

运行：`mvn -pl :ccb-architecture -am -Dtest=ArchitectureRemainingProjectScopeMigrationMySqlTest test`

预期：测试全部通过，0 个失败。

运行：`mvn -pl :ccb-architecture -am -DskipTests compile`

预期：编译通过。

- [ ] **步骤 5：建立提交检查点**

仅当上述文件不包含无法拆分的 `064` 差异时执行精确暂存并提交：`feat(architecture): add remaining project ownership schema`。否则记录“提交延后：与 064 同文件差异不可安全拆分”。

**回滚：** 未执行迁移时回退 T1 文件；已执行 V158 时仅通过数据库备份恢复或后续补偿迁移处理。

**停止条件：** V158 被占用；默认项目规则与测试数据冲突；发现 must 表无法可靠回填；需要修改历史迁移或 Flowable 表。

**升级条件：** 必须增加范围外业务表、公共项目契约或无法证明的跨项目修复规则。

### T2：网络专项工单、附件与流程项目隔离

**需求映射：** R1、R2、R3、R4、R5、R6

**前置任务：** T1

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/web/NetworkWorkOrderController.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkWorkOrderService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkWorkOrderSubmissionService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/persistence/NetworkWorkOrderStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/integration/NetworkWorkflowLifecycleConsumer.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/integration/NetworkAttachmentAccessPolicy.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/persistence/PlanStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/plan/service/PlanWorkOrderService.java`
- 测试：`NetworkWorkOrderControllerTest.java`、`NetworkWorkOrderServiceTest.java`、`NetworkWorkOrderSubmissionServiceTest.java`、`NetworkWorkOrderMySqlTest.java`、`NetworkWorkflowLifecycleConsumerTest.java`、`NetworkWorkflowIntegrationMySqlTest.java`
- 新建测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/network/integration/NetworkAttachmentAccessPolicyTest.java`

**接口：**
- 消费：`ProjectAccessService.requireAccessible(projectRef, actor)`、`ProjectAccess`、`WorkflowBusinessGateway`、附件公开契约。
- 产出：Controller 必填 `projectRef`；Service 方法接收 `ProjectAccess`；Store 方法接收 `projectId`；工作流上下文包含项目；计划生成工单继承计划项目。

- [ ] **步骤 1：建立项目隔离失败测试**

覆盖缺少 `projectRef`、非成员、双项目同工单号、直接 ID 越权、计划生成工单项目、附件跨项目读取/删除和生命周期事件项目不一致。

运行：`mvn -pl :ccb-architecture -am -Dtest=NetworkWorkOrderControllerTest,NetworkWorkOrderServiceTest,NetworkWorkOrderSubmissionServiceTest,NetworkWorkflowLifecycleConsumerTest,NetworkAttachmentAccessPolicyTest test`

预期：新增项目断言失败，既有业务规则测试保持通过。

- [ ] **步骤 2：逐层接入可信项目**

Controller 对所有工单接口增加 `@RequestParam String projectRef` 并解析 `ProjectAccess`；Service/Store 的列表、详情、写入、历史、轮次和回执增加项目条件；工单号唯一性调整为项目范围。

预期：`findWorkOrder`、`lockWorkOrder`、`listWorkOrders` 等直接 ID 路径均不能脱离项目。

- [ ] **步骤 3：补齐流程、附件和计划工单项目**

`NetworkWorkOrderSubmissionService` 使用 `project.projectRef()/projectName()` 构造上下文并校验启动结果；consumer 复用已项目化生命周期模式重新解析项目并校验业务归属；附件策略在工单项目上校验当前用户访问；计划工单写入和查询使用计划项目。

预期：流程实例、工单、附件和计划来源项目一致。

- [ ] **步骤 4：运行局部和 MySQL 回归**

运行：`mvn -pl :ccb-architecture -am -Dtest=NetworkWorkOrderControllerTest,NetworkWorkOrderServiceTest,NetworkWorkOrderSubmissionServiceTest,NetworkWorkOrderMySqlTest,NetworkWorkflowLifecycleConsumerTest,NetworkWorkflowIntegrationMySqlTest,NetworkAttachmentAccessPolicyTest test`

预期：全部通过，0 个失败。

- [ ] **步骤 5：建立提交检查点**

精确暂存 T2 文件并检查 `git diff --cached`；可拆分时提交 `feat(architecture): scope network work orders by project`，否则记录提交延后原因。

**回滚：** 回退 T2 Java 和测试差异，不回退 V158。

**停止条件：** 附件公开契约无法取得用户项目上下文；计划工单没有可靠计划项目；生命周期事件无法证明项目。

**升级条件：** 需要修改 platform/attachment、platform/workflow 或 `064` 已批准业务语义。

### T3：网络分区、网段与网络访问全链路隔离

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** T2

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/web/NetworkAccessController.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessApplicationSubmissionService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessCoverage.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/persistence/NetworkAccessStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/network/integration/NetworkAccessWorkflowLifecycleConsumer.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeploymentUnitStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/persistence/EnvironmentResourceStore.java`
- 测试：`NetworkAccessControllerTest.java`、`NetworkAccessServiceTest.java`、`NetworkAccessLifecycleServiceTest.java`、`NetworkAccessDecisionServiceTest.java`、`NetworkAccessMigrationMySqlTest.java`、`NetworkAccessWorkflowLifecycleConsumerTest.java`
- 新建测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/network/persistence/NetworkAccessProjectIsolationMySqlTest.java`

**接口：**
- 消费：可信 `ProjectAccess`、项目化部署单元、环境、资源申请和实例。
- 产出：分区、网段、外部地址、申请、关系、豁免规则、轮次和回执的项目限定读写与同项目关联。

- [ ] **步骤 1：建立双项目和跨引用失败测试**

覆盖分区编码/名称、外部地址、申请号、关系号的跨项目重复和同项目冲突；伪造其他项目分区、网段、部署单元、实例、关系和豁免规则 ID；生命周期项目不一致。

运行：`mvn -pl :ccb-architecture -am -Dtest=NetworkAccessControllerTest,NetworkAccessServiceTest,NetworkAccessWorkflowLifecycleConsumerTest,NetworkAccessProjectIsolationMySqlTest test`

预期：新增隔离断言失败。

- [ ] **步骤 2：项目化 Controller、Service 和 Store**

所有目标接口增加 `projectRef`；Store 的列表、直接 ID、状态更新、唯一性、覆盖判定、流程和历史查询增加 `projectId`；Service 对父分区、端点、实例、申请、关系和豁免规则执行同项目校验。

预期：任何其他项目 ID 均无法被当前项目查询或写入。

- [ ] **步骤 3：补齐跨模块引用与流程项目**

部署单元、资源申请和环境实例涉及分区的查询与写入同时限定自身项目；`NetworkAccessApplicationSubmissionService` 写入真实工作流项目，consumer 在状态变更前复核项目。

预期：数据库和服务层均不存在跨项目网络引用。

- [ ] **步骤 4：运行网络访问回归**

运行：`mvn -pl :ccb-architecture -am -Dtest=NetworkAccessControllerTest,NetworkAccessServiceTest,NetworkAccessLifecycleServiceTest,NetworkAccessDecisionServiceTest,NetworkAccessMigrationMySqlTest,NetworkAccessProjectIsolationMySqlTest,NetworkAccessWorkflowLifecycleConsumerTest test`

预期：全部通过，0 个失败。

- [ ] **步骤 5：建立提交检查点**

可安全拆分时提交 `feat(architecture): scope network access by project`；包含 `064` 同文件差异时延后并记录精确文件清单。

**回滚：** 回退 T3 Java 和测试，不回退 T1/T2。

**停止条件：** 已项目化资产缺少可靠项目列；迁移发现跨项目存量引用；网络覆盖算法需要租户全量数据才能正确计算。

**升级条件：** 需要修改范围外消费者、公共网络契约或改变现有访问决策语义。

### T4：架构决策、附件与发布流程项目隔离

**需求映射：** R1、R2、R3、R4、R5、R6

**前置任务：** T3

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/web/ArchitectureDecisionController.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/service/ArchitectureDecisionService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/persistence/DecisionStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/integration/ArchitectureDecisionWorkflowLifecycleConsumer.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/integration/DecisionAttachmentAccessPolicy.java`
- 测试：`ArchitectureDecisionServiceTest.java`、`ArchitectureDecisionWorkflowLifecycleConsumerTest.java`
- 新建测试：`ArchitectureDecisionControllerTest.java`、`DecisionAttachmentAccessPolicyTest.java`、`ArchitectureDecisionProjectIsolationMySqlTest.java`

**接口：**
- 消费：可信 `ProjectAccess`、`WorkflowBusinessGateway`、附件公开契约。
- 产出：决策事项、材料、评审、参与人、行动项、结论、发布意图、替代关系、序列、轮次和回执项目化。

- [ ] **步骤 1：建立决策隔离失败测试**

覆盖缺少项目、非成员、双项目同决策编号、项目独立年度序列、直接事项/评审/行动项/结论 ID 越权、跨项目替代关系、附件跨项目访问和回调项目不一致。

运行：`mvn -pl :ccb-architecture -am -Dtest=ArchitectureDecisionControllerTest,ArchitectureDecisionServiceTest,ArchitectureDecisionWorkflowLifecycleConsumerTest,DecisionAttachmentAccessPolicyTest,ArchitectureDecisionProjectIsolationMySqlTest test`

预期：新增项目断言失败。

- [ ] **步骤 2：项目化决策调用链**

Controller 所有事项、结论、附件和发布接口增加 `projectRef`；Service 方法接收 `ProjectAccess`；Store 的列表、详情、更新、子表、序列和替代关系查询全部增加项目条件。

预期：从任意子对象直接访问都必须经过事项或结论项目校验。

- [ ] **步骤 3：补齐发布流程和附件策略**

决策发布上下文写入项目并校验启动结果；consumer 复核项目后生成或更新结论；附件策略按事项项目校验用户访问，平台附件实现保持不变。

预期：业务事项、发布流程、结论、替代链和附件项目一致。

- [ ] **步骤 4：运行决策回归**

运行：`mvn -pl :ccb-architecture -am -Dtest=ArchitectureDecisionControllerTest,ArchitectureDecisionServiceTest,ArchitectureDecisionWorkflowLifecycleConsumerTest,DecisionAttachmentAccessPolicyTest,ArchitectureDecisionProjectIsolationMySqlTest test`

预期：全部通过，0 个失败。

- [ ] **步骤 5：建立提交检查点**

精确暂存并提交 `feat(architecture): scope decisions by project`；若与其他需求差异不可安全拆分则延后。

**回滚：** 回退 T4 Java 和测试，不回退数据库契约。

**停止条件：** 结论或替代链无法可靠追溯事项项目；附件策略无法校验项目成员；历史流程实例无法匹配事项。

**升级条件：** 需要修改附件平台、工作流平台或改变已发布结论语义。

### T5：项目删除保护与租户级流程定义发布

**需求映射：** R5、R8、R10、R11

**前置任务：** T4

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/integration/ArchitectureProjectDeletionGuard.java`
- 修改：`server/src/modules/architecture/src/test/java/com/ccb/architecture/integration/ArchitectureProjectDeletionGuardTest.java`
- 修改：`server/src/platform/boot/src/main/java/com/ccb/boot/workflow/LocalSeededWorkflowPublisher.java`
- 修改：`server/src/platform/boot/src/test/java/com/ccb/boot/workflow/LocalSeededWorkflowPublisherTest.java`

**接口：**
- 消费：现有 `ProjectDeletionGuard.requireNoReferences` 和租户级流程定义查询、发布接口。
- 产出：目标主表删除保护；默认发布代码包含三条新增业务流程，但每租户每定义仍只发布一次。

- [ ] **步骤 1：建立删除和定义发布失败测试**

分别插入网络工单、网络分区、网络访问申请、架构决策后调用 guard，断言 `409`；为三条流程创建草稿定义，断言 publisher 发布；已发布定义不重复发布；未知代码忽略或按既有规则处理。

运行：`mvn -pl :ccb-architecture,:ccb-boot -am -Dtest=ArchitectureProjectDeletionGuardTest,LocalSeededWorkflowPublisherTest test`

预期：新增断言在实现前失败。

- [ ] **步骤 2：扩展 guard 和 publisher 默认代码**

guard 只增加按 `tenant_id + project_id` 的主表存在性查询；publisher 默认定义代码增加 `architecture.network.work-order`、`architecture.network-access-application`、`architecture.decision.review`，不引入项目循环或定义复制。

预期：system 和 workflow 平台源码不变。

- [ ] **步骤 3：运行平台与架构聚焦回归**

运行：`mvn -pl :ccb-architecture,:ccb-boot -am -Dtest=ArchitectureProjectDeletionGuardTest,LocalSeededWorkflowPublisherTest test`

预期：全部通过，0 个失败。

- [ ] **步骤 4：建立提交检查点**

若 `LocalSeededWorkflowPublisher` 中 `064` 修改已能与本任务差异安全识别，则提交 `feat(workflow): publish remaining architecture definitions`；否则延后并记录差异来源。

**回滚：** 回退新增 guard 查询和三个默认定义代码；不会删除已发布租户定义。

**停止条件：** 三条定义未被迁移创建；publisher 现有 `064` 修改尚未稳定；项目删除检查需要扫描子表而无法由主表覆盖。

**升级条件：** 需要改变流程定义范围模型、项目删除公共接口或启动顺序。

### T6：前端当前项目接入与切换状态

**需求映射：** R2、R6、R9、R10

**前置任务：** T5

**文件：**
- 修改：`web/src/modules/architecture/api.ts`
- 修改：`web/src/modules/architecture/network.ts`
- 修改：`web/src/modules/architecture/NetworkWorkOrderListPage.vue`
- 修改：`web/src/modules/architecture/NetworkWorkOrderFormPage.vue`
- 修改：`web/src/modules/architecture/NetworkWorkOrderDetailPage.vue`
- 修改：`web/src/modules/architecture/NetworkZonePage.vue`
- 修改：`web/src/modules/architecture/NetworkAccessPage.vue`
- 修改：`web/src/modules/architecture/DecisionMatterListPage.vue`
- 修改：`web/src/modules/architecture/DecisionMatterFormPage.vue`
- 修改：`web/src/modules/architecture/DecisionMatterDetailPage.vue`
- 修改：`web/src/modules/architecture/architecture.css`（仅在现有页面无法满足移动端状态时）

**接口：**
- 消费：`useProjectContextStore().currentRef`、T2-T4 已稳定的必填 `projectRef` API。
- 产出：目标 API 使用 `projectHttp`；共享的架构规范、环境类型和平台字典继续使用普通 `http`。

- [ ] **步骤 1：用类型约束暴露遗漏调用**

将 `projectHttp` 作为架构模块内部可复用请求器，网络工单、分区、访问和决策目标调用切换到项目请求；共享接口保持不变。

运行：`npm --prefix web run build`

预期：首次构建暴露遗漏调用或类型问题；记录错误文件后逐项修复。

- [ ] **步骤 2：补齐页面状态和切换保护**

页面无当前项目时不请求；加载、空、失败、无权限、提交中状态继续使用公共组件；复用应用壳层已确认的项目切换整页刷新，写操作在提交前再次读取当前项目，避免旧项目响应保留。

预期：无第二项目选择器；旧项目列表、详情、筛选和弹层不会进入新项目页面。

- [ ] **步骤 3：实现桌面和移动布局验收要求**

桌面保留表格和详情结构；手机使用现有业务卡片、单列表单和受控弹层；长编号与附件名可换行；操作超过三个时使用现有更多操作模式；页面级无横向滚动。

- [ ] **步骤 4：构建并运行浏览器矩阵**

运行：`npm --prefix web run build`

预期：`vue-tsc` 与 Vite 成功。

浏览器路径：网络工单列表/表单/详情、网络分区、网络访问、架构决策列表/表单/详情。

视口：`1280x800`、`375x812`、`390x844`、`430x932`，覆盖明暗主题；断言 `document.documentElement.scrollWidth <= window.innerWidth`，控制台无错误。

- [ ] **步骤 5：建立提交检查点**

可安全拆分时提交 `feat(web): bind remaining architecture pages to project`；`api.ts` 或样式含 `064` 差异时先审查再决定是否延后。

**回滚：** 回退目标 API 和页面修改，不改共享接口。

**停止条件：** 必须修改公共项目 store、HTTP 拦截器或应用壳层；现有页面结构无法在不改变业务规则下满足移动端。

**升级条件：** 需要公共 UI 组件变更、路由契约变化或用户重新选择项目交互方案。

### T7：双项目集成、UAT 与收敛验收

**需求映射：** R6、R7、R8、R9、R10、R11、R12

**前置任务：** T6

**文件：**
- 修改：`mock/mock-data.json`
- 更新：`docs/requirements/REQ-20260905-065-architecture-remaining-project-scope/codex-task-scope.yaml`（仅实际新增文件与验证命令）
- 新建或更新：`.ai-control/requirements/req-20260905-065-architecture-remaining-project-scope/execution-T*.json`
- 新建或更新：`.ai-control/requirements/req-20260905-065-architecture-remaining-project-scope/observation-T*.json`
- 新建或更新：`.ai-control/requirements/req-20260905-065-architecture-remaining-project-scope/convergence.json`
- 新建或更新：`.ai-control/requirements/req-20260905-065-architecture-remaining-project-scope/handoff.json`

**接口：**
- 消费：T1-T6 全部产出。
- 产出：R1-R12 可复验的迁移、API、权限、工作流、附件、前端、治理和发布证据。

- [ ] **步骤 1：准备双项目虚构数据**

两个项目具有重复工单号、申请号、关系号、分区编码/名称、外部地址和决策编号；包含单项目成员、双项目成员和超级管理员；所有数据必须虚构。

预期：Mock 初始化后项目 A、B 数据可分别访问且共享字典一致。

- [ ] **步骤 2：运行后端聚焦与完整测试**

运行：`mvn -pl :ccb-architecture,:ccb-boot,:ccb-system,:ccb-workflow -am test`

预期：目标模块全部成功，0 个失败。

运行：`mvn test`

预期：完整反应堆成功；若出现已知范围外基线失败，单独记录且不得掩盖本任务失败。

- [ ] **步骤 3：运行迁移和工作流集成矩阵**

验证空库、既有库、默认项目缺失/不唯一、项目列非空、项目唯一索引、组合关系；验证三条流程启动项目、项目待办、生命周期不一致拒绝和 `LocalSeededWorkflowPublisher` 租户级发布。

预期：无跨项目数据或流程状态变化。

- [ ] **步骤 4：执行浏览器 UAT**

使用本地 `admin/admin123` 和两个项目，完成网络工单、分区、访问、决策的列表、详情、新建、提交/审批、通知跳转和附件操作；切换项目后验证互不可见；验证共享配置仍可见。

预期：所有指定视口、明暗主题、网络请求和控制台检查通过。

- [ ] **步骤 5：执行治理、范围和差异检查**

运行：`node scripts/check-all-governance.mjs`

运行：`node scripts/check-flyway-migrations.mjs`

运行：`git diff --check`

运行：`git status --short`

预期：本任务没有新增治理、Flyway、scope 或空白错误；任何历史基线失败按来源单列。

- [ ] **步骤 6：独立观测和收敛审计**

高保证模式至少形成迁移/数据库、服务端权限/工作流、前端浏览器三类异质传感器；R1-R12 每条 must 需求均有任务和证据；P0/P1 为零且无未关闭反馈后才允许转入 `converged`。

**回滚：** 前端、publisher/guard、决策、网络访问、网络工单按逆序回退；已执行 V158 使用备份恢复或补偿迁移；停止本地服务但保留必要测试数据卷供诊断。

**停止条件：** 任一跨项目越权、附件泄露、错误流程回调、迁移完整性失败、项目删除绕过或范围违规。

**升级条件：** 需要生产数据、真实凭据、范围外公共能力或 Owner 接受残余高风险。

## 集成检查

1. T1 后：真实 MySQL 空库、既有库、失败前置和唯一性。
2. T2-T4 后：architecture 聚焦回归、双项目直接 ID、附件和三条工作流项目一致性。
3. T5 后：项目删除保护和租户级流程定义发布。
4. T6 后：前端生产构建和七条用户路径四视口验收。
5. T7：完整 Maven、真实 MySQL、浏览器 UAT、治理、scope、Flyway 和差异收敛。

## 控制模型种子

以下均为 `hypotheses-only`，由 modeling 阶段验证：

- 边界候选：顶部项目上下文、architecture 网络/决策 API/Service/Store/表、项目删除 guard、workflow 集成、附件策略、boot 种子 publisher、Mock 和浏览器路径。
- 状态候选：`projectRef/projectId`、实体项目归属、工作流实例项目、成员/RBAC、唯一键、迁移状态、附件访问判定、删除引用计数。
- 接口候选：`ProjectAccessService`、`ProjectDeletionGuard`、`WorkflowBusinessGateway`、`AttachmentAccessPolicy`、architecture REST API。
- 传感器候选：双项目 API、MySQL 约束、Flyway 退出码、workflow event、附件策略测试、浏览器网络与 `scrollWidth`、Maven/Vite/治理退出码。
- 执行器候选：V158、Store 项目条件、Controller 参数、Service 校验、生命周期消费者、附件策略、删除 guard、publisher 默认代码、前端项目请求。
- 扰动候选：V158 版本竞争、默认项目异常、软删除、重复编号、历史 workflow 无项目、`064` 未提交差异和本地服务状态。
- 时延候选：大表回填、Flyway、完整 Maven、前端构建和浏览器矩阵。
- 假设：目标历史子表均可从父链确定项目；附件策略可从业务实体恢复项目；三条流程定义已由历史迁移创建。

## 风险与用户批准

- 高风险动作：多表 V158、三条工作流上下文与回调、附件实体授权、目标 API 强制参数、与 `064` 同文件增量修改。
- 不修改 platform/workflow、platform/attachment 或项目访问公共契约；发现必须修改时立即停止并修订设计。
- 用户于 2026-09-05 批准计划修订 1；计划状态为 `ready`，handoff 状态为 `approved`，可导入高保证工程控制账本并确认阶段为 `baseline`、下一阶段为 `modeling`。
