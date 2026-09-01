# 数迁资产内容一菜单一表与公共关系表重组 — 设计基线

> 需求：`REQ-20260831-050`（status: ready，Owner：rokeyvvz0828）
> 控制前缀：`req-20260831-050-data-migration-content-table-split`
> 基线：V98 之后的数据库结构（见 `docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/database-schema-and-relations.md`）
> 日期：2026-08-31

## 1. 目标

1. 「数迁资产内容管理」11 个二级菜单各自拥有独立数据库表，替代 `dm_asset` + `asset_type` 分区的过渡模型。
2. 附件关联收敛为一张公共关系表 `dm_content_attachment`（统一收编 `dm_meeting_attachment`）。
3. 问题关联收敛为一张公共关系表 `dm_issue_relation`。
4. 存量数据无损迁移后物理删除 `dm_asset`、`dm_asset_relation`、`dm_meeting_attachment`。

Owner 已确认决策：**全部独立成表**、**公共附件表统一收编**、**旧表迁移后删除**。

## 2. 边界问题核实结论（2026-08-31 代码勘察）

| 问题 | 结论 | 处置 |
| --- | --- | --- |
| `INTERMEDIATE_TABLE` 是否有真实写入路径 | **有**：`views/base/IntermediateTablesPage.vue` 通过 `StructuredListView structured-type="INTERMEDIATE_TABLE"` 走 `StructuredAssetService` 写 `dm_asset`（与 031 文档 7.2 的描述不一致，以代码为准） | 新增 `dm_intermediate_table` 结构化表承载 |
| `TABLE_STRUCTURE` 是否有真实写入路径 | **无**：前端无任何页面使用该类型，仅 `StructuredAssetService.TYPES` 与 `hasRelation` JSON 互查引用 | V100 断言行数为 0；非 0 则迁移失败并转人工决策 |
| `dm_asset_relation` 中 MEETING→SYSTEM 行的归宿 | 会议关联物理子系统（`MeetingService` L405-413 写入、L355-360 过滤），与问题无关，无法锚定问题 | 新增小型专属表 `dm_meeting_system` 承载，不占用公共问题关系表 |
| 问题↔会议是否双向写两份 | **是**：`IssueService` 写 `ISSUE→MEETING`（L182-184），`MeetingService` 写 `MEETING→ISSUE`（L443-451），各自维护 | `dm_issue_relation` 归一为单行（`issue_id + related_type=MEETING`），V100 用 `INSERT IGNORE` 去重；两侧同步逻辑统一对该表读写（见 5.3 语义变化说明） |

## 3. 表结构设计（V99，共 13 张）

### 3.1 六张文件型内容表

`dm_plan`（迁移方案）、`dm_mapping_doc`（迁移映射）、`dm_dependency`（迁移过程依赖文件）、`dm_script`（迁移程序）、`dm_topic`（专题材料）、`dm_release_drill`（投产及演练），结构一致：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键，迁移保留原 `dm_asset.id` |
| `tenant_id` | BIGINT NOT NULL | 租户 |
| `project_id` | BIGINT NOT NULL | 所属项目 |
| `component_id` | BIGINT NULL | 所属组件 |
| `doc_code` | VARCHAR(96) NOT NULL | 编号（原 `asset_code`），项目内唯一 |
| `doc_name` | VARCHAR(255) NOT NULL | 名称（原 `asset_name`） |
| `checksum_md5` | CHAR(32) NULL | MD5 查重 |
| `owner_id` | BIGINT NOT NULL | 负责人 |
| `deleted` / `deleted_by` / `deleted_at` | 软删三件套 | 回收站 |
| `created_by` / `created_at` / `updated_by` / `updated_at` | 审计 | |
| `active_code` | 生成列 | `IF(deleted=0, doc_code, NULL)`，对齐 V94 先例 |

- 唯一键：`uk(tenant_id, project_id, active_code)`。
- 索引：`(tenant_id, project_id, deleted, updated_at)`、`(tenant_id, checksum_md5, deleted)`。
- **不再保留 `attachment_id`/`content_type`/`file_size`**：主文件与附加文件一律落 `dm_content_attachment`（主文件 `sort_order=0`），展示元数据经 `att_file` JOIN 投影（`att_file` 已含 `file_name`/`content_type`/`file_size`，V36）。

### 3.2 `dm_report`（汇报材料）

上述字段 + `report_period` VARCHAR(16) NULL、`report_date` DATE NULL、`keywords` VARCHAR(500) NULL；追加索引 `(tenant_id, project_id, report_period, deleted)`。

### 3.3 `dm_rule` / `dm_parameter`（检核规则 / 迁移参数）

六张文件表字段去掉 `checksum_md5`，增加 `structured_data` JSON NOT NULL（主体数据）。唯一键与索引同上（不含 MD5 索引）。

### 3.4 `dm_intermediate_table`（中间表结构化资产）

结构同 `dm_rule`/`dm_parameter`，承载现 `dm_asset(asset_type='INTERMEDIATE_TABLE')` 存量与「中间表结构」菜单后续写入。

### 3.5 `dm_content_attachment`（公共附件关系表）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `tenant_id` | BIGINT NOT NULL | 租户 |
| `business_type` | VARCHAR(32) NOT NULL | PLAN/MAPPING_DOC/DEPENDENCY/SCRIPT/TOPIC/RELEASE_DRILL/REPORT/MEETING |
| `business_id` | BIGINT NOT NULL | 业务实体 ID（资产/会议） |
| `attachment_id` | BIGINT NOT NULL | 关联 `att_file.id` |
| `file_name` | VARCHAR(500) NOT NULL | 原始文件名 |
| `sort_order` | INT NOT NULL DEFAULT 0 | 主文件/首附件为 0 |
| `deleted` / `deleted_by` / `deleted_at` | 软删三件套 | 附件级回收站 |
| `created_by` / `created_at` | 审计 | |
| `active_attachment_key` | 生成列 | `IF(deleted=0, CONCAT(business_type,':',tenant_id,':',business_id,':',attachment_id), NULL)`，对齐 V98 对 `dm_meeting_attachment` 的活动唯一键改造 |

- 唯一键：`uk(tenant_id, active_attachment_key)`；索引：`(tenant_id, business_type, business_id, deleted, sort_order)`、`(tenant_id, attachment_id, deleted)`。
- `att_file` 侧绑定不变：文件型资产沿用 `DATA_MIGRATION_ASSET` + businessKey=资产 id；会议沿用 `DATA_MIGRATION_MEETING`。存量绑定因 id 保留而继续有效。

### 3.6 `dm_issue_relation`（公共问题关系表）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `tenant_id` | BIGINT NOT NULL | 租户 |
| `issue_id` | BIGINT NOT NULL | 关联 `dm_issue.id` |
| `related_type` | VARCHAR(32) NOT NULL | MEETING / TABLE / FIELD |
| `related_id` | BIGINT NOT NULL | 对应 `dm_meeting.meeting_id` / `dm_target_table.id` / `dm_target_table_field.id` |
| `created_by` / `created_at` | 审计 | 硬删全量重插，无软删 |

- 唯一键：`uk(tenant_id, issue_id, related_type, related_id)`；反查索引：`(tenant_id, related_type, related_id)`。
- MEETING→ISSUE 行反转后与 ISSUE→MEETING 行同构，`INSERT IGNORE` 去重合并。

### 3.7 `dm_meeting_system`（会议-系统关联表）

`id`、`tenant_id`、`meeting_id`、`subsystem_id`（对应 `arch_physical_subsystem.id`）、`created_by`、`created_at`；`uk(tenant_id, meeting_id, subsystem_id)`、索引 `(tenant_id, subsystem_id)`。硬删全量重插语义与现状一致。

## 4. V100 数据搬迁映射

1. `dm_asset` 按 `asset_type` 分流：PLAN→dm_plan、MAPPING_DOC→dm_mapping_doc、DEPENDENCY→dm_dependency、SCRIPT→dm_script、TOPIC→dm_topic、RELEASE_DRILL→dm_release_drill、REPORT→dm_report、RULE→dm_rule、PARAMETER→dm_parameter、INTERMEDIATE_TABLE→dm_intermediate_table。**保留原 id**，`asset_code/asset_name` 映射 `doc_code/doc_name`，软删与审计字段原样迁移。
2. `dm_asset.attachment_id IS NOT NULL` 的行 → `dm_content_attachment`（`sort_order=0`，`file_name` 取 `att_file.file_name`），`business_type` 与目标表对应。
3. `dm_meeting_attachment` 全量 → `dm_content_attachment`（`business_type='MEETING'`，id/sort_order/软删状态原样）。
4. `dm_asset_relation` 分流：
   - `source='ISSUE' AND target_type IN ('MEETING','TABLE','FIELD')` → `dm_issue_relation(issue_id=source_id, related_type=target_type, related_id=target_id)`；
   - `source='MEETING' AND target_type='ISSUE'` → 反转为 `(issue_id=target_id, related_type='MEETING', related_id=source_id)`，`INSERT IGNORE` 与上条去重；
   - `source='MEETING' AND target_type='SYSTEM'` → `dm_meeting_system(meeting_id=source_id, subsystem_id=target_id)`；
   - 其余组合（含 source/target 指向 `dm_asset` 的历史行）：**断言为 0**，非 0 用 `SIGNAL` 使迁移失败，转人工决策。
5. `asset_type IN ('TABLE_STRUCTURE','TRANSFORM_DOC','CONFIG','VALIDATION_DOC','OTHER','MEETING')` 及其他未登记类型：断言行数为 0（当前仅本地 1 行存量且属已登记类型），非 0 迁移失败。

## 5. 后端切换设计

### 5.1 服务划分（`server/src/modules/data-migration`，JdbcTemplate 风格保持）

- **`ContentFileAssetService`**（新增）：表名/类型参数化，承接 `AssetService` 的 upsert（按 project+code）、主文件替换、下载、软删/恢复/彻底删除（purge 时经 `AttachmentGateway.deleteBound` 解绑并删 `dm_content_attachment` 行）、MD5 查重（跨 6 张文件表）。`AssetService` 删除。
- **`ReportService`**：SQL 改指 `dm_report`，附件经 `dm_content_attachment`；端点路径不变。
- **`StructuredAssetService`**：类型收窄为 RULE→`dm_rule`、PARAMETER→`dm_parameter`、INTERMEDIATE_TABLE→`dm_intermediate_table`，移除 TABLE_STRUCTURE；`hasRelation` 仅跨这三张表做 JSON 互查。
- **`ContentAttachmentService`**（新增）：`business_type` 参数化，承接 `MeetingService` L310-746 的附件绑定/解绑/排序/附件回收站逻辑；会议改调它，文件型资产主文件也经它落 `sort_order=0` 行。
- **`IssueService`/`MeetingService`**：关联读写改指 `dm_issue_relation`；会议-系统改指 `dm_meeting_system`；字符串字面量收敛为常量。
- **`ExcelService`**：结构化导入导出按类型指向新表。
- **`DashboardService`/`DashboardSnapshotScheduler`**：`ASSET_TOTAL` 与 `byType` 改为跨内容表 UNION 计数（类型标签保持原 `asset_type` 值，看板语义不变）。
- **`ProjectComponentService`**：组件删除前的资产占用检查改为跨内容表。
- **`DataMigrationAssetAttachmentAccessPolicy`**：按 `business_type` 跨内容表定位 `owner_id`/`deleted`；会议策略不变。
- 审计：`dm_operation_log` 的 `entity_type` 沿用 `'ASSET'`/`'MEETING'` 等现值，历史审计可读性不变。

### 5.2 API 契约（破坏性变更，前后端同版本发布）

- 新增资源端点（`/api/data-migration` 前缀）：`/plans`、`/mappings`、`/dependencies`、`/programs`、`/topics`、`/release-drills`（列表/上传/下载/删除），`/rules`、`/parameters`（列表/保存/更新/删除/导入导出）。
- 移除：`/assets/{type}/upload`、`/assets`、`/assets/check-md5`、`/assets/{id}/download`、`/assets/delete`。
- 保留：`/reports/**`、`/meetings/**`、`/issues/**`、`/target-tables/**`、`/components/**`、`/dashboard/**`。
- **统一回收站**：`/recycle-bin`（GET 聚合全部内容类型，带 `contentTypes` 筛选）、`/recycle-bin/restore`、`/recycle-bin/purge`（按类型分发，管理员权限，写审计）；会议附件级回收站端点保留在 `/meetings` 下。
- `check-md5` 能力并入各上传端点前置校验：`/content/check-md5`（跨 6 张文件表 + `dm_report`）。

### 5.3 语义变化（需写入完成报告与发布说明）

- 问题↔会议关联从双向各存一份归一为单行：问题侧解除关联会同步反映在会议侧，反之亦然（现状两侧独立维护，可能出现单侧悬挂）。
- 文件型资产的附件元数据（文件名/大小/类型）来源从内容表列改为 `att_file` 投影；上传替换主文件变为"替换 `sort_order=0` 行"。
- 回收站从"前端仅汇报材料、后端能力分散"升级为全内容类型聚合。

## 6. 前端切换设计

- `api/data-migration.ts`：新增按资源函数与类型定义；移除通用资产函数；回收站函数改聚合契约。
- `components/AssetListView.vue`：内置 type→endpoint 映射（6 个薄包装页不动），上传链路（前端算 MD5 → 查重 → 公共附件上传 → 业务保存）不变。
- `components/StructuredListView.vue`：RULE/PARAMETER/INTERMEDIATE_TABLE 各自切新端点（注意 `IntermediateTablesPage` 共用）。
- `views/content/RecycleBinPage.vue`：接统一回收站端点，按内容类型分组展示。
- `views/content/MeetingsPage.vue`：附件端点形态不变则零改动。
- 页面全状态（加载/空/失败/无权限/提交中）沿用现有实现，不新增样式。

## 7. 发布与回退

| 发布 | 内容 | 回退 |
| --- | --- | --- |
| 发布 A | V99 + V100 + 后端前端切换 | 回滚应用版本即可（V99/V100 纯追加，旧表旧行仍在，但注意发布 A 的应用代码已不读旧表，回滚须整体回滚前后端） |
| 发布 B（观察期后） | V101 删旧表 | 执行前强制全量备份；回退=恢复备份+回滚应用 |

- V101 内容：断言应用侧无旧表写入（代码已无引用）→ 删除 `dm_asset`/`dm_asset_relation`/`dm_meeting_attachment` 残留行 → `DROP TABLE`。
- Flyway 只追加，不提供反向脚本；回退边界在需求文档与任务范围中已声明。

## 8. 测试策略

- `ContentAssetMigrationMySqlTest`（Testcontainers mysql:8.4，复制 `IssueMigrationMySqlTest` 模式）：V98 基线构造各 `asset_type` 行、会议附件、三类关系行 → V99/V100 → 断言行数、id 保留、排序/软删保留、关系映射与去重、断言保护触发（构造未登记类型行验证迁移失败）。
- 服务层单测沿用 `StubJdbcTemplate` 模式；注册测试更新文件断言。
- 全量：`mvn test`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、范围检查、本地运行时 + 浏览器验收。

## 9. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| 搬迁丢 id 导致 `att_file` 绑定失效 | V100 强制保留原 id + 迁移测试断言 |
| 多态行映射遗漏 | 未登记组合断言为 0，非 0 `SIGNAL` 失败 |
| MD5 跨表查重性能 | 单租户数据量小，逐表 UNION 计数可接受；保留各表 `(tenant_id, checksum_md5, deleted)` 索引 |
| `dm_operation_log` 历史 `entity_id` 解释 | id 保留 + `entity_type` 不变，历史可追溯 |
| 031 未提交改动与本需求改动混杂 | 本需求所有改动在独立分支 `feat/REQ-20260831-050-data-migration-content-table-split` 提交；合入前与 031 遗留改动分离 |

本设计不接触生产数据，全部测试数据虚构；平台模块零改动，仅复用 `com.ccb.attachment.integration` 公开能力。
