# 投产问题详情与双视图实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 提供问题详情、抽屉维护及不丢失查询上下文的卡片/列表切换。

**架构：** 仅修改 ReleaseIssueTrackingView.vue，详情消费列表 DTO，维护沿用现有 Write DTO 和 API。两视图共享问题数组和分页，使用局部展示状态与组件专用样式。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite；不新增依赖。

## 状态与来源

计划修订 1，用户在查看落盘计划链接后回复“确认”，已批准实施。设计修订 1 已确认；设计文档 `docs/engineering-control/designs/2026-09-07-release-issue-views-design.md`。范围和需求在 `docs/requirements/REQ-20260907-066-release-issue-views/`。

## 全局约束

- 仅改授权问题组件和本需求文档/账本，保持其他投产页面、API、后端、SQL、公共组件、路由、依赖不变。
- 保持 projectId、rowVersion、服务端分页、原字段校验和管理权限含义。
- 不读取 `.env`、生产或敏感数据；不保存/删除已有测试记录进行验收，写入测试仅使用独立虚构夹具。
- 不提交、不推送、不更换当前 licon 分支，不覆盖既有投产方案改版和菜单改名。
- 图标必须有 tooltip/可访问名称，颜色复用语义主题变量，样式不引起页面级横向滚动。
- 现有治理格式问题与分支命名检查失败如实列出，不顺带修改检查器、历史范围或分支绕过门禁。

## 文件地图与任务顺序

产品文件仅 `web/src/modules/release/components/ReleaseIssueTrackingView.vue`，已存在且目前无本任务修改。消费 `web/src/api/release.ts` 的 ReleaseIssueDto/Write 和原 CRUD API。父组件按项目 key 重建的逻辑只读。

T1 抽屉 -> T2 双视图，写同一文件，串行执行。R1/R2 由 T1 覆盖；R3 由 T2 覆盖；R4/R5 两任务共同验证。测试使用当前组件和本地路由，无新增测试框架。

## T1：详情与维护抽屉

需求：R1、R2、R4、R5。前置：无。

已证实事实：列表 DTO 包含详情字段，当前只有管理权限用户能通过编辑弹窗阅读长字段；现有保存校验编号/标题和关闭时间，删除过滤 cancel/close。

文件：修改授权组件。输入契约：ReleaseIssueDto、ReleaseIssueWrite、issues、form、editingIssue、canManage、saveIssue；输出契约：组件局部 `openDetail(issue)` 和统一抽屉展示状态，供 T2 入口绑定。

1. 基准：`git diff -- web/src/modules/release/components/ReleaseIssueTrackingView.vue`；浏览器记录当前列表、编辑弹窗、筛选分页和管理入口，无详情入口是预期基准。
2. 以 `ref<'detail' | 'create' | 'edit'>` 管理抽屉模式，详情记录与 form 分离。新增 openDetail(issue)，列表标题和详情图标绑定它；详情使用已返回 DTO 全字段及空值占位，不新增请求或 v-html。
3. 将原 dialog 换为最大 760px 的 el-drawer，详情与维护互斥。维护重排原字段，固定 footer；继续保存原 payload，保留必填、关闭时间、saving 和错误反馈。详情转编辑复用原 openIssue 数据赋值，不覆盖详情对象。
4. 添加表单未保存关闭保护：未改动直接关闭，已改动确认放弃，取消确认保留输入；保存中不关闭。所有关闭入口走同一函数，避免取消误调用保存。
5. 运行 `git diff --check`、`npm --prefix web run build`，预期退出 0；记录实际结果和已有警告。浏览器验证详情只读、详情转编辑、关闭返回、必填阻止保存、关闭时间规则、取消不污染列表、失败保留输入和重复提交防护。
6. 写 execution-T1.json，记录实际 diff、命令、角色、操作及缺口。保存工作区检查点，不执行 git commit。

验收：详情可读所有字段且无保存按钮，维护使用抽屉和原校验，关闭后查询上下文不变。回退：撤销 T1 新增 hunks。停止：需要后端或共享能力变更；升级：发现 DTO 不含真实详情、只读角色数据契约不同或无法确认权限行为。

## T2：卡片/列表与集成验收

需求：R3、R4、R5。前置：T1。

已证实事实：issues/page/pageSize/total 与 keyword/priority/status 已是单一分页状态；成员和轮次由当前项目加载；父级项目 key 已负责重建。

文件：修改同一组件。输入：原分页/筛选变量、T1 的 openDetail、原 openIssue/removeIssue；输出：无新公开接口。

1. 建立局部 `viewMode`，首次进入以 760px 为边界选择桌面列表/手机卡片；使用原生 matchMedia 或已存在能力，不引入依赖。用户切换模式只改变 ref，不调用 load，不改 page、pageSize 或筛选。
2. 使用 Element Plus 图标分段控件，保留列表业务列并增加详情入口；卡片展示编号/标题、优先级/状态、轮次、负责人、发现时间和分析摘要。两分支共享 issues，编辑/删除均按 canManage 显示。
3. 复用 UiPageHeader/UiToolbar/UiStatusTag/UiEmptyState，局部样式定义卡片响应式网格、表格横滚容器和移动分页。卡片标题与详情按钮单独绑定，不让编辑/删除冒泡到详情。
4. 场景矩阵：筛选到有结果并翻页后连续切换两视图，断言问题 ID/总数/页码和条件一致、无额外 list 请求；打开同一问题详情内容一致；选择无结果筛选验证两模式空态；恢复条件后验证错误重试、只读、403、项目切换、加载和提交中状态。无账号或故障注入能力时单列未测项。
5. 桌面 1280px 以上与手机 375x812、390x844、430x932，浅深主题分别验证卡片、列表、详情和维护抽屉。检查 document.documentElement.scrollWidth 不超过 viewport，只有表格局部横滚；长标题/全文换行，footer 与分页可达。
6. 本地写入验证仅创建独立虚构问题夹具：新增后读取、编辑状态/轮次/全文并刷新核对，最后仅删除该夹具；无可用测试角色或不满足隔离条件时不触碰已有数据，记录未测。删除取消必须无失败提示、无 DELETE 请求。
7. 运行 `git diff --check`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`，以及范围文件中的 scope 检查。治理失败区分既有与本次；复查 API 参数及项目、权限不变量。
8. 写 execution-T2 与观测证据，标明是否独立；不能获得独立复核或关键验收缺失时保留 observing，不宣称 converged。

验收：两模式共用查询与分页，全部入口可达，手机无整页溢出，原权限和取消语义保留。回退：撤销 T2 专项 hunks，不撤销其他任务。停止：需要新接口、共享组件或持久配置；升级：跨项目残留、越权、写入回归或无法验证的关键交互。

## 集成检查与控制种子

全部 must 需求绑定：R1/T1/详情字段核对；R2/T1/抽屉与表单交互；R3/T2/视图切换前后条件、页码和记录 ID；R4/T1+T2/尺寸截图；R5/T1+T2/权限、项目、网络与取消检查。两个任务完成时分别采样。

control_seed 为 hypotheses-only：被控边界候选为问题组件；状态候选为项目、记录、视图、抽屉模式、表单及查询分页；接口候选为原 release API；传感器候选为 diff、Vue 构建、浏览器 DOM/截图/请求；执行器为组件局部脚本模板样式；扰动为全局 CSS、数据长度、角色、接口失败和浏览器延迟。进入建模阶段重新取证。

## 批准与交接

设计和落盘计划均已确认，handoff 状态为 approved。导入 `req-20260907-066-release-issue-views` 前缀，执行 baseline/modeling/planning 门禁后实施。无发布、数据库迁移或自动提交动作。
