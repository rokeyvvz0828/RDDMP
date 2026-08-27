-- 数据迁移：汇报材料（数迁资产内容/汇报材料，菜单 721）字段补充。
-- 决策：复用通用资产表 dm_asset（asset_type='REPORT'），不新建独立表。
-- 1) 幂等补列（对齐 V84/V88 既有 information_schema 判断模式）：
--    report_period 汇报周期、report_date 汇报日期、keywords 关键字索引、
--    deleted_by 删除人、deleted_at 删除时间、created_by 创建人（上传人）、updated_by 更新人。
-- 2) 幂等补索引 idx_dm_asset_report（项目+周期+未删除+时间 组合筛选）。
-- 3) 幂等存量初始化：历史 asset_type='REPORT' 行周期回填 IRREGULAR（不定期汇报），
--    日期/关键字留空由用户编辑补充（存量允许空，录入/编辑时服务层强校验必填）。
-- 仅追加、不修改已发布脚本；本脚本为当前分支未发布迁移，幂等可重复执行。

-- 1) 幂等补列 --------------------------------------------------------------

SET @dm_asset_report_period_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'report_period'
);
SET @dm_asset_report_period_sql = IF(
    @dm_asset_report_period_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN report_period VARCHAR(16) NULL COMMENT ''汇报周期 DAILY/WEEKLY/BIWEEKLY/MONTHLY/IRREGULAR'' AFTER asset_type',
    'SELECT 1'
);
PREPARE dm_asset_report_period_stmt FROM @dm_asset_report_period_sql;
EXECUTE dm_asset_report_period_stmt;
DEALLOCATE PREPARE dm_asset_report_period_stmt;

SET @dm_asset_report_date_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'report_date'
);
SET @dm_asset_report_date_sql = IF(
    @dm_asset_report_date_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN report_date DATE NULL COMMENT ''汇报日期'' AFTER asset_name',
    'SELECT 1'
);
PREPARE dm_asset_report_date_stmt FROM @dm_asset_report_date_sql;
EXECUTE dm_asset_report_date_stmt;
DEALLOCATE PREPARE dm_asset_report_date_stmt;

SET @dm_asset_keywords_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'keywords'
);
SET @dm_asset_keywords_sql = IF(
    @dm_asset_keywords_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN keywords VARCHAR(500) NULL COMMENT ''关键字索引（英文逗号分隔）'' AFTER report_date',
    'SELECT 1'
);
PREPARE dm_asset_keywords_stmt FROM @dm_asset_keywords_sql;
EXECUTE dm_asset_keywords_stmt;
DEALLOCATE PREPARE dm_asset_keywords_stmt;

SET @dm_asset_deleted_by_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'deleted_by'
);
SET @dm_asset_deleted_by_sql = IF(
    @dm_asset_deleted_by_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN deleted_by BIGINT NULL COMMENT ''删除人'' AFTER deleted',
    'SELECT 1'
);
PREPARE dm_asset_deleted_by_stmt FROM @dm_asset_deleted_by_sql;
EXECUTE dm_asset_deleted_by_stmt;
DEALLOCATE PREPARE dm_asset_deleted_by_stmt;

SET @dm_asset_deleted_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'deleted_at'
);
SET @dm_asset_deleted_at_sql = IF(
    @dm_asset_deleted_at_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN deleted_at TIMESTAMP NULL COMMENT ''删除时间'' AFTER deleted_by',
    'SELECT 1'
);
PREPARE dm_asset_deleted_at_stmt FROM @dm_asset_deleted_at_sql;
EXECUTE dm_asset_deleted_at_stmt;
DEALLOCATE PREPARE dm_asset_deleted_at_stmt;

SET @dm_asset_created_by_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'created_by'
);
SET @dm_asset_created_by_sql = IF(
    @dm_asset_created_by_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN created_by BIGINT NULL COMMENT ''创建人（上传人，与 owner_id 同值）'' AFTER created_at',
    'SELECT 1'
);
PREPARE dm_asset_created_by_stmt FROM @dm_asset_created_by_sql;
EXECUTE dm_asset_created_by_stmt;
DEALLOCATE PREPARE dm_asset_created_by_stmt;

SET @dm_asset_updated_by_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND column_name = 'updated_by'
);
SET @dm_asset_updated_by_sql = IF(
    @dm_asset_updated_by_exists = 0,
    'ALTER TABLE dm_asset ADD COLUMN updated_by BIGINT NULL COMMENT ''更新人（最后编辑人）'' AFTER created_by',
    'SELECT 1'
);
PREPARE dm_asset_updated_by_stmt FROM @dm_asset_updated_by_sql;
EXECUTE dm_asset_updated_by_stmt;
DEALLOCATE PREPARE dm_asset_updated_by_stmt;

-- 2) 幂等补索引：汇报材料组合筛选（项目 + 周期 + 未删除 + 时间倒序）------------------

SET @dm_asset_report_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_asset' AND index_name = 'idx_dm_asset_report'
);
SET @dm_asset_report_index_sql = IF(
    @dm_asset_report_index_exists = 0,
    'ALTER TABLE dm_asset ADD KEY idx_dm_asset_report (tenant_id, project_id, report_period, deleted, updated_at)',
    'SELECT 1'
);
PREPARE dm_asset_report_index_stmt FROM @dm_asset_report_index_sql;
EXECUTE dm_asset_report_index_stmt;
DEALLOCATE PREPARE dm_asset_report_index_stmt;

-- 3) 幂等存量初始化：历史 REPORT 行周期回填 IRREGULAR ------------------------------

UPDATE dm_asset
SET report_period = 'IRREGULAR'
WHERE asset_type = 'REPORT'
  AND (report_period IS NULL OR report_period = '');
