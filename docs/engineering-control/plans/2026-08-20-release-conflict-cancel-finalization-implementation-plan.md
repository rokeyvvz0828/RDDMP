# 追加申请冲突取消终态实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 让追加申请冲突弹框中的本人审批中旧申请在工作流终止后直接形成永久只读的 `CANCELLED`，同时保持普通撤回形成可恢复的 `WITHDRAWN`。

**架构：** 在 release 业务模块内新增独立冲突取消命令，以审批轮次 `CANCEL_REQUESTED` 表达永久取消意图；现有生命周期消费者在工作流 `TERMINATED` 时根据轮次意图选择 `CANCELLED` 或 `WITHDRAWN`。前端冲突弹框调用新命令，继续复用现有阻塞预检和 2 秒轮询，不修改工作流平台与数据库结构。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring JDBC、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-20-release-conflict-cancel-finalization-design.md`
- 需求文档：`docs/requirements/REQ-20260820-045-release-conflict-cancel-finalization/requirement.md`
- 任务范围：`docs/requirements/REQ-20260820-045-release-conflict-cancel-finalization/codex-task-scope.yaml`
- 状态：可移交

## 全局约束

- 仅追加申请冲突弹框使用永久取消；普通列表撤回接口、按钮和 `WITHDRAWN` 行为保持不变。
- 申请必须在工作流终止事件确认后才从 `IN_REVIEW` 变为 `CANCELLED`。
- `CANCELLED` 必须保持后端写保护和前端只读，不新增恢复入口。
- 新命令沿用 `release:application:withdraw` 或 `system:admin`，服务层继续校验租户、所有者、行版本、状态、轮次和原因。
- 不修改 `server/src/platform/**`、数据库迁移、权限数据、工作流公共契约或现有包名。
- 复用现有 `WorkflowTerminateCommand`、生命周期事件幂等机制、冲突预检轮询、Element Plus 弹框与响应式样式。
- 前端不新增布局结构；桌面和移动端均使用现有卡片操作区与固定底部操作区，禁止横向滚动和不可达按钮。
- 使用 Java 17 运行 Maven；保护 `rokey` 工作区全部无关修改，不清理、不回退、不提交、不推送。
- 任务范围治理脚本要求规范分支名，而用户明确要求在本地 `rokey` 实施；此项作为已知治理偏差如实记录，不伪造分支信息。

## 文件职责地图

| 路径 | 状态 | 单一职责 | 事实依据 |
| --- | --- | --- | --- |
| `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java` | existing | 编排提交、撤回、附件与工作流命令 | 已存在 `withdraw(...)` 和工作流终止调用 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java` | existing | 版本申请 HTTP 与权限适配 | 已存在 `/withdraw`、`/cancel` 端点 |
| `server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowStore.java` | existing | release 审批轮次与申请状态原子更新 | 已存在 `markWithdrawalRequested`、`markWithdrawn` |
| `server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumer.java` | existing | 消费工作流生命周期并收敛 release 状态 | 已按 `WITHDRAW_REQUESTED` 映射 `WITHDRAWN` |
| `server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java` | existing | 工作流命令与附件写保护服务测试 | 已覆盖普通撤回不直接改申请状态 |
| `server/src/modules/release/src/test/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumerTest.java` | existing | 生命周期状态映射与幂等测试 | 已覆盖普通撤回终止映射 |
| `server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java` | existing | 申请编辑与普通取消状态规则测试 | 已覆盖普通取消，候选补充 `CANCELLED` 写保护回归 |
| `server/src/modules/release/src/test/java/com/ccb/release/application/web/ReleaseApplicationControllerSecurityTest.java` | existing | 控制器路由权限反射契约 | 已枚举所有申请端点权限 |
| `web/src/api/release.ts` | existing | release 前端 HTTP 契约 | 已提供 `withdrawReleaseApplication(...)` |
| `web/src/modules/release/ReleaseManagementPrototype.vue` | existing | 申请视图编排、冲突预检、轮询与动作恢复 | 已实现 `withdrawBlockedConflict(...)` 和 2 秒轮询 |
| `web/src/modules/release/components/ReleaseConflictDialog.vue` | existing | 冲突信息、卡片动作与继续门禁 | 已提供本人审批中旧申请动作区 |

## 任务依赖图与并行策略

```text
T1 后端取消意图与终态映射
  -> T2 前端冲突取消接入与集成验收
```

两项任务串行执行。T2 消费 T1 固定的端点、权限和异步响应契约；在 T1 完成前并行修改前端会增加接口猜测和联调返工。任务内部不委派并行写入，避免在当前重度脏工作区出现交叉覆盖。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T2 |
| R2 | T1, T2 |
| R3 | T1 |
| R4 | T1, T2 |
| R5 | T2 |
| R6 | T1, T2 |
| R7 | T1, T2 |

### T1：后端冲突取消意图与生命周期终态

**需求映射：** R2, R3, R4, R6, R7

**前置任务：** 无

**已证实输入事实：**

- `ReleaseSubmissionService.withdraw(...)` 当前将轮次置为 `WITHDRAW_REQUESTED`、调用工作流终止网关，并保持申请 `IN_REVIEW`。
- `ReleaseWorkflowLifecycleConsumer.consumeTerminated(...)` 当前将 `WITHDRAW_REQUESTED` 映射为 `WITHDRAWN`。
- `ReleaseApplicationService.ensureEditable(...)` 只允许 `DRAFT`、`RETURNED`、`WITHDRAWN`，附件删除同样排除 `CANCELLED`。
- `rel_application_round.round_status` 由字符串读写，不需要为 `CANCEL_REQUESTED` 增加数据库字段。

**文件：**

- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowStore.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumer.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumerTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/web/ReleaseApplicationControllerSecurityTest.java`

**接口：**

- 消费：`ReleaseWorkflowGateway.terminate(WorkflowTerminateCommand, AuthUser)`；现有 `StateActionRequest(long rowVersion, String reason)`；工作流 `TERMINATED` 生命周期事件。
- 产出：`ReleaseSubmissionService.conflictCancel(String code, StateActionRequest request, AuthUser user, boolean elevated)`。
- 产出：`POST /api/release/applications/{code}/conflict-cancel`，权限 `release:application:withdraw` 或 `system:admin`，响应 `WorkflowActionResult` 的 `operationStatus=CANCEL_REQUESTED`、业务 `status=IN_REVIEW`。
- 产出：`ReleaseWorkflowStore.markCancelRequested(long tenantId, long roundId)` 与 `markCancelled(Application application, long operatorId)`。
- 产出：终止映射 `CANCEL_REQUESTED -> CANCELLED`；保留 `WITHDRAW_REQUESTED -> WITHDRAWN`。

- [ ] **步骤 1：建立状态机、权限和写保护失败测试**

  在 `ReleaseSubmissionServiceTest` 增加冲突取消成功、非本人、非审批中、行版本不一致、无有效轮次、原因缺失和终止网关抛错测试；成功断言只写 `CANCEL_REQUESTED`、调用终止网关且不直接 `markCancelled`。在 `ReleaseWorkflowLifecycleConsumerTest` 增加 `CANCEL_REQUESTED` 终止形成 `CANCELLED`，并保留现有 `WITHDRAW_REQUESTED` 形成 `WITHDRAWN` 的回归断言。在控制器权限测试中登记 `conflictCancel -> release:application:withdraw`。在申请服务测试中断言 `CANCELLED` 更新被拒绝；提交和附件写保护在 submission 测试中断言。

- [ ] **步骤 2：运行聚焦检查并确认红灯来自缺失的新契约**

  运行：

  ```bash
  JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReleaseSubmissionServiceTest,ReleaseWorkflowLifecycleConsumerTest,ReleaseApplicationServiceTest,ReleaseApplicationControllerSecurityTest test
  ```

  预期：新增测试因 `conflictCancel`、`markCancelRequested` 或 `markCancelled` 不存在或行为仍为普通撤回而失败；既有测试不出现无关失败。

  证据：记录退出码、首个新断言失败和测试类名称，不保存完整构建日志。

- [ ] **步骤 3：实现最小后端命令与轮次存储**

  在 `ReleaseWorkflowStore` 增加带 `round_status='IN_REVIEW'` 条件的 `markCancelRequested`，并复用申请 `IN_REVIEW`、`row_version` 条件实现 `markCancelled`。在 `ReleaseSubmissionService` 增加 `conflictCancel`，沿用普通撤回的锁、所有者、状态、行版本、轮次、原因和终止命令，仅把轮次意图与审计事件改为 `CANCEL_REQUESTED`；终止网关异常必须向外抛出以触发事务回滚。

- [ ] **步骤 4：接入控制器与生命周期映射**

  在控制器新增 `@PostMapping("/{code}/conflict-cancel")`，权限表达式与普通撤回一致。在生命周期消费者的终止分支中显式处理三类输入：`CANCEL_REQUESTED -> CANCELLED`、`WITHDRAW_REQUESTED -> WITHDRAWN`、无显式请求的 `IN_REVIEW -> RETURNED`；事件 payload 保留来源轮次状态。

- [ ] **步骤 5：运行聚焦测试和 release 模块回归**

  运行聚焦命令，预期 4 个目标测试类全部通过、0 失败；随后运行：

  ```bash
  JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test
  ```

  预期：Maven `BUILD SUCCESS`，release 模块及依赖测试 0 失败、0 错误。

  证据：记录两条命令退出码、测试数量和关键状态映射断言。

- [ ] **步骤 6：建立任务差异检查点**

  运行：

  ```bash
  git diff --check -- server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowStore.java server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumer.java server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java server/src/modules/release/src/test/java/com/ccb/release/application/web/ReleaseApplicationControllerSecurityTest.java server/src/modules/release/src/test/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumerTest.java
  ```

  预期：退出码 0，无空白错误；不创建 Git 提交。

**验收检查：** 新命令不直接改变申请；终止事件形成 `CANCELLED`；普通撤回仍形成 `WITHDRAWN`；无权、非本人、并发过期、错误状态和网关异常均不可形成取消终态；`CANCELLED` 写操作被拒绝。

**回滚：** 同时移除控制器端点、service 命令、store 两个状态方法、生命周期 `CANCEL_REQUESTED` 分支和对应测试；不得只删除生命周期分支。

**停止条件：** 现有轮次状态列存在数据库约束不接受 `CANCEL_REQUESTED`；工作流终止网关吞掉失败而不抛异常；生命周期事件无法定位当前轮次；目标文件包含无法保留的并发用户修改。

**升级条件：** 需要修改 `server/src/platform/workflow/**`、数据库迁移、权限表或公共工作流契约；发现普通撤回依赖未记录的 `WITHDRAW_REQUESTED` 特殊语义。

### T2：前端冲突取消接入与用户路径验收

**需求映射：** R1, R2, R4, R5, R6, R7

**前置任务：** T1

**已证实输入事实：**

- `ReleaseConflictDialog.vue` 已限制卡片动作仅在预检、本人、`IN_REVIEW` 和具备撤回权限时显示。
- `ReleaseManagementPrototype.vue` 已保存原始草稿/提交动作，并在弹框开启时每 2 秒轮询；仍有 `IN_REVIEW` 时禁用继续。
- 现有弹框卡片操作区和底部操作区已覆盖桌面与移动布局，本任务只改变命令语义和文案。

**文件：**

- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/components/ReleaseConflictDialog.vue`

**接口：**

- 消费：T1 产出的 `POST /release/applications/{code}/conflict-cancel`，请求 `{ rowVersion, reason }`，响应 `ReleaseWorkflowActionResult`。
- 产出：`cancelBlockedReleaseApplication(code: string, rowVersion: number, reason: string)` 前端 API 函数。
- 产出：冲突弹框 `cancel` 事件、`cancellingCode` 属性和“取消申请/取消处理中”文案；普通 `withdrawReleaseApplication(...)` 继续仅供列表撤回使用。

- [ ] **步骤 1：建立前端基准信号**

  运行：

  ```bash
  npm --prefix web run build
  ```

  预期：改动前构建成功；记录当前撤销按钮仍调用 `/withdraw` 作为需纠正基准。

  证据：记录构建退出码与 `withdrawBlockedConflict`、`@withdraw` 的搜索结果。

- [ ] **步骤 2：接入独立冲突取消 API**

  在 `web/src/api/release.ts` 增加 `cancelBlockedReleaseApplication(...)`，保留 `withdrawReleaseApplication(...)` 不变。在页面编排中将冲突专用状态和处理函数从 withdraw 语义改为 cancel 语义；原因弹框说明永久“已取消”和不可编辑后果，成功提示说明正在等待审批流程终止，错误提示保持可恢复。

- [ ] **步骤 3：更新冲突弹框语义并保持门禁**

  将冲突卡片事件和属性改为 `cancel`、`cancellingCode`，按钮文案改为“取消申请/取消处理中”，禁用条件继续覆盖 resolving 和任一取消请求处理中。将继续禁用提示改为“前一申请仍在审批中，请先取消”；保留底部“取消/继续申请”、2 秒轮询、关闭清理和原动作恢复。

- [ ] **步骤 4：运行前端构建与静态语义检查**

  运行：

  ```bash
  npm --prefix web run build
  rg -n "withdrawBlockedConflict|@withdraw|撤销处理中|撤销申请" web/src/modules/release/ReleaseManagementPrototype.vue web/src/modules/release/components/ReleaseConflictDialog.vue
  ```

  预期：构建成功；搜索在冲突弹框和冲突处理函数中无旧撤回语义，普通列表撤回 API 与动作仍存在。

  证据：记录构建退出码、Vite 完成信号和搜索结果。

- [ ] **步骤 5：启动或确认服务并执行真实浏览器验收**

  使用当前本地后端 `127.0.0.1:8080` 与前端 `127.0.0.1:5173`；服务失效时按项目现有启动命令重启。以具备申请创建、撤回和查看权限的测试用户，在桌面 `1440x900` 与移动 `390x844` 验证：本人审批中阻塞项显示“取消申请”；他人阻塞项不显示；提交原因后显示“取消处理中”且继续禁用；终止完成后旧申请状态为“已取消”、详情无编辑入口、继续恢复；普通列表撤回后状态为“已撤回”且仍可编辑、重新提交。检查网络请求命中新端点、无 4xx/5xx、控制台无新增错误、弹框按钮无遮挡溢出。

  预期：所有指定角色、状态、路由和视口结果与设计一致。

  证据：记录测试申请单号、角色（不记录口令）、视口、关键状态、请求结果和截图路径。

- [ ] **步骤 6：执行集成回归与任务差异检查点**

  运行 release 模块测试、前端构建及：

  ```bash
  git diff --check -- web/src/api/release.ts web/src/modules/release/ReleaseManagementPrototype.vue web/src/modules/release/components/ReleaseConflictDialog.vue
  ```

  预期：全部退出码 0；不创建 Git 提交。

**验收检查：** 冲突动作显示条件不扩大；按钮、原因、处理中与错误文案均为永久取消语义；继续申请仅在旧申请退出审批中后可用；普通撤回入口和恢复编辑能力不变；桌面与移动弹框可完成操作。

**回滚：** 同时恢复 API 函数、页面冲突处理函数、弹框事件/属性/文案；后端 T1 若同时回退，前端必须同步回退到普通撤回调用。

**停止条件：** T1 端点契约或状态结果与计划不一致；测试数据无法形成审批中冲突；本地认证或服务故障导致无法区分产品问题；目标文件出现无法保留的并发用户修改。

**升级条件：** 需要修改公共 UI 组件、全局样式、路由、权限 store、工作流平台或数据库；移动端现有弹框布局无法承载操作且需要超出本任务的结构改造。

## 集成检查

1. `node scripts/check-development-entry.mjs --require-plugin`：预期 `Development entry check passed.`。
2. `JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`：预期 `BUILD SUCCESS` 且 0 失败、0 错误。
3. `npm --prefix web run build`：预期 TypeScript/Vite 生产构建成功。
4. `node scripts/check-all-governance.mjs`：预期除用户指定 `rokey` 分支命名偏差外无新增治理错误；如脚本将该偏差作为硬失败，记录真实输出，不伪造通过。
5. `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-045-release-conflict-cancel-finalization/codex-task-scope.yaml --working-tree`：当前重度脏工作区可能包含其他需求文件，预期如实区分本任务范围与既有扰动；不得通过清理工作区规避。
6. `git diff --check`：预期本任务文件无空白错误；若全工作区无关文件失败，另行对本任务路径运行并记录两者差异。
7. 浏览器：桌面与移动完成冲突取消终态、只读、继续恢复和普通撤回回归。

## 控制模型种子

以下均为 `hypotheses-only`，必须在 `$model-engineering-system` 阶段验证：

- 被控边界候选：release 申请、审批轮次、工作流终止生命周期和冲突弹框，不含工作流引擎内部。
- 状态变量候选：申请 `IN_REVIEW/CANCELLED/WITHDRAWN`，轮次 `IN_REVIEW/CANCEL_REQUESTED/WITHDRAW_REQUESTED/TERMINATED`，前端取消中代码、冲突列表和继续门禁。
- 接口候选：冲突取消 HTTP 命令、`WorkflowTerminateCommand`、`TERMINATED` 生命周期事件、冲突预检响应。
- 传感器候选：JUnit 状态映射断言、事件/轮次记录、HTTP 状态、2 秒轮询结果、按钮可用性、浏览器网络与控制台。
- 执行器候选：轮次条件更新、工作流终止、生命周期申请状态更新、前端按钮禁用与轮询。
- 扰动候选：并发行版本变化、重复或延迟终止事件、网关同步异常、本地服务或认证失效、脏工作区无关改动。
- 时延候选：工作流终止命令到生命周期事件的异步时延，以及前端最多 2 秒的轮询感知时延。
- 假设：终止网关同步失败会抛异常并参与事务回滚；生命周期事件可唯一匹配当前审批轮次；现有 `CANCELLED` 写保护覆盖全部写入口。

## 风险与用户批准

- 高风险动作：修改审批终止状态机。通过新意图分支、普通撤回回归测试和后端终态写保护限制影响。
- 无数据库迁移、无生产访问、无平台模块修改、无依赖变更。
- 用户已确认计划并要求开始实施；批准后写入交接包并从 `baseline` 开始闭环实施。
