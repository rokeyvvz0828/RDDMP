# 投产方案页面实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在功能不变的前提下交付左侧方案目录、右侧详情和层级清晰的时序抽屉。

**架构：** 仅修改 ReleaseDrillPlanView.vue 的模板、展示派生值与 scoped 样式。保留 API、现有事件处理和父组件项目切换。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite，复用现有共享 UI。

## 状态与来源

计划修订 1，用户已于 2026-09-07 对落盘计划回复“确认”。设计修订 1，用户已确认，文档为 `docs/engineering-control/designs/2026-09-07-release-plan-page-redesign-design.md`。范围来源为 `docs/requirements/REQ-20260907-065-release-plan-page-redesign/codex-task-scope.yaml`。

## 全局约束

- 不改变业务功能、字段、请求、rowVersion、projectId、权限或菜单。
- 不修改 API、后端、SQL、公共组件、共享样式或依赖。
- 保留现有未提交菜单改名；不提交、推送或切换分支，不创建并行 worktree。
- 图标使用 Element Plus 现有库并提供 tooltip 和可访问名称；颜色使用语义变量，字号固定，不使用负字距。
- 不新增统计块、介绍文案、日期时间线或整页横向滚动。
- 不保存或删除已有测试数据来进行视觉验收；数据写入测试仅允许明确独立的虚构夹具，无法提供时记录未验证项。

## 文件地图与顺序

唯一产品文件：`web/src/modules/release/components/ReleaseDrillPlanView.vue`，现存，负责方案目录、详情与维护弹层。授权文档和本需求账本负责范围与证据。无新依赖或新产品文件。

T1 -> T2 串行，两任务写同一组件，不并行。R1/R2 由 T1 覆盖；R3 由 T2 覆盖；R4/R5 两任务均检查。

## T1：方案目录与详情

需求：R1、R2、R4、R5。前置任务：无。

已证实：旧版 `.release-operations-grid` 为 `1fr / .44fr`，列表占主要宽度；当前组件已包含方案 CRUD 和正向/回退入口。消费 ReleasePlanDto、selectedPlan、plans、canManage、openPlan、openTimeline 和 removePlan；不产出新接口。

修改：授权组件的主页面模板、展示状态标签和局部样式。测试：当前组件、本地路由 `/release-operations/drill-plans`。

1. 用 `git diff -- web/src/modules/release/components/ReleaseDrillPlanView.vue` 记录已有菜单文案变更；浏览器查看旧版目录/详情宽度，记录基准，不导出敏感会话数据。
2. 引入 UiPageHeader、UiStatusTag，保留 UiEmptyState；新增 `release-plan-workspace` 等独立类，桌面网格为 `minmax(240px, 300px) minmax(0, 1fr)`，900px 以下变为一列。模板明确展示名称、编码、版本、状态，正向/回退入口分别绑定原 openTimeline 类型。
3. 为目录选中项提供 aria 状态，为刷新、编辑、删除添加 tooltip；长名称允许换行。目录局部纵向滚动、详情自然展开，移动端按钮可换行。
4. 执行 `git diff --check` 和 `npm --prefix web run build`，预期退出 0；读取 diff，确认原 API 调用与 CRUD handler 未改；浏览器切换两个方案、打开新增/编辑并取消、删除确认取消，预期无错误且保留当前方案。
5. 写 execution-T1.json，记录命令、退出码、实际修改面与未验证场景；保存工作区检查点，不执行 git commit。

验收：目录窄于详情，两个时序入口的名称与计数对应选中方案；空态与错误态不被误渲染成普通详情。回退：只撤销 T1 新增 hunks，保留原有“投产方案”文案改名。停止条件：需要更改 API 或共享样式；升级条件：发现范围外功能缺陷，记录并请求另行授权。

## T2：时序抽屉与集成验收

需求：R3、R4、R5。前置任务：T1。

已证实：timelineSequences 按 seqNo 排序，每个时序拥有 items；三级新增/编辑/删除已实现，删除取消过滤 cancel/close。消费 ReleasePlanTimelineDto、ReleasePlanItemDto、timelineName、timelineSequences 和原有维护函数；不产出新接口。

修改：同一授权组件的时序抽屉模板与局部样式。测试：正向/回退 drawer、方案与时序 dialog、指令 drawer。

1. 记录 P1/P2、指令以及新增/编辑/删除入口基准；确认抽屉 teleport 所在 DOM，避免使用失效的父容器 CSS 选择器。
2. 去除重复标题与说明文案，时序组名称和新增时序采用主次分明的操作区。使用稳定宽度的横向列，例如 `grid-auto-columns: 280px; grid-auto-flow: column`，滚动限定在该区域。
3. 时序和指令图标采用正常文档流，避免绝对定位覆盖标题。保留 P 编号、自定义名称、时间、负责人、状态、说明以及所有原有按钮回调。
4. 在 1280px 以上及 375x812、390x844、430x932 测量页面 scrollWidth 不超过 viewport；时序仅局部滚动，末列与 footer 可达，表单保持合并时间范围。检查长名称、浅/深主题、加载、空、错误重试、无权限和只读场景，缺少角色或故障注入能力时记录缺口。
5. 验证两类时序 drawer、时序编辑 dialog、指令 drawer 打开与取消；三级删除确认取消/关闭无删除请求和失败消息；通过顶部项目切换验证无旧项目内容残留。仅使用本地非敏感测试数据。
6. 执行 `git diff --check`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs` 和范围文件中的 scope 命令。记录真实退出码；既有 V156/router diff 导致范围检查失败时单独列出，不扩大范围或改动那些文件以通过检查。
7. 写 execution-T2 与非独立自测证据，独立验收留待人工；只有证据充分才推进账本阶段，不伪造 convergence。

验收：保留 P1/P2 横向与多指令关系，无时间线，操作不遮挡，局部滚动和弹层可达。回退：只撤销 T2 新增 hunks。停止条件：需改数据库、权限、服务进程或公共弹层；升级条件：范围外回归、无法验证的关键交互或缺少独立复核证据。

## 控制模型种子

全部为 hypotheses-only，导入后由建模阶段确认。边界候选：组件模板、展示派生值与局部样式；状态候选：项目、选中方案、抽屉类型、表单打开状态、loading 和 canManage；传感器：diff、Vue 构建、真实浏览器尺寸和请求观察；执行器：局部模板/CSS；扰动：已有全局样式、长文本、项目切换、后台可用性与浏览器权限；时延：Vite 热更新和接口响应。

## 批准与交接

设计和落盘实施计划均已确认，handoff_status 为 approved。无数据库、发布或提交动作。导入当前任务前缀账本，依次完成 baseline、modeling、planning 再实施。
