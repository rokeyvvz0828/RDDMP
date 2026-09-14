-- V197: 迁移参数专属域优化（REQ-20260906-067，对标迁移检核规则 V194 域化范式）
-- 背景：dm_parameter 原为通用结构化资产薄表（doc_code/doc_name/structured_data），只支持「资产编码/名称+JSON」，
--   不满足「迁移参数」的维度字段（参数类型、参数范围分类、关联系统）与资料字段（参数名称、参数说明）。
-- 用户确认：deleted 前先判定表为空再删除旧列；参数名称唯一域 = 租户 + 项目 + 系统编号；Excel 模板五列逐行填写。
-- 存量：本地开发库 dm_parameter 为 0 行，脚本在删除旧列前断言表为空，非空以 SIGNAL 失败，禁止静默丢弃数据。
-- 约束：Flyway 只追加，不改历史脚本；information_schema 条件式执行，幂等可重跑。

-- 1. 断言 dm_parameter 为空，避免删除 doc_code/doc_name 后新 NOT NULL 字段落成脏默认值
DELIMITER $$
CREATE PROCEDURE dm_v197_assert_parameter_empty()
BEGIN
    DECLARE row_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO row_count FROM dm_parameter;
    IF row_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V197 失败：dm_parameter 存在存量行，未确认数据重建前禁止删列';
    END IF;
END$$
DELIMITER ;

CALL dm_v197_assert_parameter_empty();
DROP PROCEDURE dm_v197_assert_parameter_empty;

-- 2. 删除旧通用信封键与旧列（doc_code/doc_name/structured_data）
-- 旧唯一键实际可能为 uk_dm_parameter_code 或旧基线 uk_dm_parameter_active_code，按发现的名称动态删除
SET @dm_parameter_old_uk_name = (
    SELECT index_name FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter'
      AND index_name IN ('uk_dm_parameter_active_code', 'uk_dm_parameter_code')
    LIMIT 1);
SET @dm_parameter_drop_uk_code_sql = IF(
    @dm_parameter_old_uk_name IS NOT NULL,
    CONCAT('ALTER TABLE dm_parameter DROP INDEX `', @dm_parameter_old_uk_name, '`'),
    'SELECT 1');
PREPARE dm_parameter_drop_uk_code_stmt FROM @dm_parameter_drop_uk_code_sql;
EXECUTE dm_parameter_drop_uk_code_stmt;
DEALLOCATE PREPARE dm_parameter_drop_uk_code_stmt;

SET @dm_parameter_drop_query_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND index_name = 'idx_dm_parameter_query');
SET @dm_parameter_drop_query_idx_sql = IF(
    @dm_parameter_drop_query_idx_exists > 0,
    'ALTER TABLE dm_parameter DROP INDEX idx_dm_parameter_query',
    'SELECT 1');
PREPARE dm_parameter_drop_query_idx_stmt FROM @dm_parameter_drop_query_idx_sql;
EXECUTE dm_parameter_drop_query_idx_stmt;
DEALLOCATE PREPARE dm_parameter_drop_query_idx_stmt;

SET @dm_parameter_drop_owner_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND index_name = 'idx_dm_parameter_owner');
SET @dm_parameter_drop_owner_idx_sql = IF(
    @dm_parameter_drop_owner_idx_exists > 0,
    'ALTER TABLE dm_parameter DROP INDEX idx_dm_parameter_owner',
    'SELECT 1');
PREPARE dm_parameter_drop_owner_idx_stmt FROM @dm_parameter_drop_owner_idx_sql;
EXECUTE dm_parameter_drop_owner_idx_stmt;
DEALLOCATE PREPARE dm_parameter_drop_owner_idx_stmt;

SET @dm_parameter_drop_doc_code = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'doc_code');
SET @dm_parameter_drop_doc_code_sql = IF(
    @dm_parameter_drop_doc_code > 0,
    'ALTER TABLE dm_parameter DROP COLUMN doc_code',
    'SELECT 1');
PREPARE dm_parameter_drop_doc_code_stmt FROM @dm_parameter_drop_doc_code_sql;
EXECUTE dm_parameter_drop_doc_code_stmt;
DEALLOCATE PREPARE dm_parameter_drop_doc_code_stmt;

SET @dm_parameter_drop_doc_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'doc_name');
SET @dm_parameter_drop_doc_name_sql = IF(
    @dm_parameter_drop_doc_name > 0,
    'ALTER TABLE dm_parameter DROP COLUMN doc_name',
    'SELECT 1');
PREPARE dm_parameter_drop_doc_name_stmt FROM @dm_parameter_drop_doc_name_sql;
EXECUTE dm_parameter_drop_doc_name_stmt;
DEALLOCATE PREPARE dm_parameter_drop_doc_name_stmt;

SET @dm_parameter_drop_structured_data = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'structured_data');
SET @dm_parameter_drop_structured_data_sql = IF(
    @dm_parameter_drop_structured_data > 0,
    'ALTER TABLE dm_parameter DROP COLUMN structured_data',
    'SELECT 1');
PREPARE dm_parameter_drop_structured_data_stmt FROM @dm_parameter_drop_structured_data_sql;
EXECUTE dm_parameter_drop_structured_data_stmt;
DEALLOCATE PREPARE dm_parameter_drop_structured_data_stmt;

-- 3. 重建缺失字段（幂等：列不存在才加）
SET @dm_parameter_add_type = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'parameter_type');
SET @dm_parameter_add_type_sql = IF(
    @dm_parameter_add_type = 0,
    'ALTER TABLE dm_parameter ADD COLUMN parameter_type VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''参数类型（系统参数管理码值：业务/技术参数）'' AFTER system_code',
    'SELECT 1');
PREPARE dm_parameter_add_type_stmt FROM @dm_parameter_add_type_sql;
EXECUTE dm_parameter_add_type_stmt;
DEALLOCATE PREPARE dm_parameter_add_type_stmt;

SET @dm_parameter_add_scope = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'parameter_scope');
SET @dm_parameter_add_scope_sql = IF(
    @dm_parameter_add_scope = 0,
    'ALTER TABLE dm_parameter ADD COLUMN parameter_scope VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''参数范围分类（系统参数管理码值：公共/自有参数）'' AFTER parameter_type',
    'SELECT 1');
PREPARE dm_parameter_add_scope_stmt FROM @dm_parameter_add_scope_sql;
EXECUTE dm_parameter_add_scope_stmt;
DEALLOCATE PREPARE dm_parameter_add_scope_stmt;

SET @dm_parameter_add_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'parameter_name');
SET @dm_parameter_add_name_sql = IF(
    @dm_parameter_add_name = 0,
    'ALTER TABLE dm_parameter ADD COLUMN parameter_name VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''参数名称'' AFTER system_code',
    'SELECT 1');
PREPARE dm_parameter_add_name_stmt FROM @dm_parameter_add_name_sql;
EXECUTE dm_parameter_add_name_stmt;
DEALLOCATE PREPARE dm_parameter_add_name_stmt;

-- 说明：parameter_type/scope 位于 parameter_name 之后，保持可读；字段顺序不影响应用契约。
SET @dm_parameter_add_desc = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND column_name = 'parameter_description');
SET @dm_parameter_add_desc_sql = IF(
    @dm_parameter_add_desc = 0,
    'ALTER TABLE dm_parameter ADD COLUMN parameter_description VARCHAR(500) NULL COMMENT ''参数说明（用途/取值规则/适用场景）'' AFTER parameter_name',
    'SELECT 1');
PREPARE dm_parameter_add_desc_stmt FROM @dm_parameter_add_desc_sql;
EXECUTE dm_parameter_add_desc_stmt;
DEALLOCATE PREPARE dm_parameter_add_desc_stmt;

-- 4. 新约束与查询索引（幂等）
SET @dm_parameter_add_uk = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND index_name = 'uk_dm_parameter_name');
SET @dm_parameter_add_uk_sql = IF(
    @dm_parameter_add_uk = 0,
    'ALTER TABLE dm_parameter ADD UNIQUE KEY uk_dm_parameter_name (tenant_id, project_id, system_code, parameter_name)',
    'SELECT 1');
PREPARE dm_parameter_add_uk_stmt FROM @dm_parameter_add_uk_sql;
EXECUTE dm_parameter_add_uk_stmt;
DEALLOCATE PREPARE dm_parameter_add_uk_stmt;

SET @dm_parameter_add_query_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND index_name = 'idx_dm_parameter_query');
SET @dm_parameter_add_query_idx_sql = IF(
    @dm_parameter_add_query_idx = 0,
    'ALTER TABLE dm_parameter ADD KEY idx_dm_parameter_query (tenant_id, project_id, parameter_type, parameter_scope, system_code, deleted)',
    'SELECT 1');
PREPARE dm_parameter_add_query_idx_stmt FROM @dm_parameter_add_query_idx_sql;
EXECUTE dm_parameter_add_query_idx_stmt;
DEALLOCATE PREPARE dm_parameter_add_query_idx_stmt;

SET @dm_parameter_add_keyword_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_parameter' AND index_name = 'idx_dm_parameter_keyword');
SET @dm_parameter_add_keyword_idx_sql = IF(
    @dm_parameter_add_keyword_idx = 0,
    'ALTER TABLE dm_parameter ADD KEY idx_dm_parameter_keyword (tenant_id, project_id, parameter_name(80), deleted)',
    'SELECT 1');
PREPARE dm_parameter_add_keyword_idx_stmt FROM @dm_parameter_add_keyword_idx_sql;
EXECUTE dm_parameter_add_keyword_idx_stmt;
DEALLOCATE PREPARE dm_parameter_add_keyword_idx_stmt;

-- 5. 参数管理：参数类型 / 参数范围分类（幂等，INSERT IGNORE 不覆盖管理员既有配置）
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES
    (5831, 1, 'DM_PARAMETER_TYPE', '迁移参数类型', 1, 0),
    (5832, 1, 'DM_PARAMETER_SCOPE', '迁移参数范围分类', 1, 0);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '迁移参数业务码值（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58311 id, 'DM_PARAMETER_TYPE.BUSINESS' config_key, '业务参数' config_value, 'DM_PARAMETER_TYPE' category_code
    UNION ALL SELECT 58312, 'DM_PARAMETER_TYPE.TECHNICAL', '技术参数', 'DM_PARAMETER_TYPE'
    UNION ALL SELECT 58321, 'DM_PARAMETER_SCOPE.PUBLIC', '公共参数', 'DM_PARAMETER_SCOPE'
    UNION ALL SELECT 58322, 'DM_PARAMETER_SCOPE.OWN', '自有参数', 'DM_PARAMETER_SCOPE'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;
