# 配置管理交互式前端原型修订 2 实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将现有配置管理 Mock 原型修订为“审批只代表制品准出、投产结果决定生产版本”的五视图业务原型，并提供 Web/H5 共用的审批链接。

**架构：** 保留 `web/src/modules/release` 的前端会话仓储边界，将旧基线批次模型改为规范化的窗口候选、投产历史和当前生产版本派生模型。模块页面、申请抽屉、冲突弹框、投产基线、生产版本、统计和审批页共用同一状态与纯派生函数；后端、数据库和平台真实服务保持只读。

**技术栈：** Vue 3、TypeScript 5.7、Vite 6、Element Plus 2.9、ECharts 5.6、Vue Router 4、现有 `web/src/components/ui`。

## 状态与来源

- 计划修订：2
- 设计修订：2
- 需求：`REQ-20260814-026`
- 设计文档：`docs/engineering-control/designs/2026-08-14-release-management-prototype-design.md`
- 需求文档：`docs/requirements/REQ-20260814-026-release-management-prototype/requirement.md`
- 状态：等待用户批准
- 历史边界：修订 1 的 `T1-T5`、execution、observation 和 convergence 只作为旧原型证据；修订 2 从 `T6` 开始记录。

## 全局约束

- 只修改当前任务 `codex-task-scope.yaml` 中的 `writable_paths`。
- 不修改 Java、数据库、Flyway、真实菜单、平台工作流、附件、通知、项目管理、研发管理和测试管理。
- 项目、物理子系统、交付单元、需求、审批任务和附件均使用虚构会话 Mock。
- 不新增 npm 包，不修改共享 UI、全局样式或应用壳层。
- 版本号必填且禁止空格，不比较语义版本大小。
- 版本类型固定为常规、紧急、应急；追加是申请特征，不是版本类型。
- 审批完成只产生制品准出；只有投产成功更新生产版本。
- 窗口候选按审批完成时间判断最新，不按版本字符串排序。
- 普通页面不得显示 Mock、状态选择和恢复数据工具；仅 `import.meta.env.DEV && route.query.debug === '1'` 显示开发调试入口。
- 审批统一路由为 `/workflow/review/:taskId?businessType=release&businessId=...`，不创建 H5 专属路由。
- 验收视口为 `375x812`、`390x844`、`430x932`、`1280x800` 及以上，并检查浅色、深色主题。
- SIT/UAT、投产演练、首次投产、重保期、质保期和项目阶段管理明确为本计划非目标。

## 文件职责地图

| 文件 | 状态 | 修订 2 职责 |
|---|---|---|
| `web/src/modules/release/types.ts` | existing-untracked | 版本类型、申请特征、窗口资格、申请、流程、投产明细、生产版本、统计和审批任务类型 |
| `web/src/modules/release/mock.ts` | existing-untracked | 唯一虚构事实集、会话克隆和纯派生/命令辅助函数 |
| `web/src/modules/release/ReleaseManagementPrototype.vue` | existing-untracked | 五视图壳层、会话状态所有权、跨视图业务动作和开发调试入口 |
| `web/src/modules/release/components/ReleaseWindowView.vue` | existing-untracked | 窗口列表、四个时间、状态、资格说明、编辑和常规开关 |
| `web/src/modules/release/components/ReleaseApplicationView.vue` | existing-untracked | 申请列表、筛选、状态操作和审批入口 |
| `web/src/modules/release/components/ReleaseApplicationDrawer.vue` | existing-untracked | 应急选择、窗口选择、类型派生、单元版本、需求、测试报告和校验 |
| `web/src/modules/release/components/ReleaseConflictDialog.vue` | existing-untracked | 历史申请业务快照和取消旧单、编辑旧单、新建决策 |
| `web/src/modules/release/components/ReleaseDetailDrawer.vue` | existing-untracked | 申请详情、流程轮次、准出结果、附件和审计 |
| `web/src/modules/release/components/ReleaseBaselineView.vue` | existing-untracked | 改造为按窗口维护投产结果的“投产基线”视图 |
| `web/src/modules/release/components/ReleaseCurrentProductionView.vue` | candidate-new | 跨窗口当前生产版本、来源窗口和历史轨迹 |
| `web/src/modules/release/components/ReleaseAnalyticsView.vue` | existing-untracked | 新口径指标、图表和多维下钻 |
| `web/src/modules/release/components/ReleaseChart.vue` | existing-untracked | 保持模块 ECharts 生命周期封装 |
| `web/src/modules/release/ReleaseWorkflowReviewPage.vue` | candidate-new | Web/H5 共用审批详情、签署和审批动作 |
| `web/src/modules/release/release-prototype.css` | existing-untracked | 五视图、弹层、审批页和规定视口的模块作用域样式 |
| `web/src/modules/release/prototype-route.ts` | existing-untracked | 保持唯一配置管理原型菜单节点 |
| `web/src/router/index.ts` | existing | 注册 `/release` 和无菜单的统一审批路由 |
| `web/src/stores/auth.ts` | existing | 保持服务端菜单不变并去重追加原型入口 |

## 任务依赖与并行策略

```text
T6 领域事实与派生模型
  -> T7 五视图壳层、窗口和申请交互
      -> T8 投产基线、生产版本和统计
          -> T9 Web/H5 统一审批链接
              -> T10 集成、浏览器观测和收敛验收
```

全部任务串行。`types.ts`、`mock.ts`、模块壳层和 CSS 是共享写入面，无法证明并行安全。

## 需求覆盖

| 需求 | 任务 |
|---|---|
| R1 五视图 | T7、T8 |
| R2 版本类型与追加分离 | T6、T7 |
| R3 窗口资格 | T6、T7 |
| R4 申请字段联动 | T6、T7 |
| R5 最近生产版本 | T6、T7、T8 |
| R6 重复申请决策 | T6、T7 |
| R7 审批只准出 | T6、T8、T9 |
| R8 投产基线 | T6、T8 |
| R9 生产版本 | T6、T8 |
| R10 应急归窗 | T6、T7、T9 |
| R11 响应式审批 | T9、T10 |
| R12 统计下钻 | T6、T8 |
| R13 调试隔离与全状态 | T7、T9、T10 |
| R14 前端边界与响应式 | T6-T10 |

---

### T6：重建类型安全的制品准出与投产事实模型

**需求映射：** R2、R3、R4、R5、R6、R7、R8、R9、R10、R12、R14

**前置任务：** 无

**文件：**
- 修改：`web/src/modules/release/types.ts`
- 修改：`web/src/modules/release/mock.ts`

**接口：**
- 消费：现有 `createReleasePrototypeState()` 及组件使用的会话状态。
- 产出：
  - `ReleaseVersionType = '常规版本' | '紧急版本' | '应急版本'`
  - `ReleaseApplicationCharacteristic = '普通申请' | '追加申请'`
  - `ReleaseProductionResult = '制品准出' | '投产成功' | '投产失败' | '未投产'`
  - `evaluateWindowEligibility(window, businessDate): ReleaseWindowEligibility`
  - `deriveVersionType(input): ReleaseVersionType`
  - `workflowCodeFor(versionType, characteristic): string`
  - `findCurrentProductionVersion(state, subsystemCode, deliveryUnitCode): ReleaseCurrentProductionVersion | undefined`
  - `buildWindowProductionEntries(state, windowId): ReleaseWindowProductionEntry[]`
  - `updateProductionResult(state, command): void`
  - `assignEmergencyWindow(state, approvedAt): ReleaseWindow | undefined`
  - `detectReleaseConflicts(draft, state): ReleaseConflict[]`
  - `buildReleaseMetrics(state, windowId?): ReleaseMetrics`

- [ ] **步骤 1：建立修订 1 构建基准**

  运行：`npm --prefix web run build`

  预期：退出码为 0；若失败，记录错误并停止，不能把既有故障归因于修订 2。

  证据：构建退出码和首个错误（如有）。

- [ ] **步骤 2：替换旧场景和基线类型**

  将 `ReleaseScenario`、`审批通过/已纳入基线` 和 `ReleaseBaselineBatch` 迁移为版本类型、申请特征、`制品准出`、窗口投产明细、投产历史和当前生产版本。`ReleaseApplication.windowId/windowName` 对应急草稿和审批中记录允许为空，审批完成的应急 Mock 必须已有固定归窗。

  预期：类型中不再存在 `追加常规`、`超期常规`、`超期追加`、`审批通过`、`已纳入基线`。

  证据：`rg -n "追加常规|超期常规|超期追加|审批通过|已纳入基线" web/src/modules/release/types.ts web/src/modules/release/mock.ts` 无旧业务值命中。

- [ ] **步骤 3：建立确定性事实数据**

  窗口时间改为 `YYYY-MM-DD HH:mm`；至少提供关闭、当前可申报、截止后投产前和未来未开始窗口。提供常规、紧急、应急、普通、追加、草稿、审批中、退回、撤回、取消和制品准出申请，以及成功、失败、未投产和历史回算样例。

  预期：所有页面事实来自 `createReleasePrototypeState()` 的深克隆；同一交付单元至少有两次成功历史用于回算。

  证据：类型检查和数据差异审阅。

- [ ] **步骤 4：实现纯派生与受控命令辅助函数**

  窗口资格返回 `selectable` 和 `disabledReason`；常规/紧急按申报截止与投产开始判定；候选按 `approvedAt`；生产版本只取最新有效成功历史；结果更正追加前后值、原因、操作人和时间；应急归窗按审批完成时间选择当前投产窗口，否则选择最近未来窗口。

  预期：版本字符串不参与候选排序；应急无可承接窗口时返回 `undefined`；结果从成功改为失败后派生到上一条成功记录。

  证据：调用点审阅、TypeScript 编译和 T8 浏览器交叉验证。

- [ ] **步骤 5：实现完整冲突快照与允许动作**

  `ReleaseConflict` 包含原申请除附件外的业务快照及 `allowedActions`。草稿/退回/撤回允许取消、编辑、新建；审批中只允许新建并提示先撤回；制品准出只允许新建且新单派生追加特征。

  预期：冲突检测不直接替用户取消或阻断新建，附件不进入快照。

  证据：类型检查和 T7 冲突弹框浏览器路径。

- [ ] **步骤 6：局部回归**

  运行：`npm --prefix web run build && git diff --check`

  预期：两条检查退出码均为 0。

  证据：命令退出码。

**验收检查：** 旧场景值消失；Mock 能表达全部新状态；生产版本可由历史回算；无后端文件变化。

**风险：** 类型重构会使全部现有 release 组件暂时编译失败。

**回滚：** 恢复 `types.ts` 和 `mock.ts` 到修订 1 内容；不影响路由和其他模块。

**停止条件：** 新模型必须依赖后端或共享平台类型才能编译；需求规则无法用规范化事实表达。

**升级条件：** 需要修改 `web/src/types/**`、共享 UI 或新增依赖。

---

### T7：完成五视图壳层、窗口资格和版本申请交互

**需求映射：** R1、R2、R3、R4、R5、R6、R10、R13、R14

**前置任务：** T6

**文件：**
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/components/ReleaseWindowView.vue`
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/components/ReleaseApplicationDrawer.vue`
- 修改：`web/src/modules/release/components/ReleaseConflictDialog.vue`
- 修改：`web/src/modules/release/components/ReleaseDetailDrawer.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**
- 消费：T6 的状态、窗口资格、类型派生、当前生产版本和冲突接口。
- 产出：五视图导航；`saveApplication(draft, mode)`；冲突决策事件 `resolve(action, conflict)`；开发调试入口。

- [ ] **步骤 1：移除正常页面调试工具并建立五视图导航**

  删除顶部“当前项目 / Mock 原型 / 状态”工具条。视图键改为 `windows | applications | production-baseline | current-production | analytics`。仅开发环境且 URL 含 `?debug=1` 时显示状态切换和恢复数据工具。

  预期：普通 `/release` 首屏直接展示业务内容；刷新和切换视图不出现重复项目工具条。

  证据：桌面浏览器普通 URL 与 debug URL 对照截图。

- [ ] **步骤 2：改造窗口列表和编辑弹框**

  列表及详情显示四个精确到分钟的时间、状态、常规开关、可选性和禁用原因。新增窗口的申报开始默认当前业务月 1 日 `00:00`；编辑仍要求原因并展示前后值。

  预期：关闭、未到申报时间和已进入投产的窗口均有文字状态；时间不再只显示日期。

  证据：窗口列表、详情和编辑弹框浏览器检查。

- [ ] **步骤 3：重构申请基础字段联动**

  表单第一项使用“是否应急版本”二元控件。非应急显示丰富窗口选择和只读版本类型；应急隐藏窗口、版本类型和需求编号，显示应急说明和测试报告区域。普通/紧急附件选填，应急至少一份 `TEST_REPORT`，原型通过选择虚构报告完成绑定演示。

  预期：应急与非应急切换不会留下隐藏字段参与错误校验；无可承接窗口时草稿可保存、提交被字段级提示阻止。

  证据：常规、紧急、应急三条表单路径。

- [ ] **步骤 4：展示交付单元最近生产版本**

  每个交付单元编辑行固定展示最近成功版本、版本类型和投产时间；无记录时显示“暂无生产版本”。版本输入禁止空格，制品类型保持受控且只读。

  预期：展示值来自 T6 当前生产版本派生，不取最新审批申请。

  证据：AUTH-SVC 有历史版本与新单元无历史版本的对照检查。

- [ ] **步骤 5：改造冲突弹框和动作状态**

  使用分区摘要展示历史申请除附件外的全部业务字段，提供复制申请单号。按 `allowedActions` 显示取消旧申请、修改旧申请、创建新申请；取消原因必填；审批中旧单编辑/取消入口提示先撤回；制品准出新建自动产生追加特征及原因摘要。

  预期：弹框不会统一阻断新建；选择编辑旧单关闭新建抽屉并打开对应旧单；附件名称不显示。

  证据：草稿、审批中、制品准出三种冲突路径。

- [ ] **步骤 6：更新列表和详情语义**

  列表筛选使用版本类型、申请特征和新状态；详情将“基线结果”改为“投产结果”，审批完成显示制品准出，交付单元显示当前候选/投产结果，流程轮次和签名记录保持。

  预期：页面不再出现“审批即纳基”文案。

  证据：`rg -n "审批通过后.*基线|自动纳基|已纳入基线|超期常规|追加常规" web/src/modules/release` 无旧逻辑文案命中。

- [ ] **步骤 7：局部构建与桌面检查**

  运行：`npm --prefix web run build && git diff --check`

  预期：退出码均为 0；`1280x800` 下五视图导航、窗口和申请路径无控制台错误。

  证据：构建结果、浏览器控制台和截图。

**验收检查：** R1-R6、R10、R13 的页面行为可见；表单失败保留输入；正常页面无调试工具。

**风险：** 丰富窗口选项和冲突快照会增加抽屉/弹框高度。

**回滚：** 恢复六个组件、模块壳层和 CSS 到修订 1；T6 类型必须同步回滚。

**停止条件：** 需要修改 AppLayout 或全局 CSS 才能移除工具条或保持页面可用。

**升级条件：** 现有 Element Plus 弹层无法在规定视口容纳核心操作。

---

### T8：实现投产基线、生产版本和新统计口径

**需求映射：** R1、R5、R7、R8、R9、R12、R14

**前置任务：** T7

**文件：**
- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue`
- 新建：`web/src/modules/release/components/ReleaseCurrentProductionView.vue`
- 修改：`web/src/modules/release/components/ReleaseAnalyticsView.vue`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**
- 消费：`buildWindowProductionEntries()`、`updateProductionResult()`、`findCurrentProductionVersion()`、`buildReleaseMetrics()`。
- 产出：投产结果命令事件 `update-result(command)`；生产版本历史详情；窗口/系统/单元/类型/需求/结果下钻。

- [ ] **步骤 1：将旧基线页面改为投产基线**

  顶部先选投产窗口，列表按物理子系统和交付单元展示最新准出候选、来源申请、审批时间、版本类型和当前投产结果。历史候选在详情中可追溯，不显示旧批次纳基按钮。

  预期：同窗口同单元仅当前最新候选出现在主列表，旧候选仍可展开查看。

  证据：存在追加版本的交付单元候选排序检查。

- [ ] **步骤 2：实现投产结果维护与审计**

  投产管理弹框提供制品准出、投产成功、投产失败、未投产单选；结果变化必须填写原因并二次确认。提交后追加前值、后值、操作人和时间，更新列表和详情。

  预期：空原因不能提交；重复确认不追加重复记录；成功改失败后触发生产版本回算。

  证据：成功、失败、未投产及成功撤销四条会话路径。

- [ ] **步骤 3：新增生产版本视图**

  按物理子系统和交付单元展示最近有效投产成功版本、制品类型、版本类型、投产时间和来源窗口；支持关键字、系统和制品类型筛选，详情展示成功历史及被更正记录。

  预期：制品准出、失败和未投产不会成为当前生产版本；无成功历史有明确空状态。

  证据：更正前后当前生产版本对照。

- [ ] **步骤 4：更新统计指标与下钻**

  指标覆盖窗口、物理子系统、交付单元、去重需求、申请、制品准出及三类投产结果；图表维度使用版本类型、申请状态、投产结果和窗口规模；下钻支持窗口、系统、单元、版本类型、需求和结果。

  预期：图表点击后的明细数量与指标一致；旧“基线批次/纳基数量”文案消失。

  证据：逐项交叉核对派生数量和下钻行数。

- [ ] **步骤 5：局部构建与跨视图一致性检查**

  运行：`npm --prefix web run build && git diff --check`

  预期：退出码均为 0；修改投产结果后，投产基线、生产版本、申请详情和统计同时变化。

  证据：构建结果和跨视图浏览器记录。

**验收检查：** 审批只准出；生产版本只由成功历史派生；结果更正可回算；统计与事实一致。

**风险：** 若把当前生产版本存成第二份可写状态，会与历史事实发生漂移。

**回滚：** 删除 `ReleaseCurrentProductionView.vue`，恢复旧基线/统计组件、模块导航和 T6 数据模型。

**停止条件：** 当前生产版本无法完全由投产历史派生，或跨视图更新需要复制状态。

**升级条件：** 需要真实服务端分页或持久化才能展示核心原型行为。

---

### T9：交付 Web/H5 共用的审批详情和动作

**需求映射：** R7、R10、R11、R13、R14

**前置任务：** T8

**文件：**
- 新建：`web/src/modules/release/ReleaseWorkflowReviewPage.vue`
- 修改：`web/src/modules/release/types.ts`
- 修改：`web/src/modules/release/mock.ts`
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/components/ReleaseDetailDrawer.vue`
- 修改：`web/src/modules/release/release-prototype.css`
- 修改：`web/src/router/index.ts`

**接口：**
- 消费：路由参数 `taskId`、查询参数 `businessType` 和 `businessId`；Mock 当前登录人和任务状态。
- 产出：无菜单路由 `workflow/review/:taskId`；动作 `approve | reject | return`；当前身份确认签署。

- [ ] **步骤 1：注册统一审批路由和入口**

  在现有认证布局下注册 `/workflow/review/:taskId`，不追加菜单节点。审批中的申请列表和详情显示“审核”链接，构造 `businessType=release&businessId=<id>`。

  预期：有效链接可直接刷新；非审批中申请不显示审核入口。

  证据：路由解析和菜单列表检查。

- [ ] **步骤 2：实现任务权限和失效状态 Mock**

  任务仅在当前登录人为待办人且状态为待处理时可操作。错误 `taskId` 显示不存在，已处理任务显示只读结果，非当前待办人显示无权限；原型只模拟服务端结论，不以按钮隐藏冒充授权。

  预期：有效、已处理、过期/不存在、无权限四种链接都有明确页面结果。

  证据：四组 URL 浏览器检查。

- [ ] **步骤 3：实现审批详情、签署和动作**

  页面展示申请摘要、物理子系统、交付单元、版本、需求或应急说明、附件、流程进度和历史意见。要求签名的任务必须先点击“使用当前登录身份确认签署”；同意、拒绝、退回要求审批意见并二次确认，提交中防重复。

  预期：未签署不能提交；成功动作将任务变为只读，并把申请/轮次更新为对应 Mock 状态；应急审批完成后执行归窗并生成制品准出候选。

  证据：同意、拒绝、退回和重复点击路径。

- [ ] **步骤 4：实现响应式布局**

  桌面使用完整信息区和侧边/底部动作；`760px` 以下使用业务卡片、纵向流程、局部附件滚动和可达的底部操作栏，拒绝和退回进入更多操作。页面根节点不横向滚动。

  预期：`375x812`、`390x844`、`430x932` 下标题、状态、长编号和操作不重叠；底部操作不遮挡最后一段内容。

  证据：三种手机视口截图、`scrollWidth <= innerWidth` 和实际点击记录。

- [ ] **步骤 5：局部构建与路由检查**

  运行：`npm --prefix web run build && git diff --check`

  预期：退出码均为 0；审批路由不出现在配置管理菜单子项中。

  证据：构建结果、路由和菜单检查。

**验收检查：** 同一路由覆盖 Web/H5；任务动作、签署、过期和无权限状态可复验；审批完成只产生准出。

**风险：** 固定底部操作区可能遮挡移动端详情；Mock 身份状态可能与现有登录用户显示不一致。

**回滚：** 删除审批页，移除审批路由和入口，恢复相关类型、Mock 和样式。

**停止条件：** 审批路由必须绕过现有认证布局才能访问，或移动端需要修改全局壳层。

**升级条件：** 需要平台真实任务 API、签名服务或共享工作流组件变更。

---

### T10：执行治理、构建和响应式浏览器收敛验收

**需求映射：** R1-R14

**前置任务：** T9

**文件：**
- 修改：`.ai-control/requirements/req-20260814-026-release-management-prototype/state.json`
- 修改：`.ai-control/requirements/req-20260814-026-release-management-prototype/model.json`
- 修改：`.ai-control/requirements/req-20260814-026-release-management-prototype/control-plan.json`
- 新建：`.ai-control/requirements/req-20260814-026-release-management-prototype/execution-T6.json`
- 新建：`.ai-control/requirements/req-20260814-026-release-management-prototype/execution-T7.json`
- 新建：`.ai-control/requirements/req-20260814-026-release-management-prototype/execution-T8.json`
- 新建：`.ai-control/requirements/req-20260814-026-release-management-prototype/execution-T9.json`
- 新建：`.ai-control/requirements/req-20260814-026-release-management-prototype/execution-T10.json`
- 新建：`.ai-control/requirements/req-20260814-026-release-management-prototype/observation-revision-2.json`
- 修改：`.ai-control/requirements/req-20260814-026-release-management-prototype/convergence.json`
- 修改：`docs/requirements/REQ-20260814-026-release-management-prototype/codex-task-scope.yaml`

**接口：**
- 消费：完整 `/release` 和 `/workflow/review/:taskId` 用户路径。
- 产出：修订 2 的构建、治理、范围、浏览器、截图、控制台和收敛证据。

- [ ] **步骤 1：运行静态与治理检查**

  运行：`npm --prefix web run build`

  运行：`node scripts/check-all-governance.mjs`

  运行：`git diff --check`

  预期：三条命令退出码均为 0。

  证据：完整退出码和关键汇总。

- [ ] **步骤 2：启动隔离服务并验证可达**

  启动现有 Mock API 和 Vite，使用未占用端口；验证 `/release` 与有效审批链接均返回页面，静态资源无 404。

  预期：登录后配置管理入口唯一；刷新两个路由均不落入 404 或登录循环。

  证据：服务进程、HTTP 状态和浏览器地址。

- [ ] **步骤 3：验收桌面核心路径**

  在 `1280x800` 或更大视口依次检查五视图、窗口状态、常规/紧急/应急表单、最近生产版本、三类冲突、制品准出、投产结果更正、生产版本回算、统计下钻和有效/失效审批。

  预期：各结果符合 R1-R13；控制台无 error；页面无文字遮挡和整体横向溢出。

  证据：操作清单、截图、控制台和 DOM 测量。

- [ ] **步骤 4：验收手机审批和关键业务视图**

  在 `375x812`、`390x844`、`430x932` 检查版本申请列表/表单、冲突弹框、投产结果弹框和审批详情/动作。测量 `document.documentElement.scrollWidth <= window.innerWidth`，检查底部操作可达。

  预期：三种视口均无页面级横向溢出、遮挡或被截断操作；桌面行为未改变。

  证据：每个视口截图、像素/DOM 测量和点击结果。

- [ ] **步骤 5：验收主题和开发状态**

  检查浅色、深色、长编号、长名称、加载、空、失败、无权限、只读、提交中、重复点击和 `?debug=1`；普通 URL 不显示调试入口。

  预期：状态有文字，不只依赖颜色；调试入口隔离正确。

  证据：主题和状态截图、控制台结果。

- [ ] **步骤 6：记录修订 2 控制证据并复跑最终门禁**

  将 T6-T10 实际 diff、命令和结果写入当前前缀账本；独立观测明确修订 1/2 证据边界。再次运行构建、治理和 diff 检查。

  预期：14 条 must 均有可复验信号，P0/P1 反馈为 0，范围无越界后才允许标记 converged。

  证据：execution、observation、convergence 和最终命令结果。

**验收检查：** 自动检查通过；指定桌面/手机路径已真实操作；范围和需求覆盖完整；旧证据未冒充新证据。

**风险：** 本上下文执行与观测不构成完全独立复核；浏览器登录会话或端口可能受本地环境扰动。

**回滚：** 回退 T6-T9 的前端改动并保留失败证据；不删除修订 1 历史记录，不执行数据库操作。

**停止条件：** 构建失败、治理失败、白屏、控制台错误、权限越界、页面级横向溢出或需求规则出现矛盾。

**升级条件：** 必须修改后端、数据库、共享平台、全局 CSS 或引入新依赖才能通过验收。

## 控制模型种子

以下信息均为 `hypotheses-only`，导入后由 `$model-engineering-system` 复核：

- 被控边界候选：`web/src/modules/release/**`、`web/src/router/index.ts`、`web/src/stores/auth.ts` 和当前需求前缀账本。
- 状态变量候选：当前视图、调试状态、窗口、申请、审批轮次、投产历史、窗口候选、生产版本投影、筛选、弹层和审批任务。
- 接口候选：窗口资格、版本类型派生、冲突快照、应急归窗、投产结果命令、生产版本派生、统计派生和审批路由。
- 传感器候选：Vue/TypeScript 构建、治理检查、diff 检查、浏览器 DOM、控制台、截图、`scrollWidth` 和跨视图数量核对。
- 执行器候选：会话仓储命令、表单提交、冲突决策、投产结果更正、审批动作、导航和开发状态切换。
- 扰动候选：修订 1 旧状态残留、长中文与长编号、窗口边界时间、重复点击、ECharts 重绘、抽屉动画、主题、登录会话和端口占用。
- 时延候选：模拟提交、路由加载、图表 resize、弹层过渡和浏览器服务启动。
- 假设候选：当前依赖足以完成响应式审批；会话 Mock 可完整表达正式接口形状；共享应用壳层已支持规定手机视口。

## 高风险动作与回退

- 高风险写入为 `types.ts`/`mock.ts` 的领域模型替换、共享 `router/index.ts` 的审批路由追加和 `auth.ts` 的菜单组合保持。
- 不修改服务端、数据库、真实权限和平台公共代码，因此回退只涉及当前前端原型和任务账本。
- 若应用壳层手机适配不足，停止并升级，不在本任务中顺手修改 `AppLayout.vue` 或全局 CSS。
- 若正式平台契约与当前候选接口不一致，只在 Mock Repository 边界记录差异，不扩大本任务实现真实后端。
