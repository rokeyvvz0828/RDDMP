-- V182: dm_component 自然键(system_code)+enabled，下游统一 (project_id, system_code)。
--
-- 背景（字典 2026-09-05）：组件清单 id 不再作为业务身份，改为
--   (tenant_id, project_id, system_code)；物理子系统字段改名 system_code；
--   逻辑删除 deleted 下线，改为物理删除 + enabled 启停。
--   存量 deleted=1 迁移为 enabled=0，deleted=0 迁移为 enabled=1。
--   下游内容表/看板快照由 component_id 改为 system_code，历史只读仍可显示名称。
--
-- 约束：Flyway 只追加；全部使用 information_schema 判断，幂等且可在新库/已有库执行。

-- ============ 1. dm_component：physical_subsystem_code -> system_code + enabled ============
SET @dm_component_system_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'system_code'
);
SET @dm_component_physical_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'physical_subsystem_code'
);
SET @dm_component_rename_sql = IF(
    @dm_component_system_code_exists = 0 AND @dm_component_physical_exists = 1,
    'ALTER TABLE dm_component CHANGE COLUMN physical_subsystem_code system_code VARCHAR(64) NULL COMMENT ''系统编号（arch_physical_subsystem.code），项目内唯一''',
    'SELECT 1'
);
PREPARE dm_component_rename_stmt FROM @dm_component_rename_sql;
EXECUTE dm_component_rename_stmt;
DEALLOCATE PREPARE dm_component_rename_stmt;

SET @dm_component_enabled_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'enabled'
);
SET @dm_component_enabled_add_sql = IF(
    @dm_component_enabled_exists = 0,
    'ALTER TABLE dm_component ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 COMMENT ''启停状态 1启用 0停用'' AFTER system_code',
    'SELECT 1'
);
PREPARE dm_component_enabled_stmt FROM @dm_component_enabled_add_sql;
EXECUTE dm_component_enabled_stmt;
DEALLOCATE PREPARE dm_component_enabled_stmt;

-- 空编号统一为空串，保证可先按业务键去重再收紧非空约束。
UPDATE dm_component SET system_code = '' WHERE system_code IS NULL;
-- 存量 deleted 语义 -> enabled 语义。
UPDATE dm_component SET enabled = IF(deleted = 0, 1, 0) WHERE deleted IS NOT NULL;

-- ============ 2. 下游内容表：system_code 补列 + 按 component_id 回填 ============
DROP PROCEDURE IF EXISTS dm_v182_backfill_system_code;
DELIMITER $$
CREATE PROCEDURE dm_v182_backfill_system_code()
BEGIN
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_cursor CURSOR FOR
        SELECT 'dm_mapping_doc' UNION ALL
        SELECT 'dm_dependency' UNION ALL
        SELECT 'dm_script' UNION ALL
        SELECT 'dm_topic' UNION ALL
        SELECT 'dm_release_drill' UNION ALL
        SELECT 'dm_report' UNION ALL
        SELECT 'dm_rule' UNION ALL
        SELECT 'dm_parameter';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
    OPEN v_cursor;
    content_loop: LOOP
        FETCH v_cursor INTO v_table_name;
        IF v_done = 1 THEN LEAVE content_loop; END IF;
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = v_table_name) THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = v_table_name AND column_name = 'system_code') THEN
                SET @add_col = CONCAT('ALTER TABLE `', v_table_name, '` ADD COLUMN system_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''关联系统编号(dm_component.system_code)，项目级为空串'' AFTER component_id');
                PREPARE add_col_stmt FROM @add_col;
                EXECUTE add_col_stmt;
                DEALLOCATE PREPARE add_col_stmt;
            END IF;
            SET @backfill = CONCAT('UPDATE `', v_table_name, '` t JOIN dm_component c ON c.id = t.component_id AND c.tenant_id = t.tenant_id AND c.project_id = t.project_id SET t.system_code = c.system_code WHERE (t.system_code IS NULL OR t.system_code = '''') AND t.component_id IS NOT NULL');
            PREPARE backfill_stmt FROM @backfill;
            EXECUTE backfill_stmt;
            DEALLOCATE PREPARE backfill_stmt;
        END IF;
    END LOOP;
    CLOSE v_cursor;
END$$
DELIMITER ;
CALL dm_v182_backfill_system_code();
DROP PROCEDURE dm_v182_backfill_system_code;

-- dm_plan 已带 system_code，只做漏缺回填。
UPDATE dm_plan t
JOIN dm_component c
  ON c.id = t.component_id AND c.tenant_id = t.tenant_id AND c.project_id = t.project_id
SET t.system_code = c.system_code
WHERE (t.system_code IS NULL OR t.system_code = '') AND t.component_id IS NOT NULL;

-- ============ 3. dm_dashboard_snapshot：component_id -> system_code ============
SET @dm_snapshot_system_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_dashboard_snapshot' AND column_name = 'system_code'
);
SET @dm_snapshot_add_system_code_sql = IF(
    @dm_snapshot_system_code_exists = 0,
    'ALTER TABLE dm_dashboard_snapshot ADD COLUMN system_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''关联系统编号(dm_component.system_code)'' AFTER project_id',
    'SELECT 1'
);
PREPARE dm_snapshot_add_system_code_stmt FROM @dm_snapshot_add_system_code_sql;
EXECUTE dm_snapshot_add_system_code_stmt;
DEALLOCATE PREPARE dm_snapshot_add_system_code_stmt;

UPDATE dm_dashboard_snapshot t
JOIN dm_component c
  ON c.id = t.component_id AND c.tenant_id = t.tenant_id AND c.project_id = t.project_id
SET t.system_code = c.system_code
WHERE (t.system_code IS NULL OR t.system_code = '') AND t.component_id IS NOT NULL;

SET @dm_snapshot_drop_uk_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_dashboard_snapshot' AND index_name = 'uk_dm_snapshot'),
    'ALTER TABLE dm_dashboard_snapshot DROP INDEX uk_dm_snapshot',
    'SELECT 1'
);
PREPARE dm_snapshot_drop_uk_stmt FROM @dm_snapshot_drop_uk_sql;
EXECUTE dm_snapshot_drop_uk_stmt;
DEALLOCATE PREPARE dm_snapshot_drop_uk_stmt;

SET @dm_snapshot_drop_component_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_dashboard_snapshot' AND column_name = 'component_id'),
    'ALTER TABLE dm_dashboard_snapshot DROP COLUMN component_id',
    'SELECT 1'
);
PREPARE dm_snapshot_drop_component_stmt FROM @dm_snapshot_drop_component_sql;
EXECUTE dm_snapshot_drop_component_stmt;
DEALLOCATE PREPARE dm_snapshot_drop_component_stmt;

SET @dm_snapshot_add_uk_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_dashboard_snapshot' AND index_name = 'uk_dm_snapshot'),
    'ALTER TABLE dm_dashboard_snapshot ADD UNIQUE KEY uk_dm_snapshot (tenant_id, snapshot_date, project_id, system_code, metric_code)',
    'SELECT 1'
);
PREPARE dm_snapshot_add_uk_stmt FROM @dm_snapshot_add_uk_sql;
EXECUTE dm_snapshot_add_uk_stmt;
DEALLOCATE PREPARE dm_snapshot_add_uk_stmt;

-- ============ 4. dm_component 去重并收紧为自然键结构 ============
-- 旧唯一键含 deleted，同一项目下可能同时存在活动/已删除同名记录；新模型每项目系统编号唯一。
DROP TEMPORARY TABLE IF EXISTS dm_v182_keep;
CREATE TEMPORARY TABLE dm_v182_keep (keep_id BIGINT PRIMARY KEY);
INSERT INTO dm_v182_keep (keep_id)
SELECT COALESCE(MIN(CASE WHEN deleted = 0 THEN id END), MIN(id))
FROM dm_component
GROUP BY tenant_id, project_id, system_code;

DELETE c
FROM dm_component c
LEFT JOIN dm_v182_keep k ON k.keep_id = c.id
WHERE k.keep_id IS NULL;
DROP TEMPORARY TABLE dm_v182_keep;

-- 删除历史逻辑删除列与旧索引，替换为新自然键主键与启停索引。
SET @dm_component_old_indexes_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name IN ('uk_dm_component_subsystem', 'idx_dm_component_project', 'idx_dm_component_list'))
    OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'deleted'),
    'ALTER TABLE dm_component
        DROP INDEX uk_dm_component_subsystem,
        DROP INDEX idx_dm_component_project,
        DROP INDEX idx_dm_component_list,
        DROP PRIMARY KEY,
        MODIFY COLUMN system_code VARCHAR(64) NOT NULL,
        DROP COLUMN deleted,
        DROP COLUMN id,
        ADD PRIMARY KEY (tenant_id, project_id, system_code),
        ADD KEY idx_dm_component_enabled (tenant_id, project_id, enabled),
        ADD KEY idx_dm_component_list (tenant_id, project_id, enabled, updated_at)',
    'SELECT 1'
);
PREPARE dm_component_old_indexes_stmt FROM @dm_component_old_indexes_sql;
EXECUTE dm_component_old_indexes_stmt;
DEALLOCATE PREPARE dm_component_old_indexes_stmt;

-- 清理内容表遗留 component_id 列（dm_plan 与 8 张内容表；历史回填已完成）。
DROP PROCEDURE IF EXISTS dm_v182_drop_component_id;
DELIMITER $$
CREATE PROCEDURE dm_v182_drop_component_id()
BEGIN
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_cursor CURSOR FOR
        SELECT 'dm_plan' UNION ALL
        SELECT 'dm_mapping_doc' UNION ALL
        SELECT 'dm_dependency' UNION ALL
        SELECT 'dm_script' UNION ALL
        SELECT 'dm_topic' UNION ALL
        SELECT 'dm_release_drill' UNION ALL
        SELECT 'dm_report' UNION ALL
        SELECT 'dm_rule' UNION ALL
        SELECT 'dm_parameter';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
    OPEN v_cursor;
    drop_loop: LOOP
        FETCH v_cursor INTO v_table_name;
        IF v_done = 1 THEN LEAVE drop_loop; END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = v_table_name AND column_name = 'component_id') THEN
            SET @drop_col = CONCAT('ALTER TABLE `', v_table_name, '` DROP COLUMN component_id');
            PREPARE drop_col_stmt FROM @drop_col;
            EXECUTE drop_col_stmt;
            DEALLOCATE PREPARE drop_col_stmt;
        END IF;
    END LOOP;
    CLOSE v_cursor;
END$$
DELIMITER ;
CALL dm_v182_drop_component_id();
DROP PROCEDURE dm_v182_drop_component_id;
