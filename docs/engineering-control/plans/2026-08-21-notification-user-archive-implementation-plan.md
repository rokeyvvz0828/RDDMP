# 通知用户级归档实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 为通知中心增加用户级已读归档、已归档查看与恢复、批量归档能力，同时保留共享通知和业务审计证据。

**架构：** 在 `sys_user_notification` 上增加可空 `archived_at` 作为单一归档状态源；`platform/system` 统一实现活动、未读、归档三种认证用户视图及状态转换。前端公共通知抽屉消费该契约，保留现有一秒未读轮询，只在抽屉交互时加载完整列表。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4、Flyway、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-21-notification-user-archive-design.md`
- 状态：已确认

## 全局约束

- `sys_notification` 不做物理删除，归档只更新当前用户的 `sys_user_notification`。
- 仅已读且未归档通知可归档；恢复后保持已读，不增加未读角标。
- 所有查询和更新从认证主体获取 `tenant_id`、`user_id`，客户端不得指定身份。
- 保留 `GET /api/notifications` 的旧 `unreadOnly` 参数兼容；新前端使用 `view=ALL|UNREAD|ARCHIVED`。
- 现有发布、标记已读、全部已读、一秒前台轮询和失败退避行为保持兼容。
- Flyway 只追加，使用实施预检确认未占用的 `V79`；如果实施期间被并行变更占用，停止并重新确定编号。
- 不增加自动归档、保留期限、物理清理、管理员归档、批量恢复或外部通知渠道。
- 不修改无关模块，不覆盖用户或其他开发者的工作区变更。
- 用户未要求提交前不创建 Git 提交。

---

## 文件职责地图

| 路径 | 状态 | 职责 | 事实依据 |
| --- | --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V79__notification_user_archive.sql` | candidate-new | 增加 `archived_at` 和用户归档查询索引 | 实施预检时当前最新迁移为 V78 |
| `server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationView.java` | candidate-new | 定义 ALL、UNREAD、ARCHIVED 查询视图 | 当前接口仅有 boolean `unreadOnly` |
| `server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationArchiveResult.java` | candidate-new | 返回批量归档实际变更数 | 现有 `NotificationReadAllResult` 只表达全部已读 |
| `server/src/platform/system/src/main/java/com/ccb/system/notification/SystemNotificationItem.java` | existing | 增加 `archivedAt` 返回字段 | 当前已有 readAt、createdAt |
| `server/src/platform/system/src/main/java/com/ccb/system/service/SystemNotificationService.java` | existing | 查询过滤、归档、恢复、批量归档和隔离校验 | 当前拥有全部通知读写逻辑 |
| `server/src/platform/system/src/main/java/com/ccb/system/web/NotificationController.java` | existing | 暴露认证通知视图和归档命令 | 当前统一挂载 `/api/notifications` |
| `server/src/platform/system/src/test/java/com/ccb/system/service/SystemNotificationServiceTest.java` | existing | 覆盖 SQL 范围、状态转换、幂等和兼容 | 当前唯一通知服务测试 |
| `docs/integration/system-notification-contract.md` | existing | 更新用户查询和归档接口契约 | 当前通知平台公开契约 |
| `web/src/types/notification.ts` | existing | 增加通知视图和归档时间类型 | 当前通知前端 DTO |
| `web/src/api/notifications.ts` | existing | 新增视图、归档、恢复和批量归档请求 | 当前通知 API 客户端 |
| `web/src/components/ui/UiNotificationCenter.vue` | existing | 三页签、单条动作、批量确认和刷新状态 | 当前全局通知抽屉 |
| `web/src/styles.css` | existing | 归档动作和三页签桌面/移动布局 | 当前通知样式位于同一全局文件 |

## 任务依赖图与并行策略

```text
T1 数据库与后端通知归档契约
  -> T2 通知抽屉归档与恢复交互
      -> T3 集成、真实数据和回归验收
```

三个任务串行执行。T2 依赖 T1 的最终参数、状态码和返回类型；T3 依赖迁移后的真实数据库与前端交互，不安排并行写入。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 用户级归档 | T1、T2、T3 |
| R2 已归档查看与恢复 | T1、T2、T3 |
| R3 批量归档已读 | T1、T2、T3 |
| R4 计数一致性 | T1、T2、T3 |
| R5 权限与审计边界 | T1、T3 |

### T1：数据库与后端通知归档契约

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** 无

**已证实事实：** `sys_user_notification` 当前以 `(notification_id, user_id)` 为主键并保存 `is_read/read_at`；`SystemNotificationService` 的所有用户查询已经从 `AuthUser` 获取租户和用户；当前最新 Flyway 文件是 V46；列表已有 `unreadOnly` 和 `moduleCode` 参数。

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V79__notification_user_archive.sql`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationView.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/notification/NotificationArchiveResult.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/notification/SystemNotificationItem.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/service/SystemNotificationService.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/web/NotificationController.java`
- 修改：`server/src/platform/system/src/test/java/com/ccb/system/service/SystemNotificationServiceTest.java`
- 修改：`docs/integration/system-notification-contract.md`

**接口：**
- 消费：认证 `AuthUser`、现有 `PageQuery`、旧 `unreadOnly`、可选 `moduleCode`。
- 产出：`enum NotificationView { ALL, UNREAD, ARCHIVED }`。
- 产出：`SystemPage<SystemNotificationItem> list(PageQuery, NotificationView, String, AuthUser)`，旧布尔参数在控制器中解析为视图。
- 产出：`List<NotificationModuleSummary> modules(NotificationView, AuthUser)`。
- 产出：`void archive(long notificationId, AuthUser user)`、`void restore(long notificationId, AuthUser user)`、`NotificationArchiveResult archiveRead(AuthUser user)`。
- 产出：`PATCH /api/notifications/{id}/archive`、`PATCH /api/notifications/{id}/restore`、`PATCH /api/notifications/archive-read`。

- [ ] **步骤 1：建立状态转换与查询失败测试**

  在 `SystemNotificationServiceTest` 增加测试：活动列表包含 `archived_at IS NULL`；归档列表包含 `archived_at IS NOT NULL`；模块汇总按视图过滤；未读归档抛出 409 对应业务异常；归档、恢复和批量归档 SQL 含认证租户/用户；批量条件固定为 `is_read = 1 AND archived_at IS NULL`；恢复不修改 `is_read/read_at`；旧未读参数仍解析为 UNREAD。

- [ ] **步骤 2：运行当前测试并确认红灯信号**

  运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-system -am test`

  预期：新增测试因 `NotificationView`、`archived_at` 查询或归档方法尚不存在而失败；不得出现与本需求无关的既有测试失败。

- [ ] **步骤 3：追加 V79 迁移**

  创建以下等价结构，不修改历史迁移：

  ```sql
  ALTER TABLE sys_user_notification
      ADD COLUMN archived_at TIMESTAMP NULL AFTER read_at,
      ADD KEY idx_sys_user_notification_archive
          (tenant_id, user_id, archived_at, is_read, created_at, notification_id);
  ```

  历史行保持 `archived_at = NULL`，不做回填，不删除旧未读索引。

- [ ] **步骤 4：实现视图查询和状态转换**

  `ALL` 固定追加 `archived_at IS NULL`；`UNREAD` 固定追加 `archived_at IS NULL AND is_read = 0`；`ARCHIVED` 固定追加 `archived_at IS NOT NULL`。归档先在当前用户范围查询状态：未读返回明确冲突，已归档或不可见记录保持无副作用；可归档行设置 `archived_at = now()`。恢复只清空 `archived_at`。批量归档只更新当前用户活动已读行并返回更新数。

- [ ] **步骤 5：实现控制器兼容和契约文档**

  `view` 非空时按枚举解析；缺省时使用 `unreadOnly ? UNREAD : ALL`。非法视图返回 400。模块接口接受相同 `view`，新归档命令不接受租户或用户参数。同步公开契约的状态流、接口、幂等和审计边界。

- [ ] **步骤 6：运行局部回归并记录证据**

  运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-system -am test`

  预期：目标及依赖模块 `BUILD SUCCESS`，0 个失败；新增测试覆盖 R1-R5。

**验收与证据：** V79 内容、Mockito SQL 参数断言、异常消息、更新行数、Maven 退出码和测试计数。

**回滚：** 回退 T1 Java、测试和契约文档；已执行 V79 时保留可空列与索引，不运行破坏性逆向迁移。

**停止条件：** V79 已被其他变更占用；目标数据库不是 MySQL 8.4 兼容；现有生产表结构与 V26/V44 不一致；实现需要物理删除通知。

**升级条件：** 需要改变通知发布幂等键、允许未读归档、增加管理员跨用户操作或改变审计保留策略。

### T2：通知抽屉归档与恢复交互

**需求映射：** R1、R2、R3、R4

**前置任务：** T1

**已证实事实：** `UiNotificationCenter` 当前拥有 ALL/UNREAD 两页签、业务板块筛选、分页、整体通知项点击跳转和一秒未读轮询；项目未配置 Vue 组件测试框架，前端传感器为 TypeScript/Vite 构建与浏览器交互。

**文件：**
- 修改：`web/src/types/notification.ts`
- 修改：`web/src/api/notifications.ts`
- 修改：`web/src/components/ui/UiNotificationCenter.vue`
- 修改：`web/src/styles.css`

**接口：**
- 消费：T1 的 `NotificationView` 字符值、`archivedAt`、单条归档/恢复和 `archive-read` 返回值。
- 产出：`type NotificationView = 'ALL' | 'UNREAD' | 'ARCHIVED'`。
- 产出：三页签状态映射、单条 `archiveNotification`、`restoreNotification`、批量 `archiveReadNotifications` 交互。

- [ ] **步骤 1：记录当前前端构建基线**

  运行：`npm --prefix web run build`

  预期：当前基线构建成功；若失败，先区分既有问题，不能用本需求掩盖。

- [ ] **步骤 2：扩展类型和 API 客户端**

  将列表和模块请求改为显式 `view`，增加 `archiveNotification(id)`、`restoreNotification(id)`、`archiveReadNotifications()`。保留后端兼容但新前端不再发送 `unreadOnly`。`SystemNotification` 增加可空 `archivedAt`。

- [ ] **步骤 3：实现三视图和刷新规则**

  页签为“全部 / 未读 N / 已归档”。切换页签同时重载列表和对应板块选项；切换视图时如果当前板块在新视图不存在则清空筛选。轮询只更新活动未读数；抽屉开启且未读数变化时，活动视图可重载，归档视图不因轮询刷新。

- [ ] **步骤 4：实现单条与批量操作**

  已读活动项显示仅图标归档按钮并提供 tooltip/aria-label；归档项显示恢复按钮。按钮使用 `@click.stop`，避免触发通知跳转。头部增加“归档全部已读”，仅存在活动已读项或后端可归档总数时可用；点击后用 `ElMessageBox.confirm` 二次确认，成功后显示实际归档数量并刷新列表、板块和角标。未读项不显示归档按钮。

- [ ] **步骤 5：调整稳定布局和空状态**

  通知项维持固定图标、正文和动作列；三页签不挤压溢出；“已归档”空状态使用明确文案。检查 420px 抽屉和 375px、390px、430px 移动视口，按钮不覆盖标题和时间。

- [ ] **步骤 6：运行前端构建与静态差异检查**

  运行：`npm --prefix web run build`

  预期：TypeScript 和 Vite 构建成功，无未使用导入、类型不一致或模板编译错误。

  运行：`git diff --check -- web/src/types/notification.ts web/src/api/notifications.ts web/src/components/ui/UiNotificationCenter.vue web/src/styles.css`

  预期：无空白错误。

**验收与证据：** 构建输出、桌面/移动截图、按钮可访问名称、事件不冒泡、三视图与板块选项刷新记录。

**回滚：** 回退四个前端文件；T1 后端保持兼容，旧两页签客户端仍可运行。

**停止条件：** T1 接口与计划契约不一致；通知组件存在并行未合并修改；三页签在最小视口无法保持可用且需要重构全局导航。

**升级条件：** 需要新增组件测试依赖、改变全局抽屉架构或把归档操作扩展为多选批处理。

### T3：迁移、真实数据与回归验收

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** T1、T2

**已证实事实：** 本地前端运行在 5173、后端运行在 8080；仓库已有真实认证通知接口和工作流通知发布链路；既有完整后端测试和治理检查在提交 `2804543` 上通过。

**文件：**
- 修改：`.ai-control/requirements/req-20260821-046-notification-user-archive/execution-*.json`（受控执行证据）
- 修改：`.ai-control/requirements/req-20260821-046-notification-user-archive/observation-*.json`（独立观测证据）
- 修改：`.ai-control/requirements/req-20260821-046-notification-user-archive/convergence.json`（收敛结论）

**接口：**
- 消费：T1 后端、T2 前端、本地 MySQL、现有认证会话。
- 产出：可重复的真实数据状态转换、截图/请求证据、完整回归和治理结果。

- [ ] **步骤 1：启动或重启前后端并验证迁移**

  使用仓库现有启动方式重启后端和前端，确认 Flyway 应用 V79，`GET /api/notifications/unread-count` 返回 200，前端无启动错误。不得手工伪造迁移历史。

- [ ] **步骤 2：建立真实通知数据**

  通过现有工作流或业务事件生成至少三条当前用户通知：一条保持未读、两条标记已读；如业务流程无法稳定生成，使用现有公开 `SystemNotificationPublisher` 测试入口或受控数据库夹具，但不得修改生产逻辑或前端 Mock。

- [ ] **步骤 3：验证单条归档和隔离**

  归档一条已读通知，确认活动列表、模块总数和分页减一，已归档列表增加一，未读角标不变；尝试归档未读通知，确认 409 和明确提示；用另一用户验证同一通知仍可见。

- [ ] **步骤 4：验证恢复与批量归档**

  恢复已归档通知，确认回到“全部”且保持已读；执行“归档全部已读”，确认只有已读活动通知转入归档、未读通知保留、返回数量和 UI 数量一致。

- [ ] **步骤 5：验证响应式与运行质量**

  使用浏览器在桌面和 375x812、390x844、430x932 视口检查抽屉、页签、筛选、空状态和动作按钮；记录截图，检查控制台无错误、网络无意外重复列表轮询、页面无横向溢出。

- [ ] **步骤 6：运行完整回归和治理检查**

  运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn test`

  运行：`npm --prefix web run build`

  运行：`git diff --check`

  运行：`node scripts/check-all-governance.mjs`

  预期：所有命令退出码 0；若治理只报告预先存在且与本需求无关的偏差，必须单独记录，不能宣称通过。

**验收与证据：** Flyway 迁移记录、认证 API 状态与数量断言、跨用户隔离结果、桌面/移动截图、控制台与网络检查、完整测试和治理输出。

**回滚：** 回退 T2 UI 和 T1 Java；保留已执行的 V79 可空列与索引；测试通知按现有业务清理规则处理，不删除业务审计证据。

**停止条件：** 本地认证或数据库不可用；V79 迁移失败；无法建立可追溯真实通知；完整测试出现无法归因的失败；发现跨租户或跨用户修改。

**升级条件：** 需要清空用户业务数据、修改外部环境、绕过数据库迁移校验或放宽安全边界。

## 集成检查

| 完成任务 | 命令/检查 | 预期 |
| --- | --- | --- |
| T1 | `JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-system -am test` | BUILD SUCCESS，归档服务测试通过 |
| T2 | `npm --prefix web run build` | TypeScript/Vite 构建成功 |
| T1、T2 | `git diff --check` | 无空白错误 |
| T1、T2、T3 | `JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn test` | 全部 Maven 模块测试通过 |
| T1、T2、T3 | `node scripts/check-all-governance.mjs` | 治理检查通过或只存在明确的预先偏差 |
| T1、T2、T3 | 认证桌面和移动浏览器验证 | 归档、恢复、批量、隔离和布局均符合 R1-R5 |

## 控制模型种子

- 被控边界候选：通知用户接收关系、通知查询 API、通知中心抽屉。
- 状态变量候选：`is_read`、`read_at`、`archived_at`、当前视图、业务板块、分页总数、全局未读数、操作中状态。
- 接口候选：通知列表、板块汇总、未读数、标记已读、归档、恢复、批量归档。
- 传感器候选：服务单元测试、SQL 参数捕获、Flyway 历史、HTTP 状态/载荷、浏览器 DOM、网络、控制台、截图、完整构建。
- 执行器候选：设置/清空 `archived_at`、设置 `is_read/read_at`、切换视图、刷新列表与汇总。
- 扰动候选：并发归档/恢复、轮询刷新、跨用户访问、旧客户端参数、数据库迁移占用、通知项点击冒泡。
- 时延候选：一秒未读轮询、列表请求、Flyway 索引创建、路由跳转。
- 状态：以上均为 `hypotheses-only`，必须在系统建模阶段验证。

## 风险与用户批准

- 高风险动作是数据库追加迁移；不删除列、不清理历史数据，代码回退时保留新列与索引。
- 批量归档会改变当前用户全部已读通知的可见位置，因此必须二次确认并返回实际数量。
- 真实数据验收不得清空或物理删除业务通知；只使用归档/恢复状态转换。
- 用户于 2026-08-21 明确要求开始执行消息通知归档实施，计划已获批准。
