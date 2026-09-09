# 架构与环境能力按项目隔离实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将物理子系统、部署单元、环境管理、资源申请、环境部署实例和搭建计划绑定顶部当前项目，在数据库、服务端、工作流、前端和跨模块消费者中阻断跨项目访问与关联。

**架构：** HTTP 接口接收必填 `projectRef`，由 `ProjectAccessService.requireAccessible(projectRef, actor)` 解析可信 `ProjectAccess`，Service 和 Store 使用 `tenant_id + project_id` 读写。六类主表及可独立访问、审计或回调的关键子表显式保存 `project_id`；搭建计划模板链保持租户共享。system 提供项目删除引用检查扩展点，architecture 提供实现。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring JDBC、MySQL 8.4、Flyway、JUnit 5、Mockito、Vue 3、Pinia、TypeScript、Element Plus、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-05-architecture-project-scope-design.md`
- 需求文档：`docs/requirements/REQ-20260905-064-architecture-project-scope/requirement.md`
- 状态：已批准，可移交
- handoff：`approved`

## 全局约束

- `tenant_id` 与 `project_id` 只来自认证用户和 `ProjectAccessService`，业务写 DTO 不接收二者。
- 列表、详情、选项、写入、删除、唯一性、关联和工作流回调均限定 `tenant_id + project_id`。
- 实体不属于当前项目时列表不可见，详情和写操作按不存在处理；跨项目关联 `400`，并发冲突 `409`。
- 项目成员资格与现有 `architecture:*` RBAC 叠加；超级管理员仍提供明确 `projectRef`。
- architecture 只写自身 `arch_` 表；system 不反向依赖 architecture。
- `arch_plan_template*`、`arch_task_template*` 保持租户共享，不增加 `project_id`。
- 不修改 Flowable `ACT_*`、历史 Flyway、Java 包名、数据库表名和 `/api/architecture/environments` 路径。
- “具体环境”只改用户可见文案为“环境管理”。
- 迁移候选为 `V156__scope_architecture_by_project.sql`；执行前重新扫描最高版本，冲突时顺延并同步控制记录。
- 存量数据只归入同租户唯一活动项目 `RDDMP-PLATFORM`；存在待迁移数据但项目缺失或不唯一时失败。
- 计划批准前不扩展产品代码写入范围，不访问生产或敏感数据。

---

## 文件职责地图

### 数据库

- 候选新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V156__scope_architecture_by_project.sql`。
- 修改：`ArchitectureMigrationMySqlTest.java`、`EmptyDatabaseMigrationBaselineMySqlTest.java`、`mock/mock-data.json`。
- 物理子系统链：`arch_physical_subsystem`、`arch_subsystem_change_application`、`arch_subsystem_physical_draft`、`arch_subsystem_change_history`、`arch_subsystem_change_lock`、`arch_subsystem_value_reservation`、`arch_subsystem_replacement`、`arch_subsystem_workflow_round`、`arch_subsystem_workflow_receipt`。
- 部署单元链：`arch_deployment_unit`、`arch_deployment_unit_version`、`arch_deployment_unit_number_seq`、`arch_deployment_unit_import_batch`、`arch_deployment_unit_import_item`、`arch_deployment_unit_relation`、`arch_deployment_unit_relation_history`。
- 环境资源链：`arch_environment`、`arch_resource_request`、`arch_resource_request_item`、`arch_resource_request_history`、`arch_resource_request_workflow_round`、`arch_resource_request_workflow_receipt`、`arch_environment_instance`、`arch_instance_disaster_recovery`。
- 计划执行链：`arch_setup_plan`、`arch_plan_target`、`arch_plan_stage`、`arch_plan_task`、`arch_plan_task_participant`、`arch_plan_check_item`、`arch_plan_task_dependency`、`arch_plan_stage_dependency`、`arch_plan_block`、`arch_plan_check_item_cancel_suggestion`、`arch_plan_event`、`arch_plan_work_order`、`arch_setup_plan_activity`。
- 排除：`arch_environment_type` 和全部计划模板、任务模板及模板依赖表。

### 平台与架构后端

- 候选新建：`ProjectDeletionGuard.java`，接口 `void requireDeletable(long tenantId, long projectId)`。
- 修改：`ProjectService.java`、`ProjectServiceTest.java`、`governance/modules.yaml`。
- 候选新建：`ArchitectureProjectContextService.java`，接口 `ProjectAccess require(String projectRef, AuthUser actor)`。
- 候选新建：`ArchitectureProjectDeletionGuard.java` 及两项 architecture 聚焦测试。
- 修改：物理子系统、变更申请、部署单元/导入、环境资源/工作流、搭建计划执行链的 model、Controller、Service、Store 和测试。
- 不修改：`PlanTemplateModels`、`PlanTemplateStore`、`PlanTemplateService`、`PlanTemplateController` 的租户级范围。

### 前端与消费者

- 修改：`web/src/modules/architecture/api.ts`、`planApi.ts`、`types.ts`、六类页面、三个物理子系统变更页面、`web/src/router/index.ts`、`architecture.css`。
- 修改数据迁移：`DashboardService`、`IssueService`、`ProjectComponentService`、`TargetTableService`。
- 修改测试管理：`TestAnalyticsAdvancedService`、`TestAnalyticsService`、`TestCaseService`、`TestDefectService`、`TestExecutionService`、`TestPlanService`、`TestReportService`、`TestScopeService`、`TestConfigurationService`。
- 候选新建：数据迁移、测试管理各一份双项目 MySQL 隔离测试。

## 依赖与覆盖

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6 -> T7 -> T8
```

主链串行：迁移和 Store 契约影响后续全部任务；前端等待 API 稳定；消费者等待物理子系统项目列稳定。任务内部只允许并行运行不写同一文件的测试。

| 需求 | 任务 |
|---|---|
| R1 | T1、T3、T4、T5 |
| R2 | T6 |
| R3 | T1、T3、T4 |
| R4 | T3、T4、T5 |
| R5 | T2、T3、T4、T5、T6 |
| R6 | T1、T5、T6 |
| R7 | T3、T4、T8 |
| R8 | T1、T8 |
| R9 | T2、T8 |
| R10 | T7、T8 |
| R11 | T6、T8 |

### T1：数据库迁移与模型契约

**需求映射：** R1、R3、R6、R8；**前置任务：** 无

**文件：** 候选 V156；四类业务 model；两个 architecture MySQL 迁移测试。

**接口：** 消费 `pm_project` 默认项目；产出纳入表非空 `project_id`、项目维度唯一键和内部模型 `projectId`。

- [ ] 运行 `node scripts/check-flyway-migrations.mjs`，确认版本未占用。
- [ ] 先增加失败测试：默认项目缺失/重复失败、全表无空值、跨项目同编号成功、同项目软删除后复用失败、模板表无项目列。
- [ ] 实现“可空列 -> 默认项目 guard -> 根/子表回填 -> 空值和跨项目 guard -> 非空 -> 索引与组合外键”。
- [ ] 运行 `mvn -pl :ccb-architecture -am -Dtest=ArchitectureMigrationMySqlTest,EmptyDatabaseMigrationBaselineMySqlTest test`，预期通过。
- [ ] 提交检查点：`feat(architecture): add project ownership schema`。

**验收：** 项目列完整，模板范围不变。**回滚：** 已执行迁移只用补偿迁移。**停止：** 默认项目异常、版本冲突、关系无法确定根项目。**升级：** 发现清单外关键子表或需修改非 architecture 表。

### T2：项目上下文与删除引用扩展点

**需求映射：** R5、R9；**前置任务：** T1

**文件：** 候选 `ArchitectureProjectContextService`、`ProjectDeletionGuard`、`ArchitectureProjectDeletionGuard`；修改 `ProjectService`、测试和 `governance/modules.yaml`。

**接口：** 消费 `ProjectAccessService.requireAccessible`；产出 `require(projectRef, actor)` 和 `requireDeletable(tenantId, projectId)`。

- [ ] 建立空项目 `400`、非成员 `403`、超级管理员成功、有引用删除 `409`、无引用原行为和多 guard 测试。
- [ ] system 以可选集合注入 guard，architecture guard 只统计六类未删除根数据。
- [ ] 运行 `mvn -pl :ccb-system,:ccb-architecture -am test`，预期聚焦测试通过。
- [ ] 提交检查点：`feat(system): add project deletion guard extension`。

**验收：** system 无业务反向依赖。**回滚：** 回退接口、注入点和实现。**停止：** Owner 不批准或启动回归。**升级：** 需改变访问语义或删除事务。

### T3：物理子系统、变更流程、部署单元与导入隔离

**需求映射：** R1、R3、R4、R5、R7；**前置任务：** T2

**文件：** 修改 `ArchitectureSubsystemRepository`、`PhysicalSubsystemService/Controller/model`；`SubsystemChangeModels/Store/Service/Controller`、`ArchitectureSubsystemSubmissionService`、`ArchitectureWorkflowLifecycleConsumer`；`DeploymentUnitModels/Store/Service/Controller`、`DeploymentUnitImportService/Controller`、`ArchitectureOptionsService` 及对应测试。

**接口：** 消费可信 `ProjectAccess`；产出目标 Controller 的 `@RequestParam String projectRef`、Store 的 `long projectId` 参数和真实工作流项目上下文。

- [ ] 建立双项目同编号、直接 ID 越权、跨项目部署关系、导入批次隔离和工作流项目不一致失败测试。
- [ ] Controller -> Service -> Store 逐层接入项目；唯一性、编号分配、关系和导入批次限定项目。
- [ ] `ArchitectureSubsystemSubmissionService` 填充 `projectRef/projectName`，consumer 校验业务项目与事件项目。
- [ ] 运行 `mvn -pl :ccb-architecture -am -Dtest=PhysicalSubsystemServiceTest,PhysicalSubsystemControllerTest,ArchitectureSubsystemRepositoryTest,SubsystemChangeServiceTest,ArchitectureSubsystemSubmissionServiceTest,ArchitectureWorkflowLifecycleConsumerTest,DeploymentUnitServiceTest,DeploymentUnitControllerTest,DeploymentUnitImportMySqlTest test`。
- [ ] 提交检查点：`feat(architecture): scope subsystem and deployment units by project`。

**验收：** 主数据、变更申请、部署单元、版本、关系和导入全链路隔离。**回滚：** 回退本任务 Java 提交。**停止：** 入口无项目或遗留变更与根实体项目冲突。**升级：** 网络业务必须整体项目化才能维持关系。

### T4：环境管理、资源申请、实例与工作流隔离

**需求映射：** R1、R3、R4、R5、R7；**前置任务：** T3

**文件：** 修改 `EnvironmentResourceModels/Store/Service/Controller`、`ResourceRequestSubmissionService`、`ResourceRequestWorkflowLifecycleConsumer` 和相关测试；候选新建 `EnvironmentResourceProjectIsolationMySqlTest.java`。

**接口：** 消费可信项目和同项目物理子系统/部署单元；产出环境、申请、历史、流程轮次/回执、实例、灾备关系的项目限定读写。

- [ ] 建立环境同编号、跨项目资源选择、申请 ID 越权、实例下发、灾备关系和回调项目不一致测试。
- [ ] 全部环境资源 Controller 增加 `projectRef`，Store 条件和组合关系增加 `project_id`。
- [ ] 资源申请启动填充工作流项目，consumer 在状态变化前校验事件项目。
- [ ] 运行 `mvn -pl :ccb-architecture -am -Dtest=EnvironmentResourceServiceTest,ResourceRequestSubmissionServiceTest,ResourceRequestWorkflowLifecycleConsumerTest,EnvironmentResourceProjectIsolationMySqlTest test`。
- [ ] 提交检查点：`feat(architecture): scope environments and requests by project`。

**验收：** 下发实例和灾备关系不跨项目。**回滚：** 回退环境资源实现。**停止：** provider 无法证明项目归属或旧事件会改写新事实。**升级：** 需要修改 workflow 平台或 Flowable 表。

### T5：搭建计划执行链项目隔离

**需求映射：** R1、R4、R5、R6；**前置任务：** T4

**文件：** 修改 `PlanModels`、`PlanStore`、`PlanGenerationService`、`PlanExecutionService`、`PlanQueryService`、`PlanBlockService`、`PlanDependencyService`、`PlanNotificationService`、`PlanWorkOrderService`、`PlanController`；修改模板回归测试，候选新建 `PlanProjectIsolationMySqlTest.java`、`PlanControllerTest.java`。

**接口：** 消费共享模板、当前项目和同项目环境/目标/工单；产出计划执行链项目归属，模板范围不变。

- [ ] 建立共享模板跨项目生成、计划和子对象直接 ID 越权、跨项目目标/依赖/事件/工单、后台提醒隔离测试。
- [ ] Store 全部执行链方法增加项目参数或通过锁定 plan 校验；后台扫描返回 `project_id`。
- [ ] `/plans`、`/tasks`、`/stages`、`/check-items`、`/dependencies`、`/blocks`、`/events`、`/work-orders` 请求统一带 `projectRef`。
- [ ] 运行 `mvn -pl :ccb-architecture -am -Dtest=PlanStatusCalculatorTest,PlanTemplateServiceTest,PlanProjectIsolationMySqlTest,PlanControllerTest test`。
- [ ] 提交检查点：`feat(architecture): scope setup plans by project`。

**验收：** 模板共享、执行计划私有，子对象不能越权。**回滚：** 回退计划执行链，不改模板。**停止：** 工单缺少可验证项目键或后台任务无法确定项目。**升级：** 需要整体项目化网络工单。

### T6：前端项目上下文与“环境管理”改名

**需求映射：** R2、R5、R6、R11；**前置任务：** T5

**文件：** 修改 `api.ts`、`planApi.ts`、`types.ts`；物理子系统、变更申请、部署单元/导入、环境、资源申请、实例、计划列表/详情页面；`web/src/router/index.ts` 和 `architecture.css`。

**接口：** 消费 `useProjectContextStore().currentRef` 和稳定 API；产出每个目标请求的必填 `projectRef`。

- [ ] API 层把 `projectRef` 设为显式必填参数，不从表单接收项目主键。
- [ ] 页面无项目时停止请求；切换后重置详情、分页、筛选、弹层和脏表单状态。
- [ ] 用户可见“具体环境”改为“环境管理”，内部标识和 API 路径不变。
- [ ] 运行 `npm --prefix web run build`，预期 `vue-tsc` 与 Vite 成功。
- [ ] 浏览器验证 `1280x800`、`375x812`、`390x844`、`430x932` 与明暗主题，无横向溢出。
- [ ] 提交检查点：`feat(web): bind architecture pages to current project`。

**验收：** 顶部项目是唯一上下文，切换不残留旧项目数据。**回滚：** 回退前端提交。**停止：** `currentRef` 初始化不稳定。**升级：** 必须修改公共项目选择器或 HTTP 拦截器。

### T7：数据迁移与测试管理消费者适配

**需求映射：** R10；**前置任务：** T6

**文件：** 修改已检出的数据迁移 4 个 Service、测试管理 9 个 Service 及相关测试；候选新建两个模块的 `PhysicalSubsystemProjectIsolationMySqlTest.java`。

**接口：** 消费 `arch_physical_subsystem.project_id`；产出所有按编号或 ID 的 JOIN 同时匹配业务记录项目。

- [ ] 建立两个项目相同物理子系统编号的失败测试。
- [ ] 数据迁移按 `dm_project_component.project_id` 等现有项目列匹配；无直接项目列时只允许沿已验证父链取项目。
- [ ] 测试管理所有 JOIN 增加 `p.project_id = <业务别名>.project_id`，覆盖选项、详情、统计和报表。
- [ ] 运行 `mvn -pl :ccb-data-migration,:ccb-test-management -am test`。
- [ ] 运行 `rg -n "JOIN arch_physical_subsystem" server/src/modules/data-migration/src/main/java server/src/modules/test-management/src/main/java`，每个命中必须有项目等值条件或明确排除证据。
- [ ] 提交检查点：`fix: match physical subsystems by project`。

**验收：** 13 个消费者文件完成审计，重复编号不串项目。**回滚：** 回退 SQL 和测试。**停止：** 业务表无法可靠确定项目。**升级：** 发现其他模块消费者。

### T8：集成、MySQL、浏览器与治理验收

**需求映射：** R7、R8、R9、R10、R11；**前置任务：** T7

**文件：** 修改 `mock/mock-data.json`、任务 scope；写入当前任务前缀 execution、observation、correction、convergence 证据。

**接口：** 消费 T1-T7 全部产出；产出可复验的双项目、权限、工作流、迁移、浏览器和治理证据。

- [ ] Mock 至少包含两个项目、单项目成员、双项目成员、超级管理员及重复编号的六类数据。
- [ ] 运行 `mvn -pl :ccb-architecture,:ccb-system,:ccb-workflow,:ccb-data-migration,:ccb-test-management -am test`；必要时运行 `mvn test`。
- [ ] 执行真实 MySQL 空库、既有库、默认项目缺失场景，保存版本、空值、索引和跨项目关系统计。
- [ ] 验证项目流程启动、项目成员/角色审批、跨项目待办拒绝、生命周期项目不一致不更新。
- [ ] 完成六页四视口明暗主题浏览器矩阵，覆盖加载、空、失败、无权限、提交中、重复提交、脏表单和项目切换。
- [ ] 运行 `node scripts/check-all-governance.mjs`、`node scripts/check-flyway-migrations.mjs`、scope 检查、`git diff --check`、`git status --short`。
- [ ] 独立观察与收敛验收确认 R1-R11 无未关闭 must 反馈。

**验收：** 需求文档 15 条验收标准均有真实证据。**回滚：** 前端、消费者、architecture、system 逆序回退；数据库用补偿迁移。**停止：** 任一越权、空项目列、错误回调、删除绕过或范围违规。**升级：** 需要生产数据、真实凭据或 Owner 接受残余风险。

## 集成检查

1. T1：MySQL 空库/既有库、默认项目失败和唯一性。
2. T3-T5：架构双项目 API、工作流和所有直接 ID 负向测试。
3. T7：消费者 SQL 扫描和受影响模块回归。
4. T8：完整 Maven、前端构建、真实 MySQL、浏览器、治理、scope 和 diff。

## 控制模型种子

以下均为 `hypotheses-only`，由 modeling 阶段验证：

- 边界候选：顶部项目上下文、architecture API/Service/Store/表、system 项目删除、workflow 集成、数据迁移和测试管理消费者。
- 状态候选：`projectRef/projectId`、实体归属、工作流实例项目、成员/RBAC、唯一键、迁移状态、删除引用计数。
- 接口候选：`ProjectAccessService`、`ProjectDeletionGuard`、`WorkflowBusinessGateway`、architecture REST API、消费者 SQL。
- 传感器候选：双项目 API、SQL 约束、Flyway 退出码、workflow event、浏览器请求、Maven/Vite/治理退出码。
- 执行器候选：迁移、Store 条件、Controller 参数、Service 校验、删除 guard、前端 API、消费者 JOIN。
- 扰动与时延候选：迁移版本竞争、默认项目缺失、软删除、重复编号、旧 workflow 无项目、大表回填和完整回归耗时。
- 假设：默认项目唯一，所有消费者均能从自身记录或父链取得项目 ID。

## 风险与用户批准

- 高风险动作：多表 Flyway、system 公共扩展点、跨模块 SQL、全量 API 参数变更。
- scope 已记录 module Owner 对公共能力变更的批准；执行前仍需复核各模块最小写入路径。
- 用户于 2026-09-05 已明确批准本计划修订 1；handoff 可设为 `approved`、计划可设为 `ready`，并按最小范围导入 control-engineering。
