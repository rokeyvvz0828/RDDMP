# 数据迁移三个页面批量导入统一实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将迁移参数、目标表结构和中间表结构统一为页面头部双操作及当前页完整批量导入对话框。

**架构：** 产品改动限定在 `ParametersPage.vue` 和共用的 `TargetTablesPage.vue`。两个页面各自维护与既有 API 响应匹配的本地导入状态，同时复用 `UiPageHeader`、Element Plus 和数据迁移共享上传类；不抽取公共组件、不改变 API 或路由。

**技术栈：** Vue 3 `<script setup lang="ts">`、Element Plus 2.9.5、项目 `UiPageHeader`/`UiToolbar`、Vite、Playwright/Chromium。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-07-data-migration-three-page-import-dialog-design.md`
- 机器设计：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-design.json`
- 状态：可移交
- 用户设计确认：2026-09-07，用户回复“确认”。
- 用户计划批准：2026-09-07，用户回复“批准”。

## 全局约束

- 产品代码只修改 `web/src/modules/data-migration/views/content/ParametersPage.vue` 和 `web/src/modules/data-migration/views/base/TargetTablesPage.vue`。
- 两个文件在任务开始前已有治理 diff：参数页删除列表和详情中的项目名称；表结构页移除动态项目名称并为上传区增加 `dm-upload-dropzone`。必须保留。
- 仅写入 REQ-031 `codex-task-scope.yaml` 的 `writable_paths`。
- 不修改 `web/src/components/ui`、`web/src/modules/data-migration/data-migration.css`、`web/src/api/data-migration.ts`、路由、后端、数据库、权限、审计或迁移。
- 沿用 `hasCreate`/`canCreate`、`useProjectScope()` 和 `resolvedCategory`；不在对话框新增项目或分类选择。
- 成功或部分成功刷新列表并保持对话框打开；请求失败保留文件；关闭、项目切换或分类切换清理导入状态。
- 导入错误最多渲染 20 条并显示剩余数量。
- 导入对话框不使用 `align-center`，防止移动端全局 margin 组合造成纵向拉伸。
- 验收视口为 `1280x800`、`375x812`、`390x844`、`430x932`，并检查深浅主题。
- 不处理范围外 REQ-061；全仓治理失败必须准确归因。
- 本计划不授权提交、推送、合并或发布。

## 文件职责地图

| 路径 | 状态 | 职责 | 事实依据 |
|---|---|---|---|
| `web/src/modules/data-migration/views/content/ParametersPage.vue` | existing | 迁移参数列表、页面头部、表单和带 `rows` 的导入状态 | 当前源码使用 `importParameters`，结果为 `ParameterImportResult` |
| `web/src/modules/data-migration/views/base/TargetTablesPage.vue` | existing | TARGET/INTERMEDIATE 共用列表、动态标题、表单和分类隔离的导入状态 | 两个路由共用该组件，`resolvedCategory` 来自路由元数据 |
| `docs/engineering-control/plans/2026-09-07-data-migration-three-page-import-dialog-implementation-plan.md` | new | 人类可读实施步骤和边界 | 已批准设计 |
| `.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-handoff.json` | new | 可导入控制闭环的完整设计、计划和模型种子 | writing-plans 交接契约 |
| `.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-*.json` | candidate-new | 本主题后续 baseline/model/plan/execution/observation/convergence 证据 | 控制闭环阶段按需创建，不预建空文件 |

## 任务依赖图与并行策略

```text
T1 迁移参数页统一
  -> T2 目标表/中间表共用页统一
      -> T3 三路由集成与浏览器验收
```

全部串行执行。T1 先形成页面内的本地实现基准，T2 对齐相同结构并处理分类隔离，T3 在两个最终文件稳定后统一构建和浏览器验收。虽然 T1 与 T2 写入文件不重叠，但工作区已有未提交修改，且两者共享 UI 约定和构建传感器，串行能减少样式分叉与归因噪声。

## 需求覆盖表

| 需求 | 任务 |
|---|---|
| R1 页面头部双操作 | T1, T2, T3 |
| R2 当前页导入对话框 | T1, T2, T3 |
| R3 完整导入状态 | T1, T2, T3 |
| R4 工具栏职责 | T1, T2, T3 |
| R5 上传样式与响应式 | T1, T2, T3 |
| R6 契约保持 | T1, T2, T3 |
| R7 表结构分类隔离 | T2, T3 |

### T1：迁移参数页形成统一头部和批量导入对话框

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** 无

**文件：**

- 修改：`web/src/modules/data-migration/views/content/ParametersPage.vue`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-execution-T1.json`
- 测试：无新增测试文件；使用定向源码契约、Vue 构建和浏览器传感器

**接口：**

- 消费：`downloadParameterTemplate(): Promise<BlobResponse>`、`importParameters({ projectId: number }, file: File): Promise<ApiResponse<ParameterImportResult>>`、`useProjectScope().projectId`、`hasCreate`。
- 产出：页面头部 `批量导入 -> 新增迁移参数`；`openImportDialog/resetImportDialog/beforeImportDialogClose/closeImportDialog` 状态边界；对话框内 `rows/accepted/failed/errors` 结果。

- [ ] **步骤 1：记录执行前局部 diff 和源码基线**

  运行：`git diff -- web/src/modules/data-migration/views/content/ParametersPage.vue`

  预期：只观察到此前删除项目名称列表列和详情项的治理 diff；记录当前导入入口仍位于 `UiToolbar`、结果仍位于列表正文。

  证据：执行报告记录基线 diff 摘要和既有改动保护点。

- [ ] **步骤 2：建立迁移参数源码契约探针并确认当前不满足**

  运行：

  ```bash
  node --input-type=module -e "import fs from 'node:fs'; const s=fs.readFileSync('web/src/modules/data-migration/views/content/ParametersPage.vue','utf8'); const t=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??''; const ok=[s.includes('UiPageHeader'),s.includes('title=\"迁移参数\"'),s.includes('批量导入'),s.includes('新增迁移参数'),s.includes('class=\"dm-import-dialog\"'),s.includes('class=\"dm-upload-dropzone\"'),s.includes('beforeImportDialogClose'),s.includes('errors.slice(0, 20)'),!t.includes('downloadTemplate'),!t.includes('openImportDialog'),!t.includes('openCreate')]; if(ok.some(v=>!v)) process.exit(1); console.log('parameters-import-contract:pass')"
  ```

  预期：实施前退出码非 0，至少缺少 `UiPageHeader` 和导入对话框；不得通过放宽断言获得绿色结果。

  证据：失败断言列表和退出码。

- [ ] **步骤 3：加入页面头部并收敛工具栏职责**

  修改：导入 `UiPageHeader`；在 `<main>` 首部增加标题“迁移参数”和当前项目说明；`actions` 依次放置次要“批量导入”与 `type="primary"` 的“新增迁移参数”，两者受 `hasCreate`、项目 ready、页面错误和忙碌状态约束；从 `UiToolbar` 移除新增、模板、文件输入和导入按钮，保留筛选、查询、重置、导出和批量删除。

  预期：页面只有一个主操作，列表工具栏不再承担导入流程。

  证据：目标模板片段和局部 diff。

- [ ] **步骤 4：实现单文件导入状态和恢复行为**

  修改：使用 Element Plus `UploadInstance`、`UploadFile`、`UploadRawFile`、`UploadProps` 与 `genFileId` 管理可替换的单文件；增加对话框开关、请求错误、`canSubmitImport` 和统一 reset；重新选择文件清理旧结果；请求失败保留文件；提交中禁止遮罩、Esc、关闭图标和取消关闭；项目切换关闭并重置。

  预期：状态机覆盖初始、选中文件、提交中、成功、部分失败、请求失败和关闭重置。

  证据：状态函数、computed 和 watch 的源码差异。

- [ ] **步骤 5：实现迁移参数导入对话框**

  修改：加入当前页 `el-dialog`，宽度为 `min(720px, calc(100vw - 24px))` 且不使用 `align-center`；对话框内依次放置说明、下载模板、`dm-upload-dropzone`、复用 `dm-attachment-*` 的已选文件、请求错误、结果汇总与最多 20 条错误；footer 为取消和主按钮“确认导入”。正文使用受控 `max-height`、纵向滚动和长文本断行。

  预期：模板、文件、提交和结果全部在对话框内，列表正文不再显示旧结果段落。

  证据：对话框 DOM 与局部样式片段。

- [ ] **步骤 6：保持 API 契约并调整成功/失败行为**

  修改：继续调用 `importParameters({ projectId: pid }, file)`；成功或部分成功设置默认 `ParameterImportResult`、提示汇总、调用 `load()`，但不清空文件或关闭对话框；请求失败使用 `apiErrorMessage` 写入持续错误并保留文件；模板下载使用忙碌保护和 finally 恢复。

  预期：API 参数不变，成功结果持续可见，失败可原地重试。

  证据：API 调用源码与受控响应断言。

- [ ] **步骤 7：运行 T1 局部验证**

  运行：

  ```bash
  node --input-type=module -e "import fs from 'node:fs'; const s=fs.readFileSync('web/src/modules/data-migration/views/content/ParametersPage.vue','utf8'); const t=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??''; const ok=[s.includes('UiPageHeader'),s.includes('title=\"迁移参数\"'),s.includes('批量导入'),s.includes('新增迁移参数'),s.includes('class=\"dm-import-dialog\"'),s.includes('class=\"dm-upload-dropzone\"'),s.includes('beforeImportDialogClose'),s.includes('errors.slice(0, 20)'),!t.includes('downloadTemplate'),!t.includes('openImportDialog'),!t.includes('openCreate')]; if(ok.some(v=>!v)) process.exit(1); console.log('parameters-import-contract:pass')"
  git diff --check -- web/src/modules/data-migration/views/content/ParametersPage.vue
  npm --prefix web run build
  ```

  预期：源码契约输出 `parameters-import-contract:pass`；两个命令退出码均为 0；构建允许仅出现已有 Rollup chunk 警告。

  证据：命令、退出码、关键输出和实际文件 diff。

**验收检查：**

- 头部操作顺序、主次和权限条件符合 R1。
- 工具栏和列表正文不含导入步骤或结果。
- 对话框状态、20 条错误上限、请求失败保留文件和项目切换重置均可由源码判别。
- API 调用、模板文件名和既有项目名称治理 diff 保持。

**风险：** `actionBusy` 同时影响导出、模板和导入；必须通过每条 finally 路径确认恢复。Element Plus 单文件替换若未 `clearFiles/handleStart`，同名文件可能无法重新选择。

**回滚：** 仅逆向恢复 T1 新增的 `UiPageHeader`、导入状态、对话框和局部样式，保留参数页原有项目名称治理 diff。

**停止条件：** 需要修改 API、共享 CSS、公共 UI、路由或后端；当前文件相同区域出现无法安全合并的新修改；构建失败根因必须范围外修复。

**升级条件：** 发现迁移参数批量导入使用独立权限；响应结构不再是 `ParameterImportResult`；移动端只能修改共享样式才能满足。

### T2：目标表结构和中间表结构形成统一且分类隔离的导入体验

**需求映射：** R1, R2, R3, R4, R5, R6, R7

**前置任务：** T1

**文件：**

- 修改：`web/src/modules/data-migration/views/base/TargetTablesPage.vue`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-execution-T2.json`
- 测试：无新增测试文件；使用双分类源码契约、Vue 构建和浏览器传感器

**接口：**

- 消费：`downloadTargetTableTemplate()`、`importTargetTables(category: TableCategory, projectId: number, file: File)`、`resolvedCategory: ComputedRef<'TARGET' | 'INTERMEDIATE'>`、`title`、`canCreate`。
- 产出：两个路由动态页面头部；分类匹配的新增、导入标题和模板文件名；`accepted/failed/errors` 对话框结果；项目和分类双重重置。

- [ ] **步骤 1：记录执行前局部 diff 和双分类基线**

  运行：`git diff -- web/src/modules/data-migration/views/base/TargetTablesPage.vue`

  预期：保留此前移除 `scopeProjectName`、改为固定项目上下文提示和加入 `dm-upload-dropzone` 的治理 diff；记录模板和新增仍在工具栏、成功后自动关闭对话框。

  证据：执行报告记录基线差异和保护点。

- [ ] **步骤 2：建立表结构源码契约探针并确认当前不满足**

  运行：

  ```bash
  node --input-type=module -e "import fs from 'node:fs'; const s=fs.readFileSync('web/src/modules/data-migration/views/base/TargetTablesPage.vue','utf8'); const t=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??''; const ok=[s.includes('UiPageHeader'),s.includes(':title=\"title\"'),s.includes('新增{{ title }}'),s.includes('class=\"dm-import-dialog\"'),s.includes('class=\"dm-upload-dropzone\"'),s.includes('resolvedCategory'),s.includes('errors.slice(0, 20)'),!t.includes('downloadTemplate'),!t.includes('openImport'),!t.includes('openCreate')]; if(ok.some(v=>!v)) process.exit(1); console.log('table-import-contract:pass')"
  ```

  预期：实施前退出码非 0；不得把现有简单弹框误判为完整状态。

  证据：失败断言和退出码。

- [ ] **步骤 3：增加动态页面头部并收敛表结构工具栏**

  修改：导入 `UiPageHeader`；在页面首部使用 `:title="title"` 和当前项目说明；`actions` 依次放置“批量导入”与 `type="primary"` 的 `新增${title}`；沿用 `canCreate`、项目 ready、错误和忙碌约束；从 `UiToolbar` 移除模板、批量上传和新增，保留筛选、刷新、查询、重置和导出；表格 footer 批量删除保持。

  预期：TARGET 和 INTERMEDIATE 自动得到正确标题和新增文案。

  证据：动态模板片段和工具栏 diff。

- [ ] **步骤 4：升级表结构导入状态并隔离分类**

  修改：使用 Element Plus typed upload 管理单文件替换；增加请求错误、`canSubmitImport`、统一 reset 与关闭拦截；成功或部分成功保存结果、刷新但不关闭；请求失败保留文件；将项目监听扩展为同时响应 `scopeProjectId` 和 `resolvedCategory`，分类变化时关闭并清理旧导入状态后重新加载。

  预期：从目标表切换到中间表或反向切换时，不保留旧文件、错误、结果或请求分类。

  证据：watch 依赖、reset 函数和 `importTargetTables(resolvedCategory.value, projectId, file)` 源码。

- [ ] **步骤 5：统一表结构导入对话框**

  修改：对话框标题改为 `批量导入${title}`，宽度与迁移参数一致且移除 `align-center`；模板按钮移入弹框，下载文件名使用 `${title.value}模板.xlsx`；保留现有项目编码逐行校验说明；复用上传和附件样式；显示成功、失败、最多 20 条错误和剩余数量；footer 使用“取消”和“确认导入”。

  预期：目标表和中间表共享结构但文案、文件名与分类匹配。

  证据：双分类 DOM、模板文件名和局部样式。

- [ ] **步骤 6：运行 T2 局部验证**

  运行：

  ```bash
  node --input-type=module -e "import fs from 'node:fs'; const s=fs.readFileSync('web/src/modules/data-migration/views/base/TargetTablesPage.vue','utf8'); const t=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??''; const ok=[s.includes('UiPageHeader'),s.includes(':title=\"title\"'),s.includes('新增{{ title }}'),s.includes('class=\"dm-import-dialog\"'),s.includes('class=\"dm-upload-dropzone\"'),s.includes('resolvedCategory'),s.includes('errors.slice(0, 20)'),!t.includes('downloadTemplate'),!t.includes('openImport'),!t.includes('openCreate')]; if(ok.some(v=>!v)) process.exit(1); console.log('table-import-contract:pass')"
  git diff --check -- web/src/modules/data-migration/views/base/TargetTablesPage.vue
  npm --prefix web run build
  ```

  预期：源码契约输出 `table-import-contract:pass`；空白检查和构建退出码均为 0。

  证据：命令、退出码、关键输出和实际 diff。

**验收检查：**

- 两个路由的标题、主次操作、模板文件名和请求分类正确。
- 工具栏无模板、批量上传和新增，既有刷新、导出和批量删除保持。
- 成功不自动关闭，请求失败保留文件，项目或分类切换重置。
- 既有项目上下文提示和 `dm-upload-dropzone` 治理 diff 保持。

**风险：** 路由对共用组件的复用行为可能使 `resolvedCategory` watch 触发时序复杂；必须记录切换前后实际 route、title 和请求参数。表结构响应无 `rows`，不得伪造总行数。

**回滚：** 仅逆向恢复 T2 新增的页面头部、导入状态、分类监听调整、对话框和局部样式，保留任务开始前的表结构治理 diff。

**停止条件：** 共用模板实际不支持两种分类；分类切换必须修改路由或 API；需要改共享 CSS/公共 UI；构建失败只能范围外修复。

**升级条件：** TARGET 与 INTERMEDIATE 存在不同导入权限或模板契约；路由未复用当前组件事实；浏览器发现分类请求无法由当前 `resolvedCategory` 保证。

### T3：三个路由的独立集成与四视口验收

**需求映射：** R1, R2, R3, R4, R5, R6, R7

**前置任务：** T1, T2

**文件：**

- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-observation-T1-T2.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/three-page-import-dialog-convergence-report.json`
- 测试：不修改产品文件；浏览器截图仅写入 `mktemp -d` 生成的临时目录

**接口：**

- 消费：T1/T2 最终页面、三个路由、现有前端与后端本地服务。
- 产出：R1-R7 需求测量、三页面四视口截图与尺寸、成功/部分失败/请求失败状态、分类隔离、范围和构建证据。

- [ ] **步骤 1：复跑最终静态和构建传感器**

  运行：

  ```bash
  node --input-type=module -e "import fs from 'node:fs'; const s=fs.readFileSync('web/src/modules/data-migration/views/content/ParametersPage.vue','utf8'); const t=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??''; const ok=[s.includes('UiPageHeader'),s.includes('title=\"迁移参数\"'),s.includes('批量导入'),s.includes('新增迁移参数'),s.includes('class=\"dm-import-dialog\"'),s.includes('class=\"dm-upload-dropzone\"'),s.includes('beforeImportDialogClose'),s.includes('errors.slice(0, 20)'),!t.includes('downloadTemplate'),!t.includes('openImportDialog'),!t.includes('openCreate')]; if(ok.some(v=>!v)) process.exit(1); console.log('parameters-import-contract:pass')"
  node --input-type=module -e "import fs from 'node:fs'; const s=fs.readFileSync('web/src/modules/data-migration/views/base/TargetTablesPage.vue','utf8'); const t=s.match(/<UiToolbar>[\s\S]*?<\/UiToolbar>/)?.[0]??''; const ok=[s.includes('UiPageHeader'),s.includes(':title=\"title\"'),s.includes('新增{{ title }}'),s.includes('class=\"dm-import-dialog\"'),s.includes('class=\"dm-upload-dropzone\"'),s.includes('resolvedCategory'),s.includes('errors.slice(0, 20)'),!t.includes('downloadTemplate'),!t.includes('openImport'),!t.includes('openCreate')]; if(ok.some(v=>!v)) process.exit(1); console.log('table-import-contract:pass')"
  git diff --check -- web/src/modules/data-migration/views/content/ParametersPage.vue web/src/modules/data-migration/views/base/TargetTablesPage.vue
  npm --prefix web run build
  ```

  预期：联合契约输出 `three-page-import-contract:pass`，其余退出码为 0。

  证据：完整命令、退出码和构建摘要。

- [ ] **步骤 2：执行 REQ-031 定向范围和全仓治理归因**

  运行：

  ```bash
  node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree
  node scripts/check-all-governance.mjs
  ```

  预期：REQ-031 定向范围退出码 0；全仓治理若仅因 REQ-061 非 JSON-compatible YAML 退出 1，记录为范围外扰动，不修改或隐藏。出现本主题范围失败则停止收敛。

  证据：两条命令的退出码与范围归因。

- [ ] **步骤 3：探测本地运行环境**

  运行：

  ```bash
  curl -fsS http://127.0.0.1:5173/ >/dev/null
  curl -fsS http://127.0.0.1:8080/actuator/health
  node -e "import('playwright').then(() => console.log('playwright:ready'))"
  ```

  预期：记录前端、后端和 Playwright 实际可用状态；缺失服务按仓库脚本启动到空闲端口，且只终止本次启动的进程。后端不可用时不得把真实写入分支描述为通过。

  证据：健康检查、端口、启动命令和进程所有权。

- [ ] **步骤 4：在三个路由执行四视口浏览器验收**

  路由：

  ```text
  /data-migration/content/parameters
  /data-migration/base/target-tables
  /data-migration/base/intermediate-tables
  ```

  视口：`1280x800`、`375x812`、`390x844`、`430x932`。

  断言：页面标题和两个头部操作正确；批量导入在新增之前且新增为 primary；打开弹框不改路由；模板、上传和 footer 均位于对话框；初始确认禁用、选中文件后启用；页面、dialog、body 均 `scrollWidth <= clientWidth`；长文件名与 21 条错误输入不溢出，渲染 20 条加剩余 1 条；正文滚动且 footer 底部小于视口高度；移动端不存在近整屏无意义拉伸。

  预期：三个页面共 12 组基础视口检查全部通过，截图文件名包含页面和视口。

  证据：截图路径、DOM 数值、routeBefore/routeAfter、按钮状态和控制台错误摘要。

- [ ] **步骤 5：验证状态恢复与分类隔离**

  操作：使用明确标记的受控浏览器响应分别模拟全部成功、21 条错误的部分失败和请求失败；验证提交中关闭拦截、请求失败保留文件、关闭重置；在目标表与中间表路由间切换，断言标题、模板下载文件名、文件/结果重置和请求 `category` 为当前分类。真实后端允许非敏感测试数据时补充真实模板 GET 和导入证据，并与受控响应区分。

  预期：所有状态符合 R3/R7；任何错误分类请求为 P1，不得收敛。

  证据：网络请求摘要、下载文件名、状态断言和限制说明。

- [ ] **步骤 6：形成独立观察和收敛报告**

  修改：记录 R1-R7 的期望、观察、原始证据、限制、扰动和错误等级；若有偏差，新增原子反馈并返回 correcting；只有任务 verified、反馈关闭、最终 P0/P1 为 0、范围和回归通过时才生成 `gate_result=pass`。

  预期：控制脚本 gate 独立判定通过后才能转 `converged`。

  证据：observation、convergence、state phase 和 gate 输出。

**验收检查：**

- 三个路由、四个视口和深浅主题无溢出、遮挡或移动端纵向拉伸。
- 成功、部分失败、请求失败、提交中和关闭重置均有明确证据。
- TARGET/INTERMEDIATE 分类隔离和模板文件名正确。
- 最终构建、空白检查和 REQ-031 范围检查通过。

**风险：** 受控网络响应只能证明前端状态，不能证明后端 Excel 解析；必须在报告中区分。浏览器动画可能造成过早尺寸采样，需等待 dialog 稳定后测量。

**回滚：** T3 不修改产品；仅删除本次临时截图并终止本次启动的服务。发现偏差时返回对应任务限幅修正，不回退用户既有治理改动。

**停止条件：** 任一页面出现 P0/P1；REQ-031 定向范围失败；浏览器需要生产数据或敏感凭据；修复必须扩大到未批准文件。

**升级条件：** 需要真实业务 Excel 或独立低权限账号才能完成关键门禁；共用模板不支持中间表；出现 API、权限或路由契约差异。

## 集成检查

| 完成任务 | 命令或传感器 | 通过信号 |
|---|---|---|
| T1 | 参数页源码契约、目标文件 `git diff --check`、前端构建 | 契约输出 pass，退出码 0 |
| T2 | 表结构源码契约、目标文件 `git diff --check`、前端构建 | 契约输出 pass，退出码 0 |
| T1, T2 | 联合源码契约、REQ-031 范围检查、四视口三路由浏览器验收 | R1-R7 均有证据，无本主题 P0/P1 |
| T1, T2, T3 | 控制账本 gate | `收敛门禁：PASS（通过）`，随后 transition 到 `converged` |

## 控制模型种子

以下均为 `hypotheses-only`，必须由建模阶段验证：

- 被控边界候选：两个 Vue 页面文件、三个路由、两个导入 API 和共享上传样式消费关系。
- 状态变量候选：页面/项目/分类、dialog open、selected file、submitting、request error、import result、list loading、permission。
- 接口候选：参数模板与导入、表结构模板与分类导入、`UiPageHeader.actions`、`dm-upload-dropzone`、路由 `category`。
- 传感器候选：源码契约、Vue 构建、diff/range、Chromium DOM 与截图、网络请求和下载事件。
- 执行器候选：两个页面文件内的模板、状态函数、watch 和 scoped style。
- 扰动候选：两个文件既有未提交 diff、REQ-061 治理失败、共用组件路由复用、dialog 动画、既有 Rollup 警告。
- 时延候选：构建约 20 秒、dialog 动画、列表刷新和受控响应等待。
- 假设候选：现有新增权限覆盖导入；单一表结构模板支持两分类；本地服务与 Playwright 可用；共享样式无需修改。

## 风险与用户批准

本计划没有数据库、权限、公共组件、共享 CSS、外部系统、提交、推送或发布动作。最高风险是共用 `TargetTablesPage.vue` 在分类切换时发生状态泄漏，以及覆盖两个文件已有治理 diff；计划已通过串行执行、执行前 diff 基线和双分类浏览器探针控制。

用户批准本计划修订 1 后，才会将交接包状态更新为 `approved`，导入新的 `three-page-import-dialog` 控制账本，并进入 `$control-engineering`。在此之前不修改产品文件。
