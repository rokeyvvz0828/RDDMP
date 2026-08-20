# 配置管理审批流程场景绑定实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-17-release-workflow-scene-binding-design.md`
- 状态：可移交

**目标：** 在配置管理模块实现按项目维护的五类审批场景绑定，由后端按流程定义 ID启动已发布流程，并补齐定义删除和归档引用保护。

**架构：** 工作流模块增加稳定的定义目录、按 ID 启动和业务引用 SPI；配置管理模块拥有绑定数据、审计、HTTP 接口和引用实现；版本申请提交只消费绑定解析结果。前端在配置管理内增加独立页面，不在申请单或流程定义页混入绑定职责。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring JDBC、Flowable 7、MySQL 8.4、Flyway、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Pinia。

## 全局约束

- 只在本地 `rokey` 分支工作，不提交、推送或覆盖已有未提交修改。
- Flyway 只追加 `V43`，不修改 `V1` 至 `V42`。
- 项目上下文继续使用现有 Mock `currentRef`，后端接口使用稳定字符串项目标识。
- 前端不能为版本申请指定流程；后端负责场景判定、绑定解析和已发布状态校验。
- 已启动实例固定原定义与版本，改绑不得影响历史轮次。
- 工作流模块不得直接查询配置管理 `rel_*` 表，通过 integration SPI 查询引用。
- 不实现平台级通用业务场景中心，不改变版本类型和追加判定规则。

---

## 文件职责地图

- `server/src/platform/infrastructure/src/main/resources/db/migration/V43__release_workflow_scene_binding.sql`（candidate-new）：绑定、历史、索引、权限和 `workflow_code` 可空兼容迁移。
- `server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/*`（existing + candidate-new）：跨模块定义目录、按 ID 启动命令及引用 SPI。
- `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java`（existing）：实现已发布定义查询及按定义 ID 启动。
- `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`（existing）：删除和归档前执行历史、实例与引用保护。
- `server/src/modules/release/src/main/java/com/ccb/release/workflow/*`（candidate-new）：绑定模型、仓储、服务和 HTTP 控制器。
- `server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowDefinitionReferenceProvider.java`（candidate-new）：向工作流平台提供有效业务引用。
- `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseScenarioPolicy.java`（existing）：输出稳定场景键，不再输出硬编码流程编码。
- `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`（existing）：解析绑定并按定义 ID 启动，固化实际流程编码、定义和版本。
- `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java` 与 persistence/model（existing）：草稿阶段流程编码为空，提交后展示实际编码。
- `web/src/api/release.ts`、`web/src/modules/release/types.ts`、`web/src/modules/release/ReleaseManagementPrototype.vue`（existing）：绑定 API、视图键和页面接入。
- `web/src/modules/release/components/ReleaseWorkflowBindingView.vue`（candidate-new）：五场景配置、失效状态、原因和历史交互。
- `web/src/modules/release/release-prototype.css`（existing）：配置视图响应式样式。

## 任务依赖图与并行策略

```text
T1 工作流公共契约与生命周期保护
  -> T2 配置管理绑定数据与接口
     -> T3 版本申请提交接入绑定
        -> T4 前端审批流程配置页
           -> T5 集成迁移与端到端验收
```

任务串行执行。T2 消费 T1 的公共接口，T3 消费 T2 的解析服务，T4 依赖 T2 的稳定 HTTP 契约，T5 依赖全部实现；共享编译产物和同一数据库使并行收益不足以覆盖干扰风险。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 项目级五场景配置 | T2, T4, T5 |
| R2 部分配置和命中阻断 | T2, T3, T4, T5 |
| R3 后端解析和版本固定 | T1, T3, T5 |
| R4 审计、权限和并发 | T2, T4, T5 |
| R5 删除与归档保护 | T1, T2, T5 |

### T1：工作流定义目录、按 ID 启动和生命周期引用保护

**需求映射：** R3, R5

**前置任务：** 无

**文件：**

- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowDefinitionSummary.java`
- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowDefinitionCatalog.java`
- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowStartDefinitionCommand.java`
- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowDefinitionReference.java`
- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowDefinitionReferenceProvider.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowBusinessGateway.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowBusinessIntegrationServiceTest.java`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowDefinitionLifecycleTest.java`

**接口：**

- 产出：`WorkflowDefinitionCatalog.listPublished(AuthUser)` 和 `requirePublished(long, AuthUser)`。
- 产出：`WorkflowBusinessGateway.startByDefinitionId(WorkflowStartDefinitionCommand, AuthUser)`，返回既有 `WorkflowStartResult`。
- 产出：`WorkflowDefinitionReferenceProvider.references(long definitionId, long tenantId)`，返回项目和场景摘要。

- [ ] **步骤 1：补充失败测试**：覆盖按 ID 启动只接受唯一已发布定义、候选列表租户隔离、曾发布草稿/历史实例阻止删除、有效引用阻止删除和归档。
- [ ] **步骤 2：运行基准检查**：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -Dtest=WorkflowBusinessIntegrationServiceTest,WorkflowDefinitionLifecycleTest test`；预期新增测试因接口或保护缺失失败。
- [ ] **步骤 3：实施公共契约和服务逻辑**：复用业务上下文校验和实例启动代码；定义行先锁定，引用提供者使用可选列表注入；错误返回明确中文原因和有限引用摘要。
- [ ] **步骤 4：运行局部回归**：重复步骤 2；预期所有聚焦测试通过且既有按编码启动行为不回归。
- [ ] **步骤 5：检查写入面**：`git diff --check -- server/src/platform/workflow`；证据为零格式错误和仅计划文件范围内的 diff。

**回滚：** 删除新增 integration 类型并恢复三个工作流修改文件；不涉及数据库。

**停止条件：** 现有服务存在另一个未发现的启动入口绕过 `WorkflowBusinessIntegrationService`；引用 SPI 造成 Maven 模块循环。

**升级条件：** 必须改变 Flowable 部署/实例语义，或需要删除历史实例才能实现保护。

### T2：配置管理项目场景绑定数据、服务与 HTTP 接口

**需求映射：** R1, R2, R4, R5

**前置任务：** T1

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V43__release_workflow_scene_binding.sql`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/workflow/model/ReleaseWorkflowBindingModels.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/workflow/persistence/ReleaseWorkflowBindingStore.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/workflow/service/ReleaseWorkflowBindingService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/workflow/web/ReleaseWorkflowBindingController.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowDefinitionReferenceProvider.java`
- 修改：`server/src/modules/release/src/test/java/com/ccb/release/ReleaseSchemaContractTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/workflow/service/ReleaseWorkflowBindingServiceTest.java`
- 新建测试：`server/src/modules/release/src/test/java/com/ccb/release/workflow/web/ReleaseWorkflowBindingControllerSecurityTest.java`

**接口：**

- 消费：T1 `WorkflowDefinitionCatalog` 和 `WorkflowDefinitionReferenceProvider`。
- 产出：`GET /api/release/workflow-bindings`、`GET /candidates`、`PUT /{sceneCode}`、`GET /{sceneCode}/history`。
- 产出：`ReleaseWorkflowBindingService.resolve(projectId, sceneCode, user)`，返回定义 ID、编码、名称和当前版本。

- [ ] **步骤 1：建立迁移与服务失败测试**：断言两张表、唯一键、引用索引、两项权限、超级管理员授权；覆盖五场景补齐、部分配置、原因必填、乐观锁、失效计算和租户/项目隔离。
- [ ] **步骤 2：运行基准检查**：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -Dtest=ReleaseSchemaContractTest,ReleaseWorkflowBindingServiceTest,ReleaseWorkflowBindingControllerSecurityTest test`；预期因迁移、服务和控制器缺失失败。
- [ ] **步骤 3：追加 V43 并实现最小纵切**：主表允许 `workflow_definition_id` 为空，历史不可变；解除绑定保留主记录；所有变更同事务写历史；候选仅返回已发布流程摘要。
- [ ] **步骤 4：实现引用提供者**：按租户和定义 ID 返回所有非空绑定，即使流程已取消发布也视为引用，供删除和归档保护。
- [ ] **步骤 5：运行局部回归和格式检查**：重复步骤 2，并运行 `git diff --check -- server/src/modules/release server/src/platform/infrastructure/src/main/resources/db/migration/V43__release_workflow_scene_binding.sql`。

**回滚：** 回退新增 Java 文件和 V43 源文件；若 V43 已在隔离库执行则保留空表，不执行破坏性回滚。

**停止条件：** V43 版本已被并行工作占用；当前数据库权限模型无法添加新的 release 权限而不修改已执行迁移。

**升级条件：** 产品要求平台级通用场景表，或项目管理提供的最终项目主键与当前字符串契约不兼容。

### T3：版本申请提交改为解析项目场景绑定

**需求映射：** R2, R3

**前置任务：** T2

**文件：**

- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseScenarioPolicy.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationModels.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/persistence/ReleaseApplicationStore.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseScenarioPolicyTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java`

**接口：**

- 消费：T2 `ReleaseWorkflowBindingService.resolve`。
- 消费：T1 `WorkflowBusinessGateway.startByDefinitionId`。
- 产出：场景键 `REGULAR`, `REGULAR_ADDITIONAL`, `URGENT`, `URGENT_ADDITIONAL`, `EMERGENCY`；审批轮次和申请单保存实际流程编码及实际定义版本。

- [ ] **步骤 1：修改测试表达新契约**：断言场景策略不返回硬编码流程编码；未配置/失效绑定在轮次写入前失败；绑定启动命令携带正确定义 ID；改绑不改变旧轮次。
- [ ] **步骤 2：运行基准检查**：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -Dtest=ReleaseScenarioPolicyTest,ReleaseSubmissionServiceTest,ReleaseApplicationServiceTest test`；预期因旧按编码逻辑失败。
- [ ] **步骤 3：实施提交链路**：先解析绑定，再计算摘要、写启动轮次并按 ID 启动；Spring 事务保证任一失败无部分状态；草稿 `workflow_code` 为空，提交成功后写实际编码。
- [ ] **步骤 4：运行局部和模块回归**：重复步骤 2，再运行 `env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release test`；预期全部通过。
- [ ] **步骤 5：检查调用方**：`rg -n "workflowCode\(|startByCode|release\.regular|release\.emergency" server/src/modules/release`；预期生产代码不再以硬编码流程编码决定提交。

**回滚：** 恢复五个现有文件；保留未被消费的绑定表和接口不影响旧提交路径。

**停止条件：** 申请模型对 `workflow_code` 非空存在未覆盖的外部契约；事务传播导致工作流启动无法随业务失败一致回滚。

**升级条件：** 必须改变既有申请状态机或生命周期事件契约。

### T4：配置管理审批流程配置页面

**需求映射：** R1, R2, R4

**前置任务：** T3

**文件：**

- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/types.ts`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 新建：`web/src/modules/release/components/ReleaseWorkflowBindingView.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**

- 消费：T2 四个绑定 HTTP 接口。
- 产出：配置管理导航键 `workflow-bindings`，五场景配置、有效/失效/未配置状态、原因对话框和变更历史。

- [ ] **步骤 1：建立类型与构建基线**：增加精确 DTO 和 API 函数签名后运行 `npm run build`，预期在页面接入前存在未使用或缺失组件信号。
- [ ] **步骤 2：实现独立页面**：使用表格/响应式条目展示场景，已发布流程下拉选择，修改与解除均要求原因；候选、配置、历史各自局部加载和重试。
- [ ] **步骤 3：接入项目切换**：`projectStore.currentRef` 变化时重新加载，不复用前一项目编辑状态；配置请求始终发送当前项目 ref/name。
- [ ] **步骤 4：构建与静态检查**：在 `web` 目录运行 `npm run build`；预期 TypeScript 和 Vite 构建通过，`git diff --check -- web/src` 无错误。
- [ ] **步骤 5：浏览器桌面与窄屏检查**：验证文本不溢出、按钮稳定、错误与空状态不遮挡导航，保存后状态和历史立即更新。

**回滚：** 删除新组件并恢复导航、类型、API 和样式修改；后端配置仍可通过 API 管理。

**停止条件：** 当前项目上下文没有稳定 ref/name；现有导航宽度无法容纳新项且需要重新设计全局壳层。

**升级条件：** 用户要求将页面移动到工作流定义或平台级配置中心。

### T5：迁移、跨模块回归和真实业务端到端验收

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T4

**文件：**

- 仅验证前述任务文件；不新增业务能力。
- 更新证据：`.ai-control/requirements/req-20260817-032-release-workflow-scene-binding/` 下执行、观察和收敛记录。

**接口：** 消费 T1 至 T4 全部契约，不产生新公共接口。

- [ ] **步骤 1：重新安装当前后端构件**：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-boot -am -DskipTests install`；预期 `BUILD SUCCESS`。
- [ ] **步骤 2：在隔离数据库执行严格 Flyway**：使用 `ccb_platform_rokey_20260817` 启动本地后端；预期校验并应用 V43，健康检查 `UP`，无迁移或定时任务错误。
- [ ] **步骤 3：运行聚焦与模块回归**：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow,:ccb-release -am test`；预期零失败。
- [ ] **步骤 4：运行前端与仓库检查**：`npm run build`、`git diff --check`，以及仓库已有治理/范围检查命令；预期全部通过或如实记录既有非本任务失败。
- [ ] **步骤 5：浏览器真实流程**：创建并发布测试流程；为当前项目绑定常规场景；创建并提交版本申请；从申请详情审批；验证实际定义 ID/版本、绑定历史、申请状态和工作台待办；再改绑并确认旧实例不变。
- [ ] **步骤 6：验证负路径**：未配置场景阻断、归档绑定流程阻断、取消发布后配置失效、曾发布草稿删除阻断、另一项目配置隔离。

**回滚：** 停止本地后端并恢复到任务前构建；隔离库保留 V43 数据用于审计，不操作原 `ccb_platform`。

**停止条件：** 严格迁移要求修改已执行 V1-V42；发现用户并行修改与任务文件重叠且无法安全合并。

**升级条件：** 端到端审批需要未授权的真实项目或组织数据，或发现 P0/P1 数据完整性偏差。

## 集成检查

- T1 后：工作流聚焦测试和 `ccb-workflow` 编译通过。
- T2 后：绑定服务、权限、迁移契约和引用 SPI 测试通过。
- T3 后：配置管理模块全部测试通过，生产代码不再依赖固定流程编码启动。
- T4 后：前端构建通过，三个 Mock 项目切换无配置串扰。
- T5 后：严格 Flyway、后端健康、API、浏览器正常/失败路径及数据库聚合证据一致。

## 控制模型种子

- 被控边界候选：工作流定义生命周期、项目场景绑定、版本申请提交事务和配置页面。
- 状态变量候选：绑定定义 ID、绑定行版本、定义状态、历史发布版本数、实例数、申请审批轮次和绑定有效性。
- 接口候选：定义目录、按 ID 启动、定义引用 SPI、四个绑定 HTTP 接口和生命周期事件。
- 传感器候选：JUnit/Mockito、Flyway history、MySQL 聚合查询、HTTP 状态、前端构建、浏览器 DOM/截图。
- 执行器候选：绑定 PUT、流程发布/取消发布/归档、申请提交和审批动作。
- 扰动候选：项目 Mock 标识变化、并发改绑、并行 Flyway 版本占用、工作流被外部取消发布、脏工作树重叠修改。
- 时延候选：流程生命周期事件异步投递和前端项目切换后的接口刷新。
- 以上均为 `hypotheses-only`，由后续系统建模验证。

## 风险与用户批准

- 高风险动作仅为追加 V43 并改变新申请的流程解析路径；不修改旧迁移，不迁移或删除现有实例。
- 流程归档行为增加业务引用阻断，属于预期安全收紧；必须覆盖无引用正常归档。
- 本计划不包含提交、推送或合并操作。
- 当前状态等待用户复核计划后进入闭环实施。
