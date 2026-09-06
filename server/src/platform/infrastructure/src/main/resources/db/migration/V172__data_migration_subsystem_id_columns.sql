-- V171: 存量子系统引用收敛：为问题/目标表/组件补充 arch_physical_subsystem.id 引用列。
-- 背景（REQ-20260820-031 9.2 P2「子系统引用键不统一」）：
--   dm_issue.system_code / dm_target_table.system_code / dm_component.physical_subsystem_code
--   原来只按 code 引用 arch_physical_subsystem；本迁移追加 id 引用列并按 (tenant_id, code) 回填，
--   应用层新建/更新改为同时维护 id 引用（code 列保留为录入/显示与既有一致性用途，不删除）。
-- 仅追加，不修改已发布脚本；三个 id 列均允许为空，未匹配到 arch 的存量行保持 NULL。
-- 回退：删除本迁移新增的列与索引即可（无数据损坏）。

SET @dm_issue_system_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND column_name = 'system_id'
);
SET @dm_issue_system_id_sql = IF(
    @dm_issue_system_id_exists = 0,
    'ALTER TABLE dm_issue ADD COLUMN system_id BIGINT NULL COMMENT ''关联系统（arch_physical_subsystem.id）'' AFTER system_code',
    'SELECT 1'
);
PREPARE dm_issue_system_id_stmt FROM @dm_issue_system_id_sql;
EXECUTE dm_issue_system_id_stmt;
DEALLOCATE PREPARE dm_issue_system_id_stmt;

SET @dm_target_table_system_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'system_id'
);
SET @dm_target_table_system_id_sql = IF(
    @dm_target_table_system_id_exists = 0,
    'ALTER TABLE dm_target_table ADD COLUMN system_id BIGINT NULL COMMENT ''关联系统（arch_physical_subsystem.id）'' AFTER system_code',
    'SELECT 1'
);
PREPARE dm_target_table_system_id_stmt FROM @dm_target_table_system_id_sql;
EXECUTE dm_target_table_system_id_stmt;
DEALLOCATE PREPARE dm_target_table_system_id_stmt;

SET @dm_component_subsystem_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'physical_subsystem_id'
);
SET @dm_component_subsystem_id_sql = IF(
    @dm_component_subsystem_id_exists = 0,
    'ALTER TABLE dm_component ADD COLUMN physical_subsystem_id BIGINT NULL COMMENT ''关联物理子系统（arch_physical_subsystem.id）'' AFTER physical_subsystem_code',
    'SELECT 1'
);
PREPARE dm_component_subsystem_id_stmt FROM @dm_component_subsystem_id_sql;
EXECUTE dm_component_subsystem_id_stmt;
DEALLOCATE PREPARE dm_component_subsystem_id_stmt;

-- 回填：code -> id（按租户匹配 arch_physical_subsystem 有效行；仅回填仍为 NULL 的行，保留人工修正值）
UPDATE dm_issue i
   JOIN arch_physical_subsystem s
     ON s.tenant_id = i.tenant_id AND s.code = i.system_code AND s.deleted = 0
   SET i.system_id = s.id
 WHERE i.system_id IS NULL AND i.system_code IS NOT NULL AND i.system_code <> '';

UPDATE dm_target_table t
   JOIN arch_physical_subsystem s
     ON s.tenant_id = t.tenant_id AND s.code = t.system_code AND s.deleted = 0
   SET t.system_id = s.id
 WHERE t.system_id IS NULL AND t.system_code IS NOT NULL AND t.system_code <> '';

UPDATE dm_component c
   JOIN arch_physical_subsystem s
     ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0
   SET c.physical_subsystem_id = s.id
 WHERE c.physical_subsystem_id IS NULL AND c.physical_subsystem_code IS NOT NULL AND c.physical_subsystem_code <> '';

-- 按 id 引用/过滤的辅助索引（条件式追加，幂等）
SET @idx1_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND index_name = 'idx_dm_issue_system_id'
);
SET @idx1_sql = IF(
    @idx1_exists = 0,
    'ALTER TABLE dm_issue ADD KEY idx_dm_issue_system_id (tenant_id, system_id, deleted)',
    'SELECT 1'
);
PREPARE idx1_stmt FROM @idx1_sql;
EXECUTE idx1_stmt;
DEALLOCATE PREPARE idx1_stmt;

SET @idx2_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'idx_target_table_system_id'
);
SET @idx2_sql = IF(
    @idx2_exists = 0,
    'ALTER TABLE dm_target_table ADD KEY idx_target_table_system_id (tenant_id, project_id, system_id, deleted)',
    'SELECT 1'
);
PREPARE idx2_stmt FROM @idx2_sql;
EXECUTE idx2_stmt;
DEALLOCATE PREPARE idx2_stmt;

SET @idx3_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'idx_dm_component_subsystem_id'
);
SET @idx3_sql = IF(
    @idx3_exists = 0,
    'ALTER TABLE dm_component ADD KEY idx_dm_component_subsystem_id (tenant_id, project_id, physical_subsystem_id, deleted)',
    'SELECT 1'
);
PREPARE idx3_stmt FROM @idx3_sql;
EXECUTE idx3_stmt;
DEALLOCATE PREPARE idx3_stmt;
