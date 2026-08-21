# 通知业务板块展示与筛选实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 为平台通知增加显式业务板块元数据、工作流上下文传播和服务端板块筛选，并在统一通知抽屉中提供清晰展示与筛选能力。

**架构：** `platform/system` 继续拥有通知数据和用户已读状态，公开 Publisher 增加板块字段；`platform/workflow` 只保存和传递来源业务板块，`platform/boot` 的通知桥完成工作流事件到通知的组合；配置管理在发起流程时显式声明 `release / 配置管理`。前端公共通知抽屉通过新增聚合接口获取当前用户的板块选项和计数，分页接口按 `moduleCode` 服务端过滤。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4、Flyway、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-18-notification-business-module-filter-design.md`
- 需求文档：`docs/requirements/REQ-20260818-034-notification-business-module-filter/requirement.md`
- 状态：可移交

## 全局约束

- 只修改 `codex-task-scope.yaml` 的 `writable_paths`，保护 `rokey` 工作区中其他未提交修改。
- `platform/system` 是通知和用户已读状态的数据 Owner；业务模块不得直接写 `sys_notification` 或 `sys_user_notification`。
- `platform/workflow` 不硬编码新业务模块映射；兼容映射仅服务存量上下文，新流程必须显式传递板块。
- 通知查询必须从认证主体获取租户和用户，不能接受客户端用户身份。
- 铃铛角标保持全局未读；板块筛选只影响通知页、分页总数和未读标签计数。
- `actionPath` 继续只允许 `/` 开头的站内路由；幂等键和全部已读语义保持不变。
- Flyway 只追加。实施前重新检查迁移序号；如果 V44 已被远程或用户并行修改占用，仅重命名本需求尚未执行的迁移。
- 保持 Java 17、Spring Boot 3.4.4、MySQL 8.4、Vue 3、TypeScript、Element Plus 和现有统一响应模型。
- 通知抽屉必须验证桌面及 `375x812`、`390x844`、`430x932`，不能产生页面级横向溢出。
- 不增加外部通知渠道、模板、偏好、附件、删除、板块管理或项目筛选。

---

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V44__notification_business_module.sql` | candidate-new | 追加通知与工作流板块字段、索引和存量映射 |
| `server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationPublishCommand.java` | existing | 公共通知发布命令 |
| `server/src/platform/system/src/main/java/com/ccb/system/notification/SystemNotificationItem.java` | existing | 用户通知返回模型 |
| `server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationModuleSummary.java` | candidate-new | 当前用户板块选项和总数/未读数返回模型 |
| `server/src/platform/system/src/main/java/com/ccb/system/service/SystemNotificationService.java` | existing | 发布、分页、板块聚合、计数、已读和审计 |
| `server/src/platform/system/src/main/java/com/ccb/system/web/NotificationController.java` | existing | 认证通知 HTTP 接口 |
| `server/src/platform/system/src/test/java/com/ccb/system/service/SystemNotificationServiceTest.java` | existing | 通知服务契约、隔离、筛选和兼容回归 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowBusinessContext.java` | existing | 业务工作流上下文公开契约 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java` | existing | 工作流上下文校验、持久化和查询 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleEventService.java` | existing | 生命周期事件板块字段写入 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleDispatcher.java` | existing | 生命周期事件板块字段还原与派发 |
| `server/src/platform/boot/src/main/java/com/ccb/boot/integration/WorkflowSystemNotificationBridge.java` | existing | 工作流待办和结果通知组合桥 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java` | existing | 配置管理发起工作流时提供板块元数据 |
| `web/src/types/notification.ts` | existing | 通知、分页和板块选项前端类型 |
| `web/src/api/notifications.ts` | existing | 板块筛选、板块选项和已读请求 |
| `web/src/components/ui/UiNotificationCenter.vue` | existing | 公共通知抽屉展示、筛选、分页和计数状态 |
| `web/src/styles.css` | existing | 通知抽屉桌面和移动布局 |
| `mock/mock-data.json` | existing | 本地通知板块演示数据 |
| `MockDataInitializer.java` / `MockDataInitializerTest.java` | existing | 本地 Mock 列契约与同步验证 |
| `docs/integration/system-notification-contract.md` | existing | 业务模块通知发布契约 |
| `docs/integration/workflow-module-contract.md` | existing | 工作流业务上下文接入契约 |

## 任务依赖图与并行策略

```text
T1 通知数据与平台公开 API
 ├──> T2 工作流上下文与通知桥
 │      └──> T3 配置管理显式接入与契约文档
 └────────────────────────> T4 通知抽屉展示与筛选
T2 + T3 + T4 ─────────────> 集成验收
```

执行采用 `T1 -> T2 -> T3 -> T4` 串行顺序。T4 理论上可在 T1 后与 T2 并行，但当前分支存在大量未提交跨模块修改，串行能降低共享契约漂移和误覆盖风险。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 独立板块语义 | T1、T2、T3、T4 |
| R2 工作流显式传递 | T2、T3 |
| R3 展示与服务端筛选 | T1、T4 |
| R4 全局与板块计数 | T1、T4 |
| R5 存量兼容 | T1、T2、T3 |
| R6 用户隔离与错误行为 | T1、T4 |
| R7 响应式和状态完整 | T4 |

### T1：通知数据、发布契约和用户范围查询

**需求映射：** R1、R3、R4、R5、R6

**前置任务：** 无

**已证实事实：** `sys_notification` 已保存 `business_type`、`business_key`、`source_name` 和 `action_path`；`SystemNotificationService.list` 从 `sys_user_notification` 按认证用户分页；现有公开命令只有两个仓库调用点。

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V44__notification_business_module.sql`（实施前确认编号）
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationModuleSummary.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationPublishCommand.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/notification/SystemNotificationItem.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/service/SystemNotificationService.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/web/NotificationController.java`
- 修改：`server/src/platform/system/src/test/java/com/ccb/system/service/SystemNotificationServiceTest.java`
- 修改：`server/src/platform/infrastructure/src/main/java/com/ccb/infrastructure/mock/MockDataInitializer.java`
- 修改：`server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/mock/MockDataInitializerTest.java`
- 修改：`mock/mock-data.json`

**接口：**

- 消费：现有认证 `AuthUser`、`PageQuery`、`SystemNotificationPublisher`。
- 产出：`NotificationPublishCommand(..., String moduleCode, String moduleName, String businessType, ...)`。
- 产出：`SystemPage<SystemNotificationItem> list(PageQuery pageQuery, boolean unreadOnly, String moduleCode, AuthUser user)`。
- 产出：`List<NotificationModuleSummary> modules(AuthUser user)`，元素包含 `moduleCode`、`moduleName`、`totalCount`、`unreadCount`。
- 产出：`GET /api/notifications?moduleCode=` 和 `GET /api/notifications/modules`。

- [ ] **步骤 1：建立通知字段、筛选、聚合和隔离失败测试**

  在 `SystemNotificationServiceTest` 增加：缺少/非法板块发布被拒绝；发布 SQL 持久化板块；列表 SQL 同时包含认证租户、用户和可选 `module_code`；未知板块返回空分页；板块聚合返回当前用户总数/未读数。Mock 初始化测试增加新列断言。

- [ ] **步骤 2：运行当前测试并记录预期失败**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-system,:ccb-infrastructure -am test`

  预期：新增测试因命令、DTO、方法或数据库列尚不存在而失败；原测试继续编译到受影响契约处。

- [ ] **步骤 3：追加数据库迁移和 Mock 列契约**

  为通知增加 `module_code`、`module_name` 和板块查询索引；为工作流实例和生命周期事件增加可空板块上下文字段供 T2 使用。按设计完成已知类型映射和确定性兜底，再将通知板块字段收紧为非空。同步 `mock/mock-data.json` 与初始化器列清单，不修改 V26 或 V35。

- [ ] **步骤 4：实现公共通知模型、校验、查询和接口**

  板块编码校验为小写字母开头，后续允许小写字母、数字、下划线和连字符，最长 64；名称去首尾空白后最长 128。分页只在 `moduleCode` 非空时追加等值条件；未知编码自然返回空页。板块聚合从当前用户关联表起查，按最近通知时间倒序。

- [ ] **步骤 5：运行局部回归**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-system,:ccb-infrastructure -am test`

  预期：目标模块测试通过，0 个失败；SQL 参数顺序、认证范围和存量 Mock 同步测试通过。

- [ ] **步骤 6：建立检查点**

  记录实际迁移编号、文件差异和测试输出；在用户要求提交前不执行 `git commit`。

**验收与证据：** R1/R3/R4/R5/R6 服务测试；迁移 SQL 与 Mock 列对照；目标 Maven 测试退出码 0。

**回滚：** 回退 T1 Java、Mock 和接口变更；已执行迁移时保留新增列、索引和补齐值，不删除数据。

**停止条件：** 当前集成分支已有 V44；`sys_notification` 存在无法按规则补齐的空/异常数据；模块聚合无法保持用户范围。

**升级条件：** 需要修改通知幂等键、全部已读语义或引入运行时板块管理表。

### T2：工作流板块上下文传播与通知桥

**需求映射：** R1、R2、R5

**前置任务：** T1

**已证实事实：** `WorkflowBusinessContext` 当前保存业务类型、主键、标题、轮次、项目、路由和摘要；实例、生命周期事件及 Dispatcher 均显式列出这些字段；通知桥位于 `platform/boot`，是工作流与系统通知的组合边界。

**文件：**

- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowBusinessContext.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleEventService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleDispatcher.java`
- 修改：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowBusinessIntegrationServiceTest.java`
- 修改：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowLifecycleDispatcherTest.java`
- 修改：`server/src/platform/boot/src/main/java/com/ccb/boot/integration/WorkflowSystemNotificationBridge.java`
- 修改：`server/src/platform/boot/src/test/java/com/ccb/boot/integration/WorkflowSystemNotificationBridgeTest.java`

**接口：**

- 消费：T1 的 `NotificationPublishCommand` 板块字段和迁移列。
- 产出：`WorkflowBusinessContext(String moduleCode, String moduleName, String businessType, ...)`。
- 产出：任务通知和审批结果通知原样携带上下文板块；旧上下文进入仅存量使用的兼容解析器。

- [ ] **步骤 1：建立上下文持久化、事件传播和桥接失败测试**

  断言 attach/start 将板块字段写入 `wf_instance`，生命周期事件写入并还原两个字段，待办与结果通知包含板块；增加空板块新上下文拒绝和存量空字段兼容用例。

- [ ] **步骤 2：运行工作流与组合桥测试并记录预期失败**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow,:ccb-boot -am test`

  预期：新增构造参数、SQL 字段和通知命令断言失败。

- [ ] **步骤 3：扩展工作流公开上下文和持久化链路**

  在新业务上下文校验中要求合法板块；实例更新、详情读取、生命周期事件插入和 Dispatcher 查询全部显式读写 `business_module_code`、`business_module_name`。存量数据库空字段保持可读，不改变实例版本绑定或任务状态机。

- [ ] **步骤 4：更新通知桥投影和兼容解析**

  待办 SQL 投影板块字段并发布到 T1 命令；结果通知读取事件上下文。仅当存量字段为空时按 `release_application/release`、`delivery`、`system` 的已知映射处理，未知值使用规范化 `businessType` 编码和可识别名称并记录告警；新上下文不走兜底。

- [ ] **步骤 5：运行局部回归**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-workflow,:ccb-boot -am test`

  预期：上下文、生命周期、待办、结果和存量兼容测试全部通过，0 个失败。

- [ ] **步骤 6：建立检查点**

  记录所有 `WorkflowBusinessContext` 构造点搜索结果和局部测试证据，不提交无关工作区修改。

**验收与证据：** 上下文 SQL 捕获、生命周期 Dispatcher 测试、Bridge 发布命令断言、聚焦测试退出码 0。

**回滚：** 回退工作流和组合桥代码；保留 T1 已追加的可空工作流列，不影响旧代码读取。

**停止条件：** 发现其他未纳入范围的外部模块直接构造 `WorkflowBusinessContext`；存量上下文无法确定业务类型；当前运行实例使用与 V35 不一致的结构。

**升级条件：** 需要新增板块注册中心，或兼容规则必须依赖业务模块私有数据。

### T3：配置管理显式接入与公开契约同步

**需求映射：** R1、R2、R5

**前置任务：** T2

**已证实事实：** `ReleaseSubmissionService` 是配置管理流程发起入口，业务类型为 `release_application`，详情路由为 `/release/applications/{code}`；配置管理不直接写工作流或通知表。

**文件：**

- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`
- 修改：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java`
- 修改：`server/src/modules/release/src/test/java/com/ccb/release/integration/ReleaseWorkflowLifecycleConsumerTest.java`
- 修改：`docs/integration/system-notification-contract.md`
- 修改：`docs/integration/workflow-module-contract.md`

**接口：**

- 消费：T2 的板块感知 `WorkflowBusinessContext`。
- 产出：配置管理固定声明 `MODULE_CODE = "release"`、`MODULE_NAME = "配置管理"`；公开文档提供后续业务模块接入示例和存量边界。

- [ ] **步骤 1：建立配置管理上下文断言**

  在提交流程测试中捕获 `WorkflowStartDefinitionCommand.context()`，断言 `moduleCode=release`、`moduleName=配置管理`、`businessType=release_application`、业务单号和详情路由保持不变；更新生命周期消费者测试构造器。

- [ ] **步骤 2：运行配置管理测试并记录预期失败**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am test`

  预期：新增板块断言在实现前失败或构造参数尚未满足。

- [ ] **步骤 3：实施配置管理显式板块接入**

  只在流程发起上下文中增加板块常量，不修改版本类型、窗口、附件、冲突、审批绑定、状态流或路由。

- [ ] **步骤 4：同步两个公开接入契约**

  系统通知契约说明板块、来源和业务类型的差异及查询接口；工作流契约要求新业务上下文提供板块，说明存量兼容不是新接入默认值。示例必须使用当前字段顺序和真实站内路由。

- [ ] **步骤 5：运行配置管理回归**

  运行：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am test`

  预期：配置管理测试通过，审批提交状态和既有业务规则无回归。

- [ ] **步骤 6：建立检查点**

  对照需求确认仅增加板块上下文和契约，不夹带配置管理业务规则变更。

**验收与证据：** 捕获的流程上下文字段、配置管理测试退出码 0、契约示例与 Java 签名一致。

**回滚：** 回退配置管理常量、上下文参数和契约文档；旧流程仍由 T2 存量兼容处理。

**停止条件：** 当前提交服务存在用户并行修改导致构造点或业务类型变化；流程发起已迁移到其他服务。

**升级条件：** 配置管理板块编码或名称需要管理员运行时配置，或同一申请需要归属多个板块。

### T4：通知抽屉板块展示、筛选和响应式验收

**需求映射：** R1、R3、R4、R6、R7

**前置任务：** T1、T3

**已证实事实：** `UiNotificationCenter` 现有“全部/未读”、分页、15 秒全局未读轮询、聚焦刷新、单条已读和全局全部已读；顶部当前只显示 `sourceName`；前端没有单元测试框架。

**文件：**

- 修改：`web/src/types/notification.ts`
- 修改：`web/src/api/notifications.ts`
- 修改：`web/src/components/ui/UiNotificationCenter.vue`
- 修改：`web/src/styles.css`

**接口：**

- 消费：T1 的通知新增字段、`moduleCode` 分页参数和模块聚合接口。
- 产出：`NotificationModuleSummary` 前端类型；`getNotificationModules()`；`getNotifications(page, size, unreadOnly, moduleCode?)`。
- 产出：抽屉顶部“模块 · 来源”、可搜索选择器、当前范围未读数和恢复状态。

- [ ] **步骤 1：扩展前端类型和 API 契约**

  为 `SystemNotification` 增加 `moduleCode/moduleName`，新增板块摘要类型；分页请求只在已选板块时发送 `moduleCode`，避免空字符串语义不一致。

- [ ] **步骤 2：实现抽屉状态和请求编排**

  增加 `selectedModuleCode`、板块选项、加载和失败状态；打开抽屉并行加载列表、全局未读和板块选项。切换板块或全部/未读重置分页；使用请求序号或等价机制防止旧响应覆盖新筛选。单条和全部已读后刷新权威全局及板块计数。

- [ ] **步骤 3：实现展示和筛选控件**

  顶部元信息显示 `moduleName · sourceName`，板块下拉使用 Element Plus 可搜索选择器并提供“全部业务”。板块选项加载失败时禁用筛选、显示明确重试入口，但保留未筛选通知列表。未知/过时选项清回“全部业务”。

- [ ] **步骤 4：实现桌面和移动样式**

  复用现有语义变量和通知抽屉结构；桌面选择器与状态标签分层，`760px` 以下选择器占满可用宽度。长板块名省略但保留完整可访问文本，通知标题和业务单号继续换行，不新增固定最小宽度或页面级横向滚动。

- [ ] **步骤 5：运行类型检查和生产构建**

  运行：`npm --prefix web run build`

  预期：`vue-tsc --noEmit` 和 Vite 构建成功；无 TypeScript 错误。

- [ ] **步骤 6：使用真实后端数据执行浏览器验收**

  准备至少“配置管理”和另一板块的当前用户通知；验证默认全部、配置管理筛选、未读筛选、加载更多、单条已读、全部已读、跳转、刷新和筛选失败恢复。桌面及 `375x812`、`390x844`、`430x932` 检查无重叠和横向溢出，并检查控制台错误。

- [ ] **步骤 7：建立检查点**

  保存构建退出码、浏览器路径/视口/结果和截图；在用户要求提交前不执行提交或推送。

**验收与证据：** 前端构建、真实 API 网络结果、桌面和移动截图、`scrollWidth <= clientWidth`、控制台无新增错误。

**回滚：** 回退通知抽屉、类型、API 和样式；后端新增字段和接口保持兼容且可闲置。

**停止条件：** 后端接口字段与冻结契约不一致；浏览器登录或真实双板块数据不可用；公共抽屉出现其他业务页面回归。

**升级条件：** 用户要求筛选条件跨会话持久化、按项目筛选、筛选范围内全部已读或板块管理页面。

## 集成检查

1. 运行开发入口：`node scripts/check-development-entry.mjs --require-plugin`，预期通过。
2. 解析当前任务 JSON：`node -e "const fs=require('fs'); for (const f of process.argv.slice(1)) JSON.parse(fs.readFileSync(f,'utf8')); console.log('passed')" .ai-control/requirements/req-20260818-034-notification-business-module-filter/*.json`。
3. 执行 JDK 17 全量后端：`env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`，预期 0 个失败。
4. 执行前端构建：`npm --prefix web run build`，预期成功。
5. 在隔离 MySQL 8.4 库执行 Flyway，查询通知空板块数为 0，已知类型映射正确，运行中工作流仍可完成。
6. 执行治理：`node scripts/check-all-governance.mjs`；若历史账本仍失败，记录与本需求无关的精确文件，不修改其他任务账本。
7. 执行范围检查：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260818-034-notification-business-module-filter/codex-task-scope.yaml --working-tree`。当前 `rokey` 与脚本分支命名规则冲突，实施前必须由用户继续授权该例外或切换合规分支；不得伪报通过。
8. 执行 `git diff --check` 并审计实际修改面，确认无无关格式化、迁移历史修改和用户变更覆盖。

## 控制模型种子

以下均为 `hypotheses-only`，导入后由系统建模阶段验证：

- 被控边界候选：通知发布/查询、工作流业务上下文、组合桥、配置管理流程发起、通知抽屉、追加迁移。
- 状态变量候选：通知板块字段完整率、工作流上下文板块完整率、全局未读数、筛选未读数、当前筛选、分页、请求代次、迁移版本。
- 接口候选：`NotificationPublishCommand`、`WorkflowBusinessContext`、通知分页、板块聚合、生命周期事件、配置管理流程提交。
- 传感器候选：JUnit SQL/DTO 断言、Maven 编译、Flyway 查询、API 响应、Vite 构建、浏览器 DOM/截图/控制台、范围和治理检查。
- 执行器候选：追加迁移、Java record 和 Service 变更、查询条件、前端状态与样式、兼容映射。
- 扰动候选：`rokey` 并行未提交修改、迁移编号竞争、存量空上下文、旧浏览器请求返回、通知轮询并发。
- 时延候选：15 秒角标轮询、60 秒待办通知补偿、分页/聚合网络时延、Flyway 启动时延。
- 假设：现有业务模块能在发起时确定唯一板块；全部已读保持全局语义；历史名称不追溯重命名。

## 风险与用户批准

- 高风险动作：公共 Java 契约变化、工作流持久化字段传播、追加数据库迁移和公共通知抽屉改造。
- 数据风险：迁移映射错误会造成历史通知误分类；必须先统计后迁移并抽样验证。
- 集成风险：任何 `WorkflowBusinessContext` 或 `NotificationPublishCommand` 构造点遗漏都会在编译或运行期暴露；必须全仓搜索并执行全量测试。
- 分支风险：用户明确批准继续在 `rokey` 实施并接受当前范围脚本的分支命名例外；不自行切分支、不提交、不推送，范围检查需如实记录该例外。
- 回退原则：回退应用代码，保留已执行的新增列、索引和补齐数据，不改写 Flyway 历史。
