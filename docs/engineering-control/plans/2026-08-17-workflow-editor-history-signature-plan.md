# 工作流生命周期、编辑器、历史与电子签名实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在本地 `rokey` 基线上补齐工作流定义归档/恢复、版本与更新历史、串行节点自动插入、响应式编辑器和明确的内部电子签名配置，并修复 schema 元数据同步回归。

**架构：** 保持 Flowable 运行实例和已发布版本不可变，通过 `wf_definition.status=ARCHIVED` 扩展定义生命周期，通过 `wf_version` 和 `wf_audit_event` 提供历史读取。前端只编排图模型和展示状态，生命周期约束、租户隔离、签名判定与审计均由服务端执行；数据库只追加 V42 修复迁移。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Flowable、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Vue Flow、Maven、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-17-workflow-editor-history-signature-design.md`
- 需求文档：`docs/requirements/REQ-20260817-031-workflow-editor-history-signature/requirement.md`
- 状态：可移交
- 批准依据：用户于 2026-08-17 查看任务摘要后明确回复“确认”。
- 基线：本地 `rokey` 提交 `9e83fbf` 及用户当前未提交改动的隔离副本

## 全局约束

- 仅在 `/private/tmp/rddmp-workflow-fix-rokey` 的隔离分支 `fix/REQ-20260817-031-workflow-editor-history-signature-rokey` 实施，原始 `rokey` 工作区在验证完成前不写入。
- 保护隔离副本中已有配置管理、工作流业务接入、附件与签名变更，不回退或覆盖本需求范围外的用户修改。
- 已启动实例继续绑定原 `definition_id + version_no + deployment_id`；归档、恢复和历史查询不得改变运行图。
- 归档和恢复必须填写原因；恢复使用归档前已发布版本，不创建新版本、不重新部署。
- 草稿可软删除，`PUBLISHED` 与 `ARCHIVED` 均不可删除。
- 电子签名仅使用当前登录身份确认，不接入外部 CA；服务端继续依据实例绑定版本校验同意、拒绝、退回。
- 不修改 V34、V35 及任何已执行迁移；新增迁移固定使用 `V42__repair_workflow_schema_versions_after_mock_sync.sql`。
- 项目保持 Java 17、Spring Boot 3.4.4、Vue 3、TypeScript、Element Plus 和现有统一响应结构，不增加新依赖。
- 不恢复输入项配置，不修改 `server/src/modules/**`，不把隔离分支直接推送到远程。

---

## 文件职责地图

| 路径 | 状态 | 单一职责 | 事实依据 |
| --- | --- | --- | --- |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java` | existing | 平台工作流定义、实例和任务的应用服务门禁 | 当前包含定义删除、发布、取消发布与启动入口 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java` | existing | Flowable 部署、实例执行和定义审计编排 | 当前记录定义创建、修改、发布与取消发布事件 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowAuditService.java` | existing | 写入不可变 `wf_audit_event` | 当前提供统一 `record` 方法 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java` | existing | 认证后的工作流 HTTP 契约 | 当前提供定义 CRUD、发布和取消发布接口 |
| `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowDefinitionLifecycleTest.java` | candidate-new | 生命周期、历史和租户边界回归测试 | 当前没有覆盖归档/恢复的聚焦测试 |
| `web/src/components/workflow/WorkflowDesigner.vue` | existing | 图节点、连线和右键插入交互 | 当前 `addNode` 在已有出边时直接警告并退出 |
| `web/src/components/workflow/WorkflowNodeInspector.vue` | existing | 节点与连线属性配置 | 当前签名字段使用语义不够明确的开关 |
| `web/src/views/WorkflowView.vue` | existing | 定义列表、设计弹窗、生命周期和历史界面 | 当前只有当前版本视图，弹窗画布固定高度 |
| `web/src/api/workflow.ts` | existing | 工作流前端类型和 HTTP 调用 | 当前没有归档、恢复和定义历史契约 |
| `web/src/styles.css` | existing | 工作流页面和设计器响应式样式 | 当前画布固定 `560px`，弹窗主体未形成独立滚动容器 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V42__repair_workflow_schema_versions_after_mock_sync.sql` | candidate-new | 追加修复定义与版本 schema 元数据 | V34 已执行且不可修改，本地 V40/V41 已占用 |
| `server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java` | existing | 本地 Mock 表字段白名单和同步 | 当前 `wf_definition` 白名单缺少 `model_schema_version` |
| `server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java` | candidate-new | 防止 Mock 同步重写错误 schema 元数据 | 当前缺少对应回归测试 |
| `mock/mock-data.json` | existing | 本地演示数据唯一输入 | 当前定义记录未显式提供 `model_schema_version` |

## 任务依赖图与并行策略

```text
T1 后端生命周期与历史 ─────┐
                           ├── T3 前端历史、生命周期与签名配置 ──┐
T2 图插入与响应式编辑器 ───┘                                     ├── T5 集成与浏览器验收
T4 V42 与 Mock 防回归 ────────────────────────────────────────────┘
```

- `T1`、`T2`、`T4` 写入面互不重叠，可作为并行组；当前由单一执行器串行完成，避免共享构建环境和数据库采样互相干扰。
- `T3` 消费 `T1` 的 HTTP 契约，并与 `T2` 同时落入 `WorkflowView.vue`，因此必须在两者之后实施。
- `T5` 只在 T1-T4 局部验证通过后执行。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 流程定义生命周期 | T1、T3、T5 |
| R2 自动插入节点 | T2、T5 |
| R3 编辑器视口适配 | T2、T5 |
| R4 版本历史和更新记录 | T1、T3、T5 |
| R5 平台内部电子签名 | T3、T5 |
| R6 schema 元数据一致性 | T4、T5 |

### T1：后端定义归档、恢复与历史查询

**需求映射：** R1、R4

**前置任务：** 无

**已证实输入：**

- `WorkflowService.deleteDefinition` 当前对任意状态执行软删除。
- 发起流程的服务端路径只接受 `PUBLISHED` 定义，归档可通过状态门禁阻止新实例。
- `wf_version.definition_json` 保存不可变版本快照，`wf_audit_event` 已承载定义级事件。

**文件：**

- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowAuditService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`
- 新建：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowDefinitionLifecycleTest.java`

**接口：**

- 消费：`AuthUser` 的 `tenantId` 和认证身份；现有 `wf_definition`、`wf_version`、`wf_audit_event` 表。
- 产出：`POST /api/workflows/definitions/{id}/archive`，请求 `{ "reason": string }`。
- 产出：`POST /api/workflows/definitions/{id}/restore`，请求 `{ "reason": string }`。
- 产出：`GET /api/workflows/definitions/{id}/versions`。
- 产出：`GET /api/workflows/definitions/{id}/versions/{versionNo}`。
- 产出：`GET /api/workflows/definitions/{id}/events`。
- 产出：定义状态 `ARCHIVED`，事件 `DEFINITION_ARCHIVED`、`DEFINITION_RESTORED`，并保留既有事件名。

- [ ] **步骤 1：建立生命周期失败测试**

  在 `WorkflowDefinitionLifecycleTest` 使用项目既有 JDBC/Mockito 测试风格覆盖：草稿删除成功，已发布和已归档删除失败；仅已发布可归档；仅已归档可恢复；空原因失败；租户不匹配按不存在处理。

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am -Dtest=WorkflowDefinitionLifecycleTest test`

  预期：测试编译或断言失败，明确显示归档/恢复接口尚不存在或非草稿删除未被拒绝。

  证据：保留 Maven 退出码和首个失败断言。

- [ ] **步骤 2：实现服务端状态转换与审计**

  `deleteDefinition` 先按租户读取定义并要求 `DRAFT`；`archiveDefinition` 要求 `PUBLISHED` 和非空原因后更新 `ARCHIVED`；`restoreDefinition` 要求 `ARCHIVED` 后恢复 `PUBLISHED`。更新 SQL 必须同时带 `id + tenant_id + expected status + deleted=0`，受影响行数不是 1 时返回明确业务错误；归档和恢复不修改 `wf_version`、deployment 或实例。

  预期：并发状态变化不会被静默覆盖，每次成功转换产生一条定义级审计事件。

- [ ] **步骤 3：实现版本与更新记录读取**

  版本列表按 `version_no DESC` 返回版本号、状态、schema 版本和创建时间；版本详情返回同租户指定版本及 `definition_json`；定义事件仅查询 `instance_id IS NULL` 的定义级事件，按 `created_at DESC, id DESC` 排序，并返回操作人、原因、版本和 payload。

  预期：归档定义仍可读取详情、全部版本和定义事件；跨租户或不存在版本返回统一不存在错误。

- [ ] **步骤 4：暴露 HTTP 契约并运行局部回归**

  Controller 使用现有 `ApiResponse`，原因请求采用显式请求体类型或现有 Map 风格，所有写接口继续从 `@AuthenticationPrincipal` 取身份。

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test`

  预期：`ccb-workflow` 及依赖模块测试全部通过，0 个失败。

- [ ] **步骤 5：建立提交检查点**

  运行：`git diff --check -- server/src/platform/workflow`

  预期：无空白错误；记录 T1 文件清单和测试输出到执行证据，不在隔离分支自动提交。

**验收检查：** 草稿删除、已发布归档、归档恢复、非法状态、空原因、租户隔离、版本排序、指定版本、定义事件过滤、归档后启动失败、原运行实例仍可查询和处理。

**回滚：** 回退 T1 文件差异；数据库没有结构变更，已写入的归档状态可通过同一服务恢复，审计事件保留。

**停止条件：** 发现 `wf_definition.status` 有数据库枚举或检查约束不接受 `ARCHIVED`；存在绕过平台服务直接按 definition id 启动 Flowable 的公开入口。

**升级条件：** 归档会导致 Flowable deployment 被级联删除；现有权限体系无法区分工作流管理操作且需要新增公共权限码。

### T2：串行节点自动插入与响应式设计器

**需求映射：** R2、R3

**前置任务：** 无

**已证实输入：**

- `WorkflowDesigner.addNode` 在审批/抄送节点已有出边时直接提示并返回。
- 当前右键菜单可获得节点 ID，图边模型包含 source、target、handle、label、condition 和 default。
- `.workflow-designer__canvas` 固定高度为 `560px`，弹窗 body 未建立 flex 滚动边界。

**文件：**

- 修改：`web/src/components/workflow/WorkflowDesigner.vue`
- 修改：`web/src/views/WorkflowView.vue`
- 修改：`web/src/styles.css`

**接口：**

- 消费：`WorkflowGraph.nodes`、`WorkflowGraph.edges` 与 Vue Flow 本地节点/边状态。
- 产出：`addNode(type, parentId)` 在零出边追加，在单出边原子替换为两条边；多出边保持图不变并提示选择具体连线。
- 产出：`.workflow-designer-dialog` 的 viewport 高度、固定 header/footer、独立滚动 body 与可伸缩 canvas 样式契约。

- [ ] **步骤 1：建立图变换基准样例**

  用浏览器和开发环境构造零出边、单出边、多出边三种图，记录调用前 nodes/edges 数量和原后继 ID。

  预期：当前单出边场景显示“已有出边”警告且图不变，形成可复现基准。

- [ ] **步骤 2：实现原子插入算法**

  对 parent 的出边先生成不可变快照：0 条时追加并连接；1 条时先构造新节点，再保留原边的 source 端语义创建 `parent -> new`，创建 `new -> originalTarget` 并保留原边目标 handle，最后一次性替换 nodes/edges；超过 1 条时不写状态并提示选择具体连线。插入后调用 `syncToModel` 并选中新节点。

  预期：单出边时节点数 +1、边数 +1，原后继入边改为来自新节点，原后继不丢失；失败前不修改图。

- [ ] **步骤 3：调整弹窗和画布布局**

  为普通弹窗设置 `max-height: calc(100dvh - 24px)` 和纵向 flex；header/footer 不收缩，body `min-height:0; overflow:auto`；builder 和 designer 在可用高度内伸缩，canvas 使用 `min-height` 与 flex，不再用固定 `560px` 撑开页面。全屏样式继续优先占满视口。

  预期：保存和关闭按钮在桌面及小视口可达，弹窗内容滚动不带动页面主体横向溢出。

- [ ] **步骤 4：运行前端构建与静态检查**

  运行：`npm --prefix web run build`

  预期：TypeScript 和 Vite 构建成功，0 个错误。

  运行：`git diff --check -- web/src/components/workflow/WorkflowDesigner.vue web/src/views/WorkflowView.vue web/src/styles.css`

  预期：无空白错误。

- [ ] **步骤 5：保存局部浏览器证据**

  在桌面和 `375x812` 至少验证一次单边插入与保存按钮可达，记录插入后的图节点/边数量和截图路径到执行证据。

**验收检查：** 零边追加、单边插入、原目标保留、多边不变、保存后模型通过后端校验、普通弹窗 footer 可见、全屏退出可用、页面无整体横向滚动。

**回滚：** 回退三个前端文件；图变换只发生在尚未保存的本地模型，失败时关闭弹窗可放弃更改。

**停止条件：** Vue Flow 当前边对象不能稳定保留 source/target handle；后端验证要求被替换边的 ID 在版本间保持不变。

**升级条件：** 多出边选择必须在本次新增独立分支选择 UI 才能满足现有产品操作；响应式修复需要改动公共 `el-dialog` 全局样式并影响其他模块。

### T3：前端生命周期、历史和明确签名配置

**需求映射：** R1、R4、R5

**前置任务：** T1、T2

**已证实输入：**

- T1 提供归档、恢复、版本和定义事件接口。
- `WorkflowView.vue` 当前承载定义列表和流程图弹窗，适合在定义详情内增加历史视图。
- `signatureRequired`、`signature_required` 和 `signatureConfirmed` 已存在，后端签名服务已按实例绑定版本校验。

**文件：**

- 修改：`web/src/api/workflow.ts`
- 修改：`web/src/views/WorkflowView.vue`
- 修改：`web/src/components/workflow/WorkflowNodeInspector.vue`
- 修改：`web/src/styles.css`

**接口：**

- 消费：T1 的五个生命周期/历史接口和现有 `WorkflowDefinition`、`WorkflowGraph`。
- 产出：`archiveWorkflowDefinition(id, reason)`、`restoreWorkflowDefinition(id, reason)`、`listWorkflowDefinitionVersions(id)`、`getWorkflowDefinitionVersion(id, versionNo)`、`listWorkflowDefinitionEvents(id)`。
- 产出：`WorkflowDefinitionVersion` 与 `WorkflowDefinitionEvent` 类型。
- 产出：节点配置文案“需要电子签名”以及 `是/否` 单选值，继续写入布尔 `signatureRequired`。

- [ ] **步骤 1：补齐前端 API 类型与请求**

  增加归档/恢复原因请求、版本摘要/详情和定义事件类型，沿用现有 request 封装与后端字段命名，不在前端模拟生命周期状态。

  预期：所有调用具备明确 TypeScript 返回类型，归档/恢复原因不会通过 URL 参数泄漏。

- [ ] **步骤 2：实现列表生命周期操作**

  草稿显示删除；已发布显示归档并弹出必填原因输入；已归档显示恢复发布并弹出必填原因输入。状态标签增加 `ARCHIVED: 已归档`，归档定义可查看但不可编辑、发布、取消发布或删除。

  预期：非法操作按钮不出现或禁用，服务端仍作为最终门禁；操作成功刷新列表。

- [ ] **步骤 3：实现版本与更新记录视图**

  定义详情提供“当前设计 / 历史版本 / 更新记录”三个标签。历史版本表展示版本号、状态、模型版本、创建时间，并能打开指定 `definition_json` 的只读设计器；更新记录展示事件中文名、操作人、时间、原因和版本号，失败区域独立重试且不阻断当前设计查看。

  预期：历史设计器始终 `readonly`，切换版本不覆盖当前草稿表单或当前 graph。

- [ ] **步骤 4：将签名开关改为明确的是/否**

  `WorkflowNodeInspector` 使用 `el-radio-group` 或项目既有分段选择展示“需要电子签名：是/否”，未配置值规范化为 `false`；只对 `APPROVAL` 节点展示，readonly 历史图只读展示值。

  预期：保存后仍生成布尔 `signatureRequired`，不改变现有审批请求和服务端签署事务。

- [ ] **步骤 5：构建并验证签名回归**

  运行：`npm --prefix web run build`

  预期：前端构建成功。

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am -Dtest=WorkflowSignatureServiceTest test`

  预期：需要签名的同意、拒绝、退回继续拒绝未确认请求，非受控动作不要求签名，测试全部通过。

**验收检查：** 三种定义状态操作正确；原因必填；归档定义历史可见；历史版本只读；事件顺序和操作者清晰；签名配置明确显示是/否；发布版本配置和服务端签名校验无回归。

**回滚：** 回退四个前端文件；后端 T1 契约可保留但不会被旧前端调用，已有签署记录不变。

**停止条件：** T1 返回字段无法唯一还原历史 graph；历史详情与当前编辑共用同一响应式对象导致草稿被覆盖且无法局部隔离。

**升级条件：** 产品要求版本可视化 diff 或签名证书/印章展示；现有统一权限要求为归档/恢复新增独立权限码。

### T4：V42 schema 元数据修复与 Mock 同步防回归

**需求映射：** R6

**前置任务：** 无

**已证实输入：**

- 本地 Flyway 历史已有 V40/V41，故新增版本使用 V42。
- V34 已成功执行但本地仍有 `wf_definition` 与 `wf_version` 元数据不一致。
- `MockDataInitializer.ALLOWED_COLUMNS` 的 `wf_definition` 白名单缺少 `model_schema_version`，Mock 定义行也未提供该字段。

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V42__repair_workflow_schema_versions_after_mock_sync.sql`
- 修改：`server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java`
- 修改：`mock/mock-data.json`
- 新建：`server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java`

**接口：**

- 消费：`wf_definition.definition_json`、`wf_version.definition_json` 中 `schemaVersion`。
- 产出：当 JSON schema 为 2 时，对应 `model_schema_version` 必为 2；Mock 定义同步显式写入同值。

- [ ] **步骤 1：建立 Mock 字段白名单失败测试**

  测试加载 `mock/mock-data.json` 的 `wf_definition` 行，断言每行存在 `model_schema_version=2`，并通过初始化器可写列白名单验证该字段不会被丢弃。

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-infrastructure -am -Dtest=MockDataInitializerTest test`

  预期：当前数据或白名单断言失败。

- [ ] **步骤 2：新增 V42 幂等元数据修复**

  使用 MySQL JSON 提取分别更新 `wf_definition` 和 `wf_version`：仅处理未删除且 `definition_json.schemaVersion=2`、元数据不等于 2 的行；不修改 JSON、版本号、状态或任何 Flowable 表。

  预期：迁移重复运行逻辑不会继续改变已一致数据，V34/V35 文件校验和保持不变。

- [ ] **步骤 3：修正 Mock 同步源**

  在 `wf_definition` 允许列中加入 `model_schema_version`，为所有本地定义行显式登记 2；保持 `wf_version` 现有 schema 值不变。

  预期：应用启动同步后不一致计数仍为 0。

- [ ] **步骤 4：运行基础设施测试和迁移检查**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-infrastructure -am test`

  预期：模块测试全部通过。

  在隔离 MySQL 执行应用迁移后查询两表 JSON schema 为 2 但元数据不为 2 的聚合数量。

  预期：两项数量均为 0，Flyway validate 成功。

**验收检查：** V42 版本唯一；V34/V35 未修改；两表不一致数量为 0；应用 Mock 同步后仍为 0；Mock JSON 可解析；测试覆盖字段白名单。

**回滚：** 应用代码和 Mock 可回退；V42 一经执行不删除、不改写，保留修正后的元数据，因为它不破坏旧应用读取。

**停止条件：** 远程 main 或当前数据库已存在不同内容的 V42；schemaVersion 的实际 JSON 路径不是根级 `$.schemaVersion`。

**升级条件：** 不一致来源还包括人工导入或其他同步任务；迁移需要锁表或全表扫描且数据量超出本地可接受窗口。

### T5：跨层集成、浏览器验收与原 `rokey` 安全回写

**需求映射：** R1、R2、R3、R4、R5、R6

**前置任务：** T1、T2、T3、T4

**文件：**

- 修改：`.ai-control/requirements/req-20260817-031-workflow-editor-history-signature/execution-T1.json`
- 修改：`.ai-control/requirements/req-20260817-031-workflow-editor-history-signature/observation-T1.json`
- 修改：`.ai-control/requirements/req-20260817-031-workflow-editor-history-signature/convergence.json`
- 不修改产品文件，除非观测阶段产生已裁决的需求内偏差。

**接口：**

- 消费：T1-T4 的代码、测试、HTTP 契约、迁移和 UI。
- 产出：可重复测试结果、桌面/移动截图、数据库聚合证据、治理与范围门禁结果，以及仅包含 REQ-031 文件的回写清单。

- [ ] **步骤 1：运行开发与范围门禁**

  运行：`node scripts/check-development-entry.mjs --require-plugin`

  运行：`node scripts/check-codex-scope.mjs --requirement REQ-20260817-031`

  预期：两项均退出 0，所有产品改动位于授权路径。

- [ ] **步骤 2：运行后端聚焦与全量测试**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test`

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`

  预期：全部测试通过，0 个失败；如遇默认 JDK 26 的 Mockito self-attach 问题，以显式 JDK 17 结果为准并记录环境扰动。

- [ ] **步骤 3：运行前端、治理和差异检查**

  运行：`npm --prefix web run build`

  运行：`node scripts/check-all-governance.mjs`

  运行：`git diff --check`

  预期：三项均退出 0，无 TypeScript、治理或空白错误。

- [ ] **步骤 4：启动隔离服务并完成浏览器主流程**

  在未占用端口启动隔离后端和前端，使用真实后端数据创建草稿、发布、归档、验证不可启动、处理归档前实例、恢复、再次启动；创建第二版本后查看历史图和事件；在一条已有连线处插入节点并保存发布；配置签名节点验证未确认失败和确认成功。

  预期：业务动作与数据库状态一致，UI 不依赖 Mock 生命周期或历史数据。

- [ ] **步骤 5：完成多视口视觉与溢出检查**

  在桌面、`375x812`、`390x844`、`430x932` 截图并检查 `document.documentElement.scrollWidth <= clientWidth`，确认标题、设计器主体和 footer 无重叠，保存/关闭操作可达。

  预期：四个视口均无页面级横向溢出或不可达操作。

- [ ] **步骤 6：数据库与原工作区并发保护检查**

  查询迁移后元数据不一致数、归档/恢复事件数和签名记录数；同时比较原始 `/Users/zhangwei/project/RDDMP` 中每个 REQ-031 待回写文件与隔离开始时的基准哈希。

  预期：数据库聚合与业务动作一致；原文件未发生并行变化。若有变化，停止回写并生成逐文件冲突清单。

- [ ] **步骤 7：经用户确认后回写并复验**

  仅把 REQ-031 授权文件差异应用到原始本地 `rokey`，不复制隔离工作树中的其他脏文件，不提交、不推送；在原工作区复跑前端构建、工作流聚焦测试、范围检查和 `git diff --check`。

  预期：原 `rokey` 保留用户全部既有改动，同时获得已验证的 REQ-031 修改。

**验收检查：** 十项需求验收全部有自动测试、数据库或浏览器证据；无未裁决负反馈；无运行中会话遗留；原工作区没有被整体覆盖。

**回滚：** 删除隔离工作树不会影响原 `rokey`；回写后按 REQ-031 文件级逆向补丁回退，已执行 V42 和签署/审计记录保留。

**停止条件：** 任一全量门禁失败且无法证明与本次需求无关；浏览器动作与数据库状态不一致；原工作区目标文件发生并行修改；需要删除或改写已执行迁移。

**升级条件：** 需要用户决定是否接纳范围外回归修复；需要访问受保护远程或生产环境；需要覆盖原 `rokey` 的并行修改。

## 集成检查

| 完成任务 | 命令或传感器 | 通过信号 |
| --- | --- | --- |
| T1 | `env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test` | 工作流模块与依赖测试 0 失败 |
| T2-T3 | `npm --prefix web run build` | TypeScript/Vite 构建退出 0 |
| T4 | Flyway validate + 两项 mismatch 聚合查询 | V42 成功且 mismatch 均为 0 |
| T1-T4 | `node scripts/check-all-governance.mjs` | 治理检查退出 0 |
| T1-T4 | `node scripts/check-codex-scope.mjs --requirement REQ-20260817-031` | 无越界写入 |
| T1-T4 | `git diff --check` | 无空白错误 |
| T5 | 浏览器真实业务流与四视口截图 | 生命周期、历史、插入、签名和可达性全部符合设计 |

## 控制模型种子

以下内容均为 `hypotheses-only`，必须由系统建模阶段验证：

- 被控边界候选：平台工作流定义生命周期、版本快照与审计、设计器图模型、内部签名配置、Mock schema 同步。
- 状态变量候选：定义状态、当前版本、版本 schema、节点/边集合、签名要求、签名确认、迁移版本、Mock 同步后 mismatch 数量。
- 接口候选：工作流 Controller HTTP 契约、JdbcTemplate 表访问、Flowable deployment/runtime API、Vue API 客户端、Vue Flow v-model。
- 传感器候选：JDK 17 单元/集成测试、Vite 构建、Flyway validate、MySQL 聚合查询、浏览器 DOM/截图、治理与范围脚本、`git diff --check`。
- 执行器候选：定义状态 SQL 更新、审计写入、图 nodes/edges 原子替换、弹窗 CSS、Mock 白名单与数据、V42 更新语句。
- 扰动候选：原 `rokey` 并行修改、本地 Flyway V40/V41、默认 JDK 26、已有开发服务器端口、测试数据库存量、Element Plus 弹窗 teleport。
- 时延候选：全量 Maven 测试、前端生产构建、Flyway 启动迁移、浏览器服务冷启动。
- 假设：`wf_definition.status` 可保存 `ARCHIVED`；发起入口都要求 `PUBLISHED`；V35 签名服务从实例绑定版本读取配置；V42 在目标环境尚未占用。

## 风险与批准项

- 高风险动作：公共工作流生命周期契约变化、追加 Flyway V42、图连线重写、原脏 `rokey` 的最终文件级回写。
- 风险控制：状态 SQL 使用期望状态条件；迁移只追加；图数组一次性替换；服务端签名判定不信任前端；回写前逐文件比较哈希。
- 计划批准后才允许导入闭环账本并修改产品代码。
- 最终回写原 `rokey` 前再次检查并发修改；发现冲突时不自动覆盖。
