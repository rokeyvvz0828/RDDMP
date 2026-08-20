# 通知时效优化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在不改变现有通知 API 和数据库结构的前提下，使工作流生命周期通知从事务提交到前台铃铛更新的正常链路达到 `P95 <= 2 秒`。

**架构：** `platform/workflow` 在生命周期事件事务提交后按 `eventId` 立即派发，并与 5 秒调度器共用原子抢占和恢复状态机。前端公共通知中心改为可见页 1 秒自调度计数轮询，隐藏暂停、失败退避，并仅在抽屉打开且计数变化时刷新明细。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Spring 事务同步、JUnit 5、Vue 3、TypeScript、Element Plus、Vite、MySQL 8.4。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-18-notification-latency-optimization-design.md`
- 需求文档：`docs/requirements/REQ-20260818-035-notification-latency-optimization/requirement.md`
- 状态：可移交
- 用户批准：用户复核设计后于 2026-08-18 明确要求“直接开发”。

## 全局约束

- 只修改本需求 `codex-task-scope.yaml` 授权路径，保护 `rokey` 工作区中所有其他未提交修改。
- 生命周期事件和投递记录继续在业务事务中持久化；回滚事务不得触发通知。
- 即时派发失败不得改变审批结果；5 秒调度器、最多 5 次尝试、`DEAD` 和人工重试保持兼容。
- 正常竞争只允许一个路径消费；进程崩溃恢复为至少一次投递，依靠现有消费者幂等消除重复效果。
- 不新增接口、表、字段、索引、迁移、缓存、队列、SSE 或 WebSocket。
- 未读查询继续使用认证主体的租户和用户，前端不指定用户身份。
- 隐藏页面不轮询；前台基础周期 1 秒，失败退避为 2 秒、5 秒、15 秒。
- 不增加新的前端测试框架；通过生产构建、浏览器网络观测和真实接口验收前端行为。
- 用户未要求提交或推送，本计划不执行 `git commit`、`git push` 或分支切换。

---

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `WorkflowLifecycleEventService.java` | existing | 写入生命周期事件和投递记录，注册提交后即时触发 |
| `WorkflowLifecycleDispatcher.java` | existing | 事件级/批量派发、原子抢占、成功/失败落账和中断恢复 |
| `WorkflowLifecycleEventServiceTest.java` | candidate-new | 事务同步注册、提交触发和无事务回退行为测试 |
| `WorkflowLifecycleDispatcherTest.java` | existing | 抢占、竞争、失败补偿、重试和超时恢复测试 |
| `UiNotificationCenter.vue` | existing | 可见页轮询、在途去重、退避和按需刷新 |
| 本需求 requirement/design/plan/ledger | existing/candidate-new | 唯一事实源、任务边界和执行证据 |

## 任务依赖图与并行策略

```text
T1 后端即时派发与可靠状态机
  -> T2 前端可见页低延迟轮询
  -> T3 集成、容量与浏览器验收
```

全部串行执行。T1 与 T2 技术上可并行，但当前共享工作区存在大量未提交修改，串行可在每个阶段重新读取目标文件并降低覆盖风险。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 正常链路时效 | T1、T2、T3 |
| R2 事务与业务隔离 | T1、T3 |
| R3 并发抢占与幂等 | T1、T3 |
| R4 失败补偿与恢复 | T1、T3 |
| R5 轮询生命周期 | T2、T3 |
| R6 失败退避 | T2、T3 |
| R7 200 用户容量 | T3 |

### T1：后端提交后即时派发与可靠状态机

#### 需求映射与前置事实

**需求映射：** R1、R2、R3、R4

**前置任务：** 无

**已证实事实：** `WorkflowLifecycleEventService.emit` 当前在调用方事务内写 `wf_lifecycle_event` 和 `wf_lifecycle_delivery(PENDING)`；`WorkflowLifecycleDispatcher` 每 5 秒查询 `PENDING/RETRY` 并直接消费；投递表已有 `status`、`attempt_count`、`next_attempt_at`、`last_error` 和自动更新的 `updated_at`。

#### 文件边界与接口

- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleEventService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleDispatcher.java`
- 新建测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowLifecycleEventServiceTest.java`
- 修改测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowLifecycleDispatcherTest.java`
- 消费：Spring `TransactionSynchronizationManager`、现有 `WorkflowLifecycleConsumer`、投递表状态字段。
- 产出：`WorkflowLifecycleDispatcher.dispatchEvent(String eventId)`，只处理指定事件的到期投递。
- 产出：`PENDING/RETRY -> DISPATCHING -> DELIVERED` 状态流及基于 `updated_at` 的超时恢复。
- 兼容：保留 `scheduledDispatch()`、`dispatchBatch(int)` 和 `retry(...)` 公共行为。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：扩展 Dispatcher 单元测试，先固定失败信号**

  增加用例：条件抢占影响 0 行时不消费；按 `eventId` 只派发目标记录；即时失败恢复 `PENDING` 且不抛出；调度失败继续 `RETRY/DEAD`；超时 `DISPATCHING` 可恢复；模拟即时与调度连续处理同一记录只调用消费者一次。

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am -Dtest=WorkflowLifecycleDispatcherTest test`

  预期：新增测试因 `dispatchEvent`、抢占和恢复行为尚不存在而失败，既有两个用例仍保持原有基准。

- [ ] **步骤 2：实现共用原子抢占和恢复状态机**

  `dispatchBatch` 和 `dispatchEvent` 先查询候选，再执行带 `id`、源状态和到期条件的条件更新；只有更新影响 1 行才加载事件并消费。成功仅从 `DISPATCHING` 更新 `DELIVERED`；即时失败记录尝试次数和错误并恢复当前可调度 `PENDING`；调度失败沿用退避与第五次 `DEAD`。每次调度前恢复超过配置阈值（默认 60 秒）的 `DISPATCHING`。

- [ ] **步骤 3：建立事务提交触发测试**

  在新测试中使用 Spring 事务同步工具验证：活动事务中 `emit` 只注册 after-commit，提交回调执行 `dispatchEvent(eventId)`；回滚回调不执行；无活动事务时在写入完成后直接派发；Dispatcher 异常被隔离并保留业务返回。

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am -Dtest=WorkflowLifecycleEventServiceTest test`

  预期：测试先因构造依赖和提交后触发尚不存在而失败。

- [ ] **步骤 4：实现 after-commit 触发**

  为事件服务注入 Dispatcher。`emit` 写完全部投递记录后，仅当至少生成一条投递时注册回调；事务同步活动时在 `afterCommit` 调用事件级派发，无事务时直接调用。即时派发入口内部吞掉并记录运行异常，不让其传播到业务调用方。

- [ ] **步骤 5：运行后端局部回归**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow -am test`

  预期：工作流模块及依赖测试通过，0 个失败；既有构造测试和人工重试用例兼容。

#### 验收、证据与回滚

- 验收：事务提交/回滚、事件级派发、原子抢占、即时失败补偿、超时恢复和 `DEAD` 均有可重复测试。
- 证据：测试命令、退出码、测试数量、关键状态断言和目标文件差异。
- 回滚：仅回退两个服务和两个测试文件，调度器恢复原 5 秒路径；无数据库回滚。
- 停止条件：发现 `emit` 调用方不在事务中且即时消费可能看到未完成业务状态；现有消费者不具备崩溃重放幂等；条件抢占需要数据库结构变更。
- 升级条件：需要新增消息队列、修改生命周期公开事件契约或改变 5 次重试语义。

### T2：前端可见页 1 秒轮询与失败退避

#### 需求映射与前置事实

**需求映射：** R1、R5、R6

**前置任务：** T1

**已证实事实：** `UiNotificationCenter` 当前用 `setInterval(15_000)` 静默查询未读数，`focus` 时刷新，抽屉打开时并行加载列表、计数和板块；当前没有前端测试框架。

#### 文件边界与接口

- 修改：`web/src/components/ui/UiNotificationCenter.vue`
- 消费：现有 `getNotificationUnreadCount`、`getNotifications`、`getNotificationModules` 和浏览器 `visibilitychange/focus`。
- 产出：单一自调度 `setTimeout` 轮询器、共享在途 Promise、失败级数和下一次间隔。
- 不改变：模板展示、通知数据类型、接口文件、已读和筛选语义。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：记录当前构建和浏览器网络基准**

  运行：`npm --prefix web run build`

  预期：当前工作区生产构建通过；浏览器 Network 可观察到约 15 秒未读计数周期，作为修改前证据。

- [ ] **步骤 2：实现自调度轮询和在途去重**

  用完成后 `setTimeout` 替代 `setInterval`。基础间隔为 1000ms；所有挂载、聚焦、可见恢复和抽屉打开触发复用同一个计数请求，避免重叠。组件卸载和页面隐藏时清除待执行定时器。

- [ ] **步骤 3：实现变化驱动刷新和失败退避**

  比较请求前后未读数：抽屉关闭时只更新角标；抽屉打开且数量变化时刷新列表与板块汇总。连续静默失败按 2000/5000/15000ms 安排下一次；成功、聚焦或恢复可见重置失败级数，不重复显示错误消息。

- [ ] **步骤 4：运行前端构建**

  运行：`npm --prefix web run build`

  预期：`vue-tsc --noEmit` 和 Vite 生产构建通过，0 个 TypeScript 错误。

#### 验收、证据与回滚

- 验收：模板不变；代码中不再存在 15 秒固定 interval；隐藏暂停、恢复立即刷新、在途复用、变化驱动和退避逻辑清晰可测。
- 证据：前端构建退出码、目标组件差异和 T3 浏览器 Network 记录。
- 回滚：只回退 `UiNotificationCenter.vue` 的轮询状态与生命周期监听，恢复现有 15 秒定时器。
- 停止条件：必须修改通知 API/DTO；现有抽屉并发版本控制不能兼容变化驱动刷新；浏览器生命周期要求影响其他全局组件。
- 升级条件：需要新增前端测试依赖、共享轮询基础设施或全局状态管理。

### T3：集成、容量与浏览器验收

#### 需求映射与前置事实

**需求映射：** R1-R7

**前置任务：** T1、T2

**已证实事实：** 本地前端使用 `127.0.0.1:5173`，后端使用 `127.0.0.1:8080`；未读计数查询已有 `(tenant_id, user_id, is_read, created_at, notification_id)` 索引。

#### 文件边界与接口

- 新建：`.ai-control/requirements/req-20260818-035-notification-latency-optimization/execution-T3.json`
- 新建：`.ai-control/requirements/req-20260818-035-notification-latency-optimization/observation-T3.json`
- 新建：`.ai-control/requirements/req-20260818-035-notification-latency-optimization/convergence.json`
- 不修改产品文件；只写本需求 `.ai-control` 执行、观测和收敛证据。
- 消费：T1 后端状态机、T2 前端轮询、现有认证登录和真实通知业务流程。
- 产出：局部/全量测试、端到端时效、浏览器生命周期、200 并发容量和范围审计证据。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：执行静态和全量门禁**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`

  运行：`npm --prefix web run build`

  运行：`node scripts/check-all-governance.mjs`

  运行：`git diff --check`

  预期：全部退出码 0；若出现与本需求无关的脏工作区基线故障，记录具体文件和归属，不修改范围外内容。

- [ ] **步骤 2：执行浏览器轮询生命周期回归**

  使用已登录本地会话检查：可见页计数请求间隔约 1 秒；隐藏页面不发请求；恢复可见和重新聚焦立即请求；打开抽屉立即刷新；慢请求期间无重叠；计数不变且抽屉关闭时不加载列表/板块。

  预期：控制台无新增错误，抽屉筛选、已读和跳转行为无回归。

- [ ] **步骤 3：执行真实事件时效采样**

  通过配置管理真实审批流程产生至少 20 条新待办或结果通知，记录事件提交时间与浏览器角标更新时间。

  预期：正常链路端到端 `P95 <= 2 秒`，即时失败注入不影响审批并在调度补偿后出现通知。

- [ ] **步骤 4：执行未读计数容量测试**

  使用本地测试身份获取脱敏会话，200 并发客户端持续 60 秒调用 `GET /api/notifications/unread-count`；采集总请求、错误数、响应延迟和后端日志。

  预期：错误数 0，响应时间 `P95 <= 200ms`，无连接池耗尽或应用异常。

- [ ] **步骤 5：执行范围检查并形成收敛结论**

  运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260818-035-notification-latency-optimization/codex-task-scope.yaml --working-tree`

  预期：本需求变更均在授权路径内；其他既有脏文件若被工具列出，明确标注为前置用户修改而非本需求产出。

#### 验收、证据与回滚

- 验收：R1-R7 均有测试或运行证据，所有本需求负反馈关闭，残余风险明确。
- 证据：Maven/前端/治理退出码，浏览器网络和控制台结果，20 次时效样本，60 秒容量统计及范围清单。
- 回滚：若时效或容量门禁失败，先回退 T2 至原轮询并停用 T1 即时触发，保留 5 秒调度器；不得通过降低验收目标宣布完成。
- 停止条件：本地后端或数据库不可达且连续三次恢复失败；无法获得可用于本地压测的测试身份；发现 1 秒轮询使接口超过容量门禁。
- 升级条件：必须引入缓存/推送/消息队列、修改用户确认的 2 秒目标，或需要范围外平台配置。

## 集成检查

- T1 后执行工作流聚焦测试，确认可靠状态机独立通过。
- T2 后执行前端生产构建，确认轮询逻辑类型正确。
- T3 执行后端全量、前端构建、治理、范围、浏览器、端到端和容量检查。
- 任一 must 验收失败时进入纠偏，不以部分通过或设计推理替代运行证据。

## 控制模型种子

- `hypotheses-only` 被控边界：生命周期事件事务、投递状态机、系统通知持久化、未读计数接口和前端通知中心。
- `hypotheses-only` 状态变量：投递状态/尝试次数/更新时间、未读数、页面可见性、请求在途状态、连续失败级数和轮询间隔。
- `hypotheses-only` 接口：`emit`、`dispatchEvent`、`dispatchBatch`、`GET /api/notifications/unread-count`、`visibilitychange`、`focus`。
- `hypotheses-only` 传感器：JUnit 状态断言、SQL 捕获、Maven/Vite 退出码、浏览器 Network/Console、时效采样和容量统计。
- `hypotheses-only` 执行器：after-commit 回调、条件更新抢占、超时恢复、自调度计时器、失败退避和按需列表刷新。
- `hypotheses-only` 扰动：调度竞争、消费者异常、进程中断、慢网络、隐藏页面、并行用户修改和本地环境漂移。
- `hypotheses-only` 时延：后端原 5 秒调度、前端原 15 秒轮询、1 秒新周期、数据库查询和浏览器调度抖动。

## 风险与用户批准

- 高风险动作仅限共享工作流派发行为和公共通知组件；无迁移、外部访问、提交或推送。
- 若 after-commit 同步执行显著增加业务响应耗时，先测量，再在现有栈内评审受控执行器，不擅自引入消息队列。
- 若 200 QPS 容量不通过，不放宽 2 秒目标，回到设计选择缓存或推送方案。
- 用户已于 2026-08-18 复核设计并明确要求“直接开发”，批准按本计划在本地 `rokey` 分支实施。
