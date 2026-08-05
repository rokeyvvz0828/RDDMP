# 企业级流程核心能力实施计划

> 执行要求：按 `control-engineering` 的任务边界执行；每个任务完成后运行局部验证并记录证据，不把服务任务、消息事件或复杂子流程扩入首期。

## 状态与来源

- 计划修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-05-workflow-enterprise-core-design.md`
- 设计状态：已确认
- 计划状态：可实施
- 用户授权：用户明确要求按已确认方案实施，并确认首期暂不包含服务任务和消息事件。

## 目标与全局约束

目标：将现有线性审批升级为自定义中文设计器 + Flowable 运行引擎的核心企业级流程，保持现有 `definitionJson` 接口和旧实例兼容。

约束：

- Spring Boot 3.x、JDK 17、MySQL、MyBatis-Plus、Vue 3、TypeScript、Element Plus。
- 所有流程数据和 Flowable 业务关联数据带 `tenant_id`。
- 已启动实例固化流程定义版本和 deployment id。
- 前端不直接写入 Flowable 表；后端统一封装引擎调用。
- 服务任务、消息事件、补偿事件、复杂子流程和通用表单设计器不在首期。
- 既有修改保留；涉及旧 `wf_*` 表时通过适配或新增字段迁移，禁止破坏性删除。

## 文件职责地图

- `pom.xml`：Flowable 版本属性和依赖管理。
- `ccb-workflow/pom.xml`：Flowable Spring Boot、BPMN 模型和引擎依赖。
- `ccb-boot/src/main/resources/application.yml`：引擎初始化、历史级别和运行配置。
- `ccb-infrastructure/src/main/resources/db/migration/V18__flowable_workflow_core.sql`：Flowable 引擎表和业务扩展表迁移。
- `ccb-workflow/src/main/java/com/ccb/workflow/model/`：流程业务模型、变量、动作和监控 DTO。
- `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowModelValidator.java`：业务模型校验。
- `ccb-workflow/src/main/java/com/ccb/workflow/service/BpmnModelCompiler.java`：业务模型到 BPMN 的编译和节点映射。
- `ccb-workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java`：Flowable 实例、任务、变量和历史封装。
- `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowAssigneeResolver.java`：租户范围审批人解析。
- `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowAuditService.java`：发布、实例、动作和干预审计。
- `ccb-workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`：兼容接口和新增版本、校验、监控接口。
- `ccb-workflow/src/test/java/com/ccb/workflow/`：模型、编译、运行和租户隔离测试。
- `ccb-web/src/api/workflow.ts`：流程模型和接口类型。
- `ccb-web/src/components/workflow/WorkflowDesigner.vue`：中文设计器和节点工具栏。
- `ccb-web/src/components/workflow/WorkflowNodeInspector.vue`：节点属性、条件和变量配置。
- `ccb-web/src/views/WorkflowView.vue`：定义、待办、监控和流程轨迹页面。
- `docs/integration/workflow-module-contract.md`：外部模块接入契约和版本兼容说明。

## 任务依赖与并行策略

```text
T1 Flowable 基础设施
  -> T2 业务模型与 BPMN 编译
  -> T3 Flowable 运行与兼容 API
  -> T4 中文设计器与流程监控
  -> T5 集成测试、迁移验证和文档
```

T1 完成并通过迁移/启动验证后才能开始 T2；T2 完成后才能接入 T3；T4 只能在 T2/T3 的接口契约稳定后实施。T1 的前端无依赖准备和 T2 的纯模型测试可并行，但当前先按串行执行，降低引擎依赖和模型契约冲突风险。

## T1 Flowable 基础设施与数据迁移

### 目标

引入与 Spring Boot 3.x/JDK 17 兼容的 Flowable 依赖，建立引擎配置、Flyway 表迁移和业务扩展字段，为后续运行服务提供可验证的引擎边界。

### 需求映射

R1、R6；为 R2、R3、R4、R5 提供基础设施。

### 文件与接口

- 修改：`pom.xml`、`ccb-workflow/pom.xml`、`ccb-boot/src/main/resources/application.yml`。
- 新建：`ccb-infrastructure/src/main/resources/db/migration/V18__flowable_workflow_core.sql`。
- 新建：`ccb-workflow/src/main/java/com/ccb/workflow/config/FlowableWorkflowConfiguration.java`（若自动配置无法满足单租户关联，则显式配置）。

### 实施步骤与证据

1. 固定 Flowable 依赖版本和 Spring Boot 3.x 兼容组合；运行 `mvn -pl ccb-workflow -am test -DskipTests`，预期依赖解析和编译成功。
2. 使用 Flowable 对应版本的 MySQL schema 创建引擎表，增加业务扩展表或字段：definition/version 的 BPMN XML、deployment id、流程图节点映射、变量声明和实例关联。
3. 迁移必须可重复启动，禁止删表、删历史任务或覆盖 V17；运行 `mvn -pl ccb-boot -am test` 并启动后检查 Flyway schema history 和 Flowable 表存在。
4. 配置历史级别为 audit/full 所需的首期级别，关闭生产自动建表，明确本地迁移方式。

### 验收、回滚与停止条件

- 验收：Maven 编译通过；Flyway V18 成功；应用可以初始化 Flowable；第二次启动不重复建表。
- 回滚：只回滚未应用的 V18 文件和依赖配置；已应用迁移通过补充修复迁移处理，不使用 reset/drop。
- 停止：Flowable 依赖与 Boot 3.x 不兼容、引擎表脚本不匹配或迁移出现破坏性 SQL 时停止并重新建模。

## T2 业务模型、校验和 BPMN 编译

### 目标

建立稳定的业务流程模型，将中文设计器 JSON 编译为可部署 BPMN 2.0，并输出设计器节点到引擎节点的可追踪映射。

### 需求映射

R1、R2、R3、R4、R5。

### 文件与接口

- 新建 `ccb-workflow/src/main/java/com/ccb/workflow/model/WorkflowDefinitionModel.java`、`WorkflowNodeModel.java`、`WorkflowVariableModel.java`、`WorkflowActionPolicy.java`。
- 新建 `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowModelValidator.java`、`BpmnModelCompiler.java`、`WorkflowModelAdapter.java`。
- 扩展 `WorkflowDefinitionValidator` 测试，旧 schemaVersion=1 继续可解析。

### 实施步骤与证据

1. 定义版本化 JSON：schemaVersion=2 增加 gateway、condition、parallel、multiInstance、variables、formBindings 和 actionPolicy；schemaVersion=1 转换为兼容串行模型。
2. 校验开始/结束唯一性、图连通性、网关默认路径、变量声明、表达式引用、审批人规则、会签配置和节点动作权限；每条错误返回 nodeId/edgeId/field/message。
3. 使用 Flowable BPMN Model API 生成 startEvent、userTask、receive-free gateway、parallel gateway、inclusive/conditional path、multi-instance userTask、service-free cc task 和 endEvent；当前不生成 serviceTask 或 messageEvent。
4. 编译输出 BPMN XML、deployment key、节点映射和变量声明快照；为相同模型生成稳定节点 key，避免编辑布局导致运行节点改变。
5. 运行单元测试覆盖孤立节点、无默认分支、空审批人、ALL/ANY/比例会签、变量类型错误、旧 schema 兼容和 BPMN 部署验证。

### 验收、回滚与停止条件

- 验收：模型校验测试覆盖首期所有 R2-R5；一个包含条件网关、并行汇聚和会签的模型可以编译并被 Flowable 解析。
- 回滚：保留旧 `WorkflowDefinitionValidator` 入口，删除新编译器调用即可回退旧线性执行。
- 停止：BPMN 语义无法表达某个首期动作、表达式存在任意脚本执行风险或节点 key 不稳定时停止扩展。

## T3 Flowable 运行服务、版本和兼容 API

### 目标

以 Flowable 执行新版本流程，同时保留旧流程定义的兼容读取和旧接口字段。

### 需求映射

R1、R3、R4、R5、R6。

### 文件与接口

- 新建：`FlowableWorkflowService.java`、`WorkflowAssigneeResolver.java`、`WorkflowAuditService.java`、`WorkflowMonitorService.java`。
- 修改：`WorkflowService.java`、`WorkflowController.java`、`V18` 业务扩展表。
- 接口：`POST /definitions/{id}/draft`、`POST /definitions/{id}/validate`、`POST /definitions/{id}/publish`、`POST /definitions/{id}/disable`、`POST /instances`、`GET /tasks/inbox`、`POST /tasks/{id}/actions`、`GET /instances/{id}/timeline`、`GET /monitor/instances`。

### 实施步骤与证据

1. 创建草稿和版本修订逻辑；发布事务内校验、编译、部署、保存 deployment id 和发布状态。
2. 启动实例时读取发布版本、校验变量和表单数据、传递租户与发起人上下文，并保存业务实例关联。
3. 使用 Flowable TaskService/RuntimeService/HistoryService 实现待办、已办、同意、拒绝、退回、转交、委托、加签、减签和抄送；动作写入审计且使用 task/action 幂等键。
4. 实现用户、角色、组织负责人、发起人和表达式审批人解析，解析结果快照到任务扩展数据；空结果进入 ERROR/待配置，不静默跳过。
5. 实现实例时间线、节点耗时、流程图高亮、状态过滤、管理员终止/重试权限和干预原因审计。
6. 旧定义使用 schemaVersion=1 或无 deployment id 时走旧兼容路径；新定义走 Flowable。已运行实例不迁移执行图。

### 验收、回滚与停止条件

- 验收：串行、ANY、ALL、比例会签、条件分支、并行汇聚、退回、转交、委托、加签、减签、抄送、版本固化和审计链路通过集成测试。
- 回滚：新定义发布切换可按 feature flag/版本判断关闭；旧实例继续走旧服务；不回滚已执行 Flowable 历史表。
- 停止：出现跨租户任务可见、重复审批改变结果、历史实例版本漂移或引擎/业务事务无法恢复时停止并修正。

## T4 中文流程设计器、配置面板与监控页面

### 目标

把 BPMN 能力呈现为统一中文、可操作的后台配置界面，支持属性配置、校验错误定位、版本管理和流程运行监控。

### 需求映射

R1、R2、R3、R4、R5、R6。

### 文件与接口

- 修改：`ccb-web/src/api/workflow.ts`、`WorkflowDesigner.vue`、`WorkflowNode.vue`、`WorkflowNodeInspector.vue`、`WorkflowView.vue`。
- 必要时新建：`ccb-web/src/components/workflow/WorkflowValidationPanel.vue`、`WorkflowTimeline.vue`、`WorkflowGatewayInspector.vue`。
- 复用：`UiDataTable`、`UiFormDrawer`、`UiOrgTreeSelect`、`UiUserIdentity`、`UiStatusTag`。

### 实施步骤与证据

1. 扩展前端类型和默认模型，提供节点工具栏、网关/汇聚、变量配置、条件编辑、会签配置、动作策略和校验结果定位。
2. 保持设计器节点尺寸、对齐、缩放、删除保护和中文标签；节点图标与颜色区分人工审批、网关、抄送和结束。
3. 增加草稿保存、校验、发布、版本和停用操作；发布前显示后端结构化错误，不自行假设可发布。
4. 增加流程监控、实例详情、轨迹时间线、节点高亮、审批动作记录和管理员干预入口。
5. 处理加载中遮罩、错误、空状态、权限错误和响应式布局；日期页面只展示 `yyyy-MM-dd`。
6. 运行 `npm run build`，再用真实浏览器验证新建、配置、校验、发布、发起、待办、审批和监控页面。

### 验收、回滚与停止条件

- 验收：中文页面能完成完整核心流程配置和监控；`npm run build` 通过；浏览器无控制台阻断错误。
- 回滚：前端可回退到旧节点类型映射，后端仍按旧 `definitionJson` 执行。
- 停止：设计器出现连线丢失、配置与后端 schema 不一致、深色主题遮挡或按钮权限绕过时停止。

## T5 集成验证、文档和交付证据

### 目标

闭合数据库、引擎、接口、前端和权限链路，确保首期能力可重复启动、可观测并可回退。

### 需求映射

R1-R6。

### 文件与接口

- 修改：`docs/integration/workflow-module-contract.md`。
- 测试：`ccb-workflow/src/test/java/com/ccb/workflow/`、后端集成测试、前端构建和浏览器链路。
- 证据：Maven/Flyway/HTTP/浏览器截图或 DOM 结果、残余风险记录。

### 实施步骤与证据

1. 运行 Maven 模块测试和完整 reactor 构建，确认 Flowable 表迁移可重复。
2. 运行真实 HTTP 链路：登录、创建草稿、校验失败、校验成功、发布、启动、待办、审批、时间线和管理员监控。
3. 运行跨租户、普通用户无权干预、重复提交、空审批人、变量类型错误和旧定义兼容测试。
4. 运行前端构建和真实浏览器链路，记录页面加载、设计器交互、监控图高亮和深浅主题检查结果。
5. 更新接入契约，说明新模块只依赖流程 API、动作契约、事件审计和用户/角色/组织解析接口。

### 验收、回滚与停止条件

- 验收：R1-R6 均有可重复证据；构建和迁移通过；P0/P1 为 0；剩余风险显式记录。
- 回滚：按任务边界回退；保留迁移和测试证据；不使用 `git reset --hard` 或删除用户已有文件。
- 停止：任何核心链路无证据、出现跨租户数据、流程状态不可恢复或构建失败时停止交付。

## 集成检查

- `mvn -pl ccb-boot -am test -DskipTests=false`：预期 `BUILD SUCCESS`。
- `npm run build`（工作目录 `ccb-web`）：预期 TypeScript 检查和 Vite 构建成功。
- 应用启动两次：第一次应用 V18，第二次无重复迁移；健康检查返回 200。
- 浏览器流程链路：配置 -> 校验 -> 发布 -> 发起 -> 待办 -> 审批 -> 监控详情。
- 数据断言：实例版本固定、任务动作幂等、租户隔离、审计完整。

## 控制种子

- 被控对象：流程定义、BPMN 编译、Flowable 运行状态、业务实例、任务、前端设计器和监控界面。
- 状态变量：schema 版本、发布状态、deployment id、实例状态、任务状态、变量快照、构建状态和迁移状态。
- 传感器：模型单测、Maven 构建、Flyway history、HTTP 响应、数据库断言、浏览器 DOM/截图和审计查询。
- 执行器：迁移、依赖配置、编译器、运行服务、REST API、前端组件和权限配置。
- 扰动：旧流程数据、空审批人、并行汇聚、重复动作、跨租户请求、Flowable/业务事务延迟。
- 回滚原则：先保证旧 schemaVersion=1 路径可运行，再切换新 BPMN 发布；任何引擎不一致不标记为完成。