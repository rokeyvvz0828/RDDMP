-- V194: 迁移检核规则专属域优化（并入 REQ-20260820-031，对标迁移方案/迁移映射域化范式）
-- 背景：dm_rule 原为通用结构化资产薄表（doc_code/doc_name/structured_data），只支持“资产编码/名称”这类通用信封，
--   不满足「迁移检核规则」的多维维度（检核目标类型、检核规则大类、关联系统）与规则字段（规则编码、编码说明、
--   检核规则说明、表/字段英中名）。
-- 用户确认：doc_code/doc_name 不使用并删除；缺少的字段重建；软删向回收站继续以「规则编码」充当统一回收站资产编号。
-- 存量：本地开发库 dm_rule 为 0 行，本脚本在删除旧列前断言表为空，非空时以 SIGNAL 失败，禁止静默丢弃数据。
-- 约束：Flyway 只追加，不改历史脚本；information_schema 条件式执行，幂等可重跑。

-- 1. 断言 dm_rule 为空，避免删除 doc_code/doc_name 后新 NOT NULL 字段落成脏默认值
DELIMITER $$
CREATE PROCEDURE dm_v194_assert_rule_empty()
BEGIN
    DECLARE row_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO row_count FROM dm_rule;
    IF row_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V194 失败：dm_rule 存在存量行，未确认数据重建前禁止删列';
    END IF;
END$$
DELIMITER ;

CALL dm_v194_assert_rule_empty();
DROP PROCEDURE dm_v194_assert_rule_empty;

-- 2. 删除旧通用信封键与旧列（doc_code/doc_name/structured_data）
SET @dm_rule_drop_uk_code_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND index_name = 'uk_dm_rule_code');
SET @dm_rule_drop_uk_code_sql = IF(
    @dm_rule_drop_uk_code_exists > 0,
    'ALTER TABLE dm_rule DROP INDEX uk_dm_rule_code',
    'SELECT 1');
PREPARE dm_rule_drop_uk_code_stmt FROM @dm_rule_drop_uk_code_sql;
EXECUTE dm_rule_drop_uk_code_stmt;
DEALLOCATE PREPARE dm_rule_drop_uk_code_stmt;

SET @dm_rule_drop_query_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND index_name = 'idx_dm_rule_query');
SET @dm_rule_drop_query_idx_sql = IF(
    @dm_rule_drop_query_idx_exists > 0,
    'ALTER TABLE dm_rule DROP INDEX idx_dm_rule_query',
    'SELECT 1');
PREPARE dm_rule_drop_query_idx_stmt FROM @dm_rule_drop_query_idx_sql;
EXECUTE dm_rule_drop_query_idx_stmt;
DEALLOCATE PREPARE dm_rule_drop_query_idx_stmt;

SET @dm_rule_drop_doc_code = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'doc_code');
SET @dm_rule_drop_doc_code_sql = IF(
    @dm_rule_drop_doc_code > 0,
    'ALTER TABLE dm_rule DROP COLUMN doc_code',
    'SELECT 1');
PREPARE dm_rule_drop_doc_code_stmt FROM @dm_rule_drop_doc_code_sql;
EXECUTE dm_rule_drop_doc_code_stmt;
DEALLOCATE PREPARE dm_rule_drop_doc_code_stmt;

SET @dm_rule_drop_doc_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'doc_name');
SET @dm_rule_drop_doc_name_sql = IF(
    @dm_rule_drop_doc_name > 0,
    'ALTER TABLE dm_rule DROP COLUMN doc_name',
    'SELECT 1');
PREPARE dm_rule_drop_doc_name_stmt FROM @dm_rule_drop_doc_name_sql;
EXECUTE dm_rule_drop_doc_name_stmt;
DEALLOCATE PREPARE dm_rule_drop_doc_name_stmt;

SET @dm_rule_drop_structured_data = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'structured_data');
SET @dm_rule_drop_structured_data_sql = IF(
    @dm_rule_drop_structured_data > 0,
    'ALTER TABLE dm_rule DROP COLUMN structured_data',
    'SELECT 1');
PREPARE dm_rule_drop_structured_data_stmt FROM @dm_rule_drop_structured_data_sql;
EXECUTE dm_rule_drop_structured_data_stmt;
DEALLOCATE PREPARE dm_rule_drop_structured_data_stmt;

-- 3. 重建缺失字段（幂等：列不存在才加）
SET @dm_rule_add_check_target_type = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'check_target_type');
SET @dm_rule_add_check_target_type_sql = IF(
    @dm_rule_add_check_target_type = 0,
    'ALTER TABLE dm_rule ADD COLUMN check_target_type VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''检核目标类型码值（DM_RULE_TARGET_TYPE）'' AFTER system_code',
    'SELECT 1');
PREPARE dm_rule_add_check_target_type_stmt FROM @dm_rule_add_check_target_type_sql;
EXECUTE dm_rule_add_check_target_type_stmt;
DEALLOCATE PREPARE dm_rule_add_check_target_type_stmt;

SET @dm_rule_add_rule_category = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'rule_category');
SET @dm_rule_add_rule_category_sql = IF(
    @dm_rule_add_rule_category = 0,
    'ALTER TABLE dm_rule ADD COLUMN rule_category VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''检核规则大类码值（DM_RULE_CATEGORY）'' AFTER check_target_type',
    'SELECT 1');
PREPARE dm_rule_add_rule_category_stmt FROM @dm_rule_add_rule_category_sql;
EXECUTE dm_rule_add_rule_category_stmt;
DEALLOCATE PREPARE dm_rule_add_rule_category_stmt;

SET @dm_rule_add_rule_code = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'rule_code');
SET @dm_rule_add_rule_code_sql = IF(
    @dm_rule_add_rule_code = 0,
    'ALTER TABLE dm_rule ADD COLUMN rule_code VARCHAR(96) NOT NULL DEFAULT '''' COMMENT ''规则编码（全局唯一，用户录入）'' AFTER rule_category',
    'SELECT 1');
PREPARE dm_rule_add_rule_code_stmt FROM @dm_rule_add_rule_code_sql;
EXECUTE dm_rule_add_rule_code_stmt;
DEALLOCATE PREPARE dm_rule_add_rule_code_stmt;

SET @dm_rule_add_rule_code_desc = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'rule_code_desc');
SET @dm_rule_add_rule_code_desc_sql = IF(
    @dm_rule_add_rule_code_desc = 0,
    'ALTER TABLE dm_rule ADD COLUMN rule_code_desc VARCHAR(500) NULL COMMENT ''规则编码说明'' AFTER rule_code',
    'SELECT 1');
PREPARE dm_rule_add_rule_code_desc_stmt FROM @dm_rule_add_rule_code_desc_sql;
EXECUTE dm_rule_add_rule_code_desc_stmt;
DEALLOCATE PREPARE dm_rule_add_rule_code_desc_stmt;

SET @dm_rule_add_rule_description = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'rule_description');
SET @dm_rule_add_rule_description_sql = IF(
    @dm_rule_add_rule_description = 0,
    'ALTER TABLE dm_rule ADD COLUMN rule_description TEXT NULL COMMENT ''检核规则说明'' AFTER rule_code_desc',
    'SELECT 1');
PREPARE dm_rule_add_rule_description_stmt FROM @dm_rule_add_rule_description_sql;
EXECUTE dm_rule_add_rule_description_stmt;
DEALLOCATE PREPARE dm_rule_add_rule_description_stmt;

SET @dm_rule_add_table_name_en = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'table_name_en');
SET @dm_rule_add_table_name_en_sql = IF(
    @dm_rule_add_table_name_en = 0,
    'ALTER TABLE dm_rule ADD COLUMN table_name_en VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''表英文名'' AFTER rule_description',
    'SELECT 1');
PREPARE dm_rule_add_table_name_en_stmt FROM @dm_rule_add_table_name_en_sql;
EXECUTE dm_rule_add_table_name_en_stmt;
DEALLOCATE PREPARE dm_rule_add_table_name_en_stmt;

SET @dm_rule_add_table_name_cn = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'table_name_cn');
SET @dm_rule_add_table_name_cn_sql = IF(
    @dm_rule_add_table_name_cn = 0,
    'ALTER TABLE dm_rule ADD COLUMN table_name_cn VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''表中文名'' AFTER table_name_en',
    'SELECT 1');
PREPARE dm_rule_add_table_name_cn_stmt FROM @dm_rule_add_table_name_cn_sql;
EXECUTE dm_rule_add_table_name_cn_stmt;
DEALLOCATE PREPARE dm_rule_add_table_name_cn_stmt;

SET @dm_rule_add_field_name_en = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'field_name_en');
SET @dm_rule_add_field_name_en_sql = IF(
    @dm_rule_add_field_name_en = 0,
    'ALTER TABLE dm_rule ADD COLUMN field_name_en VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''字段英文名称'' AFTER table_name_cn',
    'SELECT 1');
PREPARE dm_rule_add_field_name_en_stmt FROM @dm_rule_add_field_name_en_sql;
EXECUTE dm_rule_add_field_name_en_stmt;
DEALLOCATE PREPARE dm_rule_add_field_name_en_stmt;

SET @dm_rule_add_field_name_cn = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND column_name = 'field_name_cn');
SET @dm_rule_add_field_name_cn_sql = IF(
    @dm_rule_add_field_name_cn = 0,
    'ALTER TABLE dm_rule ADD COLUMN field_name_cn VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''字段中文名称'' AFTER field_name_en',
    'SELECT 1');
PREPARE dm_rule_add_field_name_cn_stmt FROM @dm_rule_add_field_name_cn_sql;
EXECUTE dm_rule_add_field_name_cn_stmt;
DEALLOCATE PREPARE dm_rule_add_field_name_cn_stmt;

-- 4. 新约束与查询索引（幂等）
SET @dm_rule_add_uk_code = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND index_name = 'uk_dm_rule_code');
SET @dm_rule_add_uk_code_sql = IF(
    @dm_rule_add_uk_code = 0,
    'ALTER TABLE dm_rule ADD UNIQUE KEY uk_dm_rule_code (tenant_id, rule_code)',
    'SELECT 1');
PREPARE dm_rule_add_uk_code_stmt FROM @dm_rule_add_uk_code_sql;
EXECUTE dm_rule_add_uk_code_stmt;
DEALLOCATE PREPARE dm_rule_add_uk_code_stmt;

SET @dm_rule_add_query_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND index_name = 'idx_dm_rule_query');
SET @dm_rule_add_query_idx_sql = IF(
    @dm_rule_add_query_idx = 0,
    'ALTER TABLE dm_rule ADD KEY idx_dm_rule_query (tenant_id, project_id, check_target_type, rule_category, system_code, deleted)',
    'SELECT 1');
PREPARE dm_rule_add_query_idx_stmt FROM @dm_rule_add_query_idx_sql;
EXECUTE dm_rule_add_query_idx_stmt;
DEALLOCATE PREPARE dm_rule_add_query_idx_stmt;

SET @dm_rule_add_keyword_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_rule' AND index_name = 'idx_dm_rule_keyword');
SET @dm_rule_add_keyword_idx_sql = IF(
    @dm_rule_add_keyword_idx = 0,
    'ALTER TABLE dm_rule ADD KEY idx_dm_rule_keyword (tenant_id, project_id, table_name_en(80), table_name_cn(80), field_name_en(80), deleted)',
    'SELECT 1');
PREPARE dm_rule_add_keyword_idx_stmt FROM @dm_rule_add_keyword_idx_sql;
EXECUTE dm_rule_add_keyword_idx_stmt;
DEALLOCATE PREPARE dm_rule_add_keyword_idx_stmt;

-- 5. 参数管理：检核目标类型 / 检核规则大类（幂等，INSERT IGNORE 不覆盖管理员既有配置）
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES
    (5821, 1, 'DM_RULE_TARGET_TYPE', '迁移检核目标类型', 1, 0),
    (5822, 1, 'DM_RULE_CATEGORY', '迁移检核规则大类', 1, 0);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '迁移检核规则业务码值（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58211 id, 'DM_RULE_TARGET_TYPE.MIGRATE_OUT_CHECK' config_key, '迁出检核规则' config_value, 'DM_RULE_TARGET_TYPE' category_code
    UNION ALL SELECT 58212, 'DM_RULE_TARGET_TYPE.MIGRATE_IN_MIDDLE_CHECK', '迁入中间表检核规则', 'DM_RULE_TARGET_TYPE'
    UNION ALL SELECT 58213, 'DM_RULE_TARGET_TYPE.MIGRATE_IN_TARGET_CHECK', '迁入目标表检核规则', 'DM_RULE_TARGET_TYPE'
    UNION ALL SELECT 58221, 'DM_RULE_CATEGORY.BASIC_CHECK', '基础检核', 'DM_RULE_CATEGORY'
    UNION ALL SELECT 58222, 'DM_RULE_CATEGORY.TABLE_HORIZONTAL_CHECK', '表间横向检核', 'DM_RULE_CATEGORY'
    UNION ALL SELECT 58223, 'DM_RULE_CATEGORY.VERTICAL_CHECK', '纵向检核', 'DM_RULE_CATEGORY'
    UNION ALL SELECT 58224, 'DM_RULE_CATEGORY.TOTAL_DETAIL_CHECK', '总分核对', 'DM_RULE_CATEGORY'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;
