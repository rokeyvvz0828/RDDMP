# 业务详情内审批与工作流职责收敛实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将版本申请审批收敛到完整业务详情页，并让工作流管理只保留流程配置和监控，同时保留平台统一的任务授权、电子签名、决策和审计能力。

**架构：** 配置管理通过稳定路由 `/release/applications/:applicationCode` 拥有业务详情和审批界面，首页、任务中心、通知与历史链接只负责导航。工作流新增按当前登录人查询单任务上下文的公共接口，现有决策接口继续作为唯一写入口；配置管理业务数据暂由共享会话 Mock Repository 提供，任务状态和允许动作仍由工作流接口决定。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Flowable 7.0.1、JUnit 5、MySQL 8.4/Flyway、Vue 3、TypeScript 5.7、Vite 6、Element Plus 2.9、Pinia、Vue Router 4。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 需求：`REQ-20260815-027`
- 设计文档：`docs/engineering-control/designs/2026-08-15-business-owned-approval-design.md`
- 需求文档：`docs/requirements/REQ-20260815-027-business-owned-approval/requirement.md`
- 状态：已批准，可移交
- 当前分支：用户指定本地 `rokey`；不创建 worktree，不提交、不推送。

## 全局约束

- 只修改 `docs/requirements/REQ-20260815-027-business-owned-approval/codex-task-scope.yaml` 的 `writable_paths` 覆盖文件；其他未提交改动保持原样。
- 直接在本地 `rokey` 开发，不创建工作树，不提交或推送，除非用户另行要求。
- 保持 `com.ccb.*` 包名、Maven artifact、Vue 技术栈和现有依赖，不新增 npm/Maven 依赖。
- 配置管理拥有申请详情、交付单元、需求、附件和业务状态；工作流拥有任务状态、待办人、允许动作、内部电子签名和审计。
- 首页、任务中心和通知只聚合并跳转，不出现同意、退回、拒绝等快捷审批控件。
- 保留 `/api/workflows/inbox`、`/api/workflows/done`、`/api/workflows/tasks/{id}/decision` 和监控接口；不修改 Flowable 私有表或历史 Flyway。
- 单任务上下文和决策都从认证主体取得租户和用户，不接受客户端指定审批人或签署人。
- 配置管理业务数据继续使用可替换 Mock Provider；项目、物理子系统、交付单元和需求正式接入不在本计划范围。
- 旧 `/workflow/inbox`、`/workflow/done`、`/workflow/review/:taskId` 必须安全兼容，不保留通用审批页面。
- 前端复用 `UiPageHeader`、`UiToolbar`、`UiDataTable`、`UiStatusTag`、`UiEmptyState` 及交付示范中心移动卡片模式；业务专项样式仅限 release 与 task-center 作用域。
- 页面覆盖加载、空、失败、无权限、只读、提交中、重复提交、任务过期、业务不存在和长文本状态。
- 验收视口固定为 `1280x800`、`375x812`、`390x844`、`430x932`，检查浅色与深色主题及页面级横向溢出。
- Maven 命令显式使用 `JAVA_HOME=$(/usr/libexec/java_home -v 17)`；默认 Java 26 不作为有效验证环境。
- Flyway 回退只能追加新的前向迁移恢复菜单，不删除或修改 `V37`。
- `node scripts/check-codex-scope.mjs --scope ...` 当前会因用户指定的 `rokey` 不满足通用分支正则而失败；实施中记录该已知事实，不修改分支、不修改治理检查器、不把失败报告为通过。

## 文件职责地图

| 文件 | 状态 | 单一职责 |
|---|---|---|
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java` | existing-modified | 当前登录人的单任务上下文投影，以及现有决策服务端复核 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java` | existing-modified | 暴露 `GET /api/workflows/tasks/{taskId}/context` |
| `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowServiceTaskContextTest.java` | candidate-new | 单任务正常、越权、已处理、不存在和动作投影测试 |
| `server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowTaskProjectionTest.java` | existing-untracked | 待办/已办稳定业务路由投影回归 |
| `web/src/api/workflow.ts` | existing-modified | `WorkflowTaskContext` 类型和上下文查询 API |
| `web/src/modules/release/repository.ts` | candidate-new | 跨路由共享的确定性会话 Mock Repository、按申请单号查询和决策后业务投影更新 |
| `web/src/modules/release/ReleaseApplicationDetailPage.vue` | candidate-new | 完整业务详情、流程进展、日志和审批面板的页面所有者 |
| `web/src/modules/release/components/ReleaseApprovalPanel.vue` | candidate-new | 当前任务上下文、意见、签署和允许动作提交 |
| `web/src/modules/release/components/ReleaseDetailDrawer.vue` | existing-untracked | 详情抽屉改为进入稳定业务详情，不再进入工作流审批页 |
| `web/src/modules/release/components/ReleaseApplicationView.vue` | existing-untracked | 版本申请列表统一进入业务详情路由 |
| `web/src/modules/release/ReleaseManagementPrototype.vue` | existing-untracked | 改为消费共享 Repository，保持五视图业务操作 |
| `web/src/modules/release/ReleaseWorkflowReviewPage.vue` | existing-untracked | 从通用审批实现退役为旧链接兼容跳转，不再承载审批 |
| `web/src/modules/release/types.ts` | existing-untracked | 增补业务详情和 Mock 决策同步所需类型 |
| `web/src/modules/release/mock.ts` | existing-untracked | 保留虚构事实和纯派生函数，提供稳定申请编码样例 |
| `web/src/modules/release/release-prototype.css` | existing-untracked | 业务详情、审批面板和移动布局的模块作用域样式 |
| `web/src/views/TaskCenterView.vue` | candidate-new | 待办/已办分页、项目筛选和业务跳转，不承载审批 |
| `web/src/views/DashboardView.vue` | existing-modified | 首页五条聚合、参数安全附加和“查看全部”跳转 |
| `web/src/views/WorkflowView.vue` | existing-modified | 仅保留流程定义、配置和流程监控 |
| `web/src/router/index.ts` | existing-modified | 稳定业务详情、任务中心和旧地址兼容路由 |
| `web/src/views/AppLayout.vue` | existing-modified | 任务中心和业务详情标题回退；移除旧待办标题 |
| `web/src/styles.css` | existing-modified | 任务中心桌面表格/移动卡片的共享页面样式 |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V37__business_owned_workflow_approval.sql` | candidate-new | 删除菜单 202、204 及全部角色关联 |
| `docs/integration/workflow-module-contract.md` | existing-modified | 单任务上下文、业务拥有审批 UI 和兼容路由契约 |
| `.ai-control/requirements/req-20260815-027-business-owned-approval/*.json` | current-prefix | 本需求模型、计划、执行、观测和收敛证据 |

## 任务依赖与并行策略

```text
T1 工作流单任务上下文与授权测试
  -> T2 共享业务详情与内嵌审批
      -> T3 任务中心、首页、工作流页面和兼容路由
          -> T4 菜单前向迁移与公共契约
              -> T5 集成、浏览器观测和收敛验收
```

全部任务串行。T1 固定公共接口，T2 消费接口并重建 release 共享状态，T3 同时触及路由和导航，T4 改变菜单数据，T5 验证组合结果；这些任务存在接口或共享写入依赖，不能证明并行安全。

## 需求覆盖

| 需求 | 任务 |
|---|---|
| R1 业务详情拥有审批界面 | T2、T5 |
| R2 全入口统一业务路由 | T2、T3、T5 |
| R3 首页与任务中心仅聚合跳转 | T3、T5 |
| R4 工作流仅定义与监控 | T3、T4、T5 |
| R5 单任务上下文能力 | T1、T2 |
| R6 服务端授权和状态复核 | T1、T5 |
| R7 审批成功原页刷新 | T2、T5 |
| R8 历史地址安全兼容 | T3、T5 |
| R9 前向迁移下线菜单且 API 保留 | T4、T5 |
| R10 桌面与手机响应式审批 | T2、T3、T5 |

---

### T1：提供当前用户单任务上下文与授权回归

**需求映射：** R5、R6、R9

**前置任务：** 无

**文件：**
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`
- 新建：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowServiceTaskContextTest.java`
- 修改：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowTaskProjectionTest.java`
- 修改：`web/src/api/workflow.ts`

**接口：**
- 消费：`wf_task`、`wf_instance`、发布版本 `definition_json`、`WorkflowNodeLabelResolver`、`WorkflowSignatureService.required(taskId, tenantId)` 和认证 `AuthUser`。
- 产出：`GET /api/workflows/tasks/{taskId}/context`。
- 产出类型：`WorkflowTaskContext`，字段固定为 `task_id`、`instance_id`、`business_key`、`business_type`、`business_title`、`business_round`、`project_ref`、`project_name`、`action_path`、`task_key`、`node_id`、`node_name`、`task_type`、`task_status`、`instance_status`、`allowed_actions`、`signature_required`、`actionable`。
- 错误语义：任务不存在或业务上下文不可用返回冲突；任务属于同租户其他用户返回禁止；本人已处理任务返回 `actionable=false` 和空动作，不返回可操作能力。

- [ ] **步骤 1：建立工作流测试基准**

  运行：`JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test`

  预期：退出码为 0；若既有测试失败，记录首个失败并停止，不把基线故障归入 T1。

  证据：Maven 模块汇总、测试数和退出码。

- [ ] **步骤 2：先写单任务上下文失败测试**

  在 `WorkflowServiceTaskContextTest` 构造受控 `JdbcTemplate`、节点标签和签名服务，分别断言：本人待办返回稳定业务路由、节点、`actionable=true`、签名要求和节点动作；本人已处理返回只读；其他待办人抛 `ErrorCode.FORBIDDEN`；不存在任务抛冲突；无效或外部 `action_path` 不向前端暴露可操作上下文。

  运行：`JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am -Dtest=WorkflowServiceTaskContextTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：测试先因 `taskContext` 尚不存在而失败；错误明确指向缺少方法或端点能力。

  证据：失败测试名和关键断言。

- [ ] **步骤 3：实现服务端单任务上下文投影**

  `WorkflowService.taskContext(long taskId, AuthUser user)` 先按租户读取任务、实例和业务上下文，再比较 `assignee_id`；仅 `PENDING`、实例 `RUNNING`、非抄送任务且当前用户为待办人时计算 `actionable=true`。`allowed_actions` 从当前发布版本节点 `config.actionPolicy.allowedActions` 读取，过滤平台已知动作；签名要求只由 `WorkflowSignatureService` 读取。上下文不得扫描 inbox，不接受客户端用户 ID。

  预期：同一任务状态变化后上下文立即转为只读；不泄露其他租户或其他待办人的动作能力。

  证据：服务方法测试和 SQL 参数审阅。

- [ ] **步骤 4：暴露 Controller 和 TypeScript 客户端**

  Controller 增加 `@GetMapping("/tasks/{id}/context")` 并复用类级权限；`web/src/api/workflow.ts` 增加 `WorkflowTaskContext` 与 `getWorkflowTaskContext(id)`。现有 inbox、done、decision 签名不变。

  预期：响应继续使用 `ApiResponse`；前端无需拼装 URL 或猜测动作。

  证据：Java 编译和 TypeScript 类型检查。

- [ ] **步骤 5：复核决策的二次授权与兼容动作**

  保持 `/decision` 在事务内重新检查租户、待办人、任务状态、动作策略和当前登录身份签名；若 `WorkflowService` 的旧兼容执行路径与公开动作契约不一致，只在当前可写 Service 内补齐一致校验，不修改 `FlowableWorkflowService`。

  预期：重复提交得到 `409`，越权得到 `403`，签名要求未确认得到冲突；允许动作以服务端定义为准。

  证据：正常、越权、已处理、不存在和重复请求测试。

- [ ] **步骤 6：运行局部回归**

  运行：`JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test`

  运行：`npm --prefix web run build`

  预期：两条命令退出码均为 0。

  证据：测试汇总、构建汇总和退出码。

**验收检查：** 单任务查询不扫描 inbox；本人待办、本人已办、越权、不存在和重复决策可区分；旧 API 保持可用。

**风险：** 兼容旧流程模型的节点动作可能没有显式 `actionPolicy`；必须采用现有公开动作兼容规则，不能由前端任意扩权。

**回滚：** 删除上下文端点、类型和测试，恢复 Service/Controller；不改表、不影响 inbox/done/decision。

**停止条件：** 必须修改安全模块、Flowable 私有表或只读 `FlowableWorkflowService` 才能满足授权；现有模型无法确定允许动作且没有兼容事实。

**升级条件：** 公开响应需要新增共享 Java DTO 包、改变既有决策语义或修改平台权限码。

---

### T2：交付共享业务详情与内嵌审批面板

**需求映射：** R1、R2、R5、R7、R10

**前置任务：** T1

**文件：**
- 新建：`web/src/modules/release/repository.ts`
- 新建：`web/src/modules/release/ReleaseApplicationDetailPage.vue`
- 新建：`web/src/modules/release/components/ReleaseApprovalPanel.vue`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/components/ReleaseDetailDrawer.vue`
- 修改：`web/src/modules/release/types.ts`
- 修改：`web/src/modules/release/mock.ts`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**
- 消费：路由参数 `applicationCode`，可选查询参数 `taskId`、`instanceId`，T1 的 `getWorkflowTaskContext`、`decideWorkflowTask`、`getWorkflowInstanceDetail`。
- 产出：`useReleaseRepository()`，至少提供共享 `state`、`findApplicationByCode(code)`、`recordWorkflowDecision(applicationCode, context, action, comment)`、`resetForDebug()`。
- 产出：`ReleaseApprovalPanel` props 为 `applicationCode`、`taskId`，事件为 `decided`；组件不接收审批人或签署人 ID。
- 产出：稳定业务页面 `/release/applications/:applicationCode` 的完整可刷新内容。

- [ ] **步骤 1：建立跨路由状态基准**

  运行：`npm --prefix web run build`

  预期：退出码为 0；记录现有 `ReleaseManagementPrototype` 和旧审批页分别创建状态导致的跨路由断裂事实。

  证据：构建退出码和 `createReleasePrototypeState()` 调用位置清单。

- [ ] **步骤 2：建立共享会话 Mock Repository**

  将 release 状态所有权移到模块级响应式 Repository，原型壳层和独立详情路由消费同一实例。按申请单号查询必须确定性；为工作流投影中可到达的虚构业务键提供完整 Mock 业务详情，不能仅展示任务摘要。决策成功后 Repository 只同步当前 Mock 申请的业务状态、流程节点和审计日志，不伪造服务端任务成功。

  预期：从列表、首页任务进入同一申请后看到同一业务事实；刷新可从确定性 Mock 重建，不依赖前一个页面内存对象引用。

  证据：类型检查、路由刷新和跨入口内容核对。

- [ ] **步骤 3：实现完整业务详情页面**

  页面按顺序展示申请摘要、物理子系统、交付单元与版本、需求、附件、审批进展和流转日志；复用现有详情抽屉的信息结构，但独立页面拥有返回、刷新和错误恢复。业务不存在或加载失败时显示明确结果且不渲染审批面板。

  预期：列表和历史已办无 `taskId` 时页面完整只读；`instanceId` 存在时加载流程详情与日志，失败不影响已加载的业务事实但显示流程失败状态。

  证据：只读、业务不存在、流程失败和长文本浏览器路径。

- [ ] **步骤 4：实现条件审批面板**

  仅存在 `taskId` 且完整业务详情加载成功时请求上下文。`actionable=true` 才显示服务端 `allowed_actions` 对应的同意、退回、拒绝；审批意见在动作要求下校验，签名节点必须勾选“使用当前登录身份确认签署”。提交中禁用全部动作并防重复，`403` 转无权限，`409` 保留意见并刷新上下文，其他错误保留意见允许重试。

  预期：页面不扫描 inbox、不从 Mock 推断真实权限；成功后留在原页，刷新任务上下文、流程进展和 Repository 业务投影；任务变为只读。

  证据：有效、越权、已处理、失效、重复点击和决策失败路径。

- [ ] **步骤 5：统一 release 模块入口**

  版本申请列表点击单号/详情，以及详情抽屉主操作均导航 `/release/applications/{code}`；移除“打开审核页面”到 `/workflow/review` 的行为。原型壳层改用共享 Repository，开发调试重置仍只在既有 debug 入口可见。

  预期：业务详情唯一，抽屉不再是审批容器，release 模块中没有新的通用审批入口。

  证据：`rg` 路由引用和浏览器导航记录。

- [ ] **步骤 6：完成桌面与移动布局**

  桌面使用主详情区加审批侧栏；`760px` 以下重排为单列业务区和可达审批操作区，审批低频危险操作进入更多菜单或独立确认，不使用整页横向滚动。流程明细和附件仅在自身容器滚动，底部操作不遮挡最后一段内容。

  预期：四个规定视口中完整详情和允许动作可达，长申请单号可换行，`document.documentElement.scrollWidth <= window.innerWidth`。

  证据：T5 截图、DOM 测量和实际点击。

- [ ] **步骤 7：局部构建**

  运行：`npm --prefix web run build && git diff --check`

  预期：退出码均为 0。

  证据：构建和 diff 检查结果。

**验收检查：** 详情加载是审批前置；有效待办可原地操作；无权限和过期任务只读；业务事实、任务事实职责清楚。

**风险：** 当前业务数据是会话 Mock，无法证明跨进程持久化；后端 `business_key` 与 Mock 样例不一致会造成详情不存在。

**回滚：** 删除新页面、面板和 Repository，恢复壳层局部状态及抽屉入口；T1 平台 API 可独立保留。

**停止条件：** 完整业务详情只能从工作流摘要拼装；需要让前端伪造服务端任务权限；正式业务 API 成为本任务硬依赖。

**升级条件：** 需要修改共享 UI、附件 API、项目上下文 API 或新增状态管理依赖。

---

### T3：收敛任务聚合、工作流页面和历史路由

**需求映射：** R2、R3、R4、R8、R10

**前置任务：** T2

**文件：**
- 新建：`web/src/views/TaskCenterView.vue`
- 修改：`web/src/views/DashboardView.vue`
- 修改：`web/src/views/WorkflowView.vue`
- 修改：`web/src/router/index.ts`
- 修改：`web/src/views/AppLayout.vue`
- 修改：`web/src/styles.css`
- 修改：`web/src/modules/release/ReleaseWorkflowReviewPage.vue`

**接口：**
- 消费：`listWorkflowInbox`、`listWorkflowDone`、`WorkflowTask.action_path`、`WorkflowDoneItem.action_path` 和项目上下文 `currentRef`。
- 产出：`/workbench/tasks?tab=pending|done`；列表只提供“查看详情”。
- 产出：安全导航函数只接受单斜线开头、无协议、无反斜线和无换行的站内路径，并按任务类型附加 `taskId` 或 `instanceId`。
- 产出兼容：`/workflow/inbox`、`/workflow/done` 重定向任务中心；旧 review 在可解析 release 任务时重定向业务详情，否则返回 `/dashboard` 并提示。

- [ ] **步骤 1：实现分页任务中心**

  使用待办/已办页签、服务端分页和当前项目过滤。桌面使用 `UiDataTable`，手机使用交付示范中心模式的业务卡片；身份区显示业务标题/单号，事实区显示项目、节点、发起人或动作时间，操作区只有“查看详情”。

  预期：加载、空、失败、403 和重试状态完整；页签通过 URL 查询参数保留；不存在任何审批按钮。

  证据：两页签分页、项目切换、错误和手机卡片路径。

- [ ] **步骤 2：修正首页聚合和安全导航**

  首页每类最多五条；“查看全部”分别进入任务中心待办/已办页签。待办导航在安全 `action_path` 后附加 `taskId`，已办附加 `instanceId`；无效路径回任务中心而不是旧工作流审批页。

  预期：查询参数使用 Router 对象合并，不通过字符串拼接破坏已有 query；首页没有快捷审批。

  证据：首页待办、已办和无效路径点击结果。

- [ ] **步骤 3：移除 WorkflowView 的业务审批职责**

  删除 inbox/done 数据加载、审批弹框、同意/退回/拒绝/加签/抄送控件和对应状态，仅保留流程定义、配置、发布及流程监控。直接传入旧 section 时不渲染审批内容。

  预期：`WorkflowView.vue` 不再调用 `listWorkflowInbox`、`listWorkflowDone` 或 `decideWorkflowTask`。

  证据：`rg -n "listWorkflowInbox|listWorkflowDone|decideWorkflowTask|APPROVE|REJECT|RETURN" web/src/views/WorkflowView.vue` 无业务审批命中。

- [ ] **步骤 4：实现新路由和旧地址兼容**

  注册任务中心与业务详情；将 inbox/done 定向到任务中心。旧 review 组件只解析历史 taskId 与 Mock 业务映射并执行 `router.replace`，不渲染详情或审批；无法解析时回工作台并给出一次性提示。刷新和浏览器返回不能形成重定向循环。

  预期：所有有效入口最终 URL 为 `/release/applications/:applicationCode`；无效旧链接进入工作台而非空白/404。

  证据：五类入口、刷新和返回路径记录。

- [ ] **步骤 5：补齐标题和响应式样式**

  AppLayout 增加任务中心和版本申请详情标题，移除旧待办标题；全局样式只增加 task-center 作用域规则。手机卡片不平铺四个以上按钮，不产生页面级横向溢出。

  预期：页签、面包屑、标题和菜单激活状态可理解；浅色/深色均可读。

  证据：桌面与三手机视口截图。

- [ ] **步骤 6：局部构建与路由静态检查**

  运行：`npm --prefix web run build`

  运行：`rg -n "/workflow/review|/workflow/inbox|/workflow/done" web/src | sort`

  预期：构建通过；命中仅存在于显式兼容路由或契约允许位置，不存在业务操作跳转。

  证据：构建汇总和命中审阅。

**验收检查：** 任务中心和首页只导航；工作流页面只定义与监控；旧链接安全降级；所有有效入口到同一业务页。

**风险：** 动态菜单仍可能在数据库迁移前显示旧菜单；历史 action_path 可能缺少可解析业务键。

**回滚：** 删除任务中心并恢复旧路由和 WorkflowView 分支；T2 业务详情可继续从版本列表访问。

**停止条件：** 必须在任务中心复制业务详情或审批表单；旧路由无法在不访问其他模块私有数据的情况下安全解析。

**升级条件：** 需要修改通知中心公共组件、动态菜单服务或 tabs 公共 store 才能完成导航。

---

### T4：以前向迁移下线菜单并发布公共契约

**需求映射：** R4、R8、R9

**前置任务：** T3

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V37__business_owned_workflow_approval.sql`
- 修改：`docs/integration/workflow-module-contract.md`

**接口：**
- 消费：`sys_menu.id IN (202, 204)`、`sys_role_menu.menu_id` 外键关系。
- 产出：先删除全部租户/角色的菜单关联，再删除菜单 202、204；操作可重复执行时不报错。
- 产出契约：工作流管理职责、单任务上下文、业务路由、签名/决策授权和旧地址兼容说明。

- [ ] **步骤 1：建立 Flyway 基准**

  运行：`node scripts/check-flyway-migrations.mjs`

  预期：现有迁移序列通过且下一版本为 V37；若 V37 已被其他改动占用则停止并重新确定版本号及任务范围。

  证据：迁移检查汇总。

- [ ] **步骤 2：追加菜单下线迁移**

  SQL 先执行 `DELETE FROM sys_role_menu WHERE menu_id IN (202, 204)`，再执行 `DELETE FROM sys_menu WHERE id IN (202, 204)`；不限定角色 1，不修改 V7/V29，不删除工作流根目录、定义或监控菜单。

  预期：新库与已升级存量库都只保留流程定义和流程监控；后端 API 不受菜单数据删除影响。

  证据：迁移文件审阅、Flyway 检查和运行环境菜单结果。

- [ ] **步骤 3：更新工作流公共契约**

  文档新增 `GET /api/workflows/tasks/{id}/context` 字段与错误语义，明确业务模块拥有审批详情/操作界面、任务中心只导航、决策及签名仍由工作流执行，并列出保留 API 与兼容地址。

  预期：契约不要求业务模块使用输入项配置，不把 Mock 数据描述为正式持久化。

  证据：文档与 T1 TypeScript/Java 接口逐字段核对。

- [ ] **步骤 4：运行迁移与治理检查**

  运行：`node scripts/check-flyway-migrations.mjs`

  运行：`node scripts/check-all-governance.mjs`

  运行：`git diff --check`

  预期：三条命令退出码均为 0。

  证据：命令汇总和退出码。

**验收检查：** 历史 Flyway 未变；202/204 及所有角色关联被删除；定义、监控和任务 API 保留；契约与代码一致。

**风险：** 用户浏览器仍持有迁移前缓存菜单，需重新登录或重新 hydrate 才能看到下线结果。

**回滚：** 不回改 V37；若必须恢复入口，另建后续前向迁移重建菜单与角色授权，并恢复前端路由。

**停止条件：** V37 已被占用；菜单 ID 在当前数据库代表其他对象；删除受到未知外键阻断。

**升级条件：** 需要修改 system 服务、角色权限接口、历史迁移或治理模块边界。

---

### T5：执行集成、权限和响应式收敛验收

**需求映射：** R1-R10

**前置任务：** T4

**文件：**
- 修改：`.ai-control/requirements/req-20260815-027-business-owned-approval/state.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/model.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/control-plan.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/execution-T1.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/execution-T2.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/execution-T3.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/execution-T4.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/execution-T5.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/observation-T1.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/observation-T2.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/observation-T3.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/observation-T4.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/observation-T5.json`
- 新建：`.ai-control/requirements/req-20260815-027-business-owned-approval/convergence.json`
- 修改：`docs/requirements/REQ-20260815-027-business-owned-approval/codex-task-scope.yaml`

**接口：**
- 消费：登录后的 `/dashboard`、`/workbench/tasks`、`/release`、`/release/applications/:applicationCode`、旧 workflow 地址及相关 `/api/workflows/**`。
- 产出：任务级执行/观测证据、自动化结果、四视口截图与 DOM 测量、数据库菜单结果和收敛结论。

- [ ] **步骤 1：执行后端与前端自动化**

  运行：`JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test`

  运行：`JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`

  运行：`npm --prefix web run build`

  预期：三条命令退出码均为 0；工作流测试包含单任务上下文正常、越权、已处理、不存在和重复决策。

  证据：测试数、失败数、构建摘要和退出码。

- [ ] **步骤 2：执行治理、迁移和范围检查**

  运行：`node scripts/check-all-governance.mjs`

  运行：`node scripts/check-flyway-migrations.mjs`

  运行：`git diff --check`

  运行并记录已知偏差：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260815-027-business-owned-approval/codex-task-scope.yaml`

  预期：治理、Flyway 和 diff 退出码为 0；scope 检查若仍仅因 `rokey` 分支正则失败，记录为用户已知分支约束，不修改检查器或伪报通过。若出现任何路径越界则停止。

  证据：四条命令的实际退出码与错误分类。

- [ ] **步骤 3：启动本地前后端并验证迁移后菜单**

  使用现有本地 MySQL/MinIO 配置和未占用端口启动 Java 17 后端、Vite 前端；登录后重新 hydrate 菜单。验证流程定义与流程监控存在，待办审批与流程已办不存在；直接访问旧 URL 被兼容路由处理。

  预期：健康接口和 SPA 资源可达；无登录循环；V37 成功应用；API 仍可访问。

  证据：进程、URL、健康响应、Flyway 日志和登录后菜单截图。

- [ ] **步骤 4：验收桌面端完整用户路径**

  在 `1280x800` 依次检查：首页待办/已办、任务中心两页签、版本列表、业务详情、附件/需求/交付单元、流程进展、有效审批、已处理只读、越权、重复提交、旧地址和浏览器返回。确认审批成功留在详情页并更新状态/进展/日志。

  预期：聚合页没有审批控件；完整业务加载失败时不能审批；控制台无 error；接口无意外 4xx/5xx。

  证据：操作清单、截图、网络请求和控制台记录。

- [ ] **步骤 5：验收三个手机视口和主题**

  在 `375x812`、`390x844`、`430x932` 检查任务卡片、业务详情、流程记录、审批意见、签署、同意及更多操作；分别测量 `document.documentElement.scrollWidth <= window.innerWidth`，检查浅色和深色主题、长单号、长意见、错误提示及底部操作可达。

  预期：无页面级横向滚动、遮挡、文字溢出或不可达操作；移动端与桌面使用同一路由和业务规则。

  证据：每个视口截图、DOM 测量和实际点击结果。

- [ ] **步骤 6：记录闭环证据并判定收敛**

  按任务写入实际 diff、命令、结果和扰动；执行者记录 execution，独立观测记录 observation。只有 R1-R10 均有可复验证据、P0/P1 反馈为 0、范围无越界且除已披露分支正则外没有失败门禁，才写入 convergence。

  预期：账本阶段按 `baseline -> modeling -> planning -> executing -> observing -> converged` 留痕；未通过时保留真实阶段和反馈，不提前宣布完成。

  证据：当前前缀 state、execution、observation、convergence 和最终检查结果。

**验收检查：** 服务端授权、业务内审批、聚合跳转、菜单收敛、旧路由和四视口全部有独立证据；未修改范围外文件。

**风险：** 本地数据库状态、登录身份、端口和已有未提交代码可能形成环境扰动；会话 Mock 不能证明正式持久化。

**回滚：** 分任务回退 T1-T4 的当前需求改动，保留失败证据；数据库菜单只通过新的前向迁移恢复，不重写 V37。

**停止条件：** 自动化失败、路径越界、鉴权绕过、任务重复执行、业务详情缺失仍可审批、菜单迁移异常、白屏、控制台错误或页面级横向溢出。

**升级条件：** 需要生产访问、真实数据、修改只读/禁止路径、修改分支治理规则、改动正式业务后端或引入新依赖。

## 集成检查

| 时点 | 命令或路径 | 通过信号 |
|---|---|---|
| T1 后 | `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test` | 单任务上下文与既有工作流测试 0 失败 |
| T2 后 | `npm --prefix web run build` | TypeScript 与 Vite 构建 0 错误 |
| T3 后 | `/dashboard`、`/workbench/tasks`、新旧详情路由 | 全入口只导航到业务详情，无通用审批控件 |
| T4 后 | `node scripts/check-flyway-migrations.mjs` | V37 顺序合法且历史校验通过 |
| 最终 | `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` | 全量 Maven 0 失败 |
| 最终 | `node scripts/check-all-governance.mjs && npm --prefix web run build && git diff --check` | 三项退出码均为 0 |
| 最终 | 四规定视口浏览器路径 | 无控制台错误、无页面级横向溢出、操作与状态正确 |

## 控制模型种子

以下信息全部为 `hypotheses-only`，导入后由 `$model-engineering-system` 验证：

- 被控边界候选：workflow 单任务上下文与决策授权、release 共享会话 Repository 与业务详情、工作台/任务中心导航、WorkflowView、Router、V37 菜单迁移和当前需求账本。
- 状态变量候选：业务申请加载状态、taskId/instanceId、任务状态、实例状态、允许动作、签名要求、审批意见、提交状态、流程进度、审计日志、任务页签/分页/项目筛选和菜单集合。
- 接口候选：`GET /api/workflows/tasks/{id}/context`、`POST /api/workflows/tasks/{id}/decision`、inbox/done 投影、`action_path`、`/release/applications/:applicationCode`、Repository 查询/同步和旧路由转换。
- 传感器候选：JUnit、Maven、Vue/TypeScript 构建、治理检查、Flyway 检查、scope 检查、浏览器网络/控制台、DOM `scrollWidth`、截图、菜单树和审批审计记录。
- 执行器候选：任务上下文查询、决策提交、Repository Mock 同步、Router 导航/重定向、任务筛选/分页和 Flyway 菜单删除。
- 扰动候选：脏工作区、历史 action_path、业务键与 Mock 不一致、任务并发处理、登录身份切换、菜单缓存、Java 26 默认环境、端口占用、长文本、移动安全区和主题切换。
- 时延候选：决策事务与生命周期事件、前端并发加载业务/流程、路由重建、菜单重新 hydrate、Flyway 启动和浏览器动画。
- 假设候选：现有 action policy 能确定业务页动作；单任务状态可以从 workflow 表稳定投影；release Mock 可以按业务键确定性重建；现有应用壳层支持规定手机视口。

## 高风险动作与用户批准

- 高风险动作包括公共工作流 API、服务端审批授权、Flyway 菜单删除、共享 Router/WorkflowView/Dashboard/AppLayout 以及当前脏工作区上的跨模块集成。
- 用户已于 2026-08-15 明确要求“开始实施”，批准本实施计划和交接包进入开发。
- 不执行提交、推送、PR、合并或生产操作。
- 数据库回退采用新的前向迁移；代码回退按 T1-T4 边界进行，不覆盖其他未提交改动。
