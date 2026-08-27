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
