-- V200：存量需求「系统/协同事项」物理合并为一张表
-- 以 req_legacy_system_item 为统一表：system_role 承载 主责/改造/测试；
-- 追加协同事项字段（状态/起止日期/说明），并把 req_coordination_item 存量数据复制进统一表。
-- 仅追加：不改历史迁移；req_coordination_item 保留但不再作为业务数据源。

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_system_item' AND COLUMN_NAME = 'start_date');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_system_item ADD COLUMN start_date DATE NULL COMMENT ''事项开始日期（改造/测试）'' AFTER owner_user_name',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_system_item' AND COLUMN_NAME = 'end_date');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_system_item ADD COLUMN end_date DATE NULL COMMENT ''事项结束日期（改造/测试）'' AFTER start_date',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_system_item' AND COLUMN_NAME = 'status');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_system_item ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''未开始'' COMMENT ''事项状态：未开始/进行中/已完成（改造/测试）'' AFTER end_date',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_legacy_system_item' AND COLUMN_NAME = 'description');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_legacy_system_item ADD COLUMN description VARCHAR(1000) NULL COMMENT ''事项说明（改造/测试）'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO req_legacy_system_item
    (id, tenant_id, requirement_id, system_role, system_code, system_name,
     owner_user_id, owner_user_name, start_date, end_date, status, description,
     created_by, deleted)
SELECT c.id, c.tenant_id, c.requirement_id, c.item_type, c.system_code, c.system_name,
       c.owner_user_id, c.owner_user_name, c.start_date, c.end_date,
       COALESCE(c.status, '未开始'), c.description, c.created_by, c.deleted
FROM req_coordination_item c
WHERE c.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM req_legacy_system_item s
      WHERE s.tenant_id = c.tenant_id AND s.id = c.id AND s.deleted = 0);
