-- =============================================================================
-- V95：存量需求成员（用户区分参考新建项目 req_project_member）
-- 存量需求按需求头维护成员：成员可见并维护该需求，PMO/管理员全量可见。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- =============================================================================

CREATE TABLE IF NOT EXISTS req_legacy_member (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    requirement_id BIGINT NOT NULL COMMENT '存量需求 ID（req_legacy_requirement.id）',
    user_id BIGINT NOT NULL COMMENT '成员用户 ID',
    user_name VARCHAR(64) NULL COMMENT '成员姓名',
    member_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：MEMBER 成员',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_legacy_member (tenant_id, requirement_id, user_id),
    KEY idx_legacy_member_user (tenant_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量需求成员（参考新建项目 req_project_member）';
