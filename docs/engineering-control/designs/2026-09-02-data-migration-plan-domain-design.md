# 数据迁移「迁移方案」域化改造（对标会议纪要）设计

## 状态与来源
- 设计修订：1
- 需求：REQ-20260820-031（增量，用户确认并入当前 031 范围）
- 批准：用户在 AskUserQuestion 中确认四项决策（一条方案挂多文件 / 关联系统复用会议同款 / dm_plan 确认为空不回填 / 并入 031 范围）
- 触发：参考「会议纪要」实现，把「迁移方案」从通用文件型资产链路升级为专属域功能
- 账本：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/` execution-T30 / observation-T30

## 目标
将「数据迁移 › 数迁资产内容 › 迁移方案」从通用 `ContentTypeFileAssetService` 文件链路中剥离，落地为专属服务 + 专属控制器 + 专属回收站来源，支持：所属项目 / 资产颗粒度（项目级、系统级）/ 迁移方案类型（业务、数据）/ 关联系统 的多维组合检索、方案名称关键字模糊搜索、单条录入与批量上传（多文件归入同一方案）、编辑（重传/追加源文件）、下载、在线预览、逻辑删除与统一回收站，并保留操作审计与全量权限管控。

## 方案范式
完全对标 `ReportService`/`ReportController`/`ReportRecycleBinSource`（文件型内容从通用链路提升为专属域的先例）与 `MeetingController` 的多附件范式。

## 数据库设计（V168__data_migration_plan_domain.sql，追加式）
`dm_plan` 表（V162 建表，本次域化；核实为空表，不回填）新增列：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `granularity` | VARCHAR(16) NOT NULL DEFAULT 'PROJECT' | 资产颗粒度：PROJECT/SYSTEM |
| `plan_type` | VARCHAR(16) NOT NULL DEFAULT 'DATA' | 迁移方案类型：BUSINESS/DATA |
| `system_id` | BIGINT NOT NULL DEFAULT 0 | 关联系统；项目级用 0 哨兵 |
| `plan_summary` | VARCHAR(1000) NULL | 方案简介 |
| `active_dimension_key` | VARCHAR(160) GENERATED STORED | `CASE WHEN deleted=0 THEN CONCAT_WS(':',tenant_id,project_id,granularity,plan_type,system_id) ELSE NULL END` |

约束与索引：
- `UNIQUE KEY uk_dm_plan_active_dimension (active_dimension_key)`：实现「(项目+颗粒度+方案类型+关联系统) 活动域仅一条」；软删行 `active_dimension_key` 为 NULL，MySQL 唯一索引对 NULL 判不同，故回收站中可并存、恢复时若冲突由服务端翻译为 CONFLICT。
- `idx_dm_plan_dimension (tenant_id, project_id, granularity, plan_type, deleted)`、`idx_dm_plan_system (tenant_id, system_id, deleted)`：组合筛选。
- 逻辑删除字段（`deleted/deleted_by/deleted_at`）沿用 V162 既有列。
- 多文件绑定沿用公共附件表 `dm_content_attachment`（`business_type='PLAN'`，`sort_order=0` 为主文件），首文件 MD5 记入 `dm_plan.checksum_md5`；平台侧附件绑定域沿用 `DATA_MIGRATION_ASSET`，令 `DataMigrationAssetAttachmentAccessPolicy` 跨表定位 owner 生效。
- 操作审计沿用 `dm_operation_log`（V84）：`entity_type='PLAN'`，operation_code ∈ PLAN_UPLOAD/PLAN_UPDATE/PLAN_DELETE/PLAN_RESTORE/PLAN_PURGE，记录 actor_id。
- `dm_plan` 保留在 `ContentAssetTables.FILE_TABLES`，故 `check-md5` 全域去重与看板计数不受本次 `MANAGED_TYPES` 移除影响。

## 接口清单
### 复用（已有，不改动）
- `POST /api/attachments`（上传，返回 TEMP 附件 ID）、`GET /api/attachments/{id}/download`（附件下载）。
- 文件预览：`GET /api/file-preview/capabilities`、`POST /api/file-preview/upload`。
- `GET /api/project/workbench`（所属项目下拉）、`dm_component ⋈ arch_physical_subsystem`（关联系统下拉，与会议同款 SQL）。
- 统一回收站：`GET /api/data-migration/recycle-bin`、`/restore`、`/purge`（经 `RecycleBinSource` SPI 按 `type` 分发）。
- 前端 `computeFileMd5`、`uploadAttachment`、`getAttachmentDownload`。

### 新建（专属，前缀 `/api/data-migration/plans`，Bean 名 `dataMigrationPlanController`）
- `GET /plans`：projectId/granularity/planType/systemId/keyword/page/size 组合分页；关键字仅命中 `doc_name`。
- `GET /plans/options/systems`：按项目取关联系统。
- `GET /plans/{id}`：详情含多附件。
- `POST /plans`：新增（全量元数据 + `files[]` 多文件）。
- `PUT /plans/{id}`：编辑（元数据全量可改；`files[]` 全量重设附件集合，已绑定文件不强制 MD5）。
- `DELETE /plans`（body `ids[]`）：批量逻辑删除。
- `GET /plans/{id}/download`：主文件下载路径。
- `GET /plans/{id}/attachments`：附件列表。
- 权限：类级 `data-migration:content:plans|access|write|manage|system:admin`；写/删动作级 `data-migration:content:plans:create|update|delete` 回退 `write/manage/system:admin`。

## 前后端契约要点
- 批量 vs 单条冲突以「一条方案挂多文件」化解：批量所选多文件绑定为同一条方案的多个 `dm_content_attachment`；`plan_name` 留空由后端取首文件名去扩展名。
- 前端 `PlansPage.vue` 由 12 行 `AssetListView` 薄壳重写为专属页（对标 `MeetingsPage.vue`）：5 维筛选、分页列表（方案名称点击查看）、新增/编辑抽屉（归属维度→方案信息→源文件，系统级联动必填关联系统）、下载/预览、删除入统一回收站；桌面 1280×800 + 移动 375×812 视口。

## 验证策略
1. `mvn -pl :ccb-data-migration test -Dtest=PlanDomainMigrationMySqlTest,DataMigrationModuleRegistrationTest`：V168 迁移 + 活动维度唯一 + 注册路由断言。
2. `npm --prefix web run build`：vue-tsc + Vite 构建。
3. `node scripts/check-all-governance.mjs`：治理与范围。
4. 运行取证：重启后端使 Flyway 应用 V168；`/api/data-migration/plans` 未认证 401、认证后 200、`/plans/999` 400、`recycle-bin?contentTypes=PLAN` 200。
5. 真实浏览器（通过 `E2E_ADMIN_PASSWORD` 注入测试凭据）：列表/筛选/新增抽屉/系统级联动/多文件/移动视口。

## 风险与回退
- 风险：Bean 名与 architecture 模块 `PlanController` 冲突（已以显式 Bean 名解决）；环境缺少系统主数据与迁移方案演示数据，「有数据行」的下载/预览/编辑回填/回收站恢复本轮为盲区，需灌演示数据后复验；本轮浏览器截图受内置视图隐藏限制未产出。
- 回退：回退本次代码提交；V168 为追加式迁移，历史脚本不受影响；`MANAGED_TYPES` 与 `RESOURCE_TYPES` 的 PLAN 移除需与 `PlanRecycleBinSource` 认领同批回退以避免重复认领。
