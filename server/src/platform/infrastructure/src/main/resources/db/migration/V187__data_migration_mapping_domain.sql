-- V186: 迁移映射菜单优化
-- 在 dm_mapping_doc 补充映射类型，并把映射类型放入“系统管理/参数管理”维护。
-- 仅追加、幂等；管理员已有配置不被覆盖。

-- 1. dm_mapping_doc.mapping_type
SET @dm_mapping_doc_mapping_type_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_mapping_doc' AND column_name = 'mapping_type'
);
SET @dm_mapping_doc_add_mapping_type_sql = IF(
    @dm_mapping_doc_mapping_type_exists = 0,
    'ALTER TABLE dm_mapping_doc ADD COLUMN mapping_type VARCHAR(64) NOT NULL COMMENT ''映射类型码值（DM_MAPPING_TYPE，系统管理/参数管理维护）'' AFTER system_code',
    'SELECT 1'
);
PREPARE dm_mapping_doc_add_mapping_type_stmt FROM @dm_mapping_doc_add_mapping_type_sql;
EXECUTE dm_mapping_doc_add_mapping_type_stmt;
DEALLOCATE PREPARE dm_mapping_doc_add_mapping_type_stmt;

-- 2. 映射类型/系统查询索引
SET @dm_mapping_doc_mapping_type_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_mapping_doc' AND index_name = 'idx_dm_mapping_doc_mapping_type'
);
SET @dm_mapping_doc_add_mapping_type_idx_sql = IF(
    @dm_mapping_doc_mapping_type_idx_exists = 0,
    'ALTER TABLE dm_mapping_doc ADD KEY idx_dm_mapping_doc_mapping_type (tenant_id, project_id, mapping_type, system_code, deleted)',
    'SELECT 1'
);
PREPARE dm_mapping_doc_add_mapping_type_idx_stmt FROM @dm_mapping_doc_add_mapping_type_idx_sql;
EXECUTE dm_mapping_doc_add_mapping_type_idx_stmt;
DEALLOCATE PREPARE dm_mapping_doc_add_mapping_type_idx_stmt;

-- 3. 参数管理类别与初始项
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES (5815, 1, 'DM_MAPPING_TYPE', '迁移映射类型', 1, 0);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '迁移映射业务码值（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58157 id, 'DM_MAPPING_TYPE.MIGRATE_OUT' config_key, '迁出程序' config_value, 'DM_MAPPING_TYPE' category_code UNION ALL
    SELECT 58158, 'DM_MAPPING_TYPE.MIGRATE_IN', '迁入程序', 'DM_MAPPING_TYPE'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;
