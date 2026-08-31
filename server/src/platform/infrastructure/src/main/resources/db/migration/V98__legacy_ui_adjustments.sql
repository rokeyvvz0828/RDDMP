-- =============================================================================
-- V98：存量需求 UI 调整配套
-- 1. 系统子表仅支持主责：删除演示/历史“协同”系统子表行
-- 2. 工作量评估新增字段：工作量（多少人月）
-- 3. 系统清单页面下线：删除菜单 703（页面与菜单一并移除）
-- 仅追加，不修改历史迁移；列新增按 information_schema 判断跳过。
-- =============================================================================

-- 1. 删除“协同”系统子表行（产品决策：系统子表仅主责）
DELETE FROM req_legacy_system_item WHERE system_role = '协同' AND deleted = 0;

-- 2. 工作量（多少人月）字段（已存在则跳过）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_requirement'
      AND COLUMN_NAME = 'workload_person_months');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_requirement
        ADD COLUMN workload_person_months VARCHAR(100) NULL COMMENT ''工作量（多少人月）'' AFTER workload_date',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 系统清单菜单下线
DELETE FROM sys_role_menu WHERE menu_id = 703;
DELETE FROM sys_menu WHERE id = 703;
