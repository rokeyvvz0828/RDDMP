-- Project-scoped role permissions. Existing sys_role_permission rows are retained for rollback.
CREATE TABLE pm_project_role_permission (
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT 'Tenant id',
    project_id BIGINT NOT NULL COMMENT 'Project id',
    role_id BIGINT NOT NULL COMMENT 'Project role id',
    permission_id BIGINT NOT NULL COMMENT 'Permission catalog id',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    PRIMARY KEY (tenant_id, project_id, role_id, permission_id),
    KEY idx_pm_project_role_permission_user_lookup (tenant_id, project_id, role_id),
    KEY idx_pm_project_role_permission_permission (tenant_id, permission_id)
) COMMENT='Project role permission relation';

-- PM receives every active business permission. System administration permissions are excluded.
INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, permission.id
FROM pm_project_role r
JOIN sys_menu_permission permission ON permission.tenant_id = r.tenant_id AND permission.status = 1
WHERE r.deleted = 0
  AND r.role_code = 'PM'
  AND permission.permission_code NOT LIKE 'system:%';

-- Other existing roles start read-only. Roles created after this migration start with no permissions.
INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, permission.id
FROM pm_project_role r
JOIN sys_menu_permission permission ON permission.tenant_id = r.tenant_id
    AND permission.status = 1 AND permission.action_code = 'read'
WHERE r.deleted = 0
  AND r.role_code <> 'PM'
  AND permission.permission_code NOT LIKE 'system:%';

UPDATE sys_menu
SET menu_name = '权限维护', route_name = 'SystemPermissions', route_path = '/system/permissions',
    component_path = 'system/permissions/index', permission_code = 'system:role:list'
WHERE tenant_id = 1 AND id = 102 AND deleted = 0;
