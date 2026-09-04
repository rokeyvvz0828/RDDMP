---
id: REQ-20260820-031
status: ready
owner: rokeyvvz0828
module: business/data-migration
---

# 数据迁移资产库 V3

## 业务目标

依据《数据迁移资产库功能设计说明书 V3》从 `main` 全新建设数据迁移资产库，集中管理迁移项目、组件、文档、结构化规则、参数、程序包、问题清单和表结构，并提供项目级和组件级资产看板。V3 完全替代此前未实施的资产库设计，不继承其目录树、分享、收藏、标签、备注、审核和文件版本契约。

### T37 当前口径：移除 MD5 字段并关闭查重

数据迁移模块尚未投产，当前模型不保存文件摘要，不计算或上传 MD5，不提供内容或汇报材料查重接口。7 张文件型内容表通过追加迁移 `V174__data_migration_remove_checksum_md5.sql` 移除 `checksum_md5` 列及 `idx_*_md5` 索引；附件绑定、对象存储、文件替换、权限和审计保持不变。相同内容文件按普通文件允许重复入库。本文后续 T31/T32/T50 段落中的 MD5 内容均为历史决策或缺陷记录，以本节为当前有效口径。

## 使用者与入口

- 管理员：通过“数据迁移”下的“数迁资产看板”“数迁资产内容”“基础资料管理”访问全部数据，维护基础资料和回收站。
- 普通人员：查看和下载全部未删除资产，上传资料，只能编辑或删除本人上传的数据。
- 入口由正式菜单和 RBAC 权限入库，不依赖前端硬编码菜单或 Mock 数据。

## 本次实施范围

### 数迁资产看板

- 数迁整体资产看板：按项目展示 13 类资产指标，支持项目和限定字段关键字查询，点击指标携带条件钻取实时明细。
- 数迁组件级资产看板：按项目、事业群、组件筛选，展示组件维度 14 类资产数量并钻取明细。
- 每日生成看板汇总快照；明细列表始终实时分页查询。

### 数迁资产内容管理

- 汇报材料、会议纪要、迁移方案、迁移映射、迁移检核规则、迁移参数、迁移过程依赖文件、迁移程序、专题材料、投产及演练、问题清单。
- 按 V3 字段提供组合筛选、限定字段模糊搜索、分页列表、单条录入、文件上传或 Excel 批量导入、编辑、下载、逻辑删除。
- 文件重新上传替换当前有效文件；不提供用户可见的历史版本。
- 文件型资产保存原始文件名、类型、大小、对象引用、上传人和上传时间；对象引用不得返回前端。
- 全部内容类型统一进入回收站，管理员可以查询、恢复和彻底清理。

### 基础资料管理

- 项目清单维护：编号自动生成，项目名称唯一，支持新增和编辑，不提供删除。
- 系统/组件清单：项目内组件编号唯一，支持查询、单条录入、Excel 批量导入、编辑、关联校验删除和筛选结果导出。
- 目标表结构和中间表结构：支持 Excel 批量导入、字段级编辑、关联校验删除和单表/筛选结果导出。
- 管理员维护基础资料；普通人员对表结构仅能编辑和删除本人上传的数据。

## 统一规则

- 单租户数据通过服务端认证主体的 `tenantId` 隔离，前端不得提交可信租户或操作人字段。
- RBAC 权限分为 `data-migration:access`、`data-migration:write`、`data-migration:manage`；管理员同时识别 `system:admin`。
- 管理权限允许维护全部资产；普通写权限还必须通过上传人实体授权。
- 所有写操作写入模块自有审计表，包含操作人、对象类型、对象标识、动作、结果、trace ID 和时间。
- 删除为逻辑删除；彻底清理删除业务记录及当前对象文件，但保留审计记录。
- 组件编号按项目内唯一解释；第 6 章码值表是缺陷类型、问题来源、颗粒度和问题频率的权威码值。
- V3 中误写为“参数字段”的依赖文件和问题清单 Excel 说明按各自展示字段解析。
- “数据迁移”为正式一级菜单；其下“数迁资产看板”“数迁资产内容”“基础资料管理”为二级目录；“数迁实施工艺”不在本次范围。

## 非目标

- 不实现此前资产设计中的目录树、拖拽换目录、文件编号查询、MD5/SHA-256、分享、收藏、标签、备注、审核、迁移包、外链和用户可见版本历史。
- 不实现全文检索、OCR、在线编辑、跨租户共享、项目成员角色模型或生产数据迁入。
- 不启用 `biz_form_*` 或输入项配置能力。
- 不修改现有平台安全、系统、工作流和文件预览业务行为。

## 数据与接口

- 新增项目、组件、通用资产、检核规则、迁移参数、问题、表结构字段、看板快照和操作审计表；专题类型表不在当前模型中启用。
- REST API 使用 `/api/data-migration/**`，普通 JSON 响应使用统一 `ApiResponse`，文件下载和 Excel 导出使用受认证的流式响应。
- 文件内容通过现有附件平台能力写入 `att_file` 并受控访问；业务模块只维护附件 ID、业务关联和生命周期，不再生成或持久化对象键。
- Excel 导入按行返回成功数、失败数和逐行错误，不因单行失败静默丢弃其他行。

## 前端要求

- 新增独立 `web/src/modules/data-migration/`，通过 `web/src/api/data-migration.ts` 访问后端。
- 桌面端使用项目/组件筛选、指标网格、`UiDataTable`、`UiToolbar` 和 `UiFormDrawer`；大量列保持局部横向滚动。
- 移动端在 `375x812`、`390x844`、`430x932` 使用指标两列或单列、资产卡片、单列表单和视口内弹层，不产生页面级横向滚动。
- 覆盖加载、空、筛选无结果、失败、无权限、提交中、重复提交、只读和部分导入失败状态。

## 验收标准

1. “数据迁移”一级菜单及其三个二级目录和 16 个有效功能入口由数据库正式菜单返回，管理员登录后可见且刷新不丢失；回收站保留为受控路由能力，不单独计入正式菜单。
2. 项目、组件、文档资产、结构化规则、参数、问题和表结构的正常、校验失败、无权限和租户边界测试通过。
3. 管理员可编辑全部资料；普通人员可查看/下载全部资料，只能编辑/删除本人上传资料，伪造上传人或租户无效。
4. 文件上传、重新上传、下载、逻辑删除、回收站恢复和彻底清理形成一致的数据库与对象状态。
5. Excel 导入校验项目、组件和码值，返回逐行结果；组件、目标表和中间表可导出 XLSX。
6. 整体和组件看板读取每日快照，点击指标携带项目、组件、颗粒度和类型条件进入实时明细。
7. 列表支持服务端 20/50/100 分页及 V3 指定筛选字段，限定关键字查询不执行无界全字段扫描。
8. 所有写操作存在模块审计记录；对象键、租户标识和服务器异常细节不泄露给前端。
9. `mvn -pl :ccb-data-migration -am test`、`npm --prefix web run build`、当前需求范围和当前控制前缀检查通过；`mvn test` 与全局治理检查仍执行并留证，只有可归因于数据迁移模块的失败阻塞本需求，其他模块或历史账本失败不纳入本需求治理范围。
10. 不使用 Mock 启动本地 MySQL、MinIO、后端和前端后，管理员在桌面和三个手机视口完成菜单、查询、新增、上传、编辑、删除、恢复、导出和钻取验收。

## 风险、发布与回退

- 风险：新增模块登记、Flyway 数据结构、MinIO 对象一致性、Excel 文件资源消耗、16 个入口的权限和响应式回归。
- 发布前由模块 Owner 专项复核模块边界、迁移、权限、审计和对象清理。
- 应用回退先隐藏新增菜单并停用看板任务，再回退应用提交；V98 物理删除前必须完成对象键到 `att_file` 的补偿并保留数据库备份，删除列或表的恢复只能通过备份恢复或单独审批的前向补偿迁移完成。
- 当前执行环境已由用户确认为开发测试环境；V93 删除旧 ISSUE 行及其关系时无需备份或历史迁移，旧数据删除后不可通过应用回退恢复。若执行时无法确认仍为开发测试环境，必须停止迁移。

## 准入状态

- 用户于 2026-08-20 明确确认：放弃此前资产设计，以 V3 为全新需求，使用 `feat/REQ-20260820-031-data-migration` 分支，并按确认设计建立正式需求、任务范围和控制账本后开始实现。
- 模块 Owner 为 `rokeyvvz0828`；当前任务允许本地开发和测试，不允许生产访问。

## 演进合并：REQ-20260831-050 数迁资产内容表拆分（已并入本需求）

> 2026-09-01 由模块 Owner 决定：REQ-20260831-050 本质是本需求（数迁资产库 V3）的后续演进/优化，统一合并进 REQ-20260820-031，不再作为平行需求独立保留。本节为其需求与验收登记；完整定义、任务与验证证据见控制账本 `correction-050-consolidation.json`、`execution-050-T1..T7.json`、`observation-050-T*.json`，设计基线见 `docs/engineering-control/designs/2026-08-31-data-migration-content-table-split-design.md`。

### 演进目标

将「数迁资产内容管理」的 11 个二级菜单由共用 `dm_asset`（按 `asset_type` 分区）的过渡模型改为**一菜单一表**，并把分散的附件关联、问题关联收敛为两张公共关系表；存量无损迁移后物理删除 `dm_asset`/`dm_asset_relation`/`dm_meeting_attachment`。

### 合并进来的增量（迁移 V99–V102）

- `V99` 建 9 张内容表 + `dm_content_attachment` + `dm_issue_relation`（生成列活动唯一键、租户前缀索引、中文注释）。
- `V100` 从 `dm_asset`/`dm_meeting_attachment`/`dm_asset_relation` 复制搬迁并**保留原 id**（维持 `att_file` 存量绑定），关系行按映射规则迁入公共关系表并带行数断言。
- `V101` 校验残留为 0 后物理删除三张旧表；**须与 V100 分发布版本、删表前全量备份**。
- `V102` 幂等注册「统一回收站」为 `sys_menu` 正式菜单（id 732，父 720，sort 120）+ 查看权限 + 管理员授权。
- 后端：`ContentFileAssetService`/`ContentAttachmentService`/`ContentRecycleBinService`/`ContentAssetController`/`ContentRecycleBinController` 等新服务与资源端点 `/api/data-migration/{plans|mappings|dependencies|programs|topics|release-drills|rules|parameters}`，移除 `/assets/{type}` 与旧通用 `/recycle-bin`；文件内容不再执行 MD5 查重；会议附件/问题关联切公共关系表。
- 前端：`api/data-migration.ts`、`AssetListView`、`StructuredListView`、`RecycleBinPage` 等切新端点。

### 对本需求既有验收标准的修订

- **修订验收标准 #1**：原“回收站保留为受控路由能力，不单独计入正式菜单”不再适用。演进后「统一回收站」经 `V102` 注册为 `sys_menu` 正式菜单，与其余内容菜单同级可见（管理员默认授权）；回收站聚合端点覆盖全部内容类型。
- 追加验收：11 个内容菜单各自读写独立表；附件统一走 `dm_content_attachment`、问题关联统一走 `dm_issue_relation`；旧三表在 `V101` 后不存在且全量构建通过；桌面 1280x800 与移动 390x844 完成上传→预览→软删→回收站恢复且统一回收站菜单可从导航进入。

### 演进期发现并修复的运行期缺陷（证据见对应 execution/observation-050-*）

- Spring Bean 循环依赖（`DataMigrationAssetAttachmentAccessPolicy` 误依赖 `ContentFileAssetService`）→ 改回直接注入 `JdbcTemplate`。
- 历史 `check-md5`/上传 500（`md5UnionSql` 14 占位符只传 2 参数）→ T37 直接移除 MD5 查重契约及相关 SQL。
- 统一回收站菜单缺失（只加前端路由未注册 `sys_menu`）→ `V102` 补注册。

### 状态

演进内容 T1–T7 全部 verified，REQ-20260831-050 账本收敛（revision 9）后并入本需求；合并仅为文档与账本逻辑归并，**未做 git 分支/提交操作，未 push**，050 交付物与 031 产物同处当前未提交工作树，统一提交/发布属另行授权事项。

## 演进合并：REQ-20260903-064 中间表模型唯一化（已并入本需求）

> 2026-09-05 经用户确认，REQ-20260903-064 作为本需求的数据模型治理增量并入 REQ-20260820-031。原需求、scope 和 schema 4 账本作为历史证据保留，不删除、不回写历史执行时间；活动交付、范围审计和最终收敛统一由本需求前缀承担。

- 中间表唯一使用 `dm_target_table(table_category='INTERMEDIATE')` 与 `dm_target_table_field`，应用不再读写 `dm_intermediate_table`。
- 追加迁移 `V169__data_migration_intermediate_table_canonicalization.sql` 在删除旧表前断言其为空；非空时 fail-closed 并保留数据。
- 中间表菜单、列表、字段编辑、导入导出、回收站、看板和关联校验统一走目标表/字段服务，保持既有权限码、租户隔离、项目可达性、实体授权和审计规则。
- REQ-064 的 R1–R5 分别并入本需求 R2、R3、R5、R7、R8 的现有验收链；原四项任务及 convergence 证据继续保留在 `req-20260903-064-data-migration-intermediate-table-canonicalization` 历史目录。
- 回退仍为应用版本回退与测试数据库重建，不执行反向 Flyway；发现旧表非空时必须停止并由人工处理。

## 增量：迁移方案域化改造（对标会议纪要 / 汇报材料）

> 2026-09-02 由用户提出并确认：将「数迁资产内容 › 迁移方案」从通用文件型资产链路（`ContentFileAssetService` + `AssetListView`，仅 project/component 两维）升级为对标「会议纪要/汇报材料」的专属域功能，并入 REQ-20260820-031 范围，新增追加式迁移 `V168`。

### 功能目标

迁移方案统一管理：多维度筛选检索、分页列表、查看、单条录入、批量上传、编辑（含源文件重传/追加）、下载、逻辑删除与统一回收站；覆盖加载/空/失败/无权限/提交中状态与桌面 + 手机视口。

### 字段与规则（用户确认）

- 所属项目（`pm_project` 下拉）、资产颗粒度（`PROJECT`=项目级 / `SYSTEM`=系统级，两级）、迁移方案类型（`BUSINESS`=业务迁移方案 / `DATA`=数据迁移方案）、关联系统（颗粒度为系统级时必填，关联键 `(project_id, system_code)`，数据源为当前项目 `dm_component` 活动清单，系统名称经 `arch_physical_subsystem` 按编号投影）、方案名称（必填）、方案简介、源文件。
- 必填：方案名称、所属项目、资产颗粒度、迁移方案类型；系统级时关联系统必填；项目级 `system_code` 使用空串哨兵。
- 唯一约束（软删感知生成列）：`(tenant, project, granularity, plan_type, system_code)` 仅允许一条活动记录，不保存 `arch_physical_subsystem.id`。
- **一条方案挂多文件**（用户决策）：批量上传的多个文件绑定为同一方案记录的多条 `dm_content_attachment` 附件，不拆成多条方案；与硬唯一约束不冲突。方案名称取首个文件名（去扩展名）。
- 逻辑删除记录删除人/时间；写操作记 `dm_operation_log`；回收站经 `PlanRecycleBinSource` 并入统一回收站。
- 存量 `dm_plan` 经确认为空表，`V168` 仅加列不回填。

### 范围与接口

- 后端：`server/src/modules/data-migration/**` 新增 `PlanService`/`PlanController`/`PlanRecycleBinSource`，`/api/data-migration/plans*`；`PLAN` 从 `ContentFileAssetService.MANAGED_TYPES` 与 `ContentAssetController.RESOURCE_TYPES` 移除（`dm_plan` 仍留在 `FILE_TABLES`，看板计数不变；T37 后不再执行 MD5 查重）。
- 迁移：`V168__data_migration_plan_domain.sql`（`dm_plan` 加 `granularity/plan_type/system_id/plan_summary` + 活动维度唯一键 + 查询索引；`system_id` 为历史旧口径，已由 V175 下线，系统关联统一按 `(project_id, system_code)`，见数据库关系文档基线说明）。
- 前端：`web/src/api/data-migration.ts` 新增 Plan 契约；重写 `PlansPage.vue` 为专属页；`RecycleBinPage` 的 PLAN 分发经统一类型派生，无需改动。
- 权限：沿用 `data-migration:content:plans` 菜单码 + 动作码 `:create/:update/:delete`（未单独播种，回退 `data-migration:write/manage/system:admin`），服务端强制认证/RBAC/上传人实体授权。

## 增量：数据迁移模块全面跟随全局项目上下文（T31）

> 2026-09-02 由用户提出：以顶部导航栏右侧、通知中心左侧的全局项目切换器为**唯一**项目来源，统一优化数据迁移模块所有页面对「当前项目」的消费方式。**不改动全局项目切换本身的行为**（`web/src/stores/**`、`AppLayout.vue` 只读未改），仅优化模块内页面。纯前端增量，无后端/迁移/权限变更。
>
> 2026-09-03 用户追加口径（T31-r1）：**页面无需展示「所属项目」这个字段**——当前项目只由顶部全局切换器呈现，模块内既不提供项目筛选/选择，也不以只读标识条、表格列、卡片字段、详情项或抽屉表单项回显「所属项目」。文案不再回显项目名称。

### 目标与不变量

- 数据迁移页面不再自行维护独立的「所属项目」选择状态：列表查询、回收站查询、导出、新增默认值、编辑归属、导入归属均取全局当前项目。
- 数据迁移页面**不展示「所属项目」字段**（无筛选控件、无只读标识、无列/卡片/详情/表单项），但查询与提交仍按当前项目执行，归属由上下文自动决定。
- 切换全局项目后，已打开与重新进入的页面立即同步为新项目；必须清空上一项目的列表数据、筛选条件、分页、表单默认值与弹层，不得残留。
- 解析不到当前项目（未选择 / 项目列表加载失败 / 当前项目不可访问）时页面停留在加载、提示或错误态，**不回退历史项目、不发跨项目查询、不展示旧数据**。
- 保持服务端 `/api` 前缀、统一响应结构、租户上下文、RBAC 与实体授权不变；沿用后端既有 `projectId` 契约字段。

### 项目标识解析（模块内新增）

- `web/src/modules/data-migration/composables/useProjectScope.ts`：把全局上下文的 `currentRef`（`project_code`）解析为后端契约所需数值 `projectId` 与 `projectName`；项目工作台快照按模块级共享一次，避免 16 个入口重复请求；暴露 `state`（`loading|ready|unselected|unavailable|error`）、`ensureLoaded()`、`retry()`。
- ~~`components/ProjectScopeHint.vue`~~：T31 曾引入只读当前项目标识条，按 T31-r1 口径（页面不展示所属项目字段）已**删除**，模块内无任何引用与残留样式。
- `components/ProjectScopeState.vue`：非 ready 状态的统一闸门（加载 / 加载失败可重试 / 项目不可访问可重载 / 尚未选择项目），并阻断业务查询。

### 页面改造

| 页面 | 原「所属项目」入口 | 改造后 |
| --- | --- | --- |
| `AssetListView`（程序包/发布演练/映射/参数/校验规则/主题/依赖 7 个薄壳复用） | 工具栏项目 ID 输入框、上传抽屉项目 ID 输入 | 全部移除且不再展示项目字段；上传静默固定提交当前项目 |
| `StructuredListView` | 工具栏项目筛选、编辑抽屉项目字段 | 移除筛选与表单项；结构化列表端点无 `projectId`，改前端按 `project_id` 过滤；导出沿用 `projectId` 契约；编辑回传记录自身 `project_id` 防改归属 |
| `ComponentsPage` / `TargetTablesPage` | 筛选区项目下拉 + 重置项 + 新增抽屉项目下拉 + 表格列/移动卡片字段 | 全部移除；列表/导出/新增静默固定当前项目；移动端卡片标题改用系统编号 + 系统简称 |
| `PlansPage` / `IssuesPage` / `MeetingsPage` / `ReportsPage` | 筛选区项目下拉 + 重置项 + 抽屉项目下拉（含问题 Excel 导入项目下拉）+ 列表「项目」列 + 详情「项目」项 | 全部移除；列表、回收站/附件回收站、导出、导入、新增静默固定当前项目；关联系统下拉按当前项目加载；编辑仍回传记录自身归属项目防改归属 |

统一模式：`watch(scopeProjectId, ...)` 驱动「清空 + 重查」（`immediate: true`），`onMounted` 仅 `scope.ensureLoaded()`；非 ready 由 `ProjectScopeState` 覆盖渲染，新增/上传入口在 `state !== 'ready'` 时隐藏。

### 边界与剩余项（受「不改后端」约束）

- 统一回收站 `GET /api/data-migration/content/recycle-bin` 仅接受 `contentTypes/keyword/page/size`（`ContentRecycleBinController`），不支持项目维度；该页历史上就没有项目筛选，本期不变，仍为跨项目聚合（服务端租户 + `data-migration:manage` 授权约束）。
- 两个看板 `getDataMigrationDashboard(view)` 无项目参数，统计为全局口径；改为项目口径需后端契约变更，不在本次范围。
- 「清空回收站」`purgeAllIssues()` / `purgeAllAttachments()` 后端无项目参数，语义为全量清空，前端未改变其行为。
- 目标表/组件 Excel 导入模板含「所属项目编码」列，归属由后端解析文件决定，前端无法覆盖。

### 验收标准（T31 + T31-r1）

1. 数据迁移模块任何页面、弹层、抽屉、卡片和详情**都不出现「所属项目」字段**（既无可选控件，也无只读展示），列表/回收站/导出/新增请求均带当前项目的 `projectId`。
2. 切到项目 B 后列表、抽屉默认归属立即为 B，且分页与其他筛选条件被重置，无 A 的残留行。
3. 数据迁移模块所有页面不再出现可选项目筛选控件与项目下拉。
4. 用户无法通过页面控件把记录归属到当前全局项目之外。
5. 刷新、路由切换、重进页面仍以当前全局项目为准。
6. `npm --prefix web run build`（含 vue-tsc）通过；桌面 1280x800 与移动 375x812/390x844/430x932 无遮挡、无横向溢出、无空字段。

### 验收取证（T31-r1，2026-09-03）

以本地测试环境（MySQL/MinIO 容器 + 后端 :8080 + Vite :5173）与 admin 角色完成真实取证，证据等级达**浏览器已验收**：15 个数据迁移路由 × 4 视口共 66 项断言全通过（无「所属项目」文字/下拉、无页面级横向溢出、控制台 0 error、无 ≥400 响应）；真实点击顶部切换器 P2026-001 ↔ P2026-002 后列表由 CHK-A-001 变为 CHK-B-001 且请求为 `projectId=3002`、无残留；拦截 `/api/project/workbench` 返回 500 时仅显示闸门且数据迁移列表请求数为 0；验收写入的测试数据已清理。详见 `execution-T31-r1.json` / `observation-T31-r1.json`。

## 增量：数据迁移模块服务端项目隔离（T32）

> 2026-09-03 由用户提出：**数据迁移模块均要作项目隔离，新增时项目 id 从前端传给后端，修改等维护从后端获取项目 id 且不能变更。**
>
> 本节推翻 T31 中登记为「受不改后端约束保持现状」的四项边界（统一回收站、两个看板、`purge-all`、Excel 导入归属），把它们升格为**必须实现项**。T31 只做到了「前端不展示、前端按当前项目查询」，服务端仍是「不传 projectId 即返回全租户数据」，因此本节属于**服务端强制隔离**，前端改动不得被当作隔离已完成的证据。

### 现状缺口（实测事实）

1. **查询侧无隔离（T32 历史基线）**：`/structured/{type}`、`/rules`、`/parameters` 的列表签名只有 `keyword`，完全没有 `projectId`（现由前端按 `project_id` 过滤）；`/dashboard/overall` 无项目参数（全租户口径）；统一回收站 `GET /recycle-bin` 及其 detail/restore/purge 无项目参数；`/issues`、`/meetings`、`/plans`、`/reports`、`/components`、`/target-tables`、`/content/*` 的 `projectId` 均为 `required=false`，省略即返回跨项目全集。历史 `check-md5` 查重接口已由 T37 移除。
2. **按 id 的运维不校验归属**：detail/download/delete(ids)/batch-delete/restore/purge/purge-all 只按 id + tenant + deleted 定位，任何同租户用户可对其他项目的记录执行删除、恢复与彻底清理。
3. **修改可改归属**：`IssueService.update` 取 `body.getOrDefault("projectId", current.project_id)`，`MeetingService`（`UPDATE dm_meeting SET project_id = ?`）、`PlanService`、`ReportService`（`append(", project_id = ?")`）、`StructuredAssetService`（`SET project_id = ?`）均允许把记录改到别的项目。
4. **项目合法性校验很弱**：模块内 8 份重复的私有 `ensureProject()` 只校验 `pm_project` 存在（tenant + `deleted=0`），不校验成员范围；`pm_project_member` 的成员判定只存在于 platform/system `ProjectService` 的 private 方法（`projectScope`/`requireProjectAccess`），而 `com.ccb.system.project` **不在 platform/system 的 `public_contracts`** 中。
5. **导入归属不受控**：`POST /target-tables/import`、`/structured/{type}/import`、`/rules|/parameters/import`、组件导入的归属由 Excel 内容决定，请求级无项目约束。

### 已确认决策（用户 2026-09-03 选定，均为推荐项）

- **D1 隔离判定强度**（T32-r1 修订，2026-09-03 由用户推翻原口径）：在 `business/data-migration` 模块内复用统一守卫（落在已被模块内 8 个 service 注入的 `DataMigrationPermissionService`，新增 `requireProject`/`requireAccessible`/`requireStoredProject` 三个原语，不另建 `ProjectScopeGuard` 类）。项目可达性**必须调用平台侧成员口径**，不在业务模块复制任何 `pm_project`/`pm_project_member` SQL：守卫委托 platform/system 公开契约 `com.ccb.system.capability.ProjectWorkflowDirectoryService`（`requireAccessible(long, AuthUser)` + `accessibleProjectIds(AuthUser)`），其口径为「tenant 内项目存在未删 + 平台超级管理员豁免 或 `pm_project_member` 存在 `status=1 AND deleted=0` 成员行」。原 T32 初版「模块内只读 `pm_project_member` 复制口径」的有边界让步已撤销，不再需要 Owner 复核该重复项。
- **D2 改归属处理**：所有 UPDATE 语句**不再包含 `project_id`**；请求体/参数中的 `projectId` 在修改类端点被忽略（不报错），归属恒取库中记录值。与 T31-r1「页面不展示所属项目」口径配套，避免出现用户无法解释的报错。
- **D3 纳入范围**：全部纳入（列表、详情、下载、批量删除、回收站 list/detail/restore/purge、`purge-all`、两个看板、导入），即 `projectId` 为数迁模块**所有**查询/写入选参的必要条件；历史 `check-md5` 端点不再属于当前契约。
- **D4 导入归属**：导入端点必须携带 `projectId`（并做 D1 校验）；Excel 行内的所属项目编码必须与请求项目一致，不一致按**逐行失败**返回原因，不静默改归属，也不忽略文件内容。

### 契约变更矩阵（业务模块内部契约，非平台公共能力）

| 端点/能力 | 变更前 | 变更后 |
| --- | --- | --- |
| `GET /structured/{type}`、`/rules`、`/parameters` | `keyword` only | `projectId` **必填**，SQL 恒定 `AND project_id = ?` |
| `GET /dashboard/overall` | 仅 user（读租户级快照） | `projectId` 必填，**项目内实时计数**，不再返回 `snapshotDate`（见执行期细化 1） |
| `GET /dashboard/component(s)` | `projectId` 可选 | `projectId` 必填 |
| `GET /recycle-bin`（统一回收站） | `contentTypes/keyword/page/size` | 追加 `projectId` 必填；`RecycleBinSource` SPI 的 `countDeleted`/`listDeletedPage` 增加项目参数，`detail`/`restore`/`purge` 校验记录所属项目 |
| 各列表（issues/meetings/plans/reports/components/target-tables/content） | `projectId` `required=false` | `projectId` **必填**（缺失 → 400），并做 D1 校验 |
| `GET /content/check-md5`、`/reports/check-md5` | 历史接口 | T37 已删除，不再提供替代查重接口 |
| `GET /reports/project-options` | 全租户 `pm_project` | 仅返回平台契约判定的可访问项目（D1）；前端已不消费，保留兼容 |
| 按 id 的 detail/download/delete/batch-delete/restore/purge/purge-all | 仅 tenant | 目标记录所属项目必须通过 D1；`purge-all` 仅清空当前项目 |
| 修改类（`PUT`/结构化 `update`/内容 `replace` 等） | 接受并可改 `project_id` | 忽略入参项目，`SET` 子句去掉 `project_id`（D2） |
| 导入类（目标表/组件/结构化/rules/parameters） | 归属由 Excel 决定 | 必须带 `projectId`；行内项目编码不一致 → 逐行失败（D4） |

新增/上传/导入仍由前端携带当前项目 `projectId`（T31 的 `useProjectScope` 已保证），服务端做 D1 校验；跨项目越权一律 `FORBIDDEN(40300)`，缺失项目参数一律 `BAD_REQUEST(40000)`，不返回他项目数据。

### 执行期口径细化（T32 实施回写，2026-09-03）

1. **看板不再取租户级快照**：`dm_dashboard_snapshot` 的 `PROJECT_TOTAL`/`COMPONENT_TOTAL` 只按租户汇总，读它会泄露其他项目计数，因此 `GET /dashboard/overall` 改为项目内实时计数（组件数/活动资产数/资产类型分布均限定 `project_id`），`projects` 卡片含义改为「当前调用者可访问的项目数」，响应不再返回 `snapshotDate`。快照表与其写入任务本身不改（属平台外业务数据，不在本任务范围）。
2. **T32 历史 MD5 查重域决策已废止**：T37 删除 `checksum_md5` 持久化列、MD5 索引、`assertMd5Available` 和两个 `check-md5` 端点，重复内容不再触发摘要冲突。
3. **附件级 `purge-all` 项目化**：`DELETE /meetings/attachments/purge-all` 追加必填 `projectId`，按 `JOIN dm_meeting ... AND m.project_id = ?` 选行后逐类清理；同时删除 `ContentAttachmentService.purgeAllSoftDeleted()`（租户级破坏性 `DELETE`，收敛后已无调用方），从根上消除「全租户清空」路径。附件的按会议归属判定由 `MeetingService.requireMeetingScope()`（JOIN 父表反查 `project_id`）承担，不给 `ContentAttachmentService` 引入跨表 JOIN。
4. **`projectId` 非法值口径**：查询串中 `projectId` 非数字（如 `abc`）统一返回 400，不再退化为 500；空值与必填判定先于任何数据库访问。
5. **前端契约收紧为编译期传感器**：`web/src/api/data-migration.ts` 中数据迁移的项目级查询参数类型改为 `projectId` 必填；维护类 DTO 不再接受项目字段（`ReportUpdateParams` 删除 `projectId`，新增 `IssueUpdateData`/`MeetingUpdateData`/`PlanUpdateData` 均 `Omit<..., 'projectId'>`）；`StructuredListView` 去掉按 `project_id` 的前端过滤；统一回收站页与两个看板页接入 `useProjectScope`。
6. **顺带修复的既有缺陷（历史）**：汇报材料早期前端曾存在 MD5 查重提示失效；T37 已删除该查重能力及相关代码，不再保留该兼容逻辑。
7. **口径来源改为平台契约（T32-r1 撤销让步）**：初版把 `pm_project_member` 成员判定 SQL 复制进 `requireAccessible`/`accessibleProjectCount`/`getProjectOptions`，属重复设计；用户 2026-09-03 指令「调用平台侧成员口径」后，三处统一委托 platform/system 公开契约 `ProjectWorkflowDirectoryService`，模块内不再出现 `pm_project`/`pm_project_member` 成员判定 SQL。副作用（属预期收紧）：项目数据范围的豁免角色从「`ADMIN`/`SUPER_ADMIN`/`DATA_MIGRATION_ADMIN`」收敛为平台侧判定的超级管理员；模块内 `isAdmin` 仅继续用于功能权限（RBAC 动作码）与 owner 写权限判定。错误文案随平台统一：未知项目 400「项目不存在或已删除」、非成员 403「无该项目数据访问权限」。
8. **统一回收站前端漏传 `projectId`（浏览器取证发现的真实缺陷，已修）**：`listDataMigrationRecycleBin` 的入参类型已声明 `projectId: number`，但函数体重新拼 `params` 对象时丢弃了它，导致统一回收站页在 T32 后 100% 进入失败态（列表固定返回 400 `projectId is required`，页面展示「回收站加载失败」）。该项由真实浏览器请求级传感器（比对每个 `/api/data-migration/**` 请求是否携带上下文项目）发现，不是类型检查或构建能发现的；现在 `params` 中显式转发 `projectId`。教训：契约层「参数声明必填」必须配「实际发出」的传感器，否则漏改会静默降级整个页面。
9. **`base/intermediate-tables` 路由不展示结构化中间表资产**：路由实际 `component` 为 `TargetTablesPage`（`meta.category = INTERMEDIATE`），展示 `dm_target_table` 的分类行；`structured/INTERMEDIATE_TABLE` 资产只在统一回收站（`INTERMEDIATE_TABLE` 类型）与结构化接口上存在，`views/base/IntermediateTablesPage.vue` 当前无任何路由或引用。因此项目隔离取证必须按实际路由组件建立夹具。
10. **移动端页面级横向溢出的归属与纠正**：首轮四视口取证在 375/390/430 报多个内容页 `document.documentElement.scrollWidth > clientWidth`，空态不复现。真因为 `9fec831`（本分支早于 T32 的提交）给 `AssetListView`、`PlansPage` 新加的 `<UiPagination>` 未套模块已有的 `.dm-table-footer` 约定，分页「前往 X 页」跳转器落在视口外撑宽页面。`components/ui/UiPagination.vue` 属 `read_only_paths` 且本分支零改动，因此纠正仅落在可写范围内的 `AssetListView.vue`/`PlansPage.vue`：改用 `.dm-table-footer` + `共 N 条` 结构（与 `ReportsPage` 同构），不新增样式、不改共享组件。
11. **视口传感器口径对齐 design-h5.md §5.4**：局部横向滚动（表格、页签、图表）属手册允许形态，只有「内容越出视口且未被任何 `overflow-x: auto|scroll|hidden` 祖先收容」与页面级横向滚动计为缺陷。据此修正后，原先被 `el-tabs__content`/`el-tabs__item` 触发的 6 项 FAIL 为传感器过宽，非布局回退。
12. **统一回收站与看板计数的实际覆盖集**：`RecycleBinSource` 注册表覆盖 REPORT/PLAN/MEETING + 5 类文件型资产 + 3 类结构化资产，**不含 ISSUE**（问题清单保留专属 `/issues/recycle-bin`、`/restore`、`/purge`、`/purge-all`），因此 `contentTypes=ISSUE` 返回 400「不支持的内容类型：ISSUE」属既有设计而不是 T32 缺陷；看板 `assets` 计数基于 `ContentAssetTables.ALL_TABLES`，即文件型 7 张（含 `dm_plan`、`dm_report`）+ 结构化 3 张，取证求和必须按该集合而非直觉上的「5 张内容表」。

### 不变量

- 不改 `server/src/platform/**`（含 `com.ccb.system.project`）、不改 `server/src/shared/**`、不改权限模型与菜单/角色种子。
- **不新增 Flyway 迁移**：实测 `dm_component/dm_issue/dm_meeting/dm_plan/dm_report/dm_rule/dm_parameter/dm_target_table/dm_script/dm_topic/dm_mapping_doc/dm_dependency/dm_release_drill/dm_intermediate_table/dm_dashboard_snapshot` 的查询索引均已含 `project_id`，隔离过滤不引入新索引需求。
- 保持 `com.ccb.*` 包名、Maven artifact、`/api` 前缀、`ApiResponse` 信封、软删与审计语义不变。
- 租户隔离与既有 RBAC/实体授权（`@PreAuthorize` + owner 校验）不得弱化，项目隔离是其**叠加**条件。
- 前端 `web/src/stores/**`、`web/src/components/ui/**`、`AppLayout.vue` 仍为零改动；全局项目切换器行为不变。

### 验收标准（T32）

1. 省略 `projectId` 的数据迁移查询/写入端点一律返回 400；返回数据不含任何其他项目的行。
2. 非成员且非平台豁免角色的用户请求他项目 → 403（文案与平台侧一致），且列表/详情/下载/删除/恢复/彻底清理全部一致拒绝。（本地环境唯一可登录账号为平台超级管理员（豁免），演示用户口令不可用，因此本条以「守卫委托契约」单元测试 + 平台契约自身测试 + 直连 API 400 反例代替，并如实标注未做 403 运行时/浏览器取证）
3. `PUT`/结构化更新/内容替换：请求体携带他项目 `projectId` 时记录归属不变（响应与库中 `project_id` 等于原值）。
4. 统一回收站与两个看板按当前项目取值；「清空全部」只清空当前项目。
5. 导入：请求项目与 Excel 行项目编码不一致 → 该行失败并给出原因，一致行正常入库且归属为请求项目。
6. `mvn -pl :ccb-data-migration -am test` 与 `mvn test` 通过（含新增项目隔离回归测试）；`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、任务范围检查通过。
7. 浏览器取证：项目 A↔B 切换后数据迁移各页仅显示对应项目数据；直连 API 构造他项目 `projectId` 的反例被拒；四视口布局无回退。

## 增量：内容表业务编号治理（T36）

> 2026-09-04 用户确认治理 `id` 与 `doc_code` 的派生冗余：`id` 仅作为技术主键，9 张内容表的 `doc_code` 全部由服务端生成；UUID 段不使用连字符。数据迁移模块尚未投产，本次不保留客户端写入编号的历史兼容契约。

### 业务规则

1. `dm_plan`、`dm_mapping_doc`、`dm_dependency`、`dm_script`、`dm_topic`、`dm_release_drill`、`dm_report`、`dm_rule`、`dm_parameter` 新建时，由模块内统一生成器写入不可变 `doc_code`。
2. 编号格式为 `<类型前缀>-<32 位小写十六进制 UUID>`，UUID 段无连字符；前缀为 `PLAN/MAP/DEP/SCRIPT/TOPIC/DRILL/REPORT/RULE/PARAM`。
3. `id` 只用于数据库定位、关联、附件绑定和审计。页面、搜索、回收站和导出需要业务标识时使用 `doc_code` 的 `asset_code` 投影，不把技术 `id` 当作业务编号。
4. 五类通用文件资源的 POST 上传始终创建新记录且不接收 `assetCode`；文件替换使用 `PUT /{resource}/{id}/upload`，从数据库取得项目归属和原业务编号。
5. 迁移方案、汇报材料、结构化内容更新、文件替换、软删除和恢复均不得修改 `doc_code`。旧客户端即使在 JSON 中携带 `assetCode`，服务端也忽略该字段。
6. 规则/参数 Excel 导入列为 `asset_name/project_id/component_id/asset_type/structured_data`，每行由服务端生成编号；导出仍保留 `asset_code` 只读列。

### 数据、权限与回退

- T36 历史口径为不新增 Flyway；T37 已追加 `V174__data_migration_remove_checksum_md5.sql`，现有 `doc_code`、`active_doc_code` 和 `(tenant_id, project_id, active_doc_code)` 活动唯一键保持不变。
- 复用 T32 的服务端项目隔离、RBAC、实体授权，以及附件生命周期和操作审计；T37 已移除 MD5 字段与查重能力，本次不修改 platform/shared。
- 应用回退即可恢复旧实现；模块未投产，无历史数据转换或双写期。

### 验收标准（T36）

1. 9 类生成值分别匹配登记前缀和 32 位小写十六进制 UUID 段，且代码中不存在 `PLAN-{id}`、`REPORT-{id}` 或等价 `id -> doc_code` 派生。
2. 创建请求和 Excel 导入中的客户端 `assetCode` 不能写入数据库；结构化更新 SQL 不包含 `doc_code`。
3. 通用文件按 `id` 替换后，`project_id`、`doc_code`、owner/权限和审计语义不变，主附件关系正确更新且不产生 MD5 冲突。
4. 上传表单无编号输入，规则/参数编辑请求不发送编号，计划/报告/回收站继续显示业务编号且不显示技术记录 ID。
5. 聚焦后端测试、模块测试、全量 Maven 测试、前端构建、治理与范围检查通过；本地运行和桌面/移动浏览器验收按可用环境取证。
