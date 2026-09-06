-- V185: 迁移程序菜单优化
-- 在 dm_script 补充程序类型/说明，并把程序类型放入“系统管理/参数管理”维护。
-- 仅追加、幂等；管理员已有配置不被覆盖。

-- 1. dm_script.system_code（业务关联项目内 dm_component 活动记录编号）
SET @dm_script_system_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_script' AND column_name = 'system_code'
);
SET @dm_script_add_system_code_sql = IF(
    @dm_script_system_code_exists = 0,
    'ALTER TABLE dm_script ADD COLUMN system_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''系统编号（项目内dm_component活动记录）'' AFTER doc_name',
    'SELECT 1'
);
PREPARE dm_script_add_system_code_stmt FROM @dm_script_add_system_code_sql;
EXECUTE dm_script_add_system_code_stmt;
DEALLOCATE PREPARE dm_script_add_system_code_stmt;

-- 2. dm_script.program_type
SET @dm_script_program_type_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_script' AND column_name = 'program_type'
);
SET @dm_script_add_program_type_sql = IF(
    @dm_script_program_type_exists = 0,
    'ALTER TABLE dm_script ADD COLUMN program_type VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''程序类型码值（DM_PROGRAM_TYPE，系统管理/参数管理维护）'' AFTER system_code',
    'SELECT 1'
);
PREPARE dm_script_add_program_type_stmt FROM @dm_script_add_program_type_sql;
EXECUTE dm_script_add_program_type_stmt;
DEALLOCATE PREPARE dm_script_add_program_type_stmt;

-- 3. dm_script.program_description
SET @dm_script_program_description_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_script' AND column_name = 'program_description'
);
SET @dm_script_add_program_description_sql = IF(
    @dm_script_program_description_exists = 0,
    'ALTER TABLE dm_script ADD COLUMN program_description VARCHAR(2000) NULL COMMENT ''程序包说明（功能、执行逻辑、适用场景）'' AFTER program_type',
    'SELECT 1'
);
PREPARE dm_script_add_program_description_stmt FROM @dm_script_add_program_description_sql;
EXECUTE dm_script_add_program_description_stmt;
DEALLOCATE PREPARE dm_script_add_program_description_stmt;

-- 4. 程序类型/系统查询索引
SET @dm_script_program_type_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_script' AND index_name = 'idx_dm_script_program_type'
);
SET @dm_script_add_program_type_idx_sql = IF(
    @dm_script_program_type_idx_exists = 0,
    'ALTER TABLE dm_script ADD KEY idx_dm_script_program_type (tenant_id, project_id, program_type, system_code, deleted)',
    'SELECT 1'
);
PREPARE dm_script_add_program_type_idx_stmt FROM @dm_script_add_program_type_idx_sql;
EXECUTE dm_script_add_program_type_idx_stmt;
DEALLOCATE PREPARE dm_script_add_program_type_idx_stmt;

-- 5. 参数管理类别与初始项
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES (5814, 1, 'DM_PROGRAM_TYPE', '迁移程序类型', 1, 0);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '迁移程序业务码值（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58155 id, 'DM_PROGRAM_TYPE.MIGRATE_OUT' config_key, '迁出程序' config_value, 'DM_PROGRAM_TYPE' category_code UNION ALL
    SELECT 58156, 'DM_PROGRAM_TYPE.MIGRATE_IN', '迁入程序', 'DM_PROGRAM_TYPE'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;
