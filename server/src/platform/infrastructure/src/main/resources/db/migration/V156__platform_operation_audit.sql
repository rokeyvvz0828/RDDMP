ALTER TABLE sys_operation_log
    ADD COLUMN operator_name VARCHAR(128) NULL AFTER operator_id,
    ADD COLUMN module_code VARCHAR(64) NULL AFTER operation_code,
    ADD COLUMN module_name VARCHAR(128) NULL AFTER module_code,
    ADD COLUMN operation_type VARCHAR(32) NULL AFTER module_name,
    ADD COLUMN target_type VARCHAR(64) NULL AFTER operation_type,
    ADD COLUMN target_id VARCHAR(128) NULL AFTER target_type,
    ADD COLUMN project_id BIGINT NULL AFTER target_id,
    ADD COLUMN project_name VARCHAR(128) NULL AFTER project_id,
    ADD COLUMN http_status INT NULL AFTER success,
    ADD COLUMN duration_ms BIGINT NULL AFTER http_status,
    ADD COLUMN user_agent VARCHAR(512) NULL AFTER client_ip,
    ADD COLUMN changed_fields VARCHAR(1000) NULL AFTER user_agent,
    ADD KEY idx_sys_operation_log_project_created (tenant_id, project_id, created_at),
    ADD KEY idx_sys_operation_log_type_created (tenant_id, operation_type, created_at);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path,
     permission_code, icon, sort_no, visible, status, deleted)
SELECT 108, 1, 100, 'menu', '审计日志', 'SystemAudit', '/system/audit', 'system/audit/index',
       'system:audit:list', 'document', 80, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 108 OR route_path = '/system/audit')
);

INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name, status)
SELECT 1081, 1, id, 'read', 'system:audit:list', '查看', 1
FROM sys_menu
WHERE tenant_id = 1 AND route_path = '/system/audit' AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT r.id, m.id, 1
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.route_path = '/system/audit' AND m.deleted = 0
WHERE r.tenant_id = 1 AND r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND r.deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT r.id, p.id, 1
FROM sys_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id
    AND p.permission_code = 'system:audit:list' AND p.status = 1
WHERE r.tenant_id = 1 AND r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND r.deleted = 0;
