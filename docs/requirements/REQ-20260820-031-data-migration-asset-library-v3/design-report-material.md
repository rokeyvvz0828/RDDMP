# 汇报材料（数迁资产内容）功能设计方案 v2

> **T37 当前口径（2026-09-04）**：本文件早期 v2 设计中的 `dm_asset`、MD5 字段、摘要计算、查重接口和旧通用路由均已被后续内容表拆分与 T37 设计 supersede。当前汇报材料使用独立 `dm_report` 表和 `dm_content_attachment` 关系表；不保存、计算或上传 MD5，不提供 `check-md5` 接口。以下历史段落仅保留为设计演进记录，不作为当前实现契约。

> **实现变更说明（2026-08-23，V98 收敛于 2026-08-31）**：本方案中的早期 `dm_asset` 通用资产实现已废止，相关内容仅为历史迁移背景，不再作为当前接口实现依据。

> 需求前缀：`req-20260820-031-data-migration-asset-library-v3`
> 菜单路径：`数据迁移 / 数迁资产内容管理 / 汇报材料`（菜单 721，route `/data-migration/content/reports`）
> 作者：Codex　|　状态：**已确认（v2 决策项全部确认，进入开发阶段）**
> 决策记录（v2 调整，已获用户确认）：
> 1. 存储模型：**不复用不新建，继续复用通用资产表 `dm_asset`**，通过追加迁移补充表中不存在的字段（周期、日期、关键字、删除人/时间、创建/更新人）。
> 2. 汇报周期枚举：`DAILY` 日报 / `WEEKLY` 周报 / `BIWEEKLY` 双周报 / `MONTHLY` 月报 / `IRREGULAR` 不定期汇报。
> 3. 所属项目数据源：`pm_project` 项目清单（`GET /api/project/workbench`），与系统/组件清单页一致。
> 4. 文件摘要：**不保存、不计算、不上传**，相同内容文件按普通文件处理。
> 5. 回收站形态：**汇报材料页内 "回收站" Tab，仅数据迁移管理员可见**。
> 6. 旧 `asset_type='REPORT'` 通用资产路由：**继续使用**，通用 AssetController/AssetService 保持不变，新增专属 ReportController 与之共享 `dm_asset` 表、数据互通。

---

## 1. 现状与差距分析

当前"汇报材料"页是 `ReportsPage.vue` 薄封装，复用通用 `AssetListView.vue`（`asset_type='REPORT'`），后端走通用 `AssetService`（`dm_asset` 表），**未满足需求**的关键能力如下：

| 需求能力 | 现状 | 差距 |
| --- | --- | --- |
| 汇报周期（日报/周报/双周报/月报/不定期汇报） | 无该字段，资产表仅 asset_type 区分大类 | 缺周期字段与周期筛选 |
| 汇报日期 / 关键字索引 | 无 | 缺两列及关键字模糊检索 |
| 所属项目下拉筛选 | 仅关键词（编号/名称） | 缺项目维度下拉筛选 |
| 单条/批量上传、批量自动读文件名 | 旧通用接口曾要求 projectId+assetCode+file；T36 后编号统一由服务端生成 | 缺批量上传与自动填充 |
| 列表分页（20/50/100） | 无分页，全量返回 | 需服务端分页 |
| 编辑（重新上传+改全量元数据） | 无编辑入口 | 缺编辑接口与抽屉 |
| 逻辑删除记录删除人/删除时间 | 仅 `deleted=1` 标志 | 缺 `deleted_by/deleted_at` |
| 回收站仅管理员（恢复/确认清理） | 通用回收站（所有资产类型） | 需汇报材料专属回收站入口（页内 Tab） |
| 普通用户仅能编辑/删除本人上传 | 通用 asset 已有 owner 校验 | 需在 REPORT 维度复刻 |
| 操作日志（上传/编辑/删除/恢复/清理） | 通用 asset 有审计 | 需 REPORT 专属操作码 |

**结论（v2，历史）**：早期方案曾计划复用 `dm_asset`，该方案已由内容表拆分和 T37 移除摘要字段的当前设计取代。

---

## 2. 数据库设计（Flyway 追加迁移 V89）

> 风格对齐 V84/V88：幂等补列（`information_schema.columns` 判断）、幂等补索引、幂等存量初始化；仅追加，不修改已发布脚本；生产库不手工改表。
> 新迁移文件：`V89__data_migration_report_material_enrichment.sql`

### 2.1 `dm_asset` 补列（幂等，列级判断）

需求新增字段在 `dm_asset` 中不存在，逐一动态补列（模式与 V84 补 `dm_component` 列一致，每个字段一个 `information_schema` 判断 + `PREPARE/EXECUTE` 块）：

```sql
-- 示例块（7 列均采用同一幂等模式）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'report_period');
SET @col_sql = IF(@col_exists = 0,
  'ALTER TABLE dm_asset ADD COLUMN report_period VARCHAR(16) NULL COMMENT ''汇报周期 DAILY/WEEKLY/BIWEEKLY/MONTHLY/IRREGULAR'' AFTER asset_type',
  'SELECT 1');
PREPARE stmt FROM @col_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

| 新列 | 类型 | 说明 |
| --- | --- | --- |
| `report_period` | `VARCHAR(16) NULL` | 汇报周期，仅 REPORT 类型使用；存量数据初始化后非空 |
| `report_date` | `DATE NULL` | 汇报日期（批量上传后逐条补充） |
| `keywords` | `VARCHAR(500) NULL` | 关键字索引（录入/编辑时必填，多个以英文逗号分隔） |
| `deleted_by` | `BIGINT NULL` | 删除人（sys_user.id，逻辑删除时回填） |
| `deleted_at` | `TIMESTAMP NULL` | 删除时间（逻辑删除时回填） |
| `created_by` | `BIGINT NULL` | 上传人冗余列（与 owner_id 同值，对齐 V84 dm_component 审计风格） |
| `updated_by` | `BIGINT NULL` | 最后编辑人 |

说明：
- 字段均置 `NULL`（存量行不受 NOT NULL 约束影响），新录入由服务层强校验，满足"资料名称、关键字索引不允许为空"（存量空值行列表展示 `—`，编辑时强制补充）。
- 文件摘要字段不属于当前模型；历史 `checksum_md5` 设计已由 T37 删除。
- `owner_id`/`created_at` 复用为"上传人/上传时间"，`updated_at` 复用为更新时间。

### 2.2 补索引（幂等）

```sql
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND index_name = 'idx_dm_asset_report');
SET @idx_sql = IF(@idx_exists = 0,
  'ALTER TABLE dm_asset ADD KEY idx_dm_asset_report (tenant_id, project_id, report_period, deleted, updated_at)',
  'SELECT 1');
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

- `idx_dm_asset_report (tenant_id, project_id, report_period, deleted, updated_at)`：汇报材料列表组合筛选（项目 + 周期 + 未删除 + 排序）。
- 关键字模糊检索（`asset_name`/`keywords` LIKE）沿用现有 `idx_dm_asset_query` 前缀，数据量可接受，不额外建全文索引。

### 2.3 存量数据初始化（一次性、幂等）

存量 `dm_asset(asset_type='REPORT')` 行**已在原表中**，无需迁移，仅回填周期默认值：

```sql
UPDATE dm_asset SET report_period = 'IRREGULAR'
WHERE asset_type = 'REPORT' AND (report_period IS NULL OR report_period = '');
```

- 历史数据周期缺失 → 默认 `IRREGULAR`（不定期汇报），列表可见；`report_date`/`keywords` 保持空，由用户编辑补充。
- 幂等：执行后 `report_period` 不再为空，重复执行无副作用。

### 2.4 操作日志（复用 `dm_operation_log`）

现有表结构（V84 已建）：`id, tenant_id, actor_id, operation_code, entity_type, entity_id, result_code, trace_id, detail_json, created_at`。汇报材料操作 `entity_type='REPORT'`（业务语义），新增操作码：

| operation_code | 场景 | 权限 |
| --- | --- | --- |
| `REPORT_UPLOAD` | 单条上传 | write |
| `REPORT_BATCH_UPLOAD` | 批量上传 | write |
| `REPORT_UPDATE` | 编辑（含重新上传文件） | write + owner |
| `REPORT_DELETE` | 逻辑删除 | write + owner |
| `REPORT_RESTORE` | 回收站恢复 | manage/admin |
| `REPORT_PURGE` | 回收站确认清理 | manage/admin |

`detail_json` 记录 id 列表、资料名称与变更摘要，保证"彻底清理后仍可溯源"。

### 2.5 任务范围联动（需同步更新）

- `codex-task-scope.yaml`：`database.migration_files` 与 `writable_paths` 追加 `V89__data_migration_report_material_enrichment.sql`。
- `docs/architecture/MODULES.md`：模块登记 `dm_asset` 新增列说明。

---

## 3. 后端设计

### 3.1 存储与标识（复用 dm_asset 的关键约束处理）

- `dm_asset.asset_code` NOT NULL 且唯一键 `uk_dm_asset_code (tenant_id, project_id, asset_type, asset_code, deleted)`；汇报材料需求**无 asset_code 字段**。
- 处理方案（T36 修订）：由统一 `ContentDocCodeGenerator` 生成 `REPORT-<32 位小写无连字符 UUID>`，上传时写入，编辑/文件替换不改变；列表、详情和导出作为只读业务编号展示。本文早期时间戳+随机后缀方案不再有效。
- 汇报材料行特征：`asset_type='REPORT'`，`component_id=NULL`（汇报材料不关联组件/系统）。
- 通用 AssetController 继续使用且不改动：通用页上传的 REPORT 行（无周期回填）在汇报材料列表中周期显示 `IRREGULAR` 兜底，数据互通。

### 3.2 权限模型

- 类级：`@PreAuthorize("hasAnyAuthority('data-migration:content:reports','data-migration:access','data-migration:write','data-migration:manage','system:admin')")`（菜单 721 及 V84 已生成 read/create/update/delete 动作权限）。
- 读操作（列表/下载/回收站列表）：`data-migration:content:reports` 或 `data-migration:access`。
- 写操作（单条/批量上传、编辑、删除）：`data-migration:content:reports:create/update/delete` 或 `data-migration:write`/`manage`/`system:admin`，**行级 owner 校验** `permissions.requireWrite(user, ownerId)`（管理员可改全部，普通用户仅本人）。
- 回收站管理（恢复/确认清理/回收站列表）：`data-migration:manage` / `system:admin`（`requireAdmin`）。
- 复用 `DataMigrationPermissionService`（isAdmin / requireWrite / requireAdmin），不新建权限服务。

### 3.3 新建接口清单（ReportController，路径 `/api/data-migration/reports/**`）

| # | 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/reports` | 分页列表：`projectId?`、`period?`、`keyword?`（模糊匹配 asset_name/keywords）、`page`、`size`(20/50/100)；返回 `{items,total}` | read |
| 2 | POST | `/reports/single` | 单条上传（multipart：projectId、period、reportName、reportDate?、keywords、file）；服务端生成 `doc_code`，绑定附件，回填 owner_id/created_by | write |
| 3 | POST | `/reports/batch` | 批量上传（multipart：projectId、period、files[]）；前端逐文件读文件名，服务端逐文件建行 | write |
| 4 | PUT | `/reports/{id}` | 编辑：重传文件（可选）+ 修改全量元数据（projectId、period、reportName、reportDate、keywords）；旧对象删除 | write + owner |
| 5 | DELETE | `/reports` | 逻辑删除（body ids[]），`SET deleted=1, deleted_by=?, deleted_at=NOW()` | write + owner |
| 6 | GET | `/reports/{id}/download` | 下载：返回 MinIO 预签名 URL（不暴露 object_key） | read |
| 7 | GET | `/reports/recycle-bin` | 回收站分页列表（仅 deleted=1 且 asset_type='REPORT'） | manage/admin |
| 8 | POST | `/reports/recycle-bin/restore` | 恢复（ids[]），`SET deleted=0, deleted_by=NULL, deleted_at=NULL` | manage/admin |
| 9 | POST | `/reports/recycle-bin/purge` | 确认清理（ids[]）：物理删除行 + MinIO 对象，保留 dm_operation_log | manage/admin |

响应统一 `ApiResponse`；`object_key` 严禁返回前端。

### 3.4 调用已有接口清单（复用，不新建）

| 已有能力 | 位置 | 用途 |
| --- | --- | --- |
| `GET /api/project/workbench` | `web/src/api/project.ts:getProjectWorkbench` | 所属项目下拉 |
| `MinioStorageService` | `server/src/platform/infrastructure/storage`（公共能力，只读依赖） | put / delete / presignedUrl |
| `DataMigrationPermissionService` | `server/src/modules/data-migration/.../service` | isAdmin / requireWrite / requireAdmin |
| `dm_asset` / `dm_operation_log` | V84 建表 | 主表 + 操作审计 |
| `ApiResponse` / `TraceId` | `server/src/shared/common`（只读） | 统一响应封装 |
| 前端 `UiToolbar / UiDataTable / UiFormDrawer / UiPagination / UiEmptyState / UiPageHeader` | `web/src/components/ui`（只读） | 列表、筛选、抽屉、分页、空态 |
| `delivery-showcase` 已验证设计 | `web/src/modules/delivery-showcase`（只读） | 移动卡片、状态、抽屉交互参照 |
| 菜单 721 + 动作权限 | V84 已建 | 汇报材料菜单与 read/create/update/delete 权限，无需新增 |

---

## 4. 前端设计

### 4.1 页面改造（重写 `ReportsPage.vue`，不再复用 AssetListView）

- 顶部 `UiPageHeader`：标题"汇报材料" + 主操作（单条上传、批量上传，按权限显示）。
- `UiToolbar` 筛选区：所属项目下拉（getProjectWorkbench）、汇报周期下拉（5 项）、关键字输入框（模糊匹配资料名称/关键字索引）、查询/重置/刷新。
- 列表区（桌面 `UiDataTable` / 移动卡片）列：所属项目（project_id 联表名）、汇报周期、资料名称（asset_name）、汇报日期、关键字索引（keywords）、操作（下载/编辑/删除）。
  - 下载：调用 download 接口取预签名 URL，浏览器打开。
  - 编辑：打开 `UiFormDrawer`，可重新上传最新源文件并修改全部元数据（projectId/period/reportName/reportDate/keywords）。
  - 删除：`ElMessageBox` 二次确认后调用逻辑删除接口（后端记录 deleted_by/deleted_at）。
- `UiPagination`：20/50/100 服务端分页；空/加载/失败/无权限/提交中状态复用现有模式。
- 回收站：**页面内 Tab**（仅 `data-migration:manage` / `system:admin` 可见）：恢复、确认清理（二次确认，提示"彻底销毁且不可恢复，但保留审计日志"）。

### 4.2 上传交互

- **单条上传**抽屉：全量字段（所属项目、汇报周期、资料名称、汇报日期、关键字索引、文件绑定），资料名称/关键字索引非空校验，提交后留存上传人/上传时间（owner_id/created_at 自动回填）。
- **批量上传**抽屉：
  1. 前置必选：所属项目 + 汇报周期（未选禁止选择文件）；
  2. 多文件选择后，前端读取每个文件名填充"资料名称"，不计算文件摘要；
  3. 提交创建多条记录；汇报日期、关键字索引为空，提示用户在列表中逐条"编辑"补充（服务端允许空，列表显示 `—`）。

### 4.3 权限展示

- 列表/下载：菜单 `data-migration:content:reports` 可见即可。
- 上传/批量上传按钮：`data-migration:content:reports:create` 或 `data-migration:write`/`manage`/`system:admin`。
- 编辑/删除：普通用户仅本人上传行可见按钮（前端按 `owner_id === auth.user.id` 隐藏，后端强校验），管理员全部可见。
- 回收站 Tab：仅 `data-migration:manage` / `system:admin`。

### 4.4 移动端

按 `design-h5.md` 验收 `375x812 / 390x844 / 430x932`：表格切换信息卡片（身份区=资料名称+汇报周期状态；事实区=所属项目/汇报日期/关键字索引；操作区=下载/编辑/删除分组），抽屉接近全宽、字段单列、操作区可达，不产生页面级横向滚动。

---

## 5. 测试与验证计划

- 后端：`mvn -pl :ccb-data-migration -am test`、`mvn test`（覆盖分页、组合筛选、owner 越权、管理员全量、回收站恢复/清理、asset_code 自动生成唯一性、旧对象删除）。
- 前端：`npm --prefix web run build`；桌面 1280x800 + 三个手机视口手工验收（筛选、单条/批量上传、编辑、下载、删除、回收站）。
- 治理：`node scripts/check-all-governance.mjs`、`check-codex-scope.mjs`（含 V89 范围联动）。
- 数据：V89 补列/补索引/存量初始化幂等验证（重复执行无副作用）；本地 MySQL + MinIO 启动（`MOCK_DATA_ENABLED=false`）。

## 6. 风险与回退

- 风险：存量 REPORT 行周期缺失（默认 IRREGULAR）、MinIO 对象与行删除一致性、通用资产页与汇报材料页同表数据展示差异、菜单权限回归。
- 回退：隐藏菜单 + 回退应用提交；新增列保留（无 DROP），存量初始化幂等可重复；需要清理时单独审批补偿任务（对齐 V3 回退规则）。

## 7. 决策确认记录（v2，已全部确认）

1. **asset_code 生成（T36 修订）**：服务端统一生成 `REPORT-<32 位小写无连字符 UUID>`，上传时写入，列表/详情/导出只读展示，编辑与文件替换不变更；不再采用时间戳+随机后缀。
2. **存量数据初始化**：已确认 → 存量 `REPORT` 行周期回填 `IRREGULAR`，日期/关键字留空（存量允许空、列表显示 `—`，录入/编辑时强校验必填）。
3. **数据互通范围**：已确认 → 汇报材料列表展示 `asset_type='REPORT'` 全部行（含通用接口上传的历史数据，空周期兜底 `IRREGULAR`）。
4. **通用上传联动**：已确认 → 通用 AssetController 对 REPORT 类型上传保持原样（不自动回填周期），最小改动。
