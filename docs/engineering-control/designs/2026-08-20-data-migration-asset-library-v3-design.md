# 数据迁移资产库 V3 工程设计

## 状态

- 设计修订：1
- 状态：已批准
- 来源：`数据迁移资产库功能设计说明书_v3.docx`
- 批准证据：用户确认放弃此前资产设计，以最新 `main` 的全新隔离分支按本设计建立 `REQ-20260820-031` 并开始实现。

### 菜单层级修订

用户于 2026-08-21 确认：新增“数据迁移”一级目录；将“数迁资产看板”“数迁资产内容”“基础资料管理”调整为二级目录，其原有子菜单、权限码和路由保持不变。通过追加 V36 幂等迁移完成，不修改已发布 V35。

## 边界与架构

新增 `business/data-migration` 业务模块和 Maven artifact `ccb-data-migration`。模块拥有项目、组件、资产、结构化数据、专题类型、回收站、看板快照和业务审计；只消费 `shared/common`、`platform/infrastructure` 的 JDBC/MinIO 能力和 `platform/security` 的认证主体。平台模块不反向依赖该业务模块，`boot` 仅负责装配。

前端新增 `web/src/modules/data-migration`，所有请求集中在 `web/src/api/data-migration.ts`。动态菜单指向统一业务路由 `/data-migration/:group/:section`，路由参数只选择已注册页面定义，不能决定服务端权限或 SQL 字段。

## 数据设计

- `dm_project`：**已废弃**（T9 起不再由数迁维护，仅保留历史数据；所属项目数据源切换为根菜单"项目管理"的 `pm_project`）。
- `dm_component`：项目、事业群、组件编号/名称、总分核对标记和简介；项目内编号唯一。
- `dm_asset`：11 类内容资产的公共归属、类型、颗粒度、名称、限定搜索字段、类型码、组件关系、业务元数据 JSON、当前文件信息、上传人、逻辑删除和乐观版本。
- `dm_check_rule`、`dm_parameter`、`dm_issue`、`dm_table_field`：高密度结构化数据采用独立表，保留项目、组件、上传人和删除状态。
- `dm_topic_type`：管理员维护的项目级/组件级专题类型。
- `dm_dashboard_snapshot`：按统计日期、项目、组件和指标保存每日快照。
- `dm_operation_log`：模块写操作审计，不直接写平台私有审计表。

所有表包含 `tenant_id`；业务关联同时校验租户。对象键仅保存在服务端，格式为 `data-migration/<tenant>/<project>/<asset-uuid>/<random-name>`，不返回前端。

## 权限与状态

- 查询和下载：`data-migration:access`、`data-migration:write`、`data-migration:manage` 或 `system:admin`。
- 新增和上传：`data-migration:write`、`data-migration:manage` 或 `system:admin`。
- 编辑和逻辑删除：管理员可操作全部；普通人员还需 `created_by == currentUser.id`。
- 基础项目、组件和专题类型维护：仅 `data-migration:manage` 或 `system:admin`。
- 活跃记录 `deleted=0`；逻辑删除后进入回收站；恢复改回活跃；彻底清理删除业务行和当前文件对象，审计保留。
- 重新上传先写新对象，数据库成功切换当前对象后删除旧对象；失败时保留旧记录并清理新对象。

## API 与错误

- `/api/data-migration/options`：项目、组件和码值选项。
- `/api/data-migration/projects`、`/components`：基础资料分页、维护、导入导出。
- `/api/data-migration/assets/{type}`：文件型资产分页、单条/批量上传、更新、下载、删除。
- `/api/data-migration/check-rules`、`/parameters`、`/issues`、`/table-fields/{target|intermediate}`：结构化分页、维护、XLSX 导入导出。
- `/api/data-migration/recycle-bin`：管理员查询、恢复和彻底清理。
- `/api/data-migration/dashboards/overall|components`：快照查询和钻取条件。

校验失败返回 `400`，无认证 `401`，无权限 `403`，实体不存在或不可见 `404`，乐观锁冲突 `409`，文件过大 `413`，Excel 行错误通过成功响应中的逐行结果表达。批量导入限制 5,000 行和 50MB。

## 前端设计

整体看板复用交付示范中心指标网格和工具栏；组件看板、资产列表、基础资料和问题清单复用桌面表格、移动卡片、抽屉表单、批量操作栏和分页模式。页面不使用嵌套卡片，不用装饰渐变或超大标题。

桌面端列表使用稳定列宽和表格内部滚动；移动端隐藏桌面表格并显示身份区、事实区和操作区卡片。新增/编辑抽屉在手机端近全宽且正文局部滚动。危险删除进入“更多”菜单并说明逻辑删除或彻底清理后果。加载、空、失败、无权限、提交中和部分成功均有持续可见状态。

## 性能与运行

- 列表只允许 20/50/100 条，默认 20；索引覆盖租户、删除状态、项目、组件、资产类型、上传人和限定名称字段。
- 每日 02:00 生成快照；开发和测试可调用管理员刷新接口验证，生产入口不显示手工刷新。
- 文件上传和下载不把完整内容载入业务实体；Excel 解析限制大小和行数。

## 非目标和回退

不实现旧设计能力，不启用 Mock 数据，不修改平台文件预览、工作流和系统权限实现。回退先停菜单和调度，再回退应用；新增表和对象保留，禁止生产直接删除。

---

# 系统/组件清单 增量设计（2026-08-22）

## 状态

- 设计修订：4（在已批准 V3 设计之上增量修订）
- 状态：已确认（用户四项决策 2026-08-22；组件编号/名称/简介字段清理 2026-08-22）
- 菜单：基础资料管理 → 系统/组件清单（642，`data-migration:base:components`）

## 功能规格（来自需求确认）

- 单笔新增/修改/查询：所属项目（下拉，数据源为根菜单"项目管理" `pm_project`，经平台 workbench 契约 `/project/projects/workbench` 读取，遵循 `project:project:list` 权限与成员数据范围，后端存 `project_id`）；系统编号（输入搜索，对应 `arch_physical_subsystem.code`，后端保存 `physical_subsystem_code`）；所属事业群、系统简称、系统名称、系统描述、负责团队由系统编号从物理子系统动态带出（只读展示）。
- 限制条件：系统编号（组件身份）项目内全局唯一（不同项目可重复）；仅允许修改"是否涉及总分核对"。
- 列表展示（服务端分页）：所属项目、所属事业群、系统编号、系统简称、系统名称、系统描述、负责团队、是否涉及总分核对、创建时间、创建人、更新时间、更新人。
- 筛选：所属项目（下拉）、所属事业群（模糊）、系统编号（搜索）、负责团队（模糊）、系统简称/名称（同框模糊）、是否涉及总分核对（是/否）、关键字（系统编号/系统名称/简称模糊）。
- 下载：筛选结果批量导出标准 Excel，含全量元数据字段。
- 角色权限：走"权限管理"模块（菜单 642 及 `data-migration:base:components` 动作权限）。

## 数据设计（V84 增量）

`dm_component` 增列，并**清理多余历史字段 `component_code`/`component_name`/`description`**（组件身份由系统编号承担，编号/名称与系统编号/名称重复、组件简介冗余；V84 幂等删除三列及原唯一键 `uk_dm_component_code`，以 `uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code, deleted)` 承接"项目内全局唯一"约束；列/索引存在性判断遵循 V62 模式，旧库自动清理、全新库直接建表；`dm_project.description` 项目简介不在清理范围）：

| 列 | 类型 | 说明 |
|---|---|---|
| `physical_subsystem_code` | VARCHAR(64) NULL | 系统编号（`arch_physical_subsystem.code`），保存，用于 JOIN |
| `total_check` | TINYINT NOT NULL DEFAULT 0 | 是否涉及总分核对（0 否 / 1 是） |
| `created_by` | BIGINT NULL | 创建人（列表展示，兼容既有 `owner_id`） |
| `updated_by` | BIGINT NULL | 更新人（列表展示） |

新增索引 `idx_dm_component_list (tenant_id, project_id, deleted, updated_at)` 支撑列表分页排序。

事业群/简称/名称/描述/负责团队**不落库**：通过 `LEFT JOIN arch_physical_subsystem`（`tenant_id` + `physical_subsystem_code = code` 且 `deleted=0`）实时带出；负责团队取该表 `responsible_team_name_snapshot`（快照口径，架构模块展示的动态 org 名称超出本模块边界，不在本页复现）。创建人/更新人显示名沿用既有 `sys_user.display_name` 投影（与 `DataMigrationPermissionService` 读取平台表先例一致）。

V84 以**幂等**方式补齐 `dm_project`/`dm_component` 建表（`CREATE TABLE IF NOT EXISTS` + `information_schema` 动态补列/补索引），修复源目录缺失 data-migration 建表脚本的既有缺口；同时更新 `codex-task-scope.yaml` 迁移授权为 V84。

### 字段清理决策（2026-08-22）

`component_code`/`component_name`/`description` 为 `dm_component` 历史遗留字段：编号/名称与系统编号/系统名称重复、组件简介冗余，本功能规格均无需维护；经确认作为多余字段清理：

- 数据库：V84 幂等 DROP 三列及 `uk_dm_component_code`，新增 `uk_dm_component_subsystem`（系统编号项目内唯一，存在重复历史数据时跳过、由服务层兜底）；
- 后端：列表/导出/单条视图/创建均不再读写三字段，`physicalSubsystemCode` 调整为必填，关键字筛选改为系统编号/系统名称/系统简称；
- 前端：新增表单与编辑抽屉移除三字段录入/展示，组件看板改为展示系统编号/系统名称（组件身份即物理子系统），导出为 13 列（不含组件简介）。

### 跨模块只读关联声明

- `arch_physical_subsystem` 建表脚本位于 `platform/infrastructure`（V77），按依赖矩阵属于 data-migration 允许依赖的 `platform/infrastructure` 数据资产；本次仅为只读 JOIN，不写该表。
- 前端新增时复用架构模块既有 REST 契约（`/architecture/physical-subsystems`）：在 `web/src/api/data-migration.ts` 新增 `listPhysicalSubsystemsByCode` 直调该接口完成系统编号搜索与字段带出，不引入代码级跨模块 import（保持模块边界），后端不新增跨模块接口。

### 数据迁移角色初始化决策（2026-08-22 T10）

用户要求新增两个数据迁移角色并写入初始化脚本（幂等、兼容现有角色体系）：

- **数据迁移管理员**（`DATA_MIGRATION_ADMIN`，id=200）：负责数据迁移任务的**创建、监控与管理**。分配全部数据迁移菜单（700-744 现存 20 项）与全部 `data-migration:%` 动作权限（66 项，含 read/create/update/delete）。
- **数据迁移开发人员**（`DATA_MIGRATION_DEVELOPER`，id=201）：负责迁移任务的**执行与调试**。分配同样 20 项菜单（可见）但仅 `action_code='read'` 的只读动作权限（18 项），不授予 create/update/delete 与 `data-migration:manage`。
- **落地方式**：`V87__data_migration_roles_seed.sql` 幂等新增 `sys_role.role_description` 列（`information_schema` 检查，仅加列不改平台 Java 代码），角色 INSERT 用 `WHERE NOT EXISTS`（按 `tenant_id + role_code + deleted=0` 判定），菜单/权限分配用 `INSERT IGNORE`，可重复执行。
- **权限覆盖**：两角色经菜单 700/720 获得 `data-migration:access`（类级访问），管理员经菜单 740 获得 `data-migration:manage`（写操作）；`data-migration:write/dashboard/components` 权限码不存在于菜单/权限表，无需登记。
- **兼容性**：角色 id 200/201 与既有 1（SUPER_ADMIN）/100/101（需求角色）隔离；不授予 `system:admin`；不修改既有角色分配；新角色默认未绑定用户，由管理员在角色管理界面分配。

### 项目数据源切换决策（2026-08-22 T9）

经用户定标确认，数迁"所属项目"概念**全链路收敛到根菜单"项目管理"（`pm_project`）**：

- **数据源**：所属项目下拉/筛选、组件创建、资产（普通/结构化）与 Excel 导入的项目校验、看板项目统计与快照 `PROJECT_TOTAL`，全部从数迁自建 `dm_project` 切换到 `pm_project`（V51 建表，与 `dm_*` 表同 schema，仅只读 SQL JOIN/COUNT，不写 `pm_project`）。
- **读取方式**：交互数据源走平台 workbench 契约（`/project/projects/workbench`，`project:project:list` + 成员数据范围，超管可见全部）；后端组件列表/导出/详情 JOIN `pm_project` 仅用于展示项目编码/名称，存在性校验（`ensureProject`）同样只读 `pm_project`，不新增模块 Java 依赖（不修改 `modules.yaml` 与 `pom.xml`）。
- **废弃数迁项目维护**：`/data-migration/projects` 的 GET/POST/PUT/DELETE 与 `/options` 端点、`ProjectComponentService` 的 `projects/options/createProject/updateProject/deleteProject` 及其辅助方法全部移除；前端项目清单页（`ProjectsPage.vue`）、路由 `/data-migration/base/projects` 与 `data-migration.ts` 的 4 个 project API 同步删除；`/data-migration/base` 默认重定向改为系统/组件清单。
- **数据迁移**：`dm_component` 有效数据为 0（仅 2 条软删）、`dm_asset` 引用为 0，无存量迁移负担；`dm_project` 表保留（历史软删数据），不再提供维护入口。
- **权限与数据范围**：非超管仅在下拉中看到其成员项目（workbench 范围）；后端 JOIN/COUNT 不做成员过滤，与切换前 `dm_project` 行为一致（列表展示全部组件），该口径差异在定标时已确认接受。

## 后端 API 设计

| 接口 | 说明 | 权限 |
|---|---|---|
| `GET /components`（改造） | 分页 + 全部筛选参数，返回 `PageResult`，行内 JOIN 带出项目、物理子系统与用户显示名 | 类级（`data-migration:access/write/manage`、`system:admin`） |
| `POST /components`（改造） | 新增：`projectId/physicalSubsystemCode/totalCheck`；校验项目存在、系统编号项目内唯一 | `data-migration:manage` 或 `system:admin` |
| `PUT /components/{id}`（收敛） | **仅允许修改 `totalCheck`**，其他字段忽略；更新 `updated_by` | `data-migration:manage` 或 `system:admin` |
| `DELETE /components/{id}`（保留） | 逻辑删除，有关联资产拒绝（沿用现有） | `data-migration:manage` 或 `system:admin` |
| `GET /components/export`（新增） | 同筛选参数，导出全量元数据 XLSX | 类级 |

- 新增/修改/删除方法级收敛为 `data-migration:manage`/`system:admin`，与既有设计文档"基础项目、组件和专题类型维护：仅 manage 或 admin"一致；菜单动作权限（`data-migration:base:components:create/update/delete`）由权限管理模块分配，服务端以 manage/admin 兜底。
- 审计沿用 `dm_operation_log`（`COMPONENT_CREATE/UPDATE/DELETE`）。

## 前端设计（ComponentsPage.vue 重构）

- 筛选区（UiToolbar）：项目下拉（`getProjectWorkbench`，数据源 `pm_project`）、事业群、系统编号、负责团队、简称/名称同框、总分核对下拉、关键字、查询/重置。
- 工具栏：新增、导出（筛选后，blob 下载）。
- 列表：12 列 + 操作列（编辑），分页 20/50/100，服务端分页。
- 新增抽屉：项目下拉 → 系统编号输入（搜索联动物理子系统，选中后只读带出事业群/简称/名称/描述/负责团队）→ 总分核对（默认否）。
- 编辑抽屉：仅"是否涉及总分核对"可编辑，其余字段只读展示。
- 移动端卡片化（身份/事实/操作区）、加载/空/失败/无权限/提交中状态齐备，复用 `UiDataTable/UiFormDrawer/UiToolbar` 与语义主题变量；按 design-h5.md 375/390/430 视口验收。
- 联动依赖 `architecture:physical:list` 权限（架构模块既有接口），权限管理模块需为数据迁移角色授权，否则系统编号搜索 403 并展示无权限状态。

---

# 问题清单独立存储增量设计（2026-08-24）

## 文档状态

- 设计修订：1（问题清单独立存储）
- 状态：已确认
- 用户确认依据：用户确认“问题清单不要复用 dm_asset 表”，并确认“不留存历史数据，同时删除 dm_asset 冗余字段”；后续确认按最小安全范围保留仍被其他资产使用的通用字段。

## 目标与成功信号

问题清单不再读写 `dm_asset`，改为独立的 `dm_issue` 表。历史 `dm_asset(asset_type='ISSUE')` 数据不迁移、不归档，按迁移策略直接清理；当前本地数据库中该类数据为 0 条。问题清单与会议纪要、目标表、目标字段的关联继续通过 `dm_asset_relation`，但 `source_asset_type='ISSUE'` 的源 ID 改为 `dm_issue.id`。

成功信号：

1. 数据库存在 `dm_issue`，问题字段为结构化列，不依赖 `dm_asset.structured_data`。
2. `IssueService`、问题清单 Excel 导入/导出和回收站仅访问 `dm_issue` 与 `dm_asset_relation`。
3. `AssetService`、`StructuredAssetService`、资产类型集合不再接受 `ISSUE`。
4. 清理迁移执行后，`dm_asset` 中不存在 `asset_type='ISSUE'` 数据，问题关系不存在旧源记录。
5. `dm_asset` 的通用字段保持可用：汇报材料字段、文件附件字段、规则/参数的 `structured_data`、通用审计字段不删除。

## 必须需求与验收条件

- **R-I1 独立模型**：新增 `dm_issue`，问题业务字段以列存储；验收为 `SHOW CREATE TABLE dm_issue` 包含项目、问题身份、问题分类、处理信息、审计和逻辑删除字段。
- **R-I2 无历史留存**：迁移不复制 `dm_asset(asset_type='ISSUE')` 数据；验收为迁移后该类记录数为 0，且 `dm_asset_relation` 不存在 `source_asset_type='ISSUE'` 的旧关联。
- **R-I3 服务迁移**：问题清单增删改查、筛选、导入导出、回收站和审计全部改读写 `dm_issue`；验收为服务 SQL 和模块测试不再查询/插入 `dm_asset` 的 ISSUE 类型。
- **R-I4 关系兼容**：会议纪要、目标表、目标字段关系继续可查询；验收为关系查询使用 `dm_issue.id` 与明确的目标类型，并执行租户校验。
- **R-I5 通用列保护**：不删除仍被其他资产使用的 `dm_asset` 列；验收为 `structured_data`、报告字段、附件字段和审计字段的现有测试通过。
- **R-I6 权限审计**：新表写操作保持现有认证、RBAC、实体授权、逻辑删除和 `dm_operation_log` 审计；验收为管理员、普通用户、越权、删除和恢复测试覆盖。

## 不变量与约束

- `dm_issue.tenant_id`、`project_id` 和所有关系查询必须执行租户边界校验。
- 问题编号在同一租户、项目和未删除范围内唯一。
- 删除为逻辑删除；本次历史清理仅针对旧 `dm_asset` ISSUE 数据，不保留业务副本。
- 不修改已发布 Flyway 脚本，只追加迁移；不在生产手工改表。
- 不删除 `dm_asset.structured_data`，因为规则/参数及遗留结构化资产仍使用该列。
- 不删除 `dm_asset.report_period`、`report_date`、`keywords`、文件附件列或通用审计列。

## 方案比较与选择

### 方案 A：新增 `dm_issue`，保留通用关系表（选定）

新增独立主表，关系表继续使用 `dm_asset_relation` 的多态类型字段；清理旧 ISSUE 数据和关联。优点是满足独立模型要求，改动边界清晰，目标表/字段关系契约可复用，回归面可控。

### 方案 B：新增 `dm_issue` 与专用 `dm_issue_relation`

问题关系单独建表，彻底不再使用 `dm_asset_relation`。隔离性更强，但会重复关联模型、增加接口和迁移面，当前需求没有要求关系表拆分，因此不选。

### 方案 C：保留 `dm_asset`，仅增加视图或别名

无法满足“不复用 `dm_asset` 表”，排除。

## 架构边界与组件职责

- `dm_issue`：问题清单唯一业务数据源，负责问题字段、项目归属、所有者、审计和逻辑删除。
- `IssueService`：问题清单 CRUD、筛选、回收站、码值校验和实体授权；禁止访问 `dm_asset` ISSUE 行。
- `IssueController`：保持 `/api/data-migration/issues` 路由契约，DTO 字段由 `dm_issue` 列投影。
- `ExcelService` 或问题专用导入导出逻辑：问题导入/导出改为 `dm_issue` 列，不再输出 `structured_data`。
- `dm_asset_relation`：保留多态关系存储；问题源类型为 `ISSUE`，源 ID 指向 `dm_issue.id`，目标类型为 `MEETING`、`TABLE` 或 `FIELD`。
- `dm_operation_log`：审计实体类型保留 `ISSUE`，实体 ID 指向 `dm_issue.id`。
- `dm_asset`：继续承载文件型资产和仍需 JSON 的规则/参数，不再承载 ISSUE。

## 数据结构与状态流

`dm_issue` 至少包含：`id`、`tenant_id`、`project_id`、`issue_code`、`issue_name`、`granularity`、`system_code`、`system_name`、`issue_source`、`defect_type`、`issue_description`、`solution`、`meeting_conclusion`、`processing_steps`、`business_scenario`、`handler`、`responsible_party`、`keywords`、`frequency`、`owner_id`、`created_at`、`created_by`、`updated_at`、`updated_by`、`deleted`、`deleted_by`、`deleted_at`。

迁移顺序：创建 `dm_issue` 和索引 → 清理 `dm_asset_relation` 中 `source_asset_type='ISSUE'` 的关系 → 删除 `dm_asset` 中 `asset_type='ISSUE'` 行 → 切换应用 SQL/服务 → 验证新表、权限、审计和通用资产回归。由于用户明确不留存历史数据，不执行 ISSUE 数据复制。

## 错误、降级与恢复

- 新表不存在或迁移未完成：应用启动检查失败，不回退到 `dm_asset`，避免双写和数据源漂移。
- 关系目标不存在、租户不一致或实体已删除：返回业务校验错误，不写入关系。
- 普通用户编辑他人问题：返回 `403`；管理员可按现有权限策略处理。
- 历史清理前必须完成迁移前计数和备份策略评估；本需求选择不保留历史业务数据，恢复只能依靠数据库备份，不通过应用回收站恢复旧 ISSUE 数据。

## 验证策略

- 数据库：验证 `dm_issue` 结构、唯一键、索引、逻辑删除字段，以及旧 ISSUE/关系计数为 0。
- 后端：运行 `mvn -pl :ccb-data-migration -am test`，覆盖问题 CRUD、筛选、导入导出、权限、关系和审计。
- 静态契约：搜索问题服务和结构化资产服务，确认不存在 `dm_asset` ISSUE 查询/写入；运行模块注册和治理检查。
- 回归：验证 REPORT 资产、RULE/PARAMETER 结构化资产仍可使用 `dm_asset.structured_data`，附件字段和回收站不受影响。

## 风险与回退原则

- 风险：历史数据直接删除不可逆；通过迁移前计数、明确清理语句、事务边界和数据库备份降低风险。
- 风险：关系表是多态契约，源 ID 类型切换错误会造成孤立关系；通过关系类型/租户/目标实体校验和专项测试控制。
- 回退：应用回退不得重新启用 `dm_asset` ISSUE 路径；若需要恢复历史数据，仅从数据库备份执行经审批的数据恢复，不在生产执行 DROP 或手工补数据。
