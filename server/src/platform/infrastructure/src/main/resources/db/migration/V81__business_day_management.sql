-- 说明：REQ-20260821-037 营业日管理正式业务能力、单一 Sidebar 入口与操作权限迁移。
-- 用途：补偿 V80 菜单结构，初始化细粒度权限，并创建环境、日历、需求和业务审计表。

UPDATE sys_menu
SET menu_type = 'menu',
    component_path = 'test-management/business-day',
    icon = 'calendar',
    visible = 1,
    status = 1,
    deleted = 0
WHERE tenant_id = 1 AND id = 914;

DELETE rp
FROM sys_role_permission rp
JOIN sys_menu_permission p ON p.id = rp.permission_id AND p.tenant_id = rp.tenant_id
WHERE p.tenant_id = 1 AND p.menu_id IN (948, 949, 950, 951);

DELETE FROM sys_role_menu
WHERE tenant_id = 1 AND menu_id IN (948, 949, 950, 951);

UPDATE sys_menu
SET visible = 0, status = 0, deleted = 1
WHERE tenant_id = 1 AND id IN (948, 949, 950, 951) AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (9141, 1, 914, 'read', 'test-management:business-day:access', '查看'),
    (9142, 1, 914, 'create', 'test-management:business-day:access:create', '新增'),
    (9143, 1, 914, 'update', 'test-management:business-day:access:update', '修改'),
    (9144, 1, 914, 'delete', 'test-management:business-day:access:delete', '删除'),
    (9145, 1, 914, 'import', 'test-management:business-day:access:import', '导入'),
    (9146, 1, 914, 'export', 'test-management:business-day:access:export', '导出'),
    (9147, 1, 914, 'review', 'test-management:business-day:access:review', '评审');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 914, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 914 AND status = 1;

CREATE TABLE tm_test_environment (
    id BIGINT PRIMARY KEY COMMENT '测试环境主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    env_code VARCHAR(64) NOT NULL COMMENT '环境编码',
    env_name VARCHAR(128) NOT NULL COMMENT '环境名称',
    purpose VARCHAR(255) NULL COMMENT '环境用途',
    theme VARCHAR(24) NOT NULL DEFAULT 'brand' COMMENT '语义主题',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0停用、1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tm_environment_code (tenant_id, env_code, deleted),
    KEY idx_tm_environment_list (tenant_id, enabled, sort_no, deleted)
) COMMENT='营业日测试环境';

CREATE TABLE tm_calendar_schedule (
    id BIGINT PRIMARY KEY COMMENT '日历安排主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    env_code VARCHAR(64) NOT NULL COMMENT '环境编码',
    natural_date DATE NOT NULL COMMENT '自然日',
    business_date CHAR(8) NOT NULL COMMENT '营业日 YYYYMMDD',
    has_batch TINYINT NOT NULL DEFAULT 0 COMMENT '是否跑批',
    batch_type VARCHAR(16) NULL COMMENT '全量/增量/初始化/翻数',
    batch_time TIME NULL COMMENT '跑批时间',
    systems_json JSON NOT NULL COMMENT '涉及系统 JSON 数组',
    validation_content VARCHAR(1000) NULL COMMENT '验证内容',
    maintainer VARCHAR(128) NULL COMMENT '维护人说明',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tm_schedule_natural (tenant_id, env_code, natural_date, deleted),
    KEY idx_tm_schedule_month (tenant_id, natural_date, deleted),
    KEY idx_tm_schedule_batch (tenant_id, has_batch, batch_type, deleted)
) COMMENT='营业日日历安排';

CREATE TABLE tm_batch_requirement (
    id BIGINT PRIMARY KEY COMMENT '跑批需求主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    env_code VARCHAR(64) NOT NULL COMMENT '环境编码',
    natural_date VARCHAR(10) NOT NULL COMMENT 'YYYY-MM 或 YYYY-MM-DD',
    business_date CHAR(8) NOT NULL COMMENT '目标营业日 YYYYMMDD',
    has_batch TINYINT NOT NULL DEFAULT 1 COMMENT '是否跑批',
    batch_type VARCHAR(16) NULL COMMENT '全量/增量/初始化/翻数',
    batch_time TIME NULL COMMENT '跑批时间',
    systems_json JSON NOT NULL COMMENT '涉及系统 JSON 数组',
    validation_content VARCHAR(1000) NULL COMMENT '验证内容',
    proposer_id BIGINT NOT NULL COMMENT '提出人用户主键',
    reviewer_id BIGINT NULL COMMENT '评审人用户主键',
    adoption VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    review_comment VARCHAR(500) NULL COMMENT '评审意见',
    reviewed_at TIMESTAMP NULL COMMENT '评审时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_tm_requirement_list (tenant_id, adoption, natural_date, deleted),
    KEY idx_tm_requirement_env (tenant_id, env_code, deleted),
    KEY idx_tm_requirement_proposer (tenant_id, proposer_id, deleted)
) COMMENT='营业日跑批需求';

CREATE TABLE tm_business_day_audit (
    id BIGINT PRIMARY KEY COMMENT '审计主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    entity_type VARCHAR(32) NOT NULL COMMENT 'ENVIRONMENT/SCHEDULE/REQUIREMENT',
    entity_id BIGINT NOT NULL COMMENT '业务主键',
    action_code VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/IMPORT/REVIEW',
    operator_id BIGINT NOT NULL COMMENT '操作人',
    detail_json JSON NOT NULL COMMENT '审计摘要',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tm_audit_entity (tenant_id, entity_type, entity_id, created_at),
    KEY idx_tm_audit_operator (tenant_id, operator_id, created_at)
) COMMENT='营业日管理业务审计';
