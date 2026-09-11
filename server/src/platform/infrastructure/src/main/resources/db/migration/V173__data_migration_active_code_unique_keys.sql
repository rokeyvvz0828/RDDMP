-- V172: 软删唯一键统一为“活动生成列 + 唯一索引”模型。
-- 背景（REQ-20260820-031 9.2 P2「软删唯一键策略不一致」）：
--   方案/内容/问题/会议已使用 active_*_code 生成列（软删行取 NULL，不参与唯一）；
--   dm_component / dm_target_table / dm_target_table_field 仍把 deleted 放进唯一键。
-- 本迁移把下列 8 个含 deleted 的唯一键替换为生成列唯一键（恢复冲突语义不变；
-- 活动行唯一、软删行允许同值重建），并删除 dm_issue 旧的重复约束 uk_dm_issue_code。
-- 仅追加，不修改已发布脚本；逐项条件执行，缺旧键或已存在新键时不报错。
-- 回退：重建旧唯一键并删除生成列（见文档 9.2 最终态登记）。

DELIMITER $$
CREATE PROCEDURE dm_v172_add_active_column(IN tbl_name VARCHAR(64), IN active_col VARCHAR(64),
                                           IN src_col VARCHAR(64), IN col_len VARCHAR(20), IN col_comment VARCHAR(255))
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = tbl_name AND column_name = active_col;
    IF col_count = 0 THEN
        SET @dm_v172_ddl = CONCAT(
            'ALTER TABLE ', tbl_name,
            ' ADD COLUMN ', active_col, ' VARCHAR(', col_len, ')',
            ' GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN ', src_col, ' ELSE NULL END) STORED',
            ' COMMENT ''', col_comment, ''' AFTER ', src_col);
        PREPARE dm_v172_stmt FROM @dm_v172_ddl;
        EXECUTE dm_v172_stmt;
        DEALLOCATE PREPARE dm_v172_stmt;
    END IF;
END$$

CREATE PROCEDURE dm_v172_drop_uk(IN tbl_name VARCHAR(64), IN uk_name VARCHAR(64))
BEGIN
    DECLARE uk_count INT DEFAULT 0;
    SELECT COUNT(*) INTO uk_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = tbl_name AND index_name = uk_name;
    IF uk_count > 0 THEN
        SET @dm_v172_ddl = CONCAT('ALTER TABLE ', tbl_name, ' DROP INDEX ', uk_name);
        PREPARE dm_v172_stmt FROM @dm_v172_ddl;
        EXECUTE dm_v172_stmt;
        DEALLOCATE PREPARE dm_v172_stmt;
    END IF;
END$$

CREATE PROCEDURE dm_v172_add_uk(IN tbl_name VARCHAR(64), IN uk_name VARCHAR(64), IN uk_cols VARCHAR(255))
BEGIN
    DECLARE uk_count INT DEFAULT 0;
    SELECT COUNT(*) INTO uk_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = tbl_name AND index_name = uk_name;
    IF uk_count = 0 THEN
        SET @dm_v172_ddl = CONCAT('ALTER TABLE ', tbl_name, ' ADD UNIQUE KEY ', uk_name, ' (', uk_cols, ')');
        PREPARE dm_v172_stmt FROM @dm_v172_ddl;
        EXECUTE dm_v172_stmt;
        DEALLOCATE PREPARE dm_v172_stmt;
    END IF;
END$$
DELIMITER ;

-- dm_component：物理子系统引用唯一（替换 uk_dm_component_subsystem）
CALL dm_v172_add_active_column('dm_component', 'active_physical_subsystem_code', 'physical_subsystem_code', '64', '活动系统编号（仅未删除记录取值）');
CALL dm_v172_drop_uk('dm_component', 'uk_dm_component_subsystem');
CALL dm_v172_add_uk('dm_component', 'uk_dm_component_active_subsystem', 'tenant_id, project_id, active_physical_subsystem_code');

-- dm_target_table：表编号与“项目+系统+表名”唯一（替换 uk_target_table_code/en/cn）
CALL dm_v172_add_active_column('dm_target_table', 'active_table_code', 'table_code', '64', '活动表编号（仅未删除记录取值）');
CALL dm_v172_add_active_column('dm_target_table', 'active_system_code', 'system_code', '64', '活动系统编号（仅未删除记录取值）');
CALL dm_v172_add_active_column('dm_target_table', 'active_table_name_en', 'table_name_en', '128', '活动英文表名（仅未删除记录取值）');
CALL dm_v172_add_active_column('dm_target_table', 'active_table_name_cn', 'table_name_cn', '128', '活动中文表名（仅未删除记录取值）');
CALL dm_v172_drop_uk('dm_target_table', 'uk_target_table_code');
CALL dm_v172_drop_uk('dm_target_table', 'uk_target_table_en');
CALL dm_v172_drop_uk('dm_target_table', 'uk_target_table_cn');
CALL dm_v172_add_uk('dm_target_table', 'uk_target_table_active_code', 'tenant_id, active_table_code');
CALL dm_v172_add_uk('dm_target_table', 'uk_target_table_active_en', 'tenant_id, project_id, active_system_code, active_table_name_en');
CALL dm_v172_add_uk('dm_target_table', 'uk_target_table_active_cn', 'tenant_id, project_id, active_system_code, active_table_name_cn');

-- dm_target_table_field：字段编号与“表+字段名”唯一（替换 uk_target_field_code/en/cn）
CALL dm_v172_add_active_column('dm_target_table_field', 'active_field_code', 'field_code', '64', '活动字段编号（仅未删除记录取值）');
CALL dm_v172_add_active_column('dm_target_table_field', 'active_field_name_en', 'field_name_en', '128', '活动英文字段名（仅未删除记录取值）');
CALL dm_v172_add_active_column('dm_target_table_field', 'active_field_name_cn', 'field_name_cn', '128', '活动中文字段名（仅未删除记录取值）');
CALL dm_v172_drop_uk('dm_target_table_field', 'uk_target_field_code');
CALL dm_v172_drop_uk('dm_target_table_field', 'uk_target_field_en');
CALL dm_v172_drop_uk('dm_target_table_field', 'uk_target_field_cn');
CALL dm_v172_add_uk('dm_target_table_field', 'uk_target_field_active_code', 'tenant_id, active_field_code');
CALL dm_v172_add_uk('dm_target_table_field', 'uk_target_field_active_en', 'tenant_id, table_id, active_field_name_en');
CALL dm_v172_add_uk('dm_target_table_field', 'uk_target_field_active_cn', 'tenant_id, table_id, active_field_name_cn');

-- dm_issue：删除旧的重复约束（V157 已建 uk_dm_issue_active_code 生成列唯一键）
CALL dm_v172_drop_uk('dm_issue', 'uk_dm_issue_code');

DROP PROCEDURE dm_v172_add_active_column;
DROP PROCEDURE dm_v172_drop_uk;
DROP PROCEDURE dm_v172_add_uk;
