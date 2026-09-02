-- =============================================================================
-- V96：新建项目差异 - 全员可见 + 编辑权限（管理员/创建人/当前处理人）+ 差异流转
-- 查看：所有登录用户可查看差异；编辑/删除/提交评审/撤销评审/流转仅限管理员、创建人或当前处理人。
-- req_difference 新增当前处理人字段；新增 req_difference_flow_log 流转记录表。
-- 仅追加，不修改历史迁移；列新增按 information_schema 判断跳过，建表使用 IF NOT EXISTS。
-- =============================================================================

-- req_difference 新增当前处理人字段（已存在则跳过）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_difference'
      AND COLUMN_NAME IN ('current_handler_user_id', 'current_handler_user_name'));
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_difference
        ADD COLUMN current_handler_user_id BIGINT NULL COMMENT ''当前处理人用户 ID（创建人初始/流转后为接收人）'' AFTER test_status,
        ADD COLUMN current_handler_user_name VARCHAR(64) NULL COMMENT ''当前处理人姓名'' AFTER current_handler_user_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量差异回填：默认当前处理人 = 创建人
UPDATE req_difference d
LEFT JOIN sys_user u ON u.id = d.created_by AND u.tenant_id = d.tenant_id
SET d.current_handler_user_id = COALESCE(d.current_handler_user_id, d.created_by),
    d.current_handler_user_name = COALESCE(d.current_handler_user_name, u.display_name)
WHERE d.deleted = 0 AND d.current_handler_user_id IS NULL;

-- 差异流转记录表
CREATE TABLE IF NOT EXISTS req_difference_flow_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    difference_id BIGINT NOT NULL COMMENT '差异 ID（req_difference.id）',
    action VARCHAR(16) NOT NULL COMMENT 'SEND 流转',
    from_user_id BIGINT NULL COMMENT '操作人用户 ID',
    from_user_name VARCHAR(64) NULL COMMENT '操作人姓名',
    to_user_id BIGINT NULL COMMENT '流转目标用户 ID',
    to_user_name VARCHAR(64) NULL COMMENT '流转目标用户姓名',
    comment VARCHAR(500) NULL COMMENT '流转说明',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_flow_diff (tenant_id, difference_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新建项目差异流转记录（按差异单次流转一人）';
