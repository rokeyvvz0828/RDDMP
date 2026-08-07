# 工作流设计器企业级体验实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 完成企业级工作流设计器的流程生命周期、BPMN 语义节点、连线条件编辑、上下文菜单、只读流程查看和 Dagre 自动布局。

**架构：** 保留现有 Vue Flow 和工作流服务边界；后端新增租户隔离的详情与草稿软删除接口，前端通过受控 props/emits 维护图模型。已发布流程使用固定版本只读渲染，草稿编辑和保存仍走现有定义接口。

**技术栈：** Spring Boot 3、JDK 17、JdbcTemplate/MySQL、Flowable、Vue 3、TypeScript、Element Plus、Vue Flow、Dagre。

## 全局约束

- 单租户，后端查询和写入必须带 `tenant_id`。
- 已发布版本不可原地修改；只有草稿允许软删除。
- 所有用户界面和业务错误使用中文。
- 边字段 `label`、`condition`、`default` 必须端到端保持。
- 不直接操作 Flowable `ACT_*` 表，不实现服务任务/消息事件运行语义。
- 保护工作区已有修改；当前基线已确认干净。

---

### T1：流程定义详情与草稿软删除

**需求映射：** R1

**前置任务：** 无

**文件：**
- 修改：`ccb-workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`
- 修改：`ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 测试：`ccb-workflow/src/test/java/com/ccb/workflow/service/WorkflowServiceTest.java`（若现有测试结构不支持，则在现有工作流服务测试位置增加同等测试）

**接口：**
- 消费：`AuthUser.tenantId()`、`AuthUser.id()`、现有 `wf_definition`/`wf_version` 表。
- 产出：`GET /api/workflows/definitions/{id}` 返回定义元数据和版本 JSON；`DELETE /api/workflows/definitions/{id}` 仅将草稿定义标记为 `deleted = 1`。

- [ ] **步骤 1：建立基准检查**

运行：`rg -n "definitions|publish|delete|definition_json|deleted" ccb-workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`

预期：确认当前没有详情和删除实现，且列表查询已有租户及软删除条件。

- [ ] **步骤 2：实施最小变更**

增加详情查询，按定义当前版本读取 `definition_json`；增加删除服务，使用 `UPDATE ... WHERE id = ? AND tenant_id = ? AND status = 'DRAFT' AND deleted = 0`，更新失败返回中文业务错误。控制器删除接口使用现有 `workflow:access:delete` 权限约定。

- [ ] **步骤 3：局部验证**

运行：`mvn -pl ccb-workflow -am test -DskipTests=false`

预期：服务编译通过，详情返回版本 JSON，已发布或跨租户删除被拒绝；若测试环境无法启动数据库，记录该限制并继续使用编译和静态 SQL 检查。

**回滚：** 恢复 `WorkflowController` 和 `WorkflowService` 本任务新增方法，不回滚已有工作流表数据。

**停止条件：** 发现定义表状态字段或权限编码与计划不一致，停止并回到建模阶段。

**升级条件：** 详情必须读取不存在的发布版本或需要改变已发布数据结构时，升级用户确认。

### T2：流程边字段完整保持与图校验回归

**需求映射：** R3

**前置任务：** T1

**文件：**
- 修改：`ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowModelAdapter.java`
- 修改：`ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowModelValidator.java`
- 测试：`ccb-workflow/src/test/java/com/ccb/workflow/service/WorkflowModelValidatorTest.java`

**接口：**
- 消费：前端 `WorkflowEdgeModel` JSON 的 `label`、`condition`、`default` 字段。
- 产出：适配器生成完整 `WorkflowEdgeModel`；条件网关校验条件分支和默认分支，错误信息为中文。

- [ ] **步骤 1：建立基准检查**

运行：`rg -n "parseEdges|WorkflowEdgeModel|condition|defaultFlow|defaultEdgeId" ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowModelAdapter.java ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowModelValidator.java`

预期：确认解析器边字段已部分支持，并定位任何序列化时丢失字段的位置。

- [ ] **步骤 2：实施最小变更**

保证 JSON 读取和模型重建不丢失 `label`、`condition`、`default`；对默认分支与 `defaultEdgeId` 做一致性校验，保留现有旧版 `steps` 兼容路径。

- [ ] **步骤 3：局部验证**

运行：`mvn -pl ccb-workflow -am -Dtest=WorkflowModelValidatorTest test`

预期：带条件和默认分支的网格图通过；缺少条件分支、缺少默认分支、重复默认分支被拒绝；旧串行 `steps` 定义仍可解析。

**回滚：** 仅回滚适配器和校验器本任务改动，保留 T1 接口。

**停止条件：** 发现当前校验器不支持既有已发布定义格式且没有兼容转换边界，停止并重新建模。

**升级条件：** 需要新增表达式执行器或改变发布数据契约时升级用户。

### T3：BPMN 语义节点、箭头连线与上下文操作

**需求映射：** R2, R3

**前置任务：** T2

**文件：**
- 修改：`ccb-web/src/components/workflow/WorkflowNode.vue`
- 修改：`ccb-web/src/components/workflow/WorkflowDesigner.vue`
- 修改：`ccb-web/src/styles.css`

**接口：**
- 消费：`WorkflowGraph`、`WorkflowNodeModel`、`WorkflowEdgeModel`。
- 产出：节点/连线操作通过 `update:modelValue` 输出；`select` 输出当前节点或连线选择；右键菜单输出编辑、复制、删除和添加节点动作。

- [ ] **步骤 1：建立基准检查**

运行：`rg -n "nodeTypes|flowEdges|onConnect|resetLayout|workflow-node|vue-flow__edge" ccb-web/src/components/workflow/WorkflowDesigner.vue ccb-web/src/components/workflow/WorkflowNode.vue ccb-web/src/styles.css`

预期：确认当前节点统一矩形、边无箭头、没有上下文菜单和边选择模型。

- [ ] **步骤 2：实施最小变更**

按节点类型实现圆形、圆角任务、菱形网关和抄送样式；边使用 marker-end 箭头并显示 label；增加节点/边右键菜单、复制和删除保护；保持 `condition` 和 `default` 字段；只读模式关闭拖拽、连接和菜单写操作。

- [ ] **步骤 3：局部验证**

运行：`npm run build`

预期：TypeScript 和 Vite 构建通过；检查节点类型、右键菜单和箭头相关代码无类型错误。

**回滚：** 恢复三个前端文件，保留后端接口和边模型兼容字段。

**停止条件：** Vue Flow 当前版本不支持稳定 marker/上下文事件或构建失败，停止并保留现有编辑器能力。

**升级条件：** 需要引入新的图形库或修改公共 UI 组件契约时升级用户。

### T4：检查器与 Dagre 分层布局

**需求映射：** R3, R4

**前置任务：** T3

**文件：**
- 修改：`ccb-web/package.json`
- 修改：`ccb-web/package-lock.json`
- 修改：`ccb-web/src/components/workflow/WorkflowDesigner.vue`
- 修改：`ccb-web/src/components/workflow/WorkflowNodeInspector.vue`
- 修改：`ccb-web/src/styles.css`

**接口：**
- 消费：选中的节点/边及其当前图模型。
- 产出：检查器更新节点和边；`layoutDirection` 控制 `LR`/`TB`；布局只更新草稿位置，不改变连接语义。

- [ ] **步骤 1：建立基准检查**

运行：`npm view @dagrejs/dagre version --registry=https://registry.npmmirror.com`

预期：获得可安装版本；若网络不可用，检查本地 npm 缓存并停止依赖变更。

- [ ] **步骤 2：实施最小变更**

加入 `@dagrejs/dagre`；增加边检查器字段和默认分支开关；使用 Dagre 计算节点中心位置并转换回 Vue Flow 坐标，设置节点间距、层间距和方向；只读图仅使用计算结果，不触发模型更新。

- [ ] **步骤 3：局部验证**

运行：`npm install --package-lock-only`、`npm run build`

预期：锁文件与 package.json 一致，构建通过；带条件网关的图布局后节点矩形不相交。

**回滚：** 移除 Dagre 依赖并恢复检查器/设计器布局实现，保留边字段。

**停止条件：** 依赖解析或布局结果无法在前端构建中稳定复现，停止新增布局依赖并回到方案评估。

**升级条件：** 自动布局需要改变后端图模型或引入服务端布局时升级用户。

### T5：流程列表接入查看、删除和只读编辑器

**需求映射：** R1, R2, R3, R4

**前置任务：** T1, T4

**文件：**
- 修改：`ccb-web/src/api/workflow.ts`
- 修改：`ccb-web/src/views/WorkflowView.vue`
- 修改：`ccb-web/src/styles.css`

**接口：**
- 消费：详情/删除 API、`WorkflowDesigner` 只读模式、中文日期工具。
- 产出：定义列表提供查看、编辑草稿、删除草稿、发布和发起；已发布流程查看固定版本图形。

- [ ] **步骤 1：建立基准检查**

运行：`rg -n "listWorkflowDefinitions|publishWorkflowDefinition|designerOpen|新建流程|操作" ccb-web/src/api/workflow.ts ccb-web/src/views/WorkflowView.vue`

预期：确认当前只有列表、发布和发起入口，缺少详情和删除调用。

- [ ] **步骤 2：实施最小变更**

增加详情/删除 API 类型和方法；页面打开详情加载 `definitionJson`；草稿进入编辑态，已发布进入只读态；删除前中文确认并刷新列表；保存后仍遵守现有发布流程。

- [ ] **步骤 3：局部验证**

运行：`npm run build`

预期：定义列表、编辑、只读查看和删除相关模板均构建通过；已发布流程无保存/删除写操作。

**回滚：** 隐藏详情和删除按钮并恢复旧设计器入口，不删除后端新接口。

**停止条件：** 详情返回无法区分草稿与发布版本，或页面刷新会覆盖未保存模型，停止并回到接口契约调整。

**升级条件：** 需要改变现有路由、权限或工作流列表公共组件时升级用户。

### T6：集成验证与工程证据

**需求映射：** R1-R4

**前置任务：** T5

**文件：**
- 验证：`ccb-workflow/src/test/java/com/ccb/workflow/service/WorkflowModelValidatorTest.java`
- 验证：`ccb-web/package.json`
- 验证：本次所有变更文件

**接口：**
- 消费：T1-T5 的实现和测试输出。
- 产出：构建、单元测试、静态 diff 和可复查工程账本证据。

- [ ] **步骤 1：运行后端验证**

运行：`mvn -pl ccb-boot -am test -DskipTests=false`

预期：后端编译和工作流校验测试通过；失败时区分依赖/数据库环境问题与本次代码错误。

- [ ] **步骤 2：运行前端验证**

运行：`npm run build`（工作目录 `ccb-web`）

预期：`vue-tsc` 和 Vite 构建均通过。

- [ ] **步骤 3：检查 diff 和契约**

运行：`git diff --check`、`git status --short`

预期：无空白错误，变更只落在本计划文件和必要代码/锁文件；边条件字段、租户条件和发布只读不变量可在 diff 中追踪。

**回滚：** 按 T1-T5 任务边界逐项回退，不执行破坏性数据库命令。

**停止条件：** 任一构建门禁失败且连续两次修复未降低错误，回到对应任务建模。

**升级条件：** 需要数据库结构迁移、Flowable 运行语义变更或用户界面范围扩张时，请求用户决策。

## 依赖与并行策略

任务顺序为 `T1 -> T2 -> T3 -> T4 -> T5 -> T6`。T1 与 T2 都涉及后端工作流契约，T3/T4 共享设计器文件，无法安全并行；T5 依赖后端详情接口与前端设计器完成。

## 控制种子（待建模验证）

- 被控边界：工作流定义 API、工作流图 JSON、Vue Flow 设计器和定义列表。
- 状态候选：定义状态、软删除标记、版本号、节点/边模型、只读标记、布局方向、构建/测试结果。
- 输入候选：创建/保存/发布/删除/查看操作、节点和连线编辑、布局方向切换。
- 输出候选：API 响应、页面画布、错误提示、构建日志、单元测试结果。
- 传感器候选：Maven 测试、npm 构建、git diff、浏览器 DOM/截图。
- 扰动候选：旧版 `steps` JSON、已发布固定版本、深色主题、npm 网络和 Docker 数据库状态。
- 以上均为 `hypotheses-only`，执行前由 `$model-engineering-system` 复核，不作为已观测事实。
