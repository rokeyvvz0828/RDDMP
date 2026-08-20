-- 配置管理正式业务表、菜单与权限。业务验收数据只能通过正式 API 创建。
CREATE TABLE rel_release_window (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    window_code VARCHAR(64) NOT NULL,
    window_name VARCHAR(128) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    declaration_start DATETIME(6) NOT NULL,
    declaration_end DATETIME(6) NOT NULL,
    production_start DATETIME(6) NOT NULL,
    production_end DATETIME(6) NOT NULL,
    regular_enabled TINYINT(1) NOT NULL DEFAULT 1,
    description VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_window_code (tenant_id, window_code, deleted),
    KEY idx_rel_window_project_period (tenant_id, project_id, production_start, production_end, deleted),
    KEY idx_rel_window_declaration (tenant_id, declaration_start, declaration_end, deleted)
) COMMENT='投产窗口';

CREATE TABLE rel_window_change_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    window_id BIGINT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    old_value VARCHAR(2000) NULL,
    new_value VARCHAR(2000) NULL,
    change_reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rel_window_change (tenant_id, window_id, occurred_at)
) COMMENT='投产窗口变更日志';

CREATE TABLE rel_release_application (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_code VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    emergency TINYINT(1) NOT NULL DEFAULT 0,
    window_id BIGINT NULL,
    assigned_window_id BIGINT NULL,
    subsystem_id VARCHAR(64) NOT NULL,
    subsystem_code VARCHAR(64) NOT NULL,
    subsystem_name VARCHAR(128) NOT NULL,
    version_type VARCHAR(24) NOT NULL,
    characteristic VARCHAR(24) NOT NULL,
    workflow_code VARCHAR(96) NOT NULL,
    application_status VARCHAR(24) NOT NULL,
    requester_id BIGINT NOT NULL,
    requester_name VARCHAR(128) NOT NULL,
    requester_department VARCHAR(128) NULL,
    emergency_description VARCHAR(1000) NULL,
    urgent_reason VARCHAR(1000) NULL,
    additional_reason VARCHAR(1000) NULL,
    description VARCHAR(2000) NULL,
    approved_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_application_code (tenant_id, application_code, deleted),
    KEY idx_rel_application_project (tenant_id, project_id, application_status, updated_at),
    KEY idx_rel_application_window (tenant_id, window_id, application_status),
    KEY idx_rel_application_assigned_window (tenant_id, assigned_window_id, application_status),
    KEY idx_rel_application_requester (tenant_id, requester_id, application_status)
) COMMENT='版本申请';

CREATE TABLE rel_application_delivery (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    delivery_unit_id VARCHAR(64) NOT NULL,
    delivery_unit_code VARCHAR(64) NOT NULL,
    delivery_unit_name VARCHAR(128) NOT NULL,
    artifact_type VARCHAR(24) NOT NULL,
    artifact_version VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_application_delivery (tenant_id, application_id, delivery_unit_code),
    KEY idx_rel_delivery_conflict (tenant_id, delivery_unit_code, application_id)
) COMMENT='版本申请交付单元快照';

CREATE TABLE rel_application_requirement (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    requirement_code VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_application_requirement (tenant_id, application_id, requirement_code),
    KEY idx_rel_requirement_code (tenant_id, requirement_code)
) COMMENT='版本申请需求编号';

CREATE TABLE rel_application_attachment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    attachment_category VARCHAR(32) NOT NULL,
    file_name_snapshot VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_application_attachment (tenant_id, application_id, attachment_id),
    KEY idx_rel_attachment_id (tenant_id, attachment_id)
) COMMENT='版本申请附件关联';

CREATE TABLE rel_application_round (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    workflow_code VARCHAR(96) NOT NULL,
    workflow_definition_id BIGINT NULL,
    workflow_definition_version INT NULL,
    workflow_instance_id BIGINT NULL,
    round_status VARCHAR(24) NOT NULL,
    data_digest CHAR(64) NOT NULL,
    submitted_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_application_round (tenant_id, application_id, round_no),
    UNIQUE KEY uk_rel_workflow_instance (tenant_id, workflow_instance_id),
    KEY idx_rel_round_status (tenant_id, round_status, updated_at)
) COMMENT='版本申请审批轮次';

CREATE TABLE rel_application_relation (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    related_application_id BIGINT NOT NULL,
    delivery_unit_code VARCHAR(64) NULL,
    relation_type VARCHAR(32) NOT NULL,
    previous_version VARCHAR(128) NULL,
    current_version VARCHAR(128) NULL,
    relation_reason VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_application_relation (tenant_id, application_id, related_application_id, delivery_unit_code, relation_type),
    KEY idx_rel_relation_related (tenant_id, related_application_id, relation_type)
) COMMENT='版本申请关系';

CREATE TABLE rel_application_event (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NULL,
    event_reason VARCHAR(1000) NULL,
    payload_json JSON NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rel_application_event (tenant_id, application_id, occurred_at),
    KEY idx_rel_event_type (tenant_id, event_type, occurred_at)
) COMMENT='版本申请业务事件';

CREATE TABLE rel_production_entry (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    window_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    application_code VARCHAR(64) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    subsystem_id VARCHAR(64) NOT NULL,
    subsystem_code VARCHAR(64) NOT NULL,
    subsystem_name VARCHAR(128) NOT NULL,
    delivery_unit_id VARCHAR(64) NOT NULL,
    delivery_unit_code VARCHAR(64) NOT NULL,
    delivery_unit_name VARCHAR(128) NOT NULL,
    artifact_type VARCHAR(24) NOT NULL,
    artifact_version VARCHAR(128) NOT NULL,
    version_type VARCHAR(24) NOT NULL,
    characteristic VARCHAR(24) NOT NULL,
    production_result VARCHAR(24) NOT NULL DEFAULT 'RELEASED',
    production_at DATETIME(6) NULL,
    result_reason VARCHAR(1000) NULL,
    active_candidate TINYINT(1) NOT NULL DEFAULT 1,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_production_source (tenant_id, window_id, application_id, delivery_unit_code),
    KEY idx_rel_production_candidate (tenant_id, window_id, subsystem_code, delivery_unit_code, active_candidate),
    KEY idx_rel_production_current (tenant_id, subsystem_code, delivery_unit_code, production_result, production_at)
) COMMENT='投产基线明细';

CREATE TABLE rel_production_result_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    production_entry_id BIGINT NOT NULL,
    from_result VARCHAR(24) NOT NULL,
    to_result VARCHAR(24) NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    production_at_before DATETIME(6) NULL,
    production_at_after DATETIME(6) NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rel_production_log (tenant_id, production_entry_id, occurred_at)
) COMMENT='投产结果变更日志';

CREATE TABLE rel_workflow_event_receipt (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    workflow_event_id VARCHAR(64) NOT NULL,
    workflow_instance_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    consumer_key VARCHAR(96) NOT NULL,
    receipt_status VARCHAR(24) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_workflow_receipt (tenant_id, workflow_event_id, consumer_key),
    KEY idx_rel_workflow_application (tenant_id, application_id, round_no)
) COMMENT='配置管理工作流事件回执';

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 600, 1, 0, 'menu', '配置管理', 'ReleaseManagement', '/release', 'release/index', 'release:access', 'package-check', 600
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 600 OR route_path = '/release'));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6001, 1, id, 'read', 'release:access', '访问配置管理' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6002, 1, id, 'window-view', 'release:window:view', '查看投产窗口' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6003, 1, id, 'window-create', 'release:window:create', '新增投产窗口' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6004, 1, id, 'window-update', 'release:window:update', '修改投产窗口' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6005, 1, id, 'application-view', 'release:application:view', '查看版本申请' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6006, 1, id, 'application-create', 'release:application:create', '新增版本申请' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6007, 1, id, 'application-update', 'release:application:update', '修改版本申请' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6008, 1, id, 'application-submit', 'release:application:submit', '提交版本申请' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6009, 1, id, 'application-withdraw', 'release:application:withdraw', '撤回版本申请' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6010, 1, id, 'application-cancel', 'release:application:cancel', '取消版本申请' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6011, 1, id, 'production-view', 'release:production:view', '查看投产基线' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6012, 1, id, 'production-update', 'release:production:update', '维护投产结果' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6013, 1, id, 'analytics-view', 'release:analytics:view', '查看配置统计' FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 600;
