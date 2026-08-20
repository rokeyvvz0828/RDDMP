# 投产基线维护时间限制实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-18-production-baseline-maintenance-window-design.md`
- 状态：可移交
- 用户批准依据：用户复核计划后明确回复“确认编码”。

**目标：** 未到投产窗口结束时间时，投产基线保持可查看但所有结果维护入口不可用，并由后端阻止提前维护。

**架构：** 前端复用窗口列表中的 `productionEnd` 展示原因并控制交互；后端从基线明细的 `windowId` 查询租户窗口，以固定业务时钟执行权威校验。请求、响应和数据库契约不变。

**技术栈：** Vue 3、TypeScript、Element Plus、Spring Boot、Java 17、JUnit 5、Mockito、Maven。

## 全局约束

- 所有版本类型统一受窗口结束时间限制，不设置应急例外。
- 未结束窗口仍可选择和查看基线数据。
- 开放边界固定为 `now >= productionEnd`，不依赖窗口 `CLOSED` 状态。
- 不修改 API DTO、数据库、窗口状态算法、版本归窗或移动端布局。
- 后端校验权威，前端只做提示和提前阻止。
- 只修改 REQ-20260818-037 授权路径，不覆盖 `rokey` 中其他未提交修改。

---

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `web/src/modules/release/components/ReleaseBaselineView.vue` | existing | 窗口下拉、基线加载、选择和投产结果维护交互 |
| `server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java` | existing | 投产候选、单条/批量结果校验、写入和审计 |
| `server/src/modules/release/src/test/java/com/ccb/release/production/service/ReleaseProductionServiceTest.java` | existing | 投产结果服务单元测试 |
| `docs/requirements/REQ-20260818-037-production-baseline-maintenance-window/*` | candidate-new | 需求和范围事实源 |
| `.ai-control/requirements/req-20260818-037-production-baseline-maintenance-window/*` | candidate-new | 控制交接和执行证据 |

## 任务依赖图与并行策略

```text
T1 后端权威时间门禁 ----\
                         -> T3 集成构建与真实页面验收
T2 前端查看/维护状态 ----/
```

T1 与 T2 写入面独立，可并行实施；T3 必须等待两者完成。当前共享工作区已有大量未提交内容，主 Agent 默认串行执行以减少扰动，但任务依赖不强制串行。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T2, T3 |
| R2 | T2, T3 |
| R3 | T1, T2, T3 |
| R4 | T1, T3 |
| R5 | T1, T2, T3 |

### T1：后端权威窗口结束时间门禁

**需求映射：** R3, R4, R5

**前置任务：** 无

**文件：**
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/production/service/ReleaseProductionServiceTest.java`

**接口：**
- 消费：`ReleaseProductionStore.findByIdForUpdate(entryId, tenantId)`、`ReleaseWindowStore.findById(windowId, tenantId)`、`ReleaseWindow.productionEnd()`、`Clock`
- 产出：单条和批量结果更新共享的内部校验，契约为“窗口存在且 `LocalDateTime.now(clock).isBefore(productionEnd)` 为 false”

- [ ] **步骤 1：建立结束前与边界测试**

在 `ReleaseProductionServiceTest` 增加固定 `Asia/Shanghai` 时钟和窗口 Store Mock，覆盖：结束前单条返回 `CONFLICT`；恰好到达结束时间允许；提前拒绝时 `updateResult` 与 `appendResultLog` 均未调用；批量任一明细未结束时抛错并依赖事务回滚。

- [ ] **步骤 2：运行聚焦测试并确认当前失败**

运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am -Dtest=ReleaseProductionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：新增测试因服务尚未依赖 `ReleaseWindowStore` 和 `Clock` 而失败。

证据：保存退出码、失败测试名和关键断言。

- [ ] **步骤 3：实施最小后端变更**

为生产构造函数使用 `Clock.system(ZoneId.of("Asia/Shanghai"))`，增加包可见测试构造函数注入固定 Clock。`updateResultInternal` 在锁定 `Entry` 后、原结果状态和字段校验前调用窗口门禁。错误消息包含 `productionEnd` 的分钟格式。不得读取客户端时间或版本类型。

- [ ] **步骤 4：运行聚焦与模块回归**

运行：

```bash
env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am -Dtest=ReleaseProductionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am test
```

预期：所有 Reactor 模块成功，发布模块测试零失败。

- [ ] **步骤 5：检查后端写入边界**

运行：`git diff --check -- server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java server/src/modules/release/src/test/java/com/ccb/release/production/service/ReleaseProductionServiceTest.java`

预期：无空白错误，未修改 Store、Controller、DTO 或迁移。

**验收检查：** 结束前无写入，边界允许，三类版本不分支，原校验和审计测试通过。

**回滚：** 恢复服务构造函数和内部窗口校验，删除本需求新增测试；不处理数据。

**停止条件：** 现有 `Entry.windowId` 无法定位租户窗口；必须修改数据库或公开接口才能判断资格。

**升级条件：** 用户要求管理员/应急绕过；业务边界改为窗口状态而非结束时间。

### T2：前端窗口原因与只读维护状态

**需求映射：** R1, R2, R3, R5

**前置任务：** 无

**文件：**
- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue`

**接口：**
- 消费：现有 `ReleaseWindowDto.productionEnd`、`statusLabel` 和基线列表
- 产出：`selectedWindowMaintainable`、`selectedWindowUnavailableReason` 以及合并结果状态后的 `isMaintainable(entry)`

- [ ] **步骤 1：建立静态行为基准**

记录当前行为：下拉选项只有单行标签；`isMaintainable` 只判断 `productionResult === 'RELEASED'`；批量按钮只判断是否选中。

- [ ] **步骤 2：实施时间派生与定时刷新**

增加分钟级 `now` 响应状态和组件卸载清理。以 `new Date(selectedWindow.productionEnd).getTime()` 与当前时间比较，恰好到达边界时允许。解析异常时保守禁用并提示“投产窗口结束时间无效，请刷新后重试”。

- [ ] **步骤 3：定制窗口选项和页面提示**

将 `el-option` 改为双行内容：第一行编码、名称、状态；第二行显示投产结束时间，未结束时明确“不可维护”。选项不得设置 `disabled`。选中未结束窗口后，在工具栏下方显示不可维护原因。

- [ ] **步骤 4：统一控制所有维护执行器**

`isMaintainable(entry)` 同时要求窗口已结束且明细为 `RELEASED`。表格选择、单条按钮和批量按钮共享该判断；未结束时操作列显示“窗口未结束”。`openResult`、`openBatchResult` 和 `saveResult` 保留防御性校验，防止状态变化后继续提交已打开弹窗。

- [ ] **步骤 5：运行前端构建和静态检查**

运行：

```bash
npm --prefix web run build
git diff --check -- web/src/modules/release/components/ReleaseBaselineView.vue
```

预期：`vue-tsc --noEmit` 和 Vite 构建通过；无空白错误。

**验收检查：** 未结束窗口可选择且可查看；原因和结束时间可见；所有维护入口禁用；时间到达后重新计算为可维护。

**回滚：** 恢复单行 `el-option` 和原 `isMaintainable`，移除计时器与提示，不修改 API。

**停止条件：** 现有窗口响应缺少可解析的 `productionEnd`；实现需要修改全局 CSS 或共享 UI 组件。

**升级条件：** 需要秒级倒计时、跨时区切换或移动端专用布局。

### T3：集成验证与控制证据

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T1, T2

**文件：**
- 新建：`.ai-control/requirements/req-20260818-037-production-baseline-maintenance-window/execution-T1.json`
- 新建：`.ai-control/requirements/req-20260818-037-production-baseline-maintenance-window/observation-T1.json`
- 新建：`.ai-control/requirements/req-20260818-037-production-baseline-maintenance-window/convergence.json`

**接口：**
- 消费：T1 服务端门禁、T2 页面状态、现有本地前后端服务
- 产出：可复验构建、测试、浏览器、治理和范围证据

- [ ] **步骤 1：重启或热加载本地服务**

确认前端 `127.0.0.1:5173` 和后端服务使用本次代码；后端构造函数变化后必须重启 JAR。

- [ ] **步骤 2：执行桌面浏览器验收**

在“配置管理 / 投产基线”选择未结束窗口，验证下拉第二行原因、基线仍显示、选择框不可用、批量按钮禁用、操作列显示“窗口未结束”。检查控制台无新增错误。

- [ ] **步骤 3：执行最终自动化检查**

运行发布模块完整测试、前端生产构建、`node scripts/check-all-governance.mjs`、本需求 scope checker 和 `git diff --check`。已知 `rokey` 分支名和其他历史账本失败必须如实披露，不得修改治理规则掩盖。

- [ ] **步骤 4：记录执行、观测和收敛证据**

使用控制账本记录任务状态、至少两次 standard 模式采样、需求覆盖、残余风险和最终门禁。只有 T1/T2 验证通过且无开放 P0/P1 才可转为 `converged`。

**验收检查：** 真实页面、直接服务测试、构建和范围证据共同覆盖 R1-R5。

**回滚：** 仅移除本需求控制证据和回退 T1/T2 改动；保留失败日志供复盘。

**停止条件：** 本地服务无法加载本次后端代码；浏览器数据不存在未结束窗口；新增 P0/P1 偏差。

**升级条件：** 需要修改测试数据、生产数据或用户未授权的共享平台模块才能完成验收。

## 集成检查

```bash
env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am test
npm --prefix web run build
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260818-037-production-baseline-maintenance-window/codex-task-scope.yaml --working-tree
git diff --check
```

预期：后端测试、前端构建和 diff 通过；治理与 scope 若仅命中已知历史账本或 `rokey` 分支规则，作为外部基线失败披露。

## 控制模型种子

以下均为 `hypotheses-only`：

- 被控边界候选：投产基线维护 UI、投产结果服务和服务测试。
- 状态变量候选：当前时间、选中窗口、窗口结束时间、维护资格、明细结果、批量选择和弹窗状态。
- 接口候选：窗口列表 DTO、单条结果命令、批量结果命令和服务端业务错误。
- 传感器候选：固定 Clock 单元测试、发布模块回归、前端构建、浏览器 DOM/交互、控制台和 diff。
- 执行器候选：前端维护入口启停、服务端窗口校验和结果写入。
- 扰动候选：客户端时钟偏差、管理员调整窗口时间、批量跨窗口异常、热更新未重启后端和 dirty worktree。
- 时延候选：一分钟前端计时刷新、后端重启、窗口列表刷新和弹窗过渡。
- 假设候选：`productionEnd` 可由浏览器稳定解析；Entry 的 `windowId` 始终指向同租户有效窗口；事务代理覆盖批量方法。

## 风险与用户批准

高风险动作仅为新增服务端写入前置门禁，可能改变现有提前维护调用的结果；不涉及迁移或数据变更。用户已批准当前修订，可导入控制账本并开始修改产品代码。
