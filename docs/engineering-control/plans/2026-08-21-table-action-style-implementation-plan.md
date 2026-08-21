# 需求与架构管理操作列样式统一实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源
- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-21-table-action-style-design.md`
- 状态：可移交

**目标：** 在不改变任何业务处理逻辑的前提下，统一需求管理和架构管理操作列的宽度、图标、间距及高低频操作视觉层级。

**架构：** 只修改三个 Vue 页面和一个架构局部 CSS 文件，复用 `UiDataTable`、Element Plus 图标/下拉菜单及交付示范中心模式。模板中的操作入口继续直接调用原有处理函数；不新增或修改业务处理函数、状态、权限、API、数据结构和公共样式。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite、现有语义主题变量。

## 全局约束

- 仅允许修改 `RequirementsView.vue`、两个架构页面、`architecture.css` 及当前需求治理产物。
- 所有现有 `v-if`、`canEditDiff`、`canUpdate`、`canDelete`、评审状态条件必须保持原语义。
- 不新增或修改业务处理函数；菜单入口只能调用现有函数。
- 不修改后端、API、数据库、权限编码、审计、公共 UI 组件、全局样式和依赖。
- 保留所有确认弹框、成功/失败消息和请求刷新流程。
- 不覆盖当前工作区中附件、文件预览、Mock 初始化和通知归档相关的既有改动。

---

## 文件职责地图

| 文件 | 状态 | 单一职责 | 事实依据 |
| --- | --- | --- | --- |
| `web/src/views/RequirementsView.vue` | existing | 三类需求列表操作入口与页面局部操作样式 | 当前包含宽度为 380/300/150 的三个固定操作列及原处理函数 |
| `web/src/modules/architecture/LogicalSubsystemPage.vue` | existing | 逻辑子系统桌面和移动操作模板 | 当前已有详情、编辑、更多与删除命令 |
| `web/src/modules/architecture/PhysicalSubsystemPage.vue` | existing | 物理子系统桌面和移动操作模板 | 当前与逻辑页同构但缺少标准直接操作图标 |
| `web/src/modules/architecture/architecture.css` | existing | 架构模块操作容器间距与响应式样式 | 当前只有通用页面 action 样式，没有表格 action 容器规则 |

## 任务依赖图与并行策略

`T1 需求管理操作列` 与 `T2 架构管理操作列` 写入文件互不重叠，可独立实施；`T3 集成验证` 依赖 T1、T2。当前工作区已有无关改动，主 Agent 串行执行并在每个任务后核对范围，降低误纳入风险。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 需求差异操作列 | T1 |
| R2 存量需求与系统清单操作列 | T1 |
| R3 架构管理操作列 | T2 |
| R4 视觉与响应式一致性 | T1、T2、T3 |

### T1：需求管理三个操作列完成紧凑分层

**需求映射：** R1、R2、R4

**前置任务：** 无

**文件：**
- 修改：`web/src/views/RequirementsView.vue`
- 测试：无新增测试文件；使用模板静态核对、前端构建与浏览器验收

**接口：**
- 消费：现有 `openChangeLogs`、`openApprovalLogs`、`submitDifferenceReview`、`openDiffEdit`、`removeDifference`、`openStage`、`showStageLogs`、`openLegacyEdit`、`removeLegacy`、`openSystemEdit`、`removeSystem` 处理函数及原有可见条件。
- 产出：`req-table-actions` 局部操作容器；差异不超过 280px、存量需求约 240px、系统清单约 160px 的固定操作列；Element Plus 标准图标和更多菜单入口。

- [ ] **步骤 1：记录模板和业务逻辑基准**

  运行：`git diff -- web/src/views/RequirementsView.vue && rg -n "function (openChangeLogs|openApprovalLogs|submitDifferenceReview|openDiffEdit|removeDifference|openStage|showStageLogs|openLegacyEdit|removeLegacy|openSystemEdit|removeSystem)" web/src/views/RequirementsView.vue`

  预期：任务开始前该文件没有未提交修改；所有目标处理函数均存在，记录当前行号作为不可修改基准。

  证据：命令退出码、目标函数列表和任务前 scoped diff。

- [ ] **步骤 2：调整差异列表操作入口样式**

  动作：增加已安装的 Element Plus 图标 import；用 `req-table-actions` 包裹按钮；保留修改记录、审批记录和提交评审的原 `v-if`/点击函数；仅在原 `canEditDiff` 条件成立时显示更多菜单，菜单项直接调用原编辑和删除函数；列宽收敛到不超过 280px。

  预期：已评审行不再保留 380px 大面积空白；可见条件和操作结果与基准一致。

  证据：模板 scoped diff，确认没有业务函数体变化。

- [ ] **步骤 3：调整存量需求和系统清单操作入口样式**

  动作：存量需求直接展示阶段推进、修改记录和更多，更多菜单直接调用原阶段记录、编辑、删除函数；系统清单直接展示编辑和更多，更多菜单直接调用原删除函数；增加局部不换行、间距和 Element Plus 相邻按钮 margin 归零规则。

  预期：三个列表的操作列保持紧凑、单行、左对齐，原确认与错误反馈链路不变。

  证据：模板和 style scoped diff。

- [ ] **步骤 4：运行 T1 局部检查**

  运行：`git diff --check -- web/src/views/RequirementsView.vue && git diff -- web/src/views/RequirementsView.vue`

  预期：无空白错误；diff 只包含图标 import、模板操作入口、列宽和局部样式，不包含函数体、请求或数据结构变化。

  证据：退出码与人工 diff 核对结论。

**验收检查：** 差异的修改记录始终直接可见；审批记录、提交评审、编辑/删除保持原条件；存量需求和系统操作仍调用原函数；操作列无换行或超宽空白。

**风险：** 差异行在审批记录和提交评审同时可见时可能接近列宽上限；通过浏览器状态样本确认最终宽度。

**回滚：** 仅恢复 `web/src/views/RequirementsView.vue` 中本任务修改的 import、三个操作列模板和 `req-table-actions` 局部样式。

**停止条件：** 需要修改任一业务处理函数、API、权限/状态条件或公共样式才能完成时停止。

**升级条件：** 现有处理函数无法从 Element Plus 菜单安全调用，或需求页面必须整体改成移动卡片才能避免溢出时升级给用户。

### T2：架构管理操作入口完成同构样式

**需求映射：** R3、R4

**前置任务：** 无

**文件：**
- 修改：`web/src/modules/architecture/LogicalSubsystemPage.vue`
- 修改：`web/src/modules/architecture/PhysicalSubsystemPage.vue`
- 修改：`web/src/modules/architecture/architecture.css`
- 测试：无新增测试文件；使用模板静态核对、前端构建与浏览器验收

**接口：**
- 消费：现有 `showDetail`、`editRecord`、`command` 函数及 `canUpdate`、`canDelete` 条件。
- 产出：逻辑/物理页面一致的 `architecture-table-actions` 桌面容器和移动卡片操作样式；详情、编辑、更多标准图标。

- [ ] **步骤 1：记录两个页面的权限和回调基准**

  运行：`git diff -- web/src/modules/architecture/LogicalSubsystemPage.vue web/src/modules/architecture/PhysicalSubsystemPage.vue web/src/modules/architecture/architecture.css && rg -n "function (showDetail|editRecord|command)" web/src/modules/architecture/LogicalSubsystemPage.vue web/src/modules/architecture/PhysicalSubsystemPage.vue`

  预期：三个文件没有任务前未提交修改；两个页面均使用同名现有处理函数，`canUpdate` 和 `canDelete` 条件存在。

  证据：任务前 scoped diff 与函数列表。

- [ ] **步骤 2：统一桌面表格操作模板**

  动作：增加 `View`、`Edit` 图标 import；用 `architecture-table-actions` 包裹原入口；为详情、编辑、更多补齐图标；保留 `canUpdate`、`canDelete` 与 `command($event, row)`；按内容调整固定列宽。

  预期：两个页面的桌面操作模板同构，权限隐藏不产生空占位，删除仍由原命令函数进入确认流程。

  证据：两个 Vue 文件的模板 scoped diff。

- [ ] **步骤 3：统一移动卡片操作与局部 CSS**

  动作：移动卡片沿用详情、编辑、更多三入口并补齐同样图标；在 `architecture.css` 增加不换行、稳定 gap、按钮 margin 归零及移动端可换行边界，不复制固定列背景或阴影规则。

  预期：移动卡片最多平铺三个入口，不发生文字遮挡或页面级横向溢出；固定列仍由 `UiDataTable` 负责背景与阴影。

  证据：Vue 模板和 CSS scoped diff。

- [ ] **步骤 4：运行 T2 局部检查**

  运行：`git diff --check -- web/src/modules/architecture/LogicalSubsystemPage.vue web/src/modules/architecture/PhysicalSubsystemPage.vue web/src/modules/architecture/architecture.css && git diff -- web/src/modules/architecture/LogicalSubsystemPage.vue web/src/modules/architecture/PhysicalSubsystemPage.vue web/src/modules/architecture/architecture.css`

  预期：无空白错误；diff 只包含图标 import、操作模板、列宽和局部样式，不包含业务函数体、请求或权限计算变化。

  证据：退出码与人工 diff 核对结论。

**验收检查：** 两页桌面和移动入口的文案、图标、间距一致；详情始终可见，编辑/删除保持原权限条件；删除确认流程不变。

**风险：** 图标加入后 144px 不足，需要在紧凑与完整可读之间确定约 200-214px 的宽度。

**回滚：** 恢复两个页面的图标 import、操作模板和 `architecture.css` 的本任务局部规则。

**停止条件：** 需要修改架构 API、权限计算、删除命令函数或 `UiDataTable` 才能完成时停止。

**升级条件：** 公共固定列样式出现缺陷且无法通过模块局部 action 容器解决时升级为单独公共能力任务。

### T3：集成构建与响应式验收

**需求映射：** R4

**前置任务：** T1、T2

**文件：**
- 修改：仅当前任务 `.ai-control` 执行、观测与收敛证据
- 测试：不新增产品测试文件

**接口：**
- 消费：T1、T2 产生的操作模板和局部样式。
- 产出：构建、范围、治理及桌面/移动浏览器验收证据。

- [ ] **步骤 1：检查业务逻辑不变量与任务范围**

  运行：`git diff -- web/src/views/RequirementsView.vue web/src/modules/architecture/LogicalSubsystemPage.vue web/src/modules/architecture/PhysicalSubsystemPage.vue web/src/modules/architecture/architecture.css`

  预期：只出现 import、模板、列宽和局部 CSS；没有函数体、API、类型、权限计算和状态数据变化。

  证据：最终 scoped diff 审查结论。

- [ ] **步骤 2：运行静态、构建和治理检查**

  运行：`git diff --check`；`npm --prefix web run build`；`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260821-047-table-action-style/codex-task-scope.yaml --working-tree`；`node scripts/check-all-governance.mjs`。

  预期：静态检查和构建通过；范围检查将本任务文件与既有无关脏改分开报告；无本任务引入的治理违规。

  证据：各命令退出码和关键结果。

- [ ] **步骤 3：桌面浏览器验收**

  运行：使用本地 `http://127.0.0.1:5173` 和 `http://127.0.0.1:8080`，在 `1280x800` 登录后检查需求差异、存量需求、系统清单、逻辑子系统和物理子系统。

  预期：操作列无超宽空白、按钮不换行、固定列滚动不透底；直接操作及更多菜单按原条件出现并调用原确认/详情路径；浅色和深色主题均可读，控制台无新增错误。

  证据：页面、角色、记录状态、截图、控制台和网络结果。

- [ ] **步骤 4：移动浏览器验收**

  运行：在 `375x812`、`390x844`、`430x932` 检查上述入口，重点核对架构移动卡片和 `document.documentElement.scrollWidth <= window.innerWidth`。

  预期：无页面级横向溢出、文字遮挡或不可达操作；更多菜单弹层完整显示。

  证据：每个视口的 DOM 宽度、截图和操作结果。

**验收检查：** 构建成功；样式在桌面、移动、明暗主题下稳定；所有业务操作资格和处理结果不变。

**风险：** 本地浏览器可能无有效认证或缺少覆盖全部状态的测试数据。

**回滚：** 按 T1、T2 的文件边界恢复前端变更；删除本任务未提交的执行证据。

**停止条件：** 构建失败、出现页面级横向溢出、操作条件变化，或本地服务/认证无法形成运行证据时停止收敛并记录残余风险。

**升级条件：** 需要生成新测试数据、修改权限或调整后端才能覆盖状态时，由用户决定是否扩大任务。

## 集成检查

- `git diff --check`：预期退出码 0。
- `npm --prefix web run build`：预期 Vite production build 成功且无 TypeScript 错误。
- `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260821-047-table-action-style/codex-task-scope.yaml --working-tree`：核对本任务文件均在授权范围；由于用户指定在 `rokey` 开发且工作区已有无关修改，脚本会报告已知治理偏差，不将其伪报为通过。
- `node scripts/check-all-governance.mjs`：预期本任务不引入新违规。
- 浏览器验收：预期五类列表的操作可达、可读、无溢出且业务行为不变。

## 控制模型种子

以下仅为 `hypotheses-only` 候选，待工程控制建模阶段验证：

- 被控边界候选：五类列表的操作模板、固定列宽和模块局部 action CSS。
- 状态变量候选：行状态、前端权限、可见操作数量、视口、主题、固定列滚动位置。
- 接口候选：现有处理函数、Element Plus button/dropdown、`UiDataTable` 固定列。
- 传感器候选：scoped diff、TypeScript/Vite 构建、DOM 宽度、截图、控制台、网络和点击结果。
- 执行器候选：模板入口分组、图标 import、列宽、局部 flex/gap/white-space CSS。
- 扰动候选：状态组合导致操作数量变化、长中文文案、暗色主题、已有脏工作区、无认证或测试数据。
- 时延候选：Vite 热更新、菜单弹层渲染和浏览器数据请求。
- 假设：现有 Element Plus 下拉菜单可直接调用原处理函数；若菜单事件无法保持原行对象或原条件，则该假设被推翻并触发停止条件。

## 风险与用户批准

- 高风险动作：无数据库、后端、权限、依赖、公共组件、服务重启或外部访问动作。
- 主要风险：模板入口迁移时误改可见条件，以及状态组合下列宽不足。
- 已知治理偏差：实际分支为用户指定的 `rokey`，不符合仓库脚本要求的需求分支命名；范围检查还会包含当前工作区既有无关修改。
- 用户批准：用户于 2026-08-21 明确“确认实施”，批准按修订 1 进入开发；仍严格限定为样式与操作入口排布，不改业务逻辑。
