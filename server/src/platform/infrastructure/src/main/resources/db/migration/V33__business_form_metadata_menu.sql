-- Expose the business form metadata configuration entry in system settings.
-- Form metadata tables are created by V32; this migration only adds menu and permissions.

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 107, 1, 100, 'menu', '输入项配置', 'SystemFormMetadata', '/system/form-metadata', 'system/form-metadata/index', 'system:form-config:list', 'setting', 70
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 107 OR route_path = '/system/form-metadata'));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id = 107 AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1071, 1, id, 'read', 'system:form-config:list', '查看'
FROM sys_menu WHERE tenant_id = 1 AND id = 107 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1072, 1, id, 'create', 'system:form-config:list:create', '新增'
FROM sys_menu WHERE tenant_id = 1 AND id = 107 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1073, 1, id, 'update', 'system:form-config:list:update', '修改'
FROM sys_menu WHERE tenant_id = 1 AND id = 107 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1074, 1, id, 'delete', 'system:form-config:list:delete', '删除'
FROM sys_menu WHERE tenant_id = 1 AND id = 107 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 107;

