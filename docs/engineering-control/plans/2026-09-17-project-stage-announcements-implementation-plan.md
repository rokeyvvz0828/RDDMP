# 项目阶段公告栏实施计划

## 状态与来源

- 计划修订：4
- 设计修订：4
- 设计文档：`docs/engineering-control/designs/2026-09-17-project-stage-announcements-design.md`
- 状态：可移交

> 执行要求：使用 `$control-engineering` 按受控任务实施。该计划是候选控制输入；迁移、授权、富文本安全和浏览器证据必须分别验证。

**目标：** 在项目详情提供成员可维护的阶段公告，并在概览展示当前日期命中阶段的公告。

**架构：** 项目服务拥有公告表、阶段计算、成员授权、乐观锁与审计；`ProjectView` 保持现有 Tabs 路由并使用项目 API。概览只消费服务端返回的当前阶段公告，富文本在前端受控净化后以摘要展示。

**技术栈：** Java 17/Spring JDBC、MySQL Flyway、Vue 3、Element Plus、WangEditor。

## 全局约束

- 仅修改 REQ-083 `codex-task-scope.yaml` 的 `writable_paths`。
- 迁移仅追加 `V215__project_stage_announcements.sql`；不修改已发布迁移。
- 读写均执行登录、租户、项目成员、项目归属、阶段有效性与乐观锁校验，并记录审计。
- 不复用测试管理公告或站内消息，不读取生产数据或密钥。
- 富文本禁止脚本、事件属性和危险 URL；移动端不得产生页面级横向溢出。

## 文件职责地图

- 新建 `V215__project_stage_announcements.sql`：项目公告表和查询索引。
- 修改 `ProjectController.java`：项目公告 REST 适配。
- 修改 `ProjectService.java`：公告 CRUD、当前阶段计算、授权、审计与富文本校验。
- 修改 `ProjectServiceTest.java`：成员、非成员、阶段、乐观锁、排序和当前阶段传感器。
- 修改 `project.ts`、`types/project.ts`：前端公告 DTO 和请求契约。
- 修改 `ProjectView.vue`：公告管理页签、富文本编辑、概览公告栏、净化摘要及响应式状态。

## 任务依赖图与并行策略

`T1 -> T2 -> T3`。三个任务串行：T2 消费 T1 的 API 与 DTO，T3 对组合结果做独立采样。数据库和项目页共享接口，不允许并行写入。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1, R2, R3 | T1, T2 |
| R4, R5 | T2, T3 |
| R6 | T5 |

### T1：项目公告存储、服务和安全契约

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** 无。

**文件：** 新建 `server/src/platform/infrastructure/src/main/resources/db/migration/V215__project_stage_announcements.sql`；修改 `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectController.java`、`ProjectService.java`；测试 `server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`。

**接口：** 产出 `GET/POST/PUT/DELETE /api/project/{projectId}/announcements` 与 `GET /api/project/{projectId}/announcements/current`；写入载荷含 `stage_code`、`title`、`content_html`、`pinned`、`row_version`。

1. 建立服务测试：成员可创建、非成员拒绝、无效阶段拒绝、旧版本拒绝、当前阶段并行排序和富文本危险载荷拒绝。
2. 追加 Flyway 表及 `(tenant_id, project_id, stage_code, pinned, deleted, created_at)` 查询索引。
3. 复用日历的成员授权、乐观锁和审计模式；顶级计划以 `parent_id = 0`、日期含边界计算当前阶段；校验并受控保存富文本。
4. 增加 Controller 路由并运行 `mvn -pl :ccb-system -am test`。

**验收与证据：** 服务测试通过；审计动作、跨租户/非成员拒绝、阶段与版本冲突断言可重复。

**回滚：** 回退 T1 应用代码；保留 V215 和公告记录。

**停止条件：** 现有成员授权无法覆盖读写、富文本净化需要新增未授权依赖或顶级计划阶段语义与设计不符。

**升级条件：** 富文本安全库或项目阶段语义需要 Owner 决策。

### T2：公告管理页签与项目概览公告栏

**需求映射：** R1、R2、R4、R5

**前置任务：** T1。

**文件：** 修改 `web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`。

**接口：** 消费 T1 的公告 CRUD 和当前阶段读取；产出 `tab=announcements` 路由状态、管理表单与概览公告栏。

1. 定义公告 DTO、请求封装与 `ProjectView` 加载状态；切换到公告管理时保留现有 Tabs 查询参数行为。
2. 使用现有 WangEditor 依赖实现阶段选择、标题、正文和置顶表单；保存、更新、删除和冲突提示复用项目页错误处理。
3. 在概览的项目信息区域增加当前阶段公告栏，采用置顶、阶段、时间排序结果；仅在客户端净化后渲染摘要。
4. 为 760px 以下布局增加单列公告栏、管理表格的局部滚动与加载/空/失败/无权限/提交中状态。
5. 运行 `npm --prefix web run build` 和 `git diff --check`。

**验收与证据：** 类型检查和生产构建通过；页面不改动既有 Tabs、附件或日历加载逻辑。

**回滚：** 回退 API/types/ProjectView 的 T2 修改。

**停止条件：** 当前 UI 组件无法安全呈现富文本或页面需要修改公共组件、路由或主题基础能力。

**升级条件：** 富文本安全与编辑器配置不能在既有依赖内完成。

### T3：集成验证、运行重启与浏览器验收

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** T1、T2。

**文件：** 修改 `.ai-control/requirements/req-20260917-083-project-stage-announcements/*.json` 记录真实执行与观测结果。

1. 执行范围、治理、后端聚焦测试和前端构建；记录无关工作区或历史账本导致的真实阻断，不回退他人改动。
2. 重启本地开发环境，验证 Flyway、后端健康、前端入口、成员公告 CRUD、当前阶段查询和非成员拒绝。
3. 在桌面、375x812、390x844、430x932 检查项目概览与公告管理，确认 Tabs、富文本摘要、排序、空/失败状态和页面宽度。

**验收与证据：** HTTP 和浏览器观测分别记录，未执行传感器不得标记为通过。

**回滚：** 停止本地环境并回退 T1/T2 应用代码；保留迁移和数据。

**停止条件：** Flyway、授权、XSS、浏览器溢出或现有项目页回归失败。

**升级条件：** 运行环境阻止迁移、浏览器自动化不可用或发现影响其他项目模块的回归。

## 集成检查

`mvn -pl :ccb-system -am test`、`npm --prefix web run build`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-083-project-stage-announcements/codex-task-scope.yaml --working-tree`、`git diff --check`、本地 HTTP 与浏览器路径验收。

## 控制模型种子

以下均为待验证假设：被控对象是项目公告持久化、成员授权、当前阶段查询和响应式项目页；传感器为项目服务测试、Flyway、HTTP、浏览器和页面宽度；执行器为 T1/T2 授权文件；扰动为并行计划、脏工作区、浏览器不可用和本地基础设施状态；延迟来自构建、启动和计划日期变化。

## 风险与用户批准

用户已确认当前计划修订。高风险动作仅限追加迁移、项目服务 API、富文本安全处理和本地开发环境重启；不连接生产环境。

## 增量 T4：项目概览三列布局与年度投产日历

**需求映射：** D5

**前置任务：** T2；复用已经验证的 `getProjectReleaseCalendar(projectId, month)` 与 `releaseCalendarDays` 数据模型。

**文件：** 修改 `web/src/views/ProjectView.vue`；修改本计划与同名设计、`req-20260917-083-project-stage-announcements/design.json` 记录增量决策。

**接口：** 只消费既有投产日历月查询；`releaseCalendarMonth` 仍为 `YYYY-MM`，新增年度边界前后切换动作。不得新增或变更 HTTP、持久化、权限、审计和迁移契约。

1. 建立概览状态：项目详情加载后和概览可见时加载当前 `releaseCalendarMonth` 的投产数据；月份变化后在概览或投产日历页刷新，避免重复请求和陈旧数据。
2. 在概览上半区改为三列：紧凑项目信息、公告和日历；把 `project-overview__schedule` 固定为全宽网格行。日历日期只根据 `calendarEntriesFor(date).length` 渲染带 `aria-label` 的品牌色圆点。
3. 使用带标题的左右图标按钮切换月份；年初禁用上一月、年末禁用下一月，禁止构造非当前年的月份。
4. 在 `760px` 以下变为纵向排列；日历网格使用稳定七列，内容可收缩，日期与圆点不扩张单元格。
5. 运行 `npm --prefix web run build`、`git diff --check`；本地服务健康后在 1280x800、375x812、390x844、430x932 验收项目概览，检查无水平溢出、年界禁用和投产日期圆点。

**验收与证据：** 项目信息、公告和日历桌面首行对齐；项目计划独占下行；当前月默认展示，有投产数据的日期恰有圆点；一月/十二月不能跨年；规定视口没有页面级横向滚动。

**回滚：** 仅回退 `ProjectView.vue` 的概览结构、月份切换和样式；投产日历原页与持久化数据保持不变。

**停止条件：** 现有投产日历接口无法在概览场景加载，或布局需要改动公共组件、路由或后端契约。

**升级条件：** 用户要求日期显示标题、详情或跨年浏览，因其超出“仅颜色标识”和当年切换范围。

## 增量 T5：投产日历主题内置色系

**需求映射：** R6、D7

**前置任务：** 现有投产日历 CRUD 与 `theme_key` 字段已可用。

**文件：** 修改 `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`、`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`、`web/src/types/project.ts`、`web/src/api/project.ts`、`web/src/views/ProjectView.vue`；修改本计划、同名设计和当前任务 `design.json`、`handoff.json` 记录增量决策。

**接口：** 保持 `GET/POST/PUT /api/project/{projectId}/release-calendar` 路径和 `theme_key` 字段不变。新写入只接受稳定色系键 `tone-1`、`tone-2`、`tone-3`、`tone-4`、`tone-5`；读取兼容既有 `system`、全局主题方案键及语义色键。读取旧值时前端按 `tone-1` 渲染，编辑并保存后规范化为新键。

1. 在 `ProjectServiceTest` 为 `tone-1` 至 `tone-5` 的创建和更新通过、无效键拒绝、旧主题方案键读取兼容建立断言；然后将 `ProjectService.RELEASE_CALENDAR_THEME_KEYS` 扩展为五个稳定色系键与全部既有兼容键，并将空值默认规范化为 `tone-1`。不修改 `V216`，因为现有 `VARCHAR(16)` 已覆盖新键。
2. 在 `web/src/types/project.ts` 以 `ProjectReleaseCalendarToneKey` 声明新写入值，并以单独的历史联合类型保留接口读取兼容；在 `web/src/api/project.ts` 将创建/更新的 `theme_key` 请求类型收窄为新色系键。移除对 `PaletteKey` 的业务类型依赖。
3. 在 `ProjectView.vue` 用五个带色块的“主题色系一”至“主题色系五”选项替换全局主题方案选项；编辑旧值时把选择控件回显为 `tone-1`。概览 class 通过统一归一化函数生成 `theme-tone-1` 至 `theme-tone-5`，旧值也映射至 `theme-tone-1`。
4. 在 `ProjectView.vue` 的日期格样式将五个 class 分别关联 `--brand`、`--success`、`--accent`、`--warning`、`--danger`，以 `color-mix` 生成背景和边框；删除按 `data-palette` 写死的方案色值。全局主题方案或浅深模式切换仅改变变量值，已保存记录不需要重写。
5. 运行 `mvn -pl :ccb-system -am -Dtest=ProjectServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" test`、`npm --prefix web run build` 和 `git diff --check`；重启本地服务后，以两套全局主题方案的浅色和深色模式保存、刷新并切换月份，确认色块、回显、日期背景、边框与工具提示均同步变化。

**验收与证据：** 新维护记录只提供五个带色块主题内置色系；保存后刷新回显同一色系；两套全局主题方案在浅色和深色下均可辨识；历史记录不显示旧选择且随全局主题变量变化。

**回滚：** 回退本增量的服务校验、前端选项和 CSS 映射；保留已写入的 `theme_key` 值，旧版本继续按兼容键读取。

**停止条件：** 现有五个主题变量无法在两套全局主题方案与浅深模式中保持可读性，且需要修改 `web/src/styles.css` 或公共主题模块。

**升级条件：** 用户要求新增全局主题令牌、修改公共主题模块，或要求每条投产记录保存不受当前主题影响的固定颜色。
