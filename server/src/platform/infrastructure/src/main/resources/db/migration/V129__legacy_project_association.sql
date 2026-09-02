-- =============================================================================
-- V99：存量需求归属项目（project_id）
-- 存量列表改为直接按 project_id 过滤，新建需求创建时绑定当前项目，
-- 避免“业务组为空导致列表不显示”的问题。
-- 仅追加，不修改历史迁移；列新增按 information_schema 判断跳过。
-- =============================================================================

-- 1. 存量需求新增所属项目字段（已存在则跳过）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_requirement'
      AND COLUMN_NAME = 'project_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_requirement
        ADD COLUMN project_id BIGINT NULL COMMENT ''所属项目（req_project.id）'' AFTER tenant_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 存量数据回填：按业务组匹配项目（该业务组在哪个项目的差异清单中出现过）
UPDATE req_legacy_requirement lr
SET lr.project_id = (
    SELECT d.project_id FROM req_difference d
    WHERE d.tenant_id = lr.tenant_id AND d.deleted = 0
      AND d.business_group = lr.business_group
      AND lr.business_group IS NOT NULL AND lr.business_group <> ''
    LIMIT 1
)
WHERE lr.project_id IS NULL AND lr.deleted = 0;
