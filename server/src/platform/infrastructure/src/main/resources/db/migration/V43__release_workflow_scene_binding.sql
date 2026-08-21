-- 配置管理按项目维护审批场景与已发布流程的绑定。
CREATE TABLE rel_workflow_binding (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_ref VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    scene_code VARCHAR(32) NOT NULL,
    workflow_definition_id BIGINT NULL,
    workflow_code VARCHAR(96) NULL,
    workflow_name VARCHAR(128) NULL,
    workflow_version INT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rel_workflow_binding_scene (tenant_id, project_ref, scene_code),
    KEY idx_rel_workflow_binding_definition (tenant_id, workflow_definition_id)
) COMMENT='配置管理审批场景流程绑定';

CREATE TABLE rel_workflow_binding_history (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    project_ref VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    scene_code VARCHAR(32) NOT NULL,
    before_definition_id BIGINT NULL,
    before_workflow_code VARCHAR(96) NULL,
    before_workflow_name VARCHAR(128) NULL,
    before_workflow_version INT NULL,
    after_definition_id BIGINT NULL,
    after_workflow_code VARCHAR(96) NULL,
    after_workflow_name VARCHAR(128) NULL,
    after_workflow_version INT NULL,
    change_reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rel_workflow_binding_history (tenant_id, project_ref, scene_code, occurred_at),
    KEY idx_rel_workflow_binding_history_definition (tenant_id, before_definition_id, after_definition_id)
) COMMENT='配置管理审批场景流程绑定历史';

ALTER TABLE rel_release_application MODIFY workflow_code VARCHAR(96) NULL;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6014, 1, id, 'workflow-config-view', 'release:workflow-config:view', '查看审批流程配置'
FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 6015, 1, id, 'workflow-config-update', 'release:workflow-config:update', '维护审批流程配置'
FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND permission_code IN ('release:workflow-config:view', 'release:workflow-config:update');
