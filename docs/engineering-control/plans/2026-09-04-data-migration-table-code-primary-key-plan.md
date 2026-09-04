# 2026-09-04 数据迁移目标表主键收敛为业务编号 实施计划

> 需求：REQ-20260820-031 增量（T41）。用户决策（2026-09-04）：
> 1. `dm_target_table` 不使用 `id`，改用 `table_code` 作为主键；编号用原 `id` 的生成方式（`System.currentTimeMillis()*1000 + 随机` 的纯数字），不考虑租户分区（编号全局唯一、单列主键）；覆盖此前"8 位纯数字"要求。
> 2. 唯一范围：编号全局唯一（不做租户内分区）。
> 3. 关联键联动：`dm_target_table_field`、`dm_issue_relation` 一并改为 `table_code`/`field_code` 关联。
> 4. 字段表主键一并修改：`dm_target_table_field` 主键改为 `field_code`。
> 5. 审计历史数据不追溯（`entity_id` 值=原 id=新 code 数值，天然兼容，无需回填）。
> 开发阶段免备份（用户此前确认）。

## 目标状态

- `dm_target_table`：无 `id` 列；主键 `table_code BIGINT`；保留 `uk_target_table_tenant_code (tenant_id, table_code)`（组合外键引用目标）、`active_table_code` 生成列（BIGINT）+ `uk_target_table_active_code (tenant_id, active_table_code)`、V172 其余生成列/uk 不变。
- `dm_target_table_field`：无 `id` 列；主键 `field_code BIGINT`；`table_id` 列改名为 `table_code BIGINT`（值=旧 table_id=主表旧 id=主表新 table_code，零回填）；外键改为 `(tenant_id, table_code) REFERENCES dm_target_table`；`active_field_code` 生成列改 BIGINT + uk 重建。
- `dm_issue_relation`：`related_id BIGINT` 结构不变；TABLE/FIELD 关联值=原 id 数值=新 code 数值，数据不变；JOIN 条件由 `t.id = r.related_id` 改为 `t.table_code = r.related_id`、`f.field_code = r.related_id`。
- 审计：`entity_type='TARGET_TABLE'/'TARGET_FIELD'` 的 `entity_id` 继续存编号数值（=原 id 值），语义随主键变化，无历史回填。
- 编号生成：`TargetTableService` 不再拼 `"TT"+id` / `"TF"+id`，直接以 `nextId()` 数值作为 `table_code` / `field_code`。

## 迁移（V177，information_schema 条件式、幂等、只追加）

文件：`server/src/platform/infrastructure/src/main/resources/db/migration/V177__data_migration_target_table_code_pk.sql`

主表（`id` 列存在时执行转换段）：
1. `DROP KEY uk_target_table_active_code` → `DROP COLUMN active_table_code`（基列类型变化前先释放依赖）
2. `UPDATE dm_target_table SET table_code = id`（覆盖历史 "TT"+id / asset_code 值）
3. `MODIFY table_code BIGINT NOT NULL`；`MODIFY id BIGINT NOT NULL`（去 AUTO_INCREMENT）
4. `DROP PRIMARY KEY` → `ADD PRIMARY KEY (table_code)` → `DROP COLUMN id`
5. `ADD COLUMN active_table_code BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN table_code ELSE NULL END) STORED`
6. `ADD UNIQUE uk_target_table_active_code (tenant_id, active_table_code)`；`ADD UNIQUE uk_target_table_tenant_code (tenant_id, table_code)`

字段表（`id` 列存在时执行转换段，且先于主表删除外键引用）：
1. `DROP FOREIGN KEY fk_target_field_table`
2. `DROP KEY uk_target_field_active_code` → `DROP COLUMN active_field_code`
3. `UPDATE dm_target_table_field SET field_code = id`
4. `MODIFY field_code BIGINT NOT NULL`；`MODIFY id BIGINT NOT NULL`
5. `DROP PRIMARY KEY` → `ADD PRIMARY KEY (field_code)` → `DROP COLUMN id`
6. `CHANGE COLUMN table_id table_code BIGINT NOT NULL`（值=旧 table_id）
7. `ADD COLUMN active_field_code BIGINT GENERATED ... STORED`；`ADD UNIQUE uk_target_field_active_code`
8. `ADD CONSTRAINT fk_target_field_table_code FOREIGN KEY (tenant_id, table_code) REFERENCES dm_target_table(tenant_id, table_code)`

数据零改写：主表 `table_code=旧id`、字段表 `field_code=旧id`、字段表 `table_code=旧table_id`、`dm_issue_relation.related_id` 与审计 `entity_id` 均不变。

## 服务端（server/src/modules/data-migration）

- `TargetTableService`：39 处 `tableId/table_id` → `tableCode/table_code`；`createTable` 不再拼 `"TT"+id`、`addFieldInternal` 不再拼 `"TF"+id`，直接用 `nextId()` 数值；`importTables` 表→字段分组改按 code；`getTargetTableOptions/getTargetFieldOptions` value 返回 code。
- `IssueService`：`ensureRelationTarget` TABLE 分支 `WHERE table_code=?`、FIELD 分支 `WHERE f.table_code=?`；`ensureFieldRelationsBelongToRelatedTables` `rt.related_id = f.table_code`；`baseSelect` 关联名称子查询改 `t.table_code = r.related_id`、`f.field_code = r.related_id`。
- `TargetTableController`：路径/查询参数 `{id}`/`tableId` → `{tableCode}`/`tableCode`。
- `MeetingService`：不涉及（issue_relation 仅 MEETING 分支）。

## 前端（web/src）

- `web/src/api/data-migration.ts`：`table_id: number` → `table_code: number`；`getIssueTargetFieldOptions(tableId)` → `(tableCode)`；目标表相关 API 参数改 `tableCode`。
- `TargetTablesPage.vue` / `IssuesPage.vue`：详情/编辑/字段操作/关联下传参数与选项 value 改用 code；展示即 `table_code`（数字）与 `field_code`。

## 测试

- `DataMigrationGovernanceRemediationMySqlTest`：
  - 既有服务层 fixture（v166ServiceLayer/v176）的 `dm_target_table`/`dm_target_table_field` 改为 V177 结构（`table_code`/`field_code` 主键）。
  - 新增 `v168ConvertsTargetTablePrimaryKeyToCode`：V176 基线（id 结构）→ migrate 167→168 → 断言主键/外键/生成列/索引、回填值、issue_relation 关联 JOIN、服务写侧 code（createTable/addField）、审计 entity_id。
- `IssueMigrationMySqlTest`：夹具 `dm_target_table` 主键同步（按需）。
- `IssueServiceTest`（mock）：`queryForObject` 计数按 `FROM dm_target_table(_field)` 匹配，列名变化不影响计数；按仓库当前断言核对。
- 回归：`mvn -pl :ccb-data-migration -am test`；`npm --prefix web run build`。

## 文档与账本

- `database-schema-and-relations.md`：`dm_target_table`/`dm_target_table_field` 列清单、主键/索引/外键、`dm_issue_relation` 关联说明、ER 图、表清单；基线 V176→V177。
- `codex-task-scope.yaml`：登记 V177（writable_paths/migration_files/compatibility/note）。
- 新账本 `execution-T41.json`、`observation-T41.json`。

## 验证命令与证据

1. `mvn -q -pl :ccb-data-migration -am test -Dtest=DataMigrationGovernanceRemediationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false`
2. `mvn -q -pl :ccb-data-migration -am test`
3. `npm --prefix web run build`
4. `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree`
5. 不提交、不推送、不合并。
