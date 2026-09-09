# 数据迁移整体看板实时指标与滚动下钻实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在当前可访问项目内，用 13 个独立实时指标请求构建响应式数据迁移整体看板，并提供点击后才加载、每页 20 条的滚动下钻。

**架构：** 服务端以静态白名单 `DashboardMetricDefinition` 统一生成单指标计数和下钻查询，`DashboardService` 继续负责项目校验，专用 `DashboardController` 承担看板路由与 RBAC；旧兼容汇总与组件看板路由保留。前端在 `data-migration.ts` 固化指标类型，在 `OverallDashboardPage.vue` 内实现最多 4 路并发、独立卡片状态、项目代次隔离、ECharts 派生图表和右侧滚动抽屉，不修改公共组件或全局项目 store。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring Security、JdbcTemplate、JUnit 5、Testcontainers MySQL；Vue 3、TypeScript、Element Plus、ECharts、Axios、Vite。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-08-data-migration-overall-dashboard-realtime-design.md`
- 需求来源：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/requirement.md`
- 计划状态：已批准，待工程控制导入
- 当前分支：`feat/REQ-20260820-031-data-migration`
- 控制前缀：`req-20260820-031-data-migration-asset-library-v3`

## 全局约束

- 只修改 REQ-031 `codex-task-scope.yaml` 中允许的数据迁移模块、前端数据迁移 API/页面、对应设计计划和当前前缀账本。
- 不读取或修改任务范围禁止的 `.ai-control/original/**`，不修改其他需求账本。
- 每项查询必须限定认证租户、经 `requireProject(projectId, user)` 验证的项目和 `deleted=0`。
- 指标码只允许固定的 13 项，不接受任意表名、列名或 SQL 条件。
- 计数与下钻使用同一 `DashboardMetricDefinition`，不得维护两套口径。
- 前端最多 4 个指标请求同时在途；总量仅在 13 项全部成功后显示。
- 下钻仅在点击后加载，页大小固定为 20，后续页串行请求且失败时保留已有记录。
- 项目切换必须关闭抽屉、清空旧项目数据，并通过 Axios 取消信号和请求代次丢弃旧响应。
- 不增加前端轮询、后端调度、WebSocket、SSE、缓存或快照读写；保留 `dm_dashboard_snapshot` 表。
- 不修改组件级看板行为、资产 CRUD、附件、审计、平台公共组件和全局项目 store。
- 页面使用语义主题变量；验收 `1280x800`、`375x812`、`390x844`、`430x932` 及明暗主题，无页面级横向溢出。
- 性能门槛：单指标 P95 `<=500ms`、13 项完成 `<=2s`、下钻页 P95 `<=800ms`；查询计划必须受租户、项目和活动状态索引约束。
- 不自动创建提交；每个任务以聚焦测试、`git diff --check` 和账本证据作为检查点。

---

## 文件职责地图

| 路径 | 状态 | 单一职责与证据 |
| --- | --- | --- |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardMetricDefinition.java` | candidate-new | 13 项指标白名单、表/主键/编号/名称/可选颗粒度映射，以及同源计数和下钻 SQL 片段。设计 D1-D3。 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardService.java` | existing | 保留兼容 overall/component，新增单指标计数、固定 20 条下钻、项目与租户校验。现有类已依赖 `JdbcTemplate` 和 `DataMigrationPermissionService`。 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/DashboardController.java` | candidate-new | 专用看板 HTTP 适配、参数校验、看板 RBAC 和新旧路由。 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/StructuredAssetController.java` | existing | 移除看板依赖和路由，避免与专用 Controller 重复映射；结构化资产路由不变。 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardSnapshotScheduler.java` | existing-delete | 删除每日快照调度 Bean，停止 `dm_dashboard_snapshot` 写入。 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DashboardMetricDefinitionTest.java` | candidate-new | 验证 13 项白名单、映射和未知码拒绝。 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DashboardServiceTest.java` | candidate-new | 验证项目校验、租户/项目/deleted 条件、固定分页、稳定排序和计数/下钻同源。 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DashboardMetricMySqlTest.java` | candidate-new | Testcontainers MySQL 验证 13 项结果、分页一致性、查询计划和受控数据规模下的延迟样本。 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/web/DashboardControllerSecurityTest.java` | candidate-new | 验证专用 Controller 的看板权限声明、路由和参数约束。 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java` | existing | 验证调度器不再注册、专用 Controller 可装配；仅在现有注册测试适合扩展时修改。 |
| `web/src/api/data-migration.ts` | existing | 增加闭合指标码、计数响应、统一下钻项/分页类型以及支持 `AbortSignal` 的两个请求函数。 |
| `web/src/modules/data-migration/views/dashboard/OverallDashboardPage.vue` | existing | 实现批准的高保真响应式布局、并发队列、独立状态、ECharts、项目隔离和滚动下钻。 |
| `.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-overall-dashboard-realtime-*.json` | candidate-new/current | 保存该子任务的 handoff/state/model/control-plan/execution/observation/convergence 证据；具体文件由闭环阶段创建。 |

## 固定接口契约

### 指标码

`OVERALL_PLAN | COMPONENT_PLAN | PROJECT_TOPIC | COMPONENT_TOPIC | REPORT | MEETING | ISSUE | RELEASE_DRILL | MAPPING_DOC | RULE | PARAMETER | DEPENDENCY | SCRIPT`

### `GET /api/data-migration/dashboard/overall/metrics/{metricCode}`

- 查询参数：`projectId: Long`，必填。
- 成功数据：`{ metricCode: string, count: number, calculatedAt: ISO-8601 string }`。
- 失败：未知指标或缺项目为 400，无看板权限或项目不可达为 403，使用统一 `ApiResponse` 错误模型。

### `GET /api/data-migration/dashboard/overall/drilldowns/{metricCode}`

- 查询参数：`projectId: Long`、`page: int` 默认 1、`size: int` 默认 20；服务端拒绝 `size != 20`。
- 成功数据：现有 `PageResult` 信封，记录统一字段为 `{ id, code, name, granularity, systemCode, updatedAt }`；不适用字段为 `null`。
- 排序：`updated_at DESC` 后按该表稳定主键 `DESC`；会议表使用 `meeting_id`，其他表使用 `id`。
- SQL 条件：所有指标含 `tenant_id=? AND project_id=? AND deleted=0`；`OVERALL_PLAN/PROJECT_TOPIC` 追加 `granularity='PROJECT'`，`COMPONENT_PLAN/COMPONENT_TOPIC` 追加 `granularity='SYSTEM'`。

## 任务依赖图与并行策略

```text
T1 后端指标口径与查询
 |
 v
T2 HTTP 契约与调度退役
 |
 v
T3 前端实时看板与下钻
 |
 v
T4 集成、性能、浏览器与治理验收
```

任务按顺序执行。T3 依赖 T2 的最终 HTTP 字段，T4 需要完整组合结果；当前共享工作区不安排并行写入。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 13 项实时统计 | T1, T2, T3, T4 |
| R2 取消轮询、调度和快照依赖 | T2, T3, T4 |
| R3 独立请求、状态与最多 4 路并发 | T3, T4 |
| R4 点击后滚动分页下钻 | T1, T2, T3, T4 |
| R5 白名单、认证、RBAC、租户和项目隔离 | T1, T2, T3, T4 |
| R6 高保真响应式排版与全状态 | T3, T4 |
| R7 性能门槛和查询计划 | T1, T4 |

### T1：后端单一指标定义与实时查询

**需求映射：** R1, R4, R5, R7

**前置任务：** 无

**输入事实：** `DashboardService` 已通过 `DataMigrationPermissionService.requireProject` 校验项目并使用 `JdbcTemplate`；13 张表及其索引已由现有 Flyway 历史脚本建立；`PageResult` 是模块现有分页信封。

**文件：**

- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardMetricDefinition.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardService.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DashboardMetricDefinitionTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DashboardServiceTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/DashboardMetricMySqlTest.java`

**接口：**

- 消费：`DataMigrationPermissionService.requireProject(Long, AuthUser)`、`JdbcTemplate`、`PageResult<Map<String,Object>>`。
- 产出：`DashboardMetricDefinition.require(String)`、`DashboardService.metric(String, Long, AuthUser)`、`DashboardService.drilldown(String, Long, int, int, AuthUser)`。

- [ ] **步骤 1：建立白名单和服务失败测试**

  为 13 个码逐项断言表、主键、编号、名称、颗粒度条件；断言未知码抛出统一 BAD_REQUEST；捕获 JdbcTemplate SQL 与参数，断言租户、项目、`deleted=0`、固定颗粒度、稳定排序和 `LIMIT 20`。

- [ ] **步骤 2：运行当前失败信号**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DashboardMetricDefinitionTest,DashboardServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：测试编译失败，缺少 `DashboardMetricDefinition` 及新增服务方法；证据保留退出码与首个缺失符号。

- [ ] **步骤 3：实施静态定义与服务查询**

  定义不可从外部修改的枚举/记录，仅由常量组合 SQL；`metric` 与 `drilldown` 先解析白名单、再调用 `requireProject`，以 `Instant` 返回计算时间。下钻只投影统一字段，不联表加载附件、用户目录或领域详情。

- [ ] **步骤 4：建立 MySQL 口径与性能测试**

  复用模块 Testcontainers 迁移模式，插入两个租户、两个项目、活动/删除记录和方案/专题两种颗粒度；逐项断言计数等于完整分页行数。对计数和第一页查询运行 `EXPLAIN ANALYZE`，记录索引/扫描行信号并采集重复请求 P95。

- [ ] **步骤 5：运行局部与相关回归**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DashboardMetricDefinitionTest,DashboardServiceTest,DashboardMetricMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：退出码 0；13 项、未知码、跨租户/项目、删除状态、颗粒度、分页和查询计划断言全部通过。

**验收与证据：** 测试报告、实际 SQL/参数断言、`EXPLAIN ANALYZE` 摘要和 P95 样本；`git diff --check` 无错误。

**风险：** 异构表列名错误或最新域化迁移改变字段；严格以当前 Service 和 Flyway 最终模式修正静态定义，不回退到旧 `dm_asset`。

**回滚：** 删除候选新定义/测试并回退 `DashboardService` 的新增方法；兼容 overall/component 保持原状。

**停止条件：** 任一指标没有可由当前模块确认的活动表口径；查询计划出现无租户/项目约束的全表扫描；满足性能必须新增未授权 Flyway 索引。

**升级条件：** 需要修改 `server/src/platform/infrastructure` 迁移、改变指标口径或放宽性能门槛时，提交模块 Owner/用户扩展范围。

### T2：专用看板 Controller 与快照调度退役

**需求映射：** R1, R2, R4, R5

**前置任务：** T1

**输入事实：** 现有看板路由位于 `StructuredAssetController`；现有 `DashboardSnapshotScheduler` 是 `@Configuration/@EnableScheduling` Bean，每日写 `dm_dashboard_snapshot`。

**文件：**

- 新建：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/DashboardController.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/StructuredAssetController.java`
- 删除：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/DashboardSnapshotScheduler.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/web/DashboardControllerSecurityTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java`（若现有注册测试可承载调度 Bean 断言）

**接口：**

- 消费：T1 的 `metric`、`drilldown`、兼容 `overall` 和 `component` 服务方法。
- 产出：两个固定新 GET 端点，并原样保留 `/dashboard/overall`、`/dashboard/component`、`/dashboard/components`。

- [ ] **步骤 1：建立 Controller 权限和路由测试**

  断言类级权限包含 `data-migration:dashboard`、`data-migration:access`、`data-migration:manage`、`system:admin`；反射或 MockMvc 断言新旧路由、必填 `projectId`、固定 `size=20` 和统一响应字段。

- [ ] **步骤 2：运行当前失败信号**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DashboardControllerSecurityTest,DataMigrationModuleRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：缺少专用 Controller 或调度器仍被注册的断言失败。

- [ ] **步骤 3：迁移路由并移除调度器**

  新建专用 Controller；从 `StructuredAssetController` 删除 `DashboardService` 构造参数和两个看板方法；删除 `DashboardSnapshotScheduler.java`。不删除表、不修改 Flyway、不引入替代定时任务。

- [ ] **步骤 4：运行局部与模块回归**

  运行：`mvn -pl :ccb-data-migration -am -Dtest=DashboardControllerSecurityTest,DataMigrationModuleRegistrationTest,DashboardMetricDefinitionTest,DashboardServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

  预期：退出码 0；无重复路由，专用 Controller 权限正确，兼容路由仍存在，调度器类/Bean 不存在。

**验收与证据：** 测试报告；`rg -n "Scheduled|EnableScheduling|dm_dashboard_snapshot" server/src/modules/data-migration/src/main` 无命中；`git diff --check` 通过。

**风险：** 移动路由时产生重复映射或遗漏兼容别名。

**回滚：** 恢复 `StructuredAssetController` 的原看板构造参数和路由，删除专用 Controller；调度器恢复仅用于整体变更回退，不作为新接口故障的长期修复。

**停止条件：** 发现其他代码依赖 `DashboardSnapshotScheduler` 公共方法或快照写入承担未记录业务职责。

**升级条件：** 兼容路由无法在不改变组件看板消费者的前提下迁移时，请求模块 Owner 裁决。

### T3：高保真实时看板、独立状态与滚动下钻

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** T2

**输入事实：** 页面使用 `useProjectScope` 和 `ProjectScopeState`；项目变更由 `scopeProjectId` watch 驱动；依赖已包含 Element Plus、ECharts 和 Axios；前端当前没有单测脚本。

**文件：**

- 修改：`web/src/api/data-migration.ts`
- 修改：`web/src/modules/data-migration/views/dashboard/OverallDashboardPage.vue`

**接口：**

- 消费：T2 两个新 GET 端点、`useProjectScope`、`UiPageHeader`、Element Plus 图标/抽屉和 ECharts。
- 产出：闭合 `DataMigrationDashboardMetricCode` 类型、`getDataMigrationDashboardMetric`、`getDataMigrationDashboardDrilldown`，以及具备批准布局和全状态的整体看板页面。

- [ ] **步骤 1：建立前端静态失败基准**

  在 API 中先引用新闭合类型和函数、在页面引用所需契约，运行类型构建确认缺失符号；记录当前页面无请求队列、无下钻和无图表的基线截图。

- [ ] **步骤 2：实现类型化 API**

  `metricCode` 使用字符串联合类型；计数和分页响应使用明确接口；两个请求函数接受 `AbortSignal` 并只构造固定端点。保留 `getDataMigrationDashboard` 给组件看板和兼容调用。

- [ ] **步骤 3：实现 13 项并发队列与项目隔离**

  页面为每项维护 `idle/loading/success/error`、值和错误；刷新全部按队列最多启动 4 项，单项重试只请求该项。使用当前代次和 `AbortController`，项目切换立即取消、清空、关闭抽屉并重新排队；卸载时取消请求和销毁图表，不创建计时器。

- [ ] **步骤 4：实现批准排版和 ECharts 派生视图**

  桌面按总量锚点、四项方案/专题、九项内容资产、构成与排行组织；全部 13 项成功后才展示总量。图表使用成功项，部分失败时显示“不完整”状态；全部失败显示错误状态。图表容器固定尺寸，主题和 resize 生命周期稳定。

- [ ] **步骤 5：实现右侧滚动下钻**

  指标点击后打开抽屉并请求第一页；滚动接近底部且 `hasMore && !loadingMore && !pageError` 时才请求下一页。第一页失败显示原位重试，后续页失败保留记录并显示底部重试，全部完成显示明确结束状态；关闭或切换指标时取消旧下钻请求。

- [ ] **步骤 6：实现响应式与可访问状态**

  桌面、平板、手机按确认稿重排；手机指标保持两列，抽屉近全宽且标题/关闭固定、正文局部滚动。图标按钮提供 tooltip/`aria-label`，长名称可换行，颜色不是错误/成功的唯一信号。

- [ ] **步骤 7：运行前端构建**

  运行：`npm --prefix web run build`

  预期：`vue-tsc --noEmit` 和 Vite 构建退出码均为 0，无 TypeScript、模板和样式编译错误。

**验收与证据：** 构建输出；浏览器 Network 中最多 4 个指标请求同时在途，未点击前无 drilldown 请求；项目切换后无旧响应写入；页面与抽屉状态截图；`git diff --check` 通过。

**风险：** 快速刷新/切项目导致乱序；ECharts 重复初始化或 ResizeObserver 泄漏；无限滚动重复页。

**回滚：** 回退两个前端文件至旧 3 卡看板；后端兼容 `/dashboard/overall` 仍可供旧页面调用。

**停止条件：** 实现需要修改公共 UI、全局 store 或引入新依赖；确认稿无法在 375px 下保持两列可读；无可判定方式限制并发或防重复页。

**升级条件：** 必须改变已确认视觉层级、移动布局、接口字段或下钻页大小时，请用户重新确认。

### T4：组合验收、性能采样与闭环证据

**需求映射：** R1, R2, R3, R4, R5, R6, R7

**前置任务：** T3

**输入事实：** T1-T3 已提供模块测试、生产构建和可运行页面；目标 MySQL 的真实最大项目规模仍是非阻塞未知项。

**文件：**

- 新建/更新：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-overall-dashboard-realtime-execution-*.json`
- 新建/更新：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-overall-dashboard-realtime-observation-*.json`
- 新建/更新：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-overall-dashboard-realtime-convergence.json`
- 新建/更新：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/data-migration-overall-dashboard-realtime-state.json`

**接口：**

- 消费：T1-T3 完整实现与验证命令。
- 产出：逐需求、逐任务可追踪的执行/观测/收敛证据和最终交付结论。

- [ ] **步骤 1：运行聚焦和全量自动化检查**

  运行：`mvn -pl :ccb-data-migration -am test`

  运行：`npm --prefix web run build`

  运行：`node scripts/check-all-governance.mjs`

  运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml`

  运行：`git diff --check`

  预期：目标模块测试、前端构建、当前范围检查和 diff 检查退出码 0；全局治理若仅被已知 REQ-061 越界畸形 scope 阻塞，记录为外部扰动且不修改该文件。

- [ ] **步骤 2：验证 API 口径、安全和请求行为**

  使用已授权测试账号和测试项目调用 13 个指标与对应完整下钻，核对数量；验证缺项目、未知码、跨项目和无权限失败关闭。浏览器 Network 记录最多 4 路并发、无轮询、未点击无下钻、滚动每次只请求一页。

- [ ] **步骤 3：采集性能与查询计划**

  在受控 MySQL 测试数据集重复请求并计算 P95，记录单项、13 项完成和下钻页耗时；保存 `EXPLAIN ANALYZE` 的访问索引、估算/实际行数与项目过滤。开发机抖动与代表性数据不足必须单独标注，不用单次最快值替代 P95。

- [ ] **步骤 4：真实浏览器验收**

  在 `1280x800`、`375x812`、`390x844`、`430x932` 验证浅色/深色、逐卡 loading、部分失败、全部失败、单项重试、全量刷新、空下钻、第一页失败、后续页失败、滚动完成、项目切换和无权限。每个视口检查 `document.documentElement.scrollWidth <= window.innerWidth`，抽屉标题/关闭可达且正文独立滚动。

- [ ] **步骤 5：独立观测与收敛判定**

  按 control-engineering 账本写实际执行和观测；任何 must 偏差进入纠偏，全部 R1-R7 有可重复证据后才写 convergence 并将 phase 更新为 `converged`。

**验收与证据：** 命令退出码、Surefire 报告、构建日志摘要、API/Network 记录、查询计划和 P95 表、浏览器视口截图、控制台错误检查、范围审计和闭环 JSON。

**风险：** Docker/MySQL 或浏览器环境不可用；真实数据规模不足使生产 P95 无法外推；全局治理被任务外文件阻塞。

**回滚：** 回退 T3 页面/API、T2 Controller/调度变更和 T1 查询定义；不需数据库反向迁移，快照表仍保留。

**停止条件：** 任一 must 验收失败、性能门槛未达、出现越权/跨租户数据、页面级横向溢出或工作区出现与任务冲突的未知改动。

**升级条件：** 环境无法提供 MySQL/浏览器验收，或修复性能必须扩大到平台迁移/公共能力时，向用户与模块 Owner 报告缺失证据和所需授权。

## 集成检查

| 检查 | 命令/方法 | 通过信号 |
| --- | --- | --- |
| 后端模块 | `mvn -pl :ccb-data-migration -am test` | 退出码 0，新增及相关测试无失败 |
| 前端类型与构建 | `npm --prefix web run build` | `vue-tsc`、Vite 均成功 |
| 调度/快照静态审计 | `rg -n "Scheduled|EnableScheduling|dm_dashboard_snapshot" server/src/modules/data-migration/src/main web/src/modules/data-migration/views/dashboard` | 无命中 |
| 前端轮询审计 | `rg -n "setInterval|setTimeout" web/src/modules/data-migration/views/dashboard/OverallDashboardPage.vue` | 无命中 |
| 当前任务范围 | `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml` | 退出码 0 |
| 全局治理 | `node scripts/check-all-governance.mjs` | 退出码 0，或仅记录已确认的任务外 REQ-061 扰动 |
| 格式 | `git diff --check` | 无空白错误 |
| 性能 | 重复计时 + `EXPLAIN ANALYZE` | 三项 P95 达标且项目范围索引生效 |
| 浏览器 | 四视口、明暗主题、全状态与 Network | 无控制台错误、无页面级横向溢出、请求行为符合 R3/R4 |

## 控制模型种子

以下内容均为 `hypotheses-only`，必须在 `$model-engineering-system` 阶段验证：

- 被控边界候选：数据迁移 Dashboard Service/Controller、整体看板 API/页面、MySQL 13 张资产表；公共项目 store、公共 UI、资产 CRUD 和快照表结构在边界外。
- 状态变量候选：当前项目 ID、页面请求代次、13 项状态和值、在途队列数、抽屉指标/页码/记录/hasMore/错误、ECharts 实例。
- 接口候选：两个新 GET 端点、三个兼容路由、`requireProject`、Axios `AbortSignal`、ECharts init/setOption/dispose。
- 传感器候选：JUnit/Testcontainers、SQL/参数捕获、`EXPLAIN ANALYZE`、重复请求计时、Vue 类型构建、浏览器 Network/Console、视口宽度检查、静态 `rg`、范围检查。
- 执行器候选：静态指标定义、JdbcTemplate 查询、专用 Controller、调度器删除、前端并发队列/取消代次、响应式 CSS、抽屉滚动门闩。
- 扰动候选：不同表模式、数据库数据倾斜/冷缓存、浏览器快速切项目/重复刷新、慢请求乱序、ResizeObserver 时序、外部 REQ-061 治理文件错误。
- 时延候选：最多 4 路队列等待、数据库连接池、下钻滚动触发、ECharts resize、浏览器网络与渲染。
- 假设：现有 `(tenant_id, project_id, ...)` 索引足以满足门槛；若查询计划或 P95 推翻，因迁移目录不在当前写入范围而停止并升级。

## 风险与用户批准

- 高风险动作 1：删除 `DashboardSnapshotScheduler.java`，停止每日快照写入；表和历史数据保留，回退可恢复类。
- 高风险动作 2：把兼容看板路由从 `StructuredAssetController` 移至专用 Controller；路由 URL 和组件看板行为保持不变，并以路由/安全测试防止回归。
- 高风险动作 3：13 个独立实时查询会增加数据库瞬时负载；以最多 4 路并发、项目索引、P95 和查询计划门禁控制。
- 2026-09-09 用户明确回复“进入代码开发”，批准按计划修订 1 导入 handoff 并进入产品代码实施。
