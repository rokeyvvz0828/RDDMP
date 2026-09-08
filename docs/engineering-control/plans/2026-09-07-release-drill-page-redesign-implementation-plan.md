# 投产演练页面改版实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 保持现有业务功能，以窄轮次导航、宽详情及响应式步骤改善投产演练页面。

**架构：** 只修改 ReleaseDrillExecutionView.vue 及本任务记录，复用现有数据和 CRUD。桌面导航与手机选择器共用 selectedId，轮次仍用弹窗，步骤仍用抽屉。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite；使用已安装工具，不新增依赖。

## 状态与来源

计划修订 1 已由用户在收到实施计划链接后回复“确认”批准；设计修订 1 已批准。
设计文档：docs/engineering-control/designs/2026-09-07-release-drill-page-redesign-design.md。
需求/范围：docs/requirements/REQ-20260907-067-release-drill-page-redesign/。
交接包：.ai-control/requirements/req-20260907-067-release-drill-page-redesign/handoff.json。
按当前批准计划导入本任务前缀账本，经 baseline/modeling/planning 门禁后实施，不扩大范围。

## 全局约束

- 仅变更演练组件及本任务元数据
- 继续当前 licon 工作区，不提交、推送、切分支或重启服务
- 不修改共享 UI、共享 CSS、API、路由、后端、数据库或依赖
- 不读取 .env、敏感数据或生产环境
- 完成设计文档复核及实施计划批准后才实施
- Vue3/TypeScript/Element Plus现有依赖，不增加测试框架
- 页面结构复用交付示范；样式只作用本组件
- 不扩大其他投产页面、后端或数据库范围

## 文件职责地图

- web/src/modules/release/components/ReleaseDrillExecutionView.vue：existing，演练状态、页面展示及轮次/步骤维护。依据：已完整读取；目标git diff为空。
- .ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json：candidate-new，本任务可重复的合成状态断言，仅供验证。依据：授权账本路径，计划新增，不冒充既有测试。

前端包目前没有配置独立单测运行器。state-test.json 的 script 字段可保存只读提取组件 script setup、通过现有编译器处理并注入合成 API 的 Node 断言；运行时不得读凭据、访问浏览器或真实数据库，不将它等同浏览器测试。已有目标组件无 diff，其他页面既有修改保留。

## 任务依赖与覆盖

T1 -> T2，写入同一个组件和测试文件，必须串行。每个任务结束做一次采样和工作区差异检查，不建立 Git 提交。

- R1：T1
- R2：T1、T2
- R3：T1、T2
- R4：T1、T2
- R5：T1、T2

## T1：轮次导航、当前详情和步骤的响应式展示改版

需求：R1、R2、R3、R4、R5。前置：无。

### 输入事实与文件

- ReleaseDrillExecutionView.vue 当前无未提交修改，模板用共享 release-operations-grid 将左区设得比详情宽
- rounds 与 selectedId 是单一数据/选择状态，selectedRound() 返回匹配项或首项
- DTO 已返回 roundNo/roundName/status/resultContent/releasePlanName/environmentName/plannedAt/steps
- 父 ReleaseOperationsManagement.vue 使用 drills-projectRef key 重建页面；不修改父组件

新建：.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json。
修改：web/src/modules/release/components/ReleaseDrillExecutionView.vue。
测试：.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json、web/src/modules/release/components/ReleaseDrillExecutionView.vue。

消费：ReleaseDrillExecutionDto、ReleaseDrillStepDto；rounds、selectedId、selectedRound()、load()；openRound(round?)、removeRound(round)、openStep(step?)、removeStep(step)；canManage()、UiPageHeader、UiStatusTag、UiEmptyState。
产出：同一 selectedId 驱动桌面导航和手机选择器的模板；局部 .drill-page/.drill-layout/.drill-step-track 响应式样式。

### 步骤与采样

- [x] T1-S1：记录目标组件无改动基线及原功能入口、字段、状态和 payload 清单

运行：`git diff -- web/src/modules/release/components/ReleaseDrillExecutionView.vue`。

预期：空 diff；既有其他文件改动仅记录不修改。证据：目标 diff、轮次和步骤原契约清单。

- [x] T1-S2：建立独立虚构数据的组件脚本断言，不访问浏览器、网络或真实数据库；脚本保存为当前任务 state-test.json 的 script 字段

预期：断言覆盖两个轮次的 selectedId 切换、刷新保留选中、无轮次回退、原 API projectId 参数；类型编译使用已有 Vue/TypeScript 包。证据：可重复 test JSON 和测试局限。

- [x] T1-S3：运行基准脚本

运行：`node -e "eval(JSON.parse(require('fs').readFileSync('.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json','utf8')).script)"`。

预期：原行为断言通过；新布局要求由之后的模板及浏览器采样证明，不将基准通过视为改版完成。证据：断言名称、退出码。

- [x] T1-S4：引入 UiPageHeader，统一标题、刷新和新增轮次；去除英文装饰标题及重复描述，用约260px窄导航和可收缩详情替换大面板嵌套

预期：原新增禁用条件与管理权限不变；左侧名称/状态/步骤数绑定原 DTO，右侧方案/环境/时间/结果分区。证据：模板差异和字段对应。

- [x] T1-S5：重排步骤节点为桌面约260px横向顺序节点；手机<=760px用同一 selectedId 的轮次选择器和纵向节点

预期：步骤名称、时间范围、负责人、状态、结果分行；横滚仅在桌面步骤区；无新时间比例、请求、筛选或统计。证据：专用CSS、DOM/截图及未测项。

- [x] T1-S6：运行局部断言和模板编译

运行：`node -e "eval(JSON.parse(require('fs').readFileSync('.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json','utf8')).script)"`。

预期：选择状态、刷新、字段和API不变量断言通过。证据：测试输出。

- [x] T1-S7：检查差异空白

运行：`git diff --check`。

预期：退出0，CRLF提示单列。证据：命令退出码。

- [x] T1-S8：执行前端类型及打包检查

运行：`npm --prefix web run build`。

预期：退出0；记录警告，不把打包成功等同布局验收。证据：vue-tsc与Vite结果。

- [ ] T1-S9：在可授权真实浏览器中测量桌面、手机的导航、步骤和当前轮次；保存 execution-T1 与 observation-T1

预期：1280x800、375x812、390x844、430x932无页面溢出且选择一致；工具不可用则明确待验收。证据：实际视口、路由、截图/尺寸或覆盖缺口。

### 验收与回退

- 标题和操作只保留已确认结构，详情区大于导航区
- 轮次选择、刷新和步骤对应不变
- 所有摘要和步骤字段可读，手机纵向步骤
- 无共享CSS或API变更

风险：共享CSS间接覆盖；浏览器授权不可用；长名称与结果。
回退：只撤销T1在演练组件的展示和样式hunks及本任务记录；保留其他页面改动。
停止条件：需要修改共享样式/父组件/API或未建模字段。
升级条件：新增展示需要后端字段或项目隔离契约变化。

## T2：保留原轮次弹窗和步骤抽屉的分区表单及完整回归

需求：R2、R3、R4、R5。前置：T1。

### 输入事实与文件

- 轮次维护使用 el-dialog，步骤使用 el-drawer，原字段和必填规则已逐项读取
- roundForm 与 stepForm 由 openRound/openStep 赋值；saveStep 将 range 拆成 DTO 的 plannedStart/plannedEnd
- 原保存分别用 saving/stepSaving 防重复；删除忽略 cancel/close
- web/package.json 只有 dev/build/preview，没有专用单测框架

新建：无。
修改：web/src/modules/release/components/ReleaseDrillExecutionView.vue、.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json。
测试：.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json、web/src/modules/release/components/ReleaseDrillExecutionView.vue。

消费：T1 原状态和局部模板；ReleaseDrillExecutionWrite、ReleaseDrillStepWrite；roundForm、stepForm、roundDialog、stepDrawer、editingRound、editingStep；create/update/deleteReleaseDrill 与 create/update/deleteReleaseDrillStep。
产出：无新公开接口。

### 步骤与采样

- [x] T2-S1：保留轮次 el-dialog 和步骤 el-drawer；只按关联/基础、时间/执行、结果/说明分区，保留全部输入与原字符长度、状态选项及必填条件

预期：没有丢失字段或增加必填；seqNo仍1-999；步骤仍用单个datetimerange；最多680px并受手机安全边距约束。证据：原字段逐项diff对照。

- [x] T2-S2：给本组件弹层限定高度、独立正文滚动与可达页脚；为提交期关闭/取消入口和表单提供一致保护

预期：保存中不能重复请求或关闭引起状态丢失；失败保留输入；原确认取消不写数据。证据：弹层模板、局部CSS及状态断言。

- [x] T2-S3：扩展合成脚本覆盖轮次和步骤的原增改删除、校验、失败保留、取消删除、当前项目与轮次参数

预期：两个虚构项目/轮次分别断言；创建后选中新轮次、删除后回选首轮、步骤保存按seqNo排序、关闭确认不DELETE；403与错误重试可区分。证据：state-test.json断言和局限，不伪造真实服务证据。

- [x] T2-S4：运行合成状态检查

运行：`node -e "eval(JSON.parse(require('fs').readFileSync('.ai-control/requirements/req-20260907-067-release-drill-page-redesign/state-test.json','utf8')).script)"`。

预期：退出0；每项断言名称可追踪到R2/R3/R5。证据：测试原始输出。

- [ ] T2-S5：真实浏览器回归轮次弹窗和步骤抽屉、权限、项目切换、加载空态失败与长内容；只使用独立虚构记录做真实CRUD并删除本次夹具

预期：原操作均可达，取消不报错、不更新数据库，保存值重读一致，四个视口明暗主题无遮挡；无安全测试条件就明确未测，不触碰既有记录。证据：角色、路由、实际动作/结果、网络/控制台/尺寸；缺口独立列出。

- [x] T2-S6：检查差异空白

运行：`git diff --check`。

预期：退出0。证据：退出码。

- [x] T2-S7：集成构建

运行：`npm --prefix web run build`。

预期：退出0，现有警告独立记录。证据：编译和打包输出。

- [x] T2-S8：执行全仓治理

运行：`node scripts/check-all-governance.mjs`。

预期：如实记录结果；既有REQ061 YAML问题不属于本次修改，不绕过。证据：检查输出和归因。

- [x] T2-S9：执行任务范围检查并手工归属当前diff

运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260907-067-release-drill-page-redesign/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`。

预期：本次新增改动只在授权范围，现有licon命名和其他已有文件改动单列，不能擅自改分支。证据：脚本结果和实际差异清单。

- [x] T2-S10：记录execution-T2/observation-T2及各任务采样，按control-engineering判断是否具备独立验收条件

预期：未获浏览器及独立证据不得宣称converged；不提交、不推送、不重启。证据：任务前缀账本及真实剩余风险。

### 验收与回退

- 轮次与步骤原字段和验证未变化
- 弹层正文滚动且页脚可达，取消与保存中行为正确
- CRUD 参数、版本、权限与项目切换回归
- 运行证据与合成检查明确区分

风险：弹层Teleport样式未命中；保存时目标轮次变化；真实浏览器或只读角色缺失。
回退：只撤销T2弹层和状态保护专项hunks及本任务测试记录；不撤销T1或此前任务。
停止条件：必须改变DTO、后端校验、状态枚举或共享UI才能继续。
升级条件：发现跨项目/轮次提交、权限回归或无法安全完成真实写入验收。

## 实现定位与片段

T1 改动集中在现有 template 的 release-operations-grid/release-drill-manager、轮次列表和步骤节点，不修改 CRUD 接口。可采用以下局部布局，实际尺寸必须在浏览器中复核：

```css
.drill-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 20px; min-width: 0; }
.drill-step-track { display: flex; gap: 16px; overflow-x: auto; min-width: 0; }
.drill-step { flex: 0 0 260px; min-width: 0; }
@media (max-width: 760px) {
  .drill-layout { grid-template-columns: minmax(0, 1fr); }
  .drill-step-track { flex-direction: column; overflow-x: visible; }
  .drill-step { flex: none; width: 100%; }
}
```

轮次选择器直接使用 `v-model="selectedId"` 和 rounds 选项，与桌面原 `@click="selectedId = round.id"` 共用状态，不发起新的列表请求。保留 selectedRound() 语义，右侧无选中时保留空态。

T2 定位现有 roundDialog 和 stepDrawer 区域。轮次 releasePlanId/environmentId/roundName 必填；plannedAt、status、resultContent 原样提交。步骤 stepName 必填，seqNo 原1-999限制；range仍为一个 datetimerange，提交仍以 range[0]/range[1] 映射 plannedStart/plannedEnd。ownerId、status、resultContent、description、rowVersion不丢失。不增加业务校验或修改状态枚举。

弹层使用本组件独立class限定Teleport样式；页头、页脚不随正文滚动。原取消、关闭和遮罩入口在 saving/stepSaving 期间一致禁用，其他时候保留取消语义。异步上下文保护不得改变 projectId/roundId 所有权或修改父组件。

## 集成验证矩阵

- R1：合成两轮次 A/B，选择 B 后刷新仍显示 B；无轮次回退空态；桌面约260px导航、主体详情更宽。
- R2：新轮次必须有方案与环境、非空名称；新增后选中；编辑保留版本与原全部字段；删除后回首轮。
- R3：两个不同序号步骤保存后仍按序号；时间范围输入保持一体；新增、编辑、删除请求均携带正确轮次；取消无请求。
- R4：1280x800或更大与375x812、390x844、430x932，浅深主题检查长名称、日期控件、轮次选择器、横/纵步骤、弹层滚动及页脚。 document.documentElement.scrollWidth 不超过视口宽度。
- R5：只读用户无维护入口；项目切换不留旧表单或步骤；空、缺少前置数据、加载、403、网络失败/重试、保存失败和连续点击分别验证。

命令依据任务步骤执行并记录真实退出码。浏览器验证只在获得访问权限与安全测试数据时执行。前一任务浏览器授权曾两次超时，本计划不推断授权已恢复，也不通过其他接口绕过。缺失关键运行或独立证据时保留 observing，不宣称 converged。

## 控制种子与交接

control_seed.seed_status=hypotheses-only。候选边界为当前组件；候选状态是项目、轮次集合、选择、表单、弹层和异步标志；候选传感器是diff、编译、合成断言、真实浏览器。全局样式、数据长度、权限工具、异步响应及并行改动是待建模扰动。进入控制闭环后逐项验证，不把计划种子当成模型事实。

用户批准当前落盘计划后才设置 implementation_plan.plan_status=ready、implementation_plan.approval.status=approved 和 handoff_status=approved；完整导入本任务前缀后应位于 baseline，再完成 modeling/planning 门禁。不得编辑全局或其他任务账本。

## 风险与批准边界

没有数据库、后端、公共契约、生产数据和发布操作。沿用当前 licon 工作区，保护所有既有改动。治理脚本对历史REQ061 YAML及分支命名的失败如实报告，不擅自整改或绕过。

本计划与交接包已获用户批准，进入实施及验证；若发现必须扩大写入或业务范围，停止并说明证据。

## 实施采样记录（2026-09-07）

T1、T2 代码已实现，账本 phase=observing，两个任务均未标为 verified。完成勾选表示实施或命令已执行，不等同验收通过。

- 合成状态检查：基线24项、改版后40项通过；不访问真实数据库或浏览器。
- 前端构建：基线及两次实施后均通过，最终 Vite 38.38s；保留既有 PURE 注释和大包警告。
- git diff --check：退出0，仅CRLF提示。
- 全仓治理：退出1，被历史REQ061范围文件非JSON兼容YAML阻断；模块、Skill、开发入口检查通过。
- 任务范围脚本：退出1，被既有licon分支命名阻断；实际本次patch仅涉及演练组件和本任务元数据。
- 浏览器：此前访问授权超时，本轮异步授权询问尚无答复，未尝试绕过。T1-S9、T2-S5的真实页面测量与独立验收仍未执行。
- 待验收：桌面1280x800及375x812、390x844、430x932手机视口，明暗主题、长内容、页面横向溢出、弹层关闭/滚动、真实隔离CRUD、只读和项目切换。
- 代码范围：ReleaseDrillExecutionView.vue，复用UiPageHeader、UiStatusTag、UiEmptyState及原Element Plus控件；未改变后端、API、数据库、路由或共享样式，未读取.env，未重启或提交推送。
- 回退：只撤销该组件的本任务差异及本任务元数据，不撤销其他页面和其他任务的已有修改；无需数据回退。
