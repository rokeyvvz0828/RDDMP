# 投产管理抽屉全屏实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 让投产管理5处抽屉在保留业务上下文的情况下全屏及还原。

**架构：** 保留原el-drawer，新增模块内全屏帮助函数与标题控件；每个实例独立状态。只增加专属CSS，公共UiFormDrawer和其他模块不变。

**技术栈：** Vue3、TypeScript、Element Plus、现有图标库、Node；不增加依赖。

## 状态与来源

计划修订1，已获用户“确认”批准实施。设计修订1已由用户在收到落盘设计后回复“确认”批准。
设计文档：docs/engineering-control/designs/2026-09-07-release-drawer-fullscreen-design.md。
交接包：.ai-control/requirements/req-20260907-069-release-drawer-fullscreen/handoff.json。
产品增量已实施，未修改运行环境；执行结果见当前需求目录acceptance.md。

## 全局约束

- Vue3/Element Plus现有图标库
- 模块内复用，不修改公共UiFormDrawer
- 不读取.env或后端，不重启服务、不提交推送
- 默认尺寸按各实例现值，普通弹窗不改
- 不改字段、接口、权限、数据、保存与关闭保护
- 非投产管理抽屉不变
- 保留用户既有未提交代码与测试数据
- 继续当前licon工作区，不切换分支、不创建worktree；只增量修改既有未提交代码
- 实际浏览器权限不可用时不绕过，不宣称已验收

## 文件职责地图

- web/src/modules/release/components/ReleaseDrillPlanView.vue：existing；接入已有抽屉全屏而不变业务逻辑。依据：已读取抽屉声明与原事件。
- web/src/modules/release/components/ReleaseDrillEnvironmentView.vue：existing；接入已有抽屉全屏而不变业务逻辑。依据：已读取抽屉声明与原事件。
- web/src/modules/release/components/ReleaseDrillExecutionView.vue：existing；接入已有抽屉全屏而不变业务逻辑。依据：已读取抽屉声明与原事件。
- web/src/modules/release/components/ReleaseIssueTrackingView.vue：existing；接入已有抽屉全屏而不变业务逻辑。依据：已读取抽屉声明与原事件。
- web/src/modules/release/release-operations.css：existing；本次专属全屏与表单容器样式。依据：已读取末尾模块响应式样式。
- web/src/modules/release/composables/useReleaseDrawerFullscreen.ts：candidate-new；全屏状态与尺寸。依据：本次计划候选文件，未创建。
- web/src/modules/release/components/ReleaseDrawerHeader.vue：candidate-new；标题及纯尺寸图标操作。依据：本次计划候选文件，未创建。
- docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs：candidate-new；局部可重复断言。依据：本次计划候选文件，未创建。

## 任务依赖与覆盖

T1 → T2，串行；共享CSS与测试文件，不能并行写。R1、R2、R3均由T1/T2共同覆盖。候选文件仅在计划批准及控制门禁通过后创建。

## T1：投产方案时序与指令抽屉支持互相独立的全屏和还原

需求映射：R1、R2、R3。前置：无。

### 已证实事实

- ReleaseDrillPlanView.vue中timelineDrawer、itemDrawer为两个独立ref；对应size为min(980px,100vw)、min(680px,94vw)
- 未使用UiFormDrawer，不改原表单与事件函数
- Element Plus图标库已导出FullScreen与ScaleToOriginal

### 文件与接口

新建：web/src/modules/release/composables/useReleaseDrawerFullscreen.ts；web/src/modules/release/components/ReleaseDrawerHeader.vue；docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs。
修改：web/src/modules/release/components/ReleaseDrillPlanView.vue；web/src/modules/release/release-operations.css。
测试：docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs。

消费：Vue Ref<boolean>/ref/computed/watch；Element Plus el-drawer #header的titleId/titleClass；原timelineDrawer、itemDrawer与标题表达式。
产出：useReleaseDrawerFullscreen(open: Ref<boolean>, defaultSize: string): {fullscreen: Ref<boolean>,size: ComputedRef<string>,toggle: () => void}；ReleaseDrawerHeader props title:string,titleId?:string,titleClass?:string,fullscreen:boolean; emit toggle:[]；专属类release-operations-fullscreen-drawer；表单内容类release-operations-fullscreen-form。

### 操作与采样

- [x] T1-S1：记录当前4个业务组件和模块CSS差异基线

运行：`git diff --stat -- web/src/modules/release`。

预期：识别已有改动，不覆盖或回退。证据：基线diff和文件归属。

- [x] T1-S2：新建局部Node测试并先验证失败信号

运行：`node docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs --phase T1`。

预期：帮助函数未实现时明确失败，不触碰真实数据。证据：退出码和失败断言。

- [x] T1-S3：实现帮助函数及头部控件，仅接入方案的两个抽屉

预期：默认false；全屏size=100vw；再次切换还原；关闭重开false；两实例互不影响；header按钮native-type=button。证据：实际diff和接口对照。

- [x] T1-S4：增加只命中全屏class的视口尺寸和滚动规则

预期：覆盖旧max-width；正文可收缩滚动，header/footer不收缩；普通尺寸不变。证据：专属CSS差异。

- [x] T1-S5：运行局部状态和模板检查

运行：`node docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs --phase T1`。

预期：开关/重开/双实例/零业务请求及两个接入点通过。证据：断言名称、退出码。

- [x] T1-S6：运行前端类型及打包检查

运行：`npm --prefix web run build`。

预期：退出0，既有警告单列。证据：vue-tsc/Vite结果。

- [ ] T1-S7：验证父子抽屉的真实界面并形成一次采样

预期：在方案页面全屏父层、打开/全屏/关闭子层、父层保持；未授权则明确缺口。证据：execution-T1/observation-T1及浏览器证据或限制。


### 验收、回退与边界

- 投产/回退共用时序抽屉、指令抽屉都可放大还原
- 父子不串状态；标题id和关闭按钮不丢失
- 原表单内容、打开和CRUD函数不变

风险：Element Plus插槽标题语义；Teleport规则优先级；关闭动画期间重开。
回退：只撤销本任务新增帮助函数/头部、方案接入和专属CSS，不回退方案页面原有改版。
停止条件：需要改公共组件、依赖或CRUD。
升级条件：原层级机制无法容纳父子全屏，需扩大行为范围。


## T2：环境、演练步骤与问题抽屉完成全屏接入并集成回归

需求映射：R1、R2、R3。前置：T1。

### 已证实事实

- 环境dialogOpen size=min(760px,94vw)
- 演练stepDrawer size=min(680px,calc(100vw - 24px))，closeStep保留保存中关闭保护
- 问题drawerOpen size=min(760px,calc(100vw - 24px))，详情/新增/编辑共用；closeDrawer处理保存及未保存确认
- 问题.issue-drawer.el-drawer含max-width规则

### 文件与接口

新建：无。
修改：web/src/modules/release/components/ReleaseDrillEnvironmentView.vue；web/src/modules/release/components/ReleaseDrillExecutionView.vue；web/src/modules/release/components/ReleaseIssueTrackingView.vue；web/src/modules/release/release-operations.css；docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs。
测试：docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs。

消费：T1帮助函数、头部控件及CSS；原dialogOpen/stepDrawer/drawerOpen refs、标题、before-close、show-close和footer。
产出：全部5个抽屉实例接入；未新增公共接口。

### 操作与采样

- [x] T2-S1：扩展局部检查覆盖全部5处接入与关闭保护

运行：`node docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs --phase all`。

预期：接入前准确定位剩余3处缺失。证据：预期失败断言。

- [x] T2-S2：接入剩余3处抽屉，保留标题表达式及全部事件

预期：更换静态size为帮助函数计算值，添加header插槽及专属class；不修改保存/关闭函数和表单v-model。证据：实际diff。

- [x] T2-S3：限定全屏表单内层最大宽度，处理问题详情及手机布局

预期：全屏外壳占满；表单桌面最大960px、width100%；手机单列；时序画布不受表单限宽。证据：CSS作用域与视口检查。

- [x] T2-S4：运行全部局部测试

运行：`node docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs --phase all`。

预期：5处接入、开关重开、实例隔离、保留关闭保护与无请求通过。证据：测试原始输出。

- [x] T2-S5：执行完整前端构建

运行：`npm --prefix web run build`。

预期：退出0；警告如实记录。证据：类型及打包结果。

- [x] T2-S6：检查空白及治理（差异通过，治理历史失败详见验收记录）

运行：`git diff --check`。

预期：退出0；再执行check-all-governance和当前范围检查，旧失败独立报告。证据：真实命令和结果。

- [ ] T2-S7：在4类视口、明暗主题下进行真实操作验收

预期：五类抽屉全部支持全屏/还原；字段输入和选中对象保留；保存中切换不提交或关闭；原取消、确认、Esc语义不变；项目切换重置；无页面溢出。证据：浏览器截图/DOM尺寸/网络及控制台，或明确缺口。

- [x] T2-S8：记录执行、采样与交付边界

预期：只修改授权文件；无独立或浏览器证据则保留observing，不宣称converged。证据：execution-T2/observation-T2和最终账本状态。


### 验收、回退与边界

- 环境新增/编辑、步骤新增/编辑、问题详情/新增/编辑均可全屏还原
- 输入不丢失、0额外业务请求、0误提交
- 原关闭保护、权限、未保存提示不变
- 4种视口和主题尺寸可达

风险：问题页非scoped样式优先级；长标题挤压两个图标；全屏表单宽度与页脚错位。
回退：仅撤销T2接入和本次专属样式；不撤销演练/问题改版及数据。
停止条件：需要更改关闭保护或后端业务行为。
升级条件：必须修改全局抽屉或应用壳才能满足视口要求。

## 接口与实现定位

帮助函数使用独立ref，初始false；size计算为全屏时100vw，否则原尺寸。toggle仅在抽屉打开时切换；watch在每次open变为true时同步重置，避免关动画中提前收窄。切换时不设置open、不访问API、不改表单，不加改变实例身份的key。组件卸载时watch由Vue自动清理。

每个调用点将帮助函数返回值解构为顶层ref，避免模板中嵌套ref的解包歧义。接入形式如下（计划片段，不是已实施代码）：

```ts
const { fullscreen: stepFullscreen, size: stepSize, toggle: toggleStepFullscreen } =
  useReleaseDrawerFullscreen(stepDrawer, 'min(680px, calc(100vw - 24px))')
```

执行时默认值与原size逐字核对。使用header插槽的titleId/titleClass绑定标题，避免丢失aria-labelledby；原show-close和before-close全部保留。图标使用已验证存在的FullScreen/ScaleToOriginal，native-type=button，aria-label及Tooltip分别为全屏/还原。

全屏class使用 `.release-operations-fullscreen-drawer.el-drawer`，将width/max-width设为100vw、height/max-height为100dvh，并采用足够但仅限本class的选择器优先级覆盖问题抽屉max-width。不得用通用.el-drawer或全局!important修复单个实例。正文min-height:0、独立overflow-y:auto，头尾flex-shrink:0。全屏表单内层最大960px、width100%、box-sizing:border-box；普通模式不受影响。手机保留内部安全边距，时序区域不套表单限宽。

## 集成验证矩阵

- 方案时序：投产、回退分别全屏/还原；时序中打开指令子层，验证父子状态互不影响。
- 指令、环境、步骤：新增和编辑输入后切换两次；字段、关联和时间范围保持，网络没有新增请求。
- 问题：详情、新增、编辑全部接入；未保存确认和保存中关闭保护保持。
- 关闭重开：包括取消、原X和Esc允许路径，再打开回到普通尺寸；不额外改变关闭语义。
- 视口：1280x800、375x812、390x844、430x932；明暗主题、长标题、正文滚动与页脚可达。
- 全屏尺寸：抽屉getBoundingClientRect覆盖内容视口；document.documentElement.scrollWidth不得大于视口宽度。普通模式恢复原尺寸约束。
- 项目切换：旧组件销毁，新组件无遗留全屏；不调用旧项目请求或误保存。

测试文件使用Node内置assert，借助现有Vue/TypeScript解析帮助函数与SFC模板，不新增测试框架；测试创建发生在执行阶段。记录合成检查与真实浏览器证据的区别，不把模板断言当作实际尺寸。

运行：`node docs/requirements/REQ-20260907-069-release-drawer-fullscreen/fullscreen.test.mjs --phase all`。
预期：全部5处的状态及接入检查通过。

运行：`npm --prefix web run build`。
预期：退出0。

运行：`node scripts/check-all-governance.mjs`。
预期：报告真实结果；历史REQ061 YAML故障不在本任务修复。

运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260907-069-release-drawer-fullscreen/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`。
预期：报告真实结果；既有licon命名与此前改动分别归属。

## 控制种子与批准边界

交接包control_seed为hypotheses-only，已重新对照代码事实建模。用户确认后设置plan_status=ready、plan approval=approved、handoff_status=approved；已导入并从baseline/modeling/planning依次进入实施与观测。当前phase=observing，保留真实浏览器证据缺口。

本次沿用当前licon工作区，只做授权增量；不提交、推送、重启、切分支。历史治理错误和已有其他任务改动只记录，不扩大整改。浏览器权限缺口如实报告，缺失独立/真实验收证据时保留observing。

无需再次批准本计划。代码已实施，T1-S7/T2-S7真实浏览器检查尚未完成。
