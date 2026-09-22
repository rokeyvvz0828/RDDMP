# 项目投产日历日期范围实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 让项目投产日历以开始日期和结束日期维护多条不重叠安排，并在范围内每一天的月历/移动列表中显示安排；同一项目同一日期不得存在第二条有效安排。

**架构：** 在现有项目日历 API、服务和 `ProjectView.vue` 上扩展日期范围契约。追加 V218 Flyway 迁移，将 V214 的 `release_date` 存量回填为同日范围；服务端按月份与范围重叠查询，前端用 `daterange` 选择器并展开范围到每天。项目成员授权、审计、乐观锁和现有页签路由保持不变。

**技术栈：** Java 17、Spring Boot、JdbcTemplate、MySQL 8/Flyway、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 只修改 REQ-081 `codex-task-scope.yaml` 的 `writable_paths`；保护工作区中 REQ-084 的无关未提交改动。
- 不修改 V214；仅追加 `V218__project_release_calendar_date_range.sql`。
- 同一项目的有效安排日期范围不得重叠；`release_start_date <= release_end_date`，两端均包含；同日表示单日安排。
- 月份查询返回与月份相交的范围：`release_start_date < next_month_start AND release_end_date >= month_start`。
- 不读取或写入配置管理投产窗口，不新增时间点、重复、提醒、审批或跨项目汇总能力。
- 服务端继续执行认证、租户、项目成员、实体归属、乐观锁和审计校验。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V218__project_release_calendar_date_range.sql` | candidate-new | 增加日期范围字段、回填存量单日数据和范围查询索引。 |
| `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java` | existing | 范围校验、月份重叠查询、CRUD 返回。 |
| `server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java` | existing | 服务端范围校验、SQL 参数和乐观锁测试。 |
| `server/src/platform/system/src/test/java/com/ccb/system/project/ProjectReleaseCalendarMigrationTest.java` | existing | V218 迁移契约测试。 |
| `web/src/types/project.ts` | existing | 日期范围 DTO 和表单类型。 |
| `web/src/api/project.ts` | existing | 日期范围 payload 封装。 |
| `web/src/views/ProjectView.vue` | existing | 日期范围选择、每天展开、桌面月历、移动日期分组和反馈状态。 |
| `.ai-control/requirements/req-20260917-081-project-release-calendar/*.json` | existing | 本轮基线、模型、计划、执行、观测和收敛证据。 |

## 任务依赖图与并行策略

`T1 -> T2 -> T3` 串行。T2 消费 T1 的新字段契约，T3 依赖两层实现和迁移结果；共享 API、数据库和浏览器运行环境不能并行修改或验证。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 页签和路由保持可用 | T2, T3 |
| R2 日期范围独立持久化与 CRUD | T1, T2, T3 |
| R3 项目成员服务端授权 | T1, T3 |
| R4 范围内每天展示及响应式布局 | T2, T3 |
| R5 页签视觉和主题兼容 | T2, T3 |

### T1：日期范围存储和服务端契约

**需求映射：** R2, R3

**前置任务：** 无

**文件边界：** 新建 V218；修改 `ProjectService.java`；修改 `ProjectServiceTest.java`、`ProjectReleaseCalendarMigrationTest.java`。不修改 V214 或其他模块。

**接口：** POST/PUT 接收 `release_start_date`、`release_end_date`；GET 返回这两个字段并按月份重叠过滤；删除仍使用 `row_version`。

- [ ] **T1-S1：建立范围边界基线测试。** 增加开始日大于结束日、跨月、同日、非重叠第二条安排、重叠安排和旧版本冲突断言。运行 `mvn -pl :ccb-system -am -Dtest=ProjectServiceTest,ProjectReleaseCalendarMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，保存基线退出码和断言。
- [ ] **T1-S2：追加 V218。** 增加 `release_start_date`、`release_end_date`，用旧 `release_date` 回填同值，增加 `(tenant_id, project_id, release_start_date, release_end_date, deleted)` 索引；不删除旧列。
- [ ] **T1-S3：实现服务端范围 CRUD。** 校验 ISO 日期和开始不晚于结束；创建或更新排除自身后拒绝与其他有效记录重叠的日期范围；列表使用月份相交条件；创建/更新返回范围字段，保留成员授权、租户/项目条件、乐观锁和审计。
- [ ] **T1-S4：运行聚焦和模块回归。** 运行 `mvn -Dnet.bytebuddy.experimental=true -pl :ccb-system -am -Dtest=ProjectServiceTest,ProjectReleaseCalendarMigrationTest test`，再运行 `mvn -Dnet.bytebuddy.experimental=true -pl :ccb-system -am test`。

**验收：** 同日和跨月范围可持久化；范围外月份不返回；相交月份返回；非成员/跨租户拒绝；旧版本不能覆盖。

**回滚：** 回退服务和测试代码，保留 V218 及历史数据；不得修改或删除 V214。

**停止和升级：** 若 V218 无法安全回填、范围字段需要修改已发布迁移，或现有成员授权无法复用，停止并回到建模/请求 Owner 决策。

### T2：前端日期范围维护和每日展示

**需求映射：** R1, R2, R4, R5

**前置任务：** T1

**文件边界：** 修改 `web/src/types/project.ts`、`web/src/api/project.ts`、`web/src/views/ProjectView.vue`。

**接口：** API 类型和 payload 使用 `release_start_date`、`release_end_date`；月份返回的范围记录在桌面日期格和移动日期组中按包含关系展开。

- [ ] **T2-S1：更新类型/API。** 将表单字段改为 `releaseDateRange: [string, string]`，DTO 改为开始/结束日期，保留主题色和版本字段。
- [ ] **T2-S2：更新数据加载和匹配。** 月份加载继续受当前项目和当前年限制；`calendarEntriesFor(date)` 改为日期在范围内匹配；跨月范围由后端返回并在每一天显示。
- [ ] **T2-S3：更新维护表单。** 使用 Element Plus `type="daterange"`、`value-format="YYYY-MM-DD"`；编辑回显范围，校验空值和顺序，保存提示包含范围；单日快速新增传同日范围。
- [ ] **T2-S4：更新概览日期格和移动分组。** 项目概览日期格直接在主题色背景框中显示投产标题，不使用悬浮框展示详情；维护页允许继续新增不重叠安排，已有安排可编辑或删除，空日期格提供按日期新增入口；移动端每条跨日安排出现在每个对应日期组；保留失败、空、提交中状态和原有页签路由。
- [ ] **T2-S5：运行前端构建和差异检查。** 运行 `npm --prefix web run build` 和 `git diff --check`，检查不得有未授权文件变化。

**验收：** 可选择日期范围并刷新回显；跨月范围在两个相邻月份可见；桌面和 375x812/390x844/430x932 无页面横向溢出；主题和现有 tabs 不回归。

**回滚：** 回退三处前端文件，保留服务端范围数据和 V218。

**停止和升级：** 若 Element Plus 范围选择器或每日展开造成页面级溢出，或需新增公共组件，停止并回到规划。

### T3：独立观测和集成验收

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T1, T2

**文件边界：** 只更新当前 REQ-081 的 `.ai-control` 证据；发现产品缺陷时仅修改 T1/T2 已列文件。

- [ ] **T3-S1：后端/迁移传感器。** 运行项目聚焦测试、V218 迁移测试和本地 API；断言同日、跨月、范围外、权限和乐观锁路径。
- [ ] **T3-S2：前端/治理传感器。** 运行 `npm --prefix web run build`、`node scripts/check-all-governance.mjs`、REQ-081 scope check 和 `git diff --check`。
- [ ] **T3-S3：浏览器传感器。** 在 `1280x800`、`375x812`、`390x844`、`430x932` 验收新增跨月范围、编辑、删除、刷新、月切换、tooltip、移动分组和 `document.documentElement.scrollWidth <= window.innerWidth`；记录接口和控制台结果。

**验收：** 所有 must 需求有自动化或浏览器证据，P0/P1 为零，范围审计仅包含本任务授权文件和既有用户改动。

**回滚：** 按 T1/T2 回滚应用代码，保留追加迁移；必要时只追加补偿迁移。

**停止和升级：** 迁移失败、跨项目泄露、服务端越权、页面溢出或浏览器传感器不可用时，保留证据并停止宣布完成。

## 集成检查

依次执行 `mvn -Dnet.bytebuddy.experimental=true -pl :ccb-system -am test`、`npm --prefix web run build`、`node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs`、REQ-081 scope check 和 `git diff --check`；浏览器证据单独记录，不用构建结果替代。

## 控制模型种子

`hypotheses-only`：被控边界为项目日历表、项目服务/API、项目详情页签和浏览器流程；状态变量为当前项目、当前月份、范围边界、版本、成员授权、页签查询参数和移动布局；传感器为服务测试、迁移校验、API、前端构建、治理/scope、浏览器视口和控制台；执行器为 V218、服务/API、类型/API/页面变更和聚焦测试；扰动为跨月边界、并发编辑、成员变化、过期请求、长标题和视口变化；主要时延为 Flyway、HTTP 和 Vue 重渲染。

## 风险与用户批准

高风险动作是追加迁移、公开 API 字段变化、项目成员授权回归和移动日期展开。用户已确认日期范围设计；Owner 复核仍需覆盖迁移回填、授权和跨月查询。

## 公告分类扩展任务

### T4：固定公告栏位与服务端查询契约

**需求映射：** A1, A2, A3

**前置任务：** T1

**文件：**
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`

**接口：** 保留 `/api/project/{projectId}/announcements`、`/current`、创建、更新、删除路径；`stageCode` 参数和 `stage_code` 字段改表示固定公告分类编码。服务端接受 `PROJECT`、`REQUIREMENT`、`DEVELOPMENT`、`TEST`、`PRODUCTION`、`DATA_MIGRATION`，拒绝其他新建/更新编码。列表按当前分类过滤；概览查询返回全部分类中所有置顶和最新 3 条非置顶。

- [ ] **T4-S1：建立公告分类和概览排序测试。** 覆盖六个有效编码、非法编码拒绝、按分类过滤、置顶优先以及非置顶最多 3 条的 SQL/服务断言。
- [ ] **T4-S2：实施固定分类校验。** 在 `ProjectService` 增加固定编码集合和显示名映射，创建/更新使用公告分类校验，不再调用项目计划阶段校验。
- [ ] **T4-S3：实施分类列表与概览查询。** 管理列表按 `stage_code` 过滤并用固定名称返回；`currentAnnouncements` 改为跨六类汇总，置顶全部保留，非置顶按创建时间取 3 条，保持项目、租户、逻辑删除和授权条件。
- [ ] **T4-S4：运行后端聚焦测试。** 运行 `mvn -Dnet.bytebuddy.experimental=true -pl :ccb-system -am -Dtest=ProjectServiceTest test`，预期公告分类、排序和现有项目服务测试通过。

**回滚：** 仅回退 `ProjectService.java` 和对应测试修改，不修改数据库迁移。

**停止和升级：** 若旧公告数据与固定分类共存需要数据迁移，或查询无法在当前表结构实现置顶/最新规则，停止并请求扩展范围。

### T5：公告管理六栏位与概览摘要

**需求映射：** A1, A3, A4

**前置任务：** T4

**文件：**
- 修改：`web/src/views/ProjectView.vue`
- 修改：`web/src/api/project.ts`
- 修改：`web/src/types/project.ts`

**接口：** 前端使用固定分类数组和当前分类 `getProjectAnnouncements(id, categoryCode)`；创建/更新 payload 的 `stage_code` 传固定分类编码；概览使用服务端摘要接口返回的置顶和最新公告。

- [ ] **T5-S1：更新类型和 API 语义。** 将公告类型字段注释/类型语义改为固定分类，增加六分类常量及标签，保留既有 HTTP 字段兼容。
- [ ] **T5-S2：改造公告管理 tabs。** 使用六个固定 tabs 过滤列表，发布按钮默认当前 tab，表单移除项目阶段下拉框；编辑时保持公告分类，置顶 checkbox 只影响当前分类公告。
- [ ] **T5-S3：更新项目概览摘要。** 使用后端摘要结果展示全部置顶和最新 3 条非置顶，保留一行一条标题/日期、详情弹窗、加载/空/失败/重试状态。
- [ ] **T5-S4：运行前端构建和差异检查。** 运行 `npm --prefix web run build`、`git diff --check`，确认公告管理和概览无页面级横向溢出。

**回滚：** 回退三处前端文件，保留后端固定分类实现可独立回退。

**停止和升级：** 若当前接口无法区分管理列表和概览摘要而必须新增路由，先回到计划并确认是否扩展 `ProjectController` 文件范围。

### T6：公告分类集成验收

**需求映射：** A1, A2, A3, A4

**前置任务：** T4, T5

**文件：** 只更新当前 REQ-081 的 `.ai-control/requirements/req-20260917-081-project-release-calendar/*.json` 证据文件。

- [ ] **T6-S1：后端传感器。** 运行项目服务聚焦测试并验证项目成员、非成员、跨租户和乐观锁路径。
- [ ] **T6-S2：前端/治理传感器。** 运行 `npm --prefix web run build`、`node scripts/check-all-governance.mjs`、REQ-081 scope check 和 `git diff --check`。
- [ ] **T6-S3：浏览器传感器。** 在桌面和规定移动视口验证六个 tabs、各栏位独立发布/置顶、概览置顶全部 + 最新 3 条非置顶、详情弹窗、刷新和错误恢复。

**验收：** A1-A4 全部有自动化或浏览器证据，P0/P1 为零，范围审计只包含授权文件和既有用户改动。

**回滚：** 按 T4/T5 回退应用代码，保留无数据库变更的公告数据。
