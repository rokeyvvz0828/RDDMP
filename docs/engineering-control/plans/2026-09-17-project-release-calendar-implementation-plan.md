# 项目投产日历实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-17-project-release-calendar-design.md`
- 状态：已确认
- 用户确认依据：用户在本对话中明确批准 REQ-081 计划并要求开始实施。

**目标：** 在项目详情交付独立维护、单日期投产安排的响应式日历，并由服务端项目成员授权保护。

**架构：** `ccb-system` 的项目域新增日历表、项目范围 REST CRUD、成员实体授权和审计；`ProjectView.vue` 添加日历页签并改造现有页签导航样式。前端通过既有 `project.ts` HTTP 封装读写数据，桌面显示月历，移动端重排为日期列表。

**技术栈：** Java 17、Spring Boot、JdbcTemplate、MySQL 8/Flyway、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 仅修改 `REQ-20260917-081` 的 `writable_paths`；不读取 `.env`、密钥、构建产物或 `node_modules`。
- `pm_project_release_calendar` 是项目域独立数据；不得读取、写入或同步配置管理投产窗口。
- 所有读写在服务端完成认证、租户、项目成员和实体归属检查；写入增加乐观锁和操作审计。
- Flyway 仅追加 `V214__project_release_calendar.sql`，回退不删除表或历史记录。
- 条目只有标题、投产日期和备注；不扩展时间段、状态、重复、提醒、附件、审批或跨项目查询。
- 项目现有 `?tab=` 路由、其他页签业务行为、语义主题变量和移动端适配均保持兼容。

---

## 文件职责地图

| 路径 | 状态 | 职责与事实来源 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V214__project_release_calendar.sql` | candidate-new | 追加项目日历表及月查询索引。 |
| `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectController.java` | existing | 已以 `/api/project` 暴露项目范围 REST 端点。 |
| `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java` | existing | 已集中使用 JdbcTemplate、项目访问校验与 `audit()`。 |
| `server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java` | existing | 项目服务的单元测试基线。 |
| `server/src/platform/system/src/test/java/com/ccb/system/project/ProjectReleaseCalendarMySqlTest.java` | candidate-new | 迁移后的日历持久化、项目和租户隔离测试。 |
| `web/src/types/project.ts` | existing | 项目 DTO 类型定义。 |
| `web/src/api/project.ts` | existing | 项目 HTTP API 封装。 |
| `web/src/views/ProjectView.vue` | existing | 已维护项目详情 `?tab=` 状态、项目页签、主题样式和移动规则。 |

## 任务依赖图与并行策略

`T1 -> T2 -> T3`。三项串行：T2 消费 T1 的 DTO/API，T3 验证组合后的迁移、授权与前端流程。没有安全并行组。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 项目详情日历页签 | T2, T3 |
| R2 独立持久化 CRUD | T1, T2, T3 |
| R3 成员服务端授权 | T1, T3 |
| R4 响应式日历 | T2, T3 |
| R5 配置管理风格页签 | T2, T3 |

### T1：项目日历持久化与服务端契约

#### 需求映射与前置事实

**需求映射：** R2, R3

**前置任务：** 无

**已证实事实：** `ProjectController` 已使用 `/api/project/{projectId}/...` 路由；`ProjectService` 已拥有项目访问校验和 `audit()` 模式；项目域持久化使用 `tenant_id`、`project_id` 和逻辑删除。

#### 文件边界与接口

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V214__project_release_calendar.sql`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectController.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`
- 新建测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectReleaseCalendarMySqlTest.java`

**消费接口：** 既有 `AuthUser`、项目成员查询、项目访问校验与项目审计。

**产出接口：**

- `GET /api/project/{projectId}/release-calendar?month=YYYY-MM` 返回该项目该月的条目。
- `POST /api/project/{projectId}/release-calendar` 接收 `title`、`release_date`、`remark`。
- `PUT /api/project/{projectId}/release-calendar/{calendarId}` 接收相同字段和 `row_version`。
- `DELETE /api/project/{projectId}/release-calendar/{calendarId}?rowVersion=<n>` 执行逻辑删除。

#### 操作步骤、命令和预期信号

- [ ] **T1-S1：建立服务端失败测试。** 在 `ProjectServiceTest` 断言成员可创建/更新/删除，非成员或跨项目 ID 被拒绝，旧 `row_version` 返回冲突；在 MySQL 测试断言月份列表只返回所属项目和租户的数据。运行：`mvn -pl :ccb-system -am -Dtest=ProjectServiceTest,ProjectReleaseCalendarMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test`。预期：当前因接口和表不存在而失败；证据保存失败断言。
- [ ] **T1-S2：追加迁移。** 创建含主键、`tenant_id`、`project_id`、`title`、`release_date`、`remark`、创建/更新审计、`deleted`、`row_version` 的表，并建立 `(tenant_id, project_id, release_date, deleted)` 索引与 `pm_project` 外键。运行：聚焦 MySQL 迁移测试。预期：Flyway 追加成功，表和索引存在；证据保存迁移版本和查询结果。
- [ ] **T1-S3：实现项目范围 CRUD。** 在 `ProjectService` 新增 list/create/update/delete 方法，复用项目成员验证，读取前校验访问、写入前校验成员，按项目/租户/未删除/版本更新，审计动作使用 `project:release-calendar:create|update|delete`。在 `ProjectController` 添加对应端点。运行：T1-S1 命令。预期：全部断言通过，旧版本不覆盖新数据。
- [ ] **T1-S4：执行项目模块回归。** 运行：`mvn -pl :ccb-system -am test`。预期：退出码 0；证据记录测试摘要。若 MySQL 不可达，记录为环境扰动而不声称迁移验证通过。

#### 验收、证据与回滚

**验收：** CRUD 持久化、月份过滤、成员授权、跨项目/跨租户拒绝、乐观锁和审计测试通过。

**回滚：** 回退本任务控制器、服务和测试；保留 V214 表与历史数据，必要时仅追加补偿迁移。

**停止条件：** 现有项目成员校验无法表示“所有项目成员可编辑”，或迁移需要修改已发布脚本。

**升级条件：** 项目成员是否等同于附加 RBAC 角色出现冲突，或平台系统 Owner 要求新的权限码/菜单授权。

### T2：项目日历页面与页签导航

#### 需求映射与前置事实

**需求映射：** R1, R2, R4, R5

**前置任务：** T1

**已证实事实：** `ProjectView.vue` 的 `projectTabs`、`activeTab` 和 `setProjectTab()` 维护 `?tab=`；`project.ts` 是项目 API 封装；`project.ts` 类型定义项目 DTO；配置管理以导航按钮样式组织投产窗口视图。

#### 文件边界与接口

- 修改：`web/src/types/project.ts`
- 修改：`web/src/api/project.ts`
- 修改：`web/src/views/ProjectView.vue`

**消费接口：** T1 的月列表和带 `row_version` 的 CRUD 契约。

**产出接口：** `ProjectReleaseCalendar` 类型、四个 API 包装函数，以及 `tab=release-calendar` 的月历和移动列表交互。

#### 操作步骤、命令和预期信号

- [ ] **T2-S1：建立前端类型和交互基线。** 为标题、日期、备注、版本建立 DTO/Payload；在 `ProjectView` 的项目页签集合加入 `release-calendar`，为页面状态增加当前月、加载、错误、表单、删除和提交状态。运行：`npm --prefix web run build`。预期：在调用未实现 API 前出现类型或构建失败；证据记录结果。
- [ ] **T2-S2：接入 API 与月度状态。** 在 `project.ts` 封装 list/create/update/delete，所有请求保留当前项目上下文。项目或月份切换时取消/忽略过期响应，成功后只刷新当前月；加载、空、失败、403、409、提交中均有可恢复反馈。运行：`npm --prefix web run build`。预期：类型检查和生产构建通过。
- [ ] **T2-S3：实现桌面月历与维护表单。** 在 `ProjectView` 以 Element Plus 既有组件渲染固定单月网格，日期格展示当日条目；新增/编辑表单只包含标题、日期和备注；删除确认明确条目名称及不可恢复后果。所有项目成员显示维护操作，非授权响应不继续操作。
- [ ] **T2-S4：改造页签和移动布局。** 将既有 `el-tabs` 外观改为借鉴配置管理投产窗口的图标/文字导航样式，但继续通过 `activeTab` 和 `setProjectTab()` 更新查询参数。`760px` 以下显示日期分组列表、稳定的主操作和局部内容滚动；使用语义主题变量，不制造整页横向滚动。运行：`npm --prefix web run build`。预期：退出码 0。

#### 验收、证据与回滚

**验收：** 现有页签仍可访问，日历 CRUD UI 与 API 一致，长标题/备注不遮挡，移动端改为日期列表。

**回滚：** 回退三处前端文件，保留服务端条目和表；不影响既有项目详情页签逻辑。

**停止条件：** 复用的 Element Plus 月历无法满足移动端无页面溢出的要求，或页签样式改造破坏既有 query-tab 路由。

**升级条件：** 需要新增公共 UI 组件或修改共享应用壳层，或设计确认的“项目成员可编辑”与实际权限目录冲突。

### T3：跨层集成与响应式验收

#### 需求映射与前置事实

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T1, T2

**已证实事实：** 本需求要求系统模块测试、MySQL 迁移验证、前端构建、治理/范围检查和桌面及三种手机视口验收。

#### 文件边界与接口

- 测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectReleaseCalendarMySqlTest.java`
- 修改（仅发现问题时）：T1/T2 已列文件；不得扩大范围。

**消费接口：** T1 REST/持久化契约与 T2 页面交互。

**产出接口：** 可重复的系统、迁移、构建、范围和浏览器验收证据。

#### 操作步骤、命令和预期信号

- [ ] **T3-S1：运行后端和迁移传感器。** 运行：`mvn -pl :ccb-system -am test`，以及聚焦 `ProjectReleaseCalendarMySqlTest`。预期：成员 CRUD、拒绝路径、版本冲突、月份过滤和迁移测试通过；证据记录退出码和测试数。
- [ ] **T3-S2：运行前端和治理传感器。** 运行：`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-081-project-release-calendar/codex-task-scope.yaml --working-tree`、`git diff --check`。预期：均为退出码 0；若分支命名门禁阻止范围检查，如实记录并在获得用户授权后迁移到合规分支。
- [ ] **T3-S3：浏览器验收。** 以项目成员完成新增、编辑、删除、刷新和月切换；以非成员验证服务端拒绝。分别检查 `1280x800`、`375x812`、`390x844`、`430x932` 的页签导航、空/失败状态、长文本、删除确认、移动列表和 `document.documentElement.scrollWidth <= window.innerWidth`。预期：所有指定路径可达且无控制台错误；证据记录角色、路由、视口、接口和控制台结果。

#### 验收、证据与回滚

**验收：** 所有 R1-R5 的自动化和浏览器传感器通过，且没有未关闭 P0/P1 授权、迁移或导航问题。

**回滚：** 依 T1/T2 回滚本需求应用和前端文件，保留追加迁移；若发现数据修复需要，新增补偿迁移。

**停止条件：** 发现跨项目数据泄露、前端可绕过服务端授权、Flyway 校验失败或手机页面级横向溢出。

**升级条件：** 迁移版本冲突、平台 Owner 审批撤回、测试依赖环境不可恢复或范围检查需要分支策略决定。

## 集成检查

T3 后依序执行需求范围列出的系统测试、迁移集成、权限、前端构建、治理、范围、差异和浏览器检查。任何失败都回写对应执行观测，不将单次构建成功替代 API/浏览器证据。

## 控制模型种子

以下均为 `hypotheses-only`，由后续工程控制建模验证：

- 被控边界：项目日历表、项目服务/API、项目详情页签和浏览器流程。
- 状态变量：当前项目、当前月份、日历条目版本、成员授权结果、页签查询参数、移动布局模式。
- 传感器：项目服务测试、MySQL 迁移测试、HTTP 状态、前端构建、范围/治理检查、浏览器视口和控制台。
- 执行器：Flyway 追加、服务/API 修改、前端页面/API/类型修改、聚焦测试和浏览器操作。
- 扰动：并发编辑、项目成员变化、数据库不可达、日期边界、长文本、过期响应、窗口尺寸变化。
- 时延：Flyway 启动时间、异步 HTTP、前端重渲染和月切换请求。

## 风险与用户批准

高风险动作是平台系统服务端授权、追加数据库迁移和项目详情导航改造。用户已确认本计划；`licon` 的精确任务分支例外已由 REQ-082 纳入范围校验，实施仍须遵循工程控制闭环。
