# 数据迁移“迁移检核规则”批量导入交互实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将“批量导入”和“新增迁移检核规则”布置到页面头部，并在当前列表页的响应式对话框内完成模板下载、Excel 选择、确认导入、结果查看与失败重试。

**架构：** 仅重组 `ValidationRulesPage.vue` 的页面结构和本地状态，复用 `UiPageHeader`、Element Plus `el-dialog`/`el-upload` 及模块既有 `dm-upload-dropzone`、`dm-attachment-*` 样式。既有模板下载、导入 API、权限判断、全局项目范围和列表加载函数保持不变，不新增路由或依赖。

**技术栈：** Vue 3 Composition API、TypeScript、Element Plus、项目 UI 组件、模块共享 CSS、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-07-data-migration-validation-rule-import-dialog-design.md`
- 机器设计：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-design.json`
- 设计批准：2026-09-07，用户回复“批准”
- 计划状态：已批准
- 计划批准：2026-09-07，用户回复“确认实施”

## 全局约束

- 只修改 REQ-031 `writable_paths` 覆盖的文件，保护工作区已有未提交修改。
- 产品代码只修改 `web/src/modules/data-migration/views/content/ValidationRulesPage.vue`。
- 不修改 `web/src/components/ui`、`data-migration.css`、路由、API、后端、数据库、权限或审计。
- 继续调用 `downloadRuleTemplate()` 与 `importMigrationCheckRules({ projectId }, file)`。
- `projectId` 继续来自 `useProjectScope()`；对话框不增加项目或 Excel 行级字段选择。
- 复用 `.dm-upload-dropzone` 与 `.dm-attachment-*`；本页只增加导入对话框布局所需的局部语义样式。
- 成功或部分成功后刷新列表并保持对话框打开；请求失败保留文件；关闭或项目切换清理导入状态。
- 浏览器验收覆盖 `1280x800`、`375x812`、`390x844`、`430x932`。
- `REQ-061` 不在本次范围；仓库全量治理若因其失败必须记录为外部扰动。
- 未经用户另行要求，不执行提交、推送、合并或发布。

---

## 文件职责地图

| 路径 | 状态 | 本计划职责 | 事实依据 |
|---|---|---|---|
| `web/src/modules/data-migration/views/content/ValidationRulesPage.vue` | existing / modify | 页面头部、筛选工具栏、导入对话框、本地导入状态与局部响应式样式 | 当前文件包含 `pendingImportFile`、`importResult`、`submitImport()`、模板下载和全部列表交互 |
| `web/src/modules/data-migration/data-migration.css` | existing / read-only | 提供 `dm-upload-dropzone`、`dm-attachment-*` 共享上传视觉 | 当前第 15-46 行已定义上传区和文件状态样式 |
| `web/src/modules/data-migration/views/content/PlansPage.vue` | existing / read-only | `UiPageHeader` 与主按钮位置参照 | 当前第 313-317 行使用页面头部 `actions` 插槽 |
| `web/src/modules/test-management/casework/TestCasePage.vue` | existing / read-only | 当前页对话框导入的结构参照 | 当前包含 `el-dialog`、拖拽上传、结果汇总和 footer 操作 |
| `.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-*.json` | candidate-new / control evidence | 保存基准、模型、计划、执行、观测和收敛证据 | REQ-031 当前前缀允许写入；使用独立主题避免复用已收敛账本 |

## 任务依赖图与并行策略

```text
T1 页面头部与导入对话框实现
  |
  v
T2 自动化、范围与真实浏览器验收
```

T1 与 T2 串行执行。T2 依赖 T1 的稳定产品输出；本计划不存在可证明互不冲突的并行写入组。现有工作区其他数据迁移治理改动视为扰动，执行期间只审查并保留，不回退也不批量格式化。

## 需求覆盖表

| 需求 | T1 | T2 |
|---|---:|---:|
| R1 页面头部双操作 | 实现 | 验收 |
| R2 当前页导入对话框 | 实现 | 验收 |
| R3 全状态与恢复 | 实现 | 验收 |
| R4 工具栏职责 | 实现 | 验收 |
| R5 响应式与上传样式 | 实现 | 验收 |
| R6 契约与范围不变 | 保护 | 验收 |

### T1：页面头部与批量导入对话框可用

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** 无

**已证实输入事实：**

- `ValidationRulesPage.vue` 当前未导入或渲染 `UiPageHeader`，导入操作位于 `UiToolbar`。
- 当前页面已有 `pendingImportFile`、`importResult`、`actionBusy`、`downloadTemplate()`、`submitImport()` 和项目切换清理逻辑。
- 当前 `submitImport()` 在请求成功后清空文件并刷新列表，请求失败仅发消息；这与批准设计的“结果留在对话框、失败保留文件”存在可测偏差。
- `data-migration.css` 已提供全宽 `.dm-upload-dropzone` 与 `.dm-attachment-*` 文件状态样式。
- 项目无前端单元测试脚本，局部行为使用源码契约检查、TypeScript/Vite 构建和真实浏览器作为传感器。

**文件：**

- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue:10`
- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue:88`
- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue:258`
- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue:365`
- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue:388`
- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue:546`
- 测试：无新增测试文件；仓库未配置前端单元测试 runner，使用下述可重复传感器。

**接口：**

- 消费：`hasCreate: ComputedRef<boolean>`、`scopeState`、`scopeProjectId`、`actionBusy`、`load()`、`downloadRuleTemplate()`、`importMigrationCheckRules({ projectId }, file)`、Element Plus `UploadFile`/`UploadInstance`。
- 产出：`openImportDialog(): void`、`resetImportDialog(): void`、`onImportFileChange(file: UploadFile): void`、`onImportFileRemove(): void`、`beforeImportDialogClose(done: () => void): void`，以及 `importDialogOpen`、`importError`、`importUploadRef`、`canSubmitImport` 页面状态。

- [ ] **步骤 1：建立当前结构基准。**

运行：

```bash
rg -n "UiPageHeader|模板下载|选择 Excel|提交导入|importResult" web/src/modules/data-migration/views/content/ValidationRulesPage.vue
```

预期：不存在 `UiPageHeader`；“模板下载”“选择 Excel”“提交导入”与 `importResult` 命中位于 `UiToolbar` 后的列表正文区域。

证据：保存命中行号，作为 R1、R2、R4 的执行前偏差。

- [ ] **步骤 2：增加对话框状态和上传事件契约。**

在 Element Plus 类型导入中增加 `UploadFile`、`UploadInstance`，导入 `UiPageHeader`。删除原生隐藏 input 的 `importInput` 和 `chooseImportFile()`，增加：

```ts
const importDialogOpen = ref(false)
const importError = ref('')
const importUploadRef = ref<UploadInstance>()
const canSubmitImport = computed(() => canImport.value && Boolean(pendingImportFile.value) && !actionBusy.value)

function resetImportDialog() {
  pendingImportFile.value = null
  importResult.value = null
  importError.value = ''
  importUploadRef.value?.clearFiles()
}

function openImportDialog() {
  resetImportDialog()
  importDialogOpen.value = true
}

function onImportFileChange(file: UploadFile) {
  pendingImportFile.value = file.raw ?? null
  importResult.value = null
  importError.value = ''
}

function onImportFileRemove() {
  pendingImportFile.value = null
  importResult.value = null
  importError.value = ''
}

function beforeImportDialogClose(done: () => void) {
  if (actionBusy.value) return
  done()
}
```

预期：选择或移除文件时旧结果和错误同步清理；关闭钩子在提交中不执行 `done()`；`@closed="resetImportDialog"` 负责最终重置。

证据：保存状态函数 diff，并用 TypeScript 构建验证 Element Plus 类型签名。

- [ ] **步骤 3：调整导入提交状态流。**

修改 `submitImport()`：提交前清空 `importError` 和旧结果；成功或部分成功后保留 `pendingImportFile` 与 `importResult`、调用 `await load()` 且不关闭对话框；请求失败将 `apiErrorMessage(cause, '导入失败')` 写入 `importError` 并保留文件；`finally` 恢复 `actionBusy`。

预期：全部成功和部分失败结果可留在对话框中查看；请求级错误与行级结果互不混淆；重复提交受 `canSubmitImport` 和函数入口双重保护。

证据：保存状态流 diff；源码检查确认成功分支不再执行 `pendingImportFile.value = null`。

- [ ] **步骤 4：增加页面头部并收敛工具栏。**

在 `main.dm-page-root` 的第一个业务子节点增加：

```vue
<UiPageHeader title="迁移检核规则" description="列表、新增与导入均固定属于顶部项目切换器选择的当前项目。">
  <template #actions>
    <el-button v-if="hasCreate && scopeState === 'ready'" :disabled="loading || actionBusy" @click="openImportDialog">
      <el-icon><UploadFilled /></el-icon>批量导入
    </el-button>
    <el-button v-if="hasCreate && scopeState === 'ready'" type="primary" :disabled="loading || actionBusy" @click="openCreate">
      <el-icon><Plus /></el-icon>新增迁移检核规则
    </el-button>
  </template>
</UiPageHeader>
```

从 `UiToolbar` 删除模板下载、隐藏 input、选择 Excel、提交导入和新增按钮，只保留筛选、查询、重置、导出和批量删除；从列表正文删除待提交文件警告和导入结果块。

预期：页面级双操作顺序和主次关系与批准设计一致；工具栏回归列表职责；无新增权限不渲染两个头部入口。

证据：源码契约检查和浏览器头部截图。

- [ ] **步骤 5：实现当前页面导入对话框。**

在 `UiFormDrawer` 后增加 `el-dialog`，约束如下：

```vue
<el-dialog
  v-model="importDialogOpen"
  title="批量导入迁移检核规则"
  width="min(720px, calc(100vw - 24px))"
  align-center
  destroy-on-close
  :close-on-click-modal="!actionBusy"
  :close-on-press-escape="!actionBusy"
  :show-close="!actionBusy"
  :before-close="beforeImportDialogClose"
  @closed="resetImportDialog"
>
  <!-- 导入说明与下载模板 -->
  <!-- dm-upload-dropzone 单文件 Excel 拖拽区 -->
  <!-- dm-attachment-* 已选择文件状态 -->
  <!-- importError 请求错误 -->
  <!-- importResult 汇总与最多 20 条错误 -->
  <template #footer>
    <el-button :disabled="actionBusy" @click="importDialogOpen = false">取消</el-button>
    <el-button type="primary" :loading="actionBusy" :disabled="!canSubmitImport" @click="submitImport">确认导入</el-button>
  </template>
</el-dialog>
```

`el-upload` 使用 `ref="importUploadRef"`、`class="dm-upload-dropzone"`、`drag`、`:auto-upload="false"`、`:limit="1"`、`accept=".xlsx,.xls"`、`:show-file-list="false"`、`:on-change="onImportFileChange"`、`:on-remove="onImportFileRemove"`。结果类型按 `importResult.failed > 0` 显示 warning，否则 success；错误只展示 `errors.slice(0, 20)`，超出时显示剩余数量。

预期：模板、选文件、提交和结果均在同一对话框；未选文件、项目不可用或提交中时确认按钮禁用；提交中不可通过遮罩、Esc、关闭图标或 footer 取消关闭。

证据：Vue 模板 diff、构建输出和浏览器交互记录。

- [ ] **步骤 6：收敛项目切换与局部响应式样式。**

在 `watch(scopeProjectId, ...)` 中先将 `importDialogOpen` 置为 `false` 并调用 `resetImportDialog()`。删除 `.dm-hidden-file`，保留并扩展导入对话框局部样式：正文设置 `min-width: 0`、明确 `max-height` 和 `overflow-y: auto`；工具行允许换行；错误列表使用 `overflow-wrap: anywhere`；移动端不固定按钮或文件名宽度。

预期：项目切换不会把旧项目文件带入新项目；长文件名和错误信息不导致横向溢出；底部操作位于 Element Plus footer 并保持可达。

证据：样式 diff和四视口 `scrollWidth <= clientWidth` 断言。

- [ ] **步骤 7：运行局部契约与构建检查。**

运行：

```bash
node --input-type=module -e "import fs from 'node:fs';const s=fs.readFileSync('web/src/modules/data-migration/views/content/ValidationRulesPage.vue','utf8');const toolbar=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??'';for(const token of ['UiPageHeader','批量导入','新增迁移检核规则','dm-upload-dropzone','beforeImportDialogClose','importError'])if(!s.includes(token))throw new Error('missing '+token);for(const token of ['模板下载','选择 Excel','提交导入'])if(toolbar.includes(token))throw new Error('toolbar contains '+token);"
npm --prefix web run build
git diff --check -- web/src/modules/data-migration/views/content/ValidationRulesPage.vue
```

预期：三个命令退出码均为 0；`vue-tsc` 和 Vite 无错误；工具栏不含导入流程，页面包含头部、对话框、上传区和错误状态契约。

证据：记录退出码、构建摘要与 `git diff --check` 输出。

**验收检查：**

- 头部按顺序显示次要“批量导入”和主“新增迁移检核规则”。
- 导入相关步骤和结果全部位于当前页对话框，路由不变化。
- 初始、已选文件、提交中、成功、部分失败、请求失败、关闭重置均有明确状态。
- 工具栏只保留列表筛选和批量管理操作。
- 复用模块上传样式，长文件名与长错误信息不撑破容器。
- API、`projectId`、权限和 Excel 行级字段契约不变。

**风险：** `actionBusy` 同时约束导出、删除和导入，导入对话框打开期间其他页面操作仍可见；通过对话框遮罩和所有动作禁用条件验证，避免互相解锁或重复请求。

**回滚：** 仅恢复 `ValidationRulesPage.vue` 中 T1 引入的头部、导入对话框、状态函数与局部样式；不得覆盖该文件在 T1 开始前已有的治理修改。保留设计、计划和控制证据。

**停止条件：** 实现需要修改导入 API、后端、公共 UI、路由或 `data-migration.css`；当前文件出现无法与已有未提交改动安全合并的同一区域新修改；Element Plus 现有版本不支持计划中的关闭或上传契约。

**升级条件：** 用户要求增加预校验、字段映射、导入历史或独立页面；发现 `hasCreate` 不能合法代表导入权限；项目切换行为需要改变全局 store。

### T2：自动化、范围与真实浏览器验收完成

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** T1

**已证实输入事实：**

- 项目要求前端构建、REQ-031 定向范围检查和桌面/三个手机视口真实浏览器验收。
- 本地 Vite 服务可能运行在 `127.0.0.1:5173`，后端 8080 当前可能未运行；运行时可用性必须在执行时重新探测。
- 全仓治理当前可能因范围外 REQ-061 的非 JSON-compatible YAML 失败，不能归因于本任务。

**文件：**

- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-state.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-baseline.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-model.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-control-plan.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-execution-T1.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-observation-T1.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/validation-rule-import-dialog-convergence-report.json`
- 测试：不新增产品测试文件；使用源码契约、构建、范围检查和真实浏览器传感器。

**接口：**

- 消费：T1 更新后的 `ValidationRulesPage.vue`、本地开发服务器、现有认证与项目上下文。
- 产出：源码契约、构建、范围、四视口布局与交互证据，以及范围外扰动分类。

- [ ] **步骤 1：复验静态契约、构建和差异质量。**

运行：

```bash
node --input-type=module -e "import fs from 'node:fs';const s=fs.readFileSync('web/src/modules/data-migration/views/content/ValidationRulesPage.vue','utf8');const toolbar=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??'';for(const token of ['UiPageHeader','批量导入','新增迁移检核规则','dm-upload-dropzone','beforeImportDialogClose','importError'])if(!s.includes(token))throw new Error('missing '+token);for(const token of ['模板下载','选择 Excel','提交导入'])if(toolbar.includes(token))throw new Error('toolbar contains '+token);"
npm --prefix web run build
git diff --check
```

预期：退出码均为 0；无 TypeScript、Vue、Vite 或空白错误。

证据：保存完整命令、退出码和关键摘要。

- [ ] **步骤 2：执行 REQ-031 定向范围检查。**

运行：

```bash
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree
```

预期：退出码 0，变更都位于 REQ-031 `writable_paths`；若失败，原子记录具体越界文件并停止收敛。

证据：保存范围检查退出码和输出。

- [ ] **步骤 3：探测并启动可用的本地验收环境。**

运行：

```bash
curl -fsS http://127.0.0.1:5173/ >/dev/null
curl -fsS http://127.0.0.1:8080/actuator/health
```

预期：确认前端与后端的真实状态；已运行则复用，未运行则按项目开发脚本在空闲端口启动，并记录 URL、进程和 Mock/真实后端模式。不得把未启动后端描述为成功场景通过。

证据：健康检查输出和实际使用 URL。

- [ ] **步骤 4：执行四视口浏览器结构与响应式验收。**

在 `1280x800`、`375x812`、`390x844`、`430x932` 依次验证：

- 页面头部按钮顺序、主次样式和权限可见性正确。
- 点击“批量导入”不改变路由，模板、拖拽区和 footer 同时可见。
- 初始确认按钮禁用；选择 `.xlsx` 后启用；移除或重新选择时结果清理。
- 长文件名和模拟超过 20 条的错误列表不产生横向溢出，正文可滚动，footer 可达。
- 页面和对话框分别满足 `scrollWidth <= clientWidth`。
- 提交中关闭入口和重复提交不可用。

预期：四个视口全部满足；截图或浏览器断言能定位视口、页面与状态。

证据：每个视口的截图、路由值、按钮状态、页面/对话框宽度断言。

- [ ] **步骤 5：按环境能力验收导入结果与失败恢复。**

后端和测试数据可用时，使用非敏感测试 Excel 分别验证全部成功、部分失败和请求失败：成功/部分失败刷新列表但对话框保持打开；结果汇总准确且错误最多显示 20 条；请求失败保留文件并允许重试。后端不可用时，只能记录这些动态分支为未执行，不能用静态检查替代并宣称通过。

预期：已执行分支与设计一致；未执行分支明确列入残余风险和上线验证项。

证据：网络请求摘要、对话框状态截图和列表刷新结果，或环境阻断记录。

- [ ] **步骤 6：运行全仓治理并分类外部扰动。**

运行：

```bash
node scripts/check-all-governance.mjs
```

预期：若仅因 `docs/requirements/REQ-20260904-061-release-plan-timeline-instructions/codex-task-scope.yaml` 非 JSON-compatible YAML 失败，将其记录为范围外扰动；出现任何 REQ-031 或本次文件相关失败则视为本任务负反馈并进入纠偏。

证据：保存退出码、失败文件和归因结论，不宣称全仓治理通过。

- [ ] **步骤 7：完成独立观测和收敛审计。**

将 R1-R6 逐项映射到源码、构建、范围和浏览器证据；对未执行的动态分支登记残余风险与上线验证；只有本任务负反馈清零且必需验收有充分证据时，才允许控制状态进入 `converged`。

预期：每个 must 需求至少一条独立证据；范围外 REQ-061 不被误关闭；无本任务开放 P0/P1/P2 偏差。

证据：独立 observation 与 convergence report。

**验收检查：**

- 源码契约、前端构建和 `git diff --check` 通过。
- REQ-031 定向范围检查通过。
- 四个规定视口无横向溢出且 footer 可达。
- 动态导入状态已真实验证，或未验证部分被明确报告为残余风险。
- REQ-061 只作为范围外扰动，不修改、不忽略输出、不误报全仓通过。

**风险：** 后端、认证或测试数据不可用会限制真实成功/部分失败分支验收；这不阻止静态和布局验证，但会阻止把动态流程描述为完整通过。

**回滚：** 若发现产品回归，回到 T1 的单文件回滚点并保留所有失败观测；控制记录不删除。浏览器启动的临时进程按启动记录终止，不影响用户既有服务。

**停止条件：** REQ-031 定向范围失败；构建失败且原因来自本次修改；四视口出现无法在单文件范围内修复的公共组件问题；动态验收要求生产数据或敏感凭据。

**升级条件：** 必须修改公共 UI、全局样式、API 或后端才能满足验收；出现本任务导致的 P0/P1 回归；需要用户提供非现有授权或外部测试环境。

## 集成检查

在 T1 完成后运行源码契约与前端构建；T2 再独立复跑，并追加范围检查和浏览器验收。最终交付必须包含：

```bash
npm --prefix web run build
git diff --check
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree
node scripts/check-all-governance.mjs
```

前三项预期退出码 0。最后一项若受已知 REQ-061 阻断，必须报告非零退出码、准确文件和范围外归因。

## 控制模型种子

以下内容均为 `hypotheses-only`，必须由 `$model-engineering-system` 验证后才能作为控制结论：

- 被控边界候选：`ValidationRulesPage.vue` 的页面头部、工具栏、导入本地状态、导入对话框和局部样式。
- 状态变量候选：导入对话框开闭、待提交文件、请求错误、业务导入结果、动作忙碌、当前项目范围、列表数据。
- 接口候选：`downloadRuleTemplate()`、`importMigrationCheckRules({ projectId }, file)`、`load()`、`el-upload` change/remove、`el-dialog` before-close/closed、`UiPageHeader` actions。
- 传感器候选：源码契约脚本、`vue-tsc`/Vite 构建、`git diff --check`、REQ-031 范围检查、浏览器 DOM/路由/溢出断言、网络响应。
- 执行器候选：Vue 状态函数、头部与工具栏模板重组、对话框属性、上传事件和局部响应式 CSS。
- 扰动候选：工作区已有治理改动、本地后端不可用、认证或测试数据缺失、REQ-061 全仓治理失败。
- 时延候选：导入网络请求、列表刷新、Vite 首次构建和浏览器启动。
- 假设：`hasCreate` 同时合法控制单条新增和批量导入；依据是当前页面两个入口均使用 `hasCreate`；若后端或菜单存在独立导入权限则推翻。
- 假设：`.dm-upload-dropzone` 与 `.dm-attachment-*` 足以承载该对话框；依据是当前共享 CSS 和其他数据迁移页面；若四视口出现仅能修改共享样式才能修复的问题则推翻。

## 风险与用户批准

高风险动作：无数据库、后端、公共能力、依赖、路由或外部系统变更。主要风险是同一页面的既有未提交改动、`actionBusy` 状态耦合，以及后端不可用造成动态验收盲区。

本计划和交接包已由用户于 2026-09-07 回复“确认实施”批准。按 `control-engineering` 完成 baseline、modeling、planning、executing、observing、必要纠偏和 verifying，不得直接编辑产品代码跳过门禁。
