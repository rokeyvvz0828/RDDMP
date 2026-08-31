-- =============================================================================
-- V94：存量需求-系统人员
-- 一条需求对应主责/协同系统子表多行；系统人员表记录每个系统行的成员用户，
-- 系统成员（或系统行负责人）与当前流转处理人可查看该需求，PMO/管理员全量可见。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- =============================================================================

CREATE TABLE IF NOT EXISTS req_legacy_system_member (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    system_item_id BIGINT NOT NULL COMMENT '系统子表行 ID（req_legacy_system_item.id）',
    user_id BIGINT NOT NULL COMMENT '系统人员用户 ID',
    user_name VARCHAR(64) NULL COMMENT '系统人员姓名',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_member (tenant_id, system_item_id, user_id),
    KEY idx_sys_member_user (tenant_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求-系统人员（主责/协同系统成员可见该需求）';
