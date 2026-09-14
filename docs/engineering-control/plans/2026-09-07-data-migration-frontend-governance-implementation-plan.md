# 数据迁移前端规范与上传样式统一实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 清除数据迁移模块的项目字段展示残留，并统一文件上传区域的响应式与语义主题样式。

**架构：** 页面保留现有上传业务状态和 API，只在模块共享 CSS 中建立两个上传视觉原语，并用共享附件列表类替代各页重复样式。项目上下文继续参与请求，但从所有业务可见 UI 中移除。

**技术栈：** Vue 3、TypeScript、Element Plus、模块共享 CSS、Vite。

## 全局约束

- 只修改 REQ-031 `writable_paths` 覆盖的文件。
- 不改变服务端、数据库、权限、审计、上传 API 和全局项目切换器。
- 不修改 `web/src/components/ui`，优先复用现有 Element Plus 和语义主题变量。
- REQ-061 不在本次范围；全量治理失败必须如实记录。
- 浏览器验收覆盖 `1280x800`、`375x812`、`390x844`、`430x932`。

---

### T1：需求基线消歧与范围登记

**需求映射：** R1, R5

**前置任务：** 无

**文件：**
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/requirement.md`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/frontend-governance-*.json`

**接口：**
- 消费：T31-r1 模块级“项目不回显”不变量和用户批准计划。
- 产出：无冲突、可测量的当前增量基准与外部扰动记录。

- [ ] **步骤 1：登记本次增量、边界和验收标准。**
- [ ] **步骤 2：修正规则域后续文字中与 T31-r1 冲突的项目展示描述。**
- [ ] **步骤 3：导入批准交接包并完成 baseline、modeling、planning 门禁。**

**验收：** 当前需求文档不再同时要求“禁止项目回显”和“列表展示项目”。

**回滚：** 恢复 T1 开始前的需求文档并保留账本历史。

**停止条件：** 发现用户最新指令明确要求重新展示项目字段。

**升级条件：** 必须修改 REQ-061 或全局治理脚本才能继续。

### T2：清理项目字段与项目名称回显

**需求映射：** R1

**前置任务：** T1

**文件：**
- 修改：`web/src/modules/data-migration/views/content/ParametersPage.vue`
- 修改：`web/src/modules/data-migration/views/content/ValidationRulesPage.vue`
- 修改：`web/src/modules/data-migration/views/base/TargetTablesPage.vue`
- 修改：`web/src/modules/data-migration/views/content/IssuesPage.vue`
- 修改：`web/src/modules/data-migration/views/content/MeetingsPage.vue`

**接口：**
- 消费：`useProjectScope().projectId` 仍作为请求和提交归属。
- 产出：业务可见 UI 不再渲染项目字段或动态项目名称。

- [ ] **步骤 1：删除参数和检核规则的项目列表列与详情项。**
- [ ] **步骤 2：将项目不可用提示改为顶部切换器语义，不使用“所属项目”。**
- [ ] **步骤 3：移除导入和清空确认中的动态项目名称，保留项目隔离含义。**
- [ ] **步骤 4：运行定向 `rg` 扫描并审查剩余命中是否仅为注释或文件契约。**

**验收：** 运行时页面文本中无“所属项目”“项目名称”及动态项目名称。

**回滚：** 恢复 T2 五个 Vue 文件。

**停止条件：** 移除字段导致接口请求不再携带 `projectId`。

**升级条件：** 后端契约要求用户必须在页面选择项目。

### T3：统一上传区域和附件列表样式

**需求映射：** R2, R3, R4, R5

**前置任务：** T2

**文件：**
- 修改：`web/src/modules/data-migration/data-migration.css`
- 修改：`web/src/modules/data-migration/components/AssetListView.vue`
- 修改：`web/src/modules/data-migration/views/base/TargetTablesPage.vue`
- 修改：`web/src/modules/data-migration/views/content/IssuesPage.vue`
- 修改：`web/src/modules/data-migration/views/content/ReportsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/MeetingsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/PlansPage.vue`
- 修改：`web/src/modules/data-migration/views/content/MappingsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/ProgramsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/TopicsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/ReleaseDrillsPage.vue`
- 修改：`web/src/modules/data-migration/views/content/RecycleBinPage.vue`

**接口：**
- 消费：Element Plus `el-upload` 的 `on-change`、`multiple`、`limit` 和现有页面文件状态。
- 产出：`.dm-upload-dropzone`、`.dm-upload-hint`、`.dm-attachment-*` 模块级样式契约。

- [ ] **步骤 1：在共享 CSS 中定义全宽拖拽区、提示和附件列表状态。**
- [ ] **步骤 2：为拖拽上传入口应用全宽类，并关闭自定义预览场景的默认文件列表。**
- [ ] **步骤 3：将重复的附件列表类迁移到共享命名并删除页面重复样式。**
- [ ] **步骤 4：将待上传、空态和提示的内联颜色替换为语义变量。**
- [ ] **步骤 5：运行前端构建和静态上传一致性扫描。**

**验收：** 两种上传形态一致、无重复默认列表、无上传相关内联十六进制颜色，拖拽器宽度为父容器 100%。

**回滚：** 恢复 T3 文件并移除共享上传样式块。

**停止条件：** 样式收敛需要改变上传 API、公共 UI 组件或后端行为。

**升级条件：** 页面存在未建模的第三种上传流程，无法映射到两个标准形态。

### T4：集成与真实浏览器验收

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T3

**文件：**
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/frontend-governance-*.json`

**接口：**
- 消费：T2/T3 的页面输出。
- 产出：构建、静态扫描、浏览器和治理范围证据。

- [ ] **步骤 1：运行 `npm --prefix web run build` 和 `git diff --check`。**
- [ ] **步骤 2：运行 REQ-031 定向任务范围检查。**
- [ ] **步骤 3：启动本地应用并在四个视口验证项目字段、上传区和横向溢出。**
- [ ] **步骤 4：运行仓库全量治理，记录 REQ-061 外部失败且不归因于本次修改。**
- [ ] **步骤 5：完成观察、必要纠偏和收敛门禁。**

**验收：** 数据迁移专项传感器全部通过；全量治理若仅剩 REQ-061，则作为已知外部阻断报告。

**回滚：** 回退 T2/T3 产品改动，保留失败证据。

**停止条件：** 浏览器环境不可用且无法获得真实视觉证据。

**升级条件：** 出现本次修改导致的 P0/P1 回归或范围外文件需求。

## 依赖与并行策略

任务按 `T1 -> T2 -> T3 -> T4` 串行执行。T2 与 T3 共享多个 Vue 文件，不并行修改；T4 在所有产品代码稳定后统一采样。

## 控制模型种子

以下仅为 `hypotheses-only`：项目字段输出由五个 Vue 模板和三个确认文案产生；上传宽度由 Element Plus 默认拖拽器和页面 CSS 共同决定；共享 CSS、模板类名和 `show-file-list` 是主要执行器；静态扫描、构建和 Playwright 视口检查是候选传感器；REQ-061 是可检测但本任务不可控的外部扰动。
