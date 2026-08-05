# 可视化审批工作流实施计划

> 执行要求：按已确认设计实施；计划本身需要用户确认后再进入代码修改，并在实现阶段按任务采样编译、测试和浏览器行为。

## 状态与来源

- 计划修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-05-workflow-visual-approval-design.md`
- 当前状态：已确认，执行中
- 目标：完成流程图设计、审批人配置、审批动作和运行时闭环。

## 全局约束

- 保持单租户、中文界面和现有工作流 API 路径。
- 后端所有工作流查询和写入带 `tenant_id`；发布版本不可原地修改。
- 前端统一使用已有 UiPageHeader、UiToolbar、UiDataTable、UiStatusTag、UiFormDrawer 等组件风格。
- 第一阶段只实现串行节点图；不得在实现中偷偷扩大到条件分支、并行网关或转交退回。
- 每个任务完成后先运行对应局部验证，出现数据契约冲突或迁移失败即停止并回到建模/计划阶段。

## 文件职责地图

- 修改 `ccb-infrastructure/src/main/resources/db/migration/V16__workflow_visual_approval.sql`：扩展任务字段，增加任务动作记录表和必要索引。
- 修改 `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`：接入图定义、审批人解析、节点流转、审批动作、加签和抄送。
- 新建 `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowDefinitionValidator.java`：解析和校验节点/边 JSON。
- 修改 `ccb-workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`：扩展定义、待办和决策请求契约。
- 新建 `ccb-workflow/src/test/java/com/ccb/workflow/service/WorkflowDefinitionValidatorTest.java`：验证合法图、断链图、重复开始节点、审批人缺失和环路。
- 修改 `ccb-workflow/pom.xml`：加入工作流模块测试所需 JUnit 依赖。
- 修改 `ccb-web/package.json`、`ccb-web/package-lock.json`：锁定 `@vue-flow/core` 依赖。
- 新建 `ccb-web/src/components/workflow/WorkflowDesigner.vue`：画布、节点增删、连线和位置保存。
- 新建 `ccb-web/src/components/workflow/WorkflowNode.vue`：开始、审批、抄送、结束节点展示。
- 新建 `ccb-web/src/components/workflow/WorkflowNodeInspector.vue`：节点名称、审批人和抄送人配置。
- 修改 `ccb-web/src/api/workflow.ts`：流程定义、任务动作和节点/边类型。
- 修改 `ccb-web/src/views/WorkflowView.vue`：移除 JSON 文本编辑器，接入设计器、配置面板、审批动作和中文状态。
- 修改 `ccb-web/src/styles.css`：工作流画布、节点和配置面板的主题变量适配。

## 任务依赖与并行策略

依赖关系：`T1 -> T2 -> T3 -> T4 -> T5 -> T6`。T1 数据库契约完成后，T2 校验器可独立开发；T4 前端 API 类型和组件可在 T2/T3 完成后联调。为避免前后端契约漂移，数据库和后端状态流转先于页面最终接入。

### T1：建立工作流持久化契约

**需求映射：** R3、R4、R5

**文件：** 修改 `ccb-infrastructure/src/main/resources/db/migration/V16__workflow_visual_approval.sql`；修改 `ccb-workflow/pom.xml`。

**步骤：**

1. 为 `wf_task` 增加 `node_id`、`task_type`、`task_group_key`、`parent_task_id`、`assignee_type`、`assignee_name` 等字段，允许抄送记录使用不同状态并保持原审批数据兼容。
2. 创建 `wf_task_action`，记录任务、实例、动作、操作者、目标用户、备注和创建时间；为任务待办和动作查询建立租户索引。
3. 确认已有 V5 数据能通过新迁移，新增列使用兼容默认值，不删除旧字段。

**验证：** 使用 Flyway 启动或现有数据库迁移命令检查 V16 执行成功；查询表结构确认新增字段和索引；旧任务查询仍能返回。

**回滚：** 停止新后端并恢复上一 JAR；保留 V16 新增对象，只有在确认无新实例依赖时按数据库备份恢复，不执行无备份删表。

**停止条件：** 迁移无法在现有 MySQL 上执行、旧数据无法读取或字段语义与任务流转冲突。

### T2：实现定义解析和图校验

**需求映射：** R1、R2、R5

**文件：** 新建 `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowDefinitionValidator.java`；新建 `ccb-workflow/src/test/java/com/ccb/workflow/service/WorkflowDefinitionValidatorTest.java`。

**步骤：**

1. 定义 Java 内部模型，解析 `schemaVersion`、`nodes`、`edges`、节点位置和配置。
2. 校验开始/结束节点数量、节点 ID 唯一、边引用存在、所有节点从开始可达、无环路、非结束节点最多一条出边。
3. 校验审批节点的 `USER`/`ROLE`/`STARTER` 配置、审批模式和抄送节点收件人；保留旧 `steps` 数组转换为兼容的线性图。
4. 将所有失败转换成可显示的中文业务错误，校验器不直接访问数据库。

**验证：** `mvn -pl ccb-workflow -am test -DskipTests=false`；测试合法图通过，缺少开始/结束、断链、环路、重复 ID、审批人为空和未知节点类型分别失败，并断言错误信息非空且为中文。

**回滚：** 删除新增校验器和测试，保留 T1 数据迁移；不得修改既有运行逻辑直到校验器测试通过。

**停止条件：** 不能在不访问数据库的情况下稳定判断图结构，或旧 `steps` 定义无法兼容解析。

### T3：升级后端流程运行和动作状态机

**需求映射：** R2、R3、R4、R5

**文件：** 修改 `ccb-workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`；修改 `ccb-workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`。

**步骤：**

1. 保存定义时调用校验器，发布时重新校验最新草稿；启动实例时读取固定发布版本，从开始节点沿唯一边推进。
2. 实现用户、角色和发起人审批人解析，只选当前租户有效用户；多人任务写入同一 `task_group_key`。
3. 实现 `APPROVE` 和 `REJECT` 的原子状态更新；ANY 完成后取消同组待办，ALL 只有全部同意后推进。
4. 实现 `ADD_SIGN`，校验目标用户后创建串行子任务并保留父任务；实现 `CC`，写入 CC 任务/动作记录但不改变原审批任务状态。
5. 自动推进抄送节点和结束节点；待办返回中文节点名、任务类型、审批人和时间；每个动作写入 `wf_task_action`。
6. 将重复提交、非审批人、非 PENDING 任务、跨租户和不存在目标用户转换为明确业务错误。

**验证：** `mvn -pl ccb-boot -am compile -DskipTests`；使用接口或数据库测试验证启动、ANY、ALL、拒绝、加签、抄送、完成和重复决策；确认任务状态变化发生在单事务内。

**回滚：** 恢复 `WorkflowService` 和 `WorkflowController` 的上一版本，保留可向后兼容的新增字段/表；已产生的动作记录只读保留。

**停止条件：** 并发决策可创建两条下一节点任务、角色用户跨租户、旧流程无法启动或动作无法审计。

### T4：接入 Vue Flow 设计器和节点配置组件

**需求映射：** R1、R2、R5

**文件：** 修改 `ccb-web/package.json`、`ccb-web/package-lock.json`、`ccb-web/src/api/workflow.ts`；新建 `ccb-web/src/components/workflow/WorkflowDesigner.vue`、`WorkflowNode.vue`、`WorkflowNodeInspector.vue`。

**步骤：**

1. 安装并锁定 `@vue-flow/core`，为画布配置统一主题、缩放、网格、连接规则和只允许串行连线的交互。
2. 实现节点工具栏、拖拽新增、选中节点、连线、删除节点和节点位置同步；序列化为设计契约。
3. 实现审批配置：审批节点名称、审批人类型、用户/角色选择、发起人选项、ANY/ALL 模式；抄送节点配置用户列表。
4. 复用现有系统用户、角色和组织 API，显示中文姓名、账号、角色名和组织名；统一表单校验和空状态组件。
5. 为设计器提供新增、编辑、加载和保存所需的响应式 props/emits，避免将 API 请求耦合进纯节点组件。

**验证：** `npm run build`；浏览器验证新增四类节点、拖拽、连线、删除、配置、刷新恢复、非法配置提示和深浅主题下画布可读性。

**回滚：** 将 `WorkflowView` 切回旧定义抽屉和列表，移除新增设计器引用；保留后端接口向后兼容。

**停止条件：** Vue Flow 构建失败、节点位置无法稳定序列化、深色主题节点文字不可读或操作后产生无效图契约。

### T5：重构工作流页面和审批待办

**需求映射：** R1、R2、R3、R4

**文件：** 修改 `ccb-web/src/views/WorkflowView.vue`、`ccb-web/src/api/workflow.ts`、`ccb-web/src/styles.css`。

**步骤：**

1. 将定义列表的“新建定义”改为中文流程设计弹窗/页面，布局为画布、节点配置、基本信息和保存/发布操作区。
2. 待办列表显示业务单号、节点名称、任务类型、审批人、进入时间和状态；抄送行隐藏同意/拒绝按钮。
3. 为同意、拒绝、加签、抄送提供中文操作弹窗，目标用户使用用户选择器，动作失败提示具体后端信息。
4. 统一显示状态和日期格式，补充空状态、加载状态、保存中状态和刷新后的选中节点恢复。
5. 保留发起审批入口，并在发布、启动、动作成功后刷新定义/待办。

**验证：** `npm run build`；浏览器完成“设计 -> 保存 -> 发布 -> 发起 -> 待办 -> 同意/拒绝/加签/抄送”闭环，确认面包屑和状态全部中文。

**回滚：** 恢复 `WorkflowView.vue` 和工作流样式，后端仍可通过 API 保持数据可用。

**停止条件：** 页面出现 JSON 编辑器残留、抄送可审批、操作按钮与任务类型不匹配、刷新后流程图丢失或中文时间格式回退。

### T6：集成验证与交付采样

**需求映射：** R1-R5

**文件：** 无新增业务文件；检查前述迁移、后端、前端和文档。

**步骤：**

1. 运行 `mvn -pl ccb-boot -am compile -DskipTests` 和 `npm run build`，保存输出和工作区变更。
2. 确认 Docker MySQL 上 Flyway 已执行 V16；如后端正在运行，先使用新构建启动方式验证，不删除用户现有容器数据。
3. 浏览器逐项采样设计器、节点配置、保存发布、启动、ANY/ALL、拒绝、加签、抄送和权限错误。
4. 检查工作区 diff，确认只包含本功能文件和必要锁文件，不覆盖用户并行修改。

**验收：** R1-R5 每项都有编译、接口、数据库或浏览器证据；任一关键门禁失败则回到对应任务，不宣布完成。

**回滚：** 交付前保留新旧 JAR 和数据库备份点；前端可恢复旧页面，后端可恢复上一版本。

**停止条件：** Flyway、Maven、npm 任一基础门禁失败，或浏览器关键闭环不能完成。

## 集成检查

- `mvn -pl ccb-boot -am compile -DskipTests`：预期 Spring Boot 模块编译成功。
- `npm run build`（工作目录 `ccb-web`）：预期 Vue/TypeScript/Vite 构建成功。
- 数据库迁移：预期 V16 成功且旧 wf_definition/wf_version/wf_task 数据可读。
- HTTP：预期定义、发布、实例、待办、决策接口返回 2xx 或明确中文业务错误。
- 浏览器：预期流程图编辑和四类动作均可操作，深浅主题无白底遮挡或文字不可读。

## 控制模型种子

- 被控边界：MySQL/Flyway、Spring Boot 工作流模块、Vue/Element Plus 工作流页面。
- 状态变量候选：定义版本、图校验结果、实例状态、任务组状态、待办数量、前端画布序列化结果。
- 传感器候选：Flyway 输出、Maven 编译、npm 构建、HTTP 状态/响应、浏览器 DOM 和截图。
- 执行器候选：数据库迁移、后端服务代码、前端组件代码、工作流 API 请求。
- 扰动候选：旧 steps JSON、并发审批、停用用户、深色主题、后端已有运行进程。
- 关键时延：事务提交、前端刷新、Flyway 启动迁移、浏览器 API 重试。

## 风险与用户确认

- 高风险动作：V16 数据库迁移、任务状态机改造、引入前端图编辑器依赖。
- 计划确认：请用户确认本实施计划后再开始代码修改；确认后将导入交接包并按 T1-T6 执行。
