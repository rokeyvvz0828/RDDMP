-- Repair the metadata view designer menu without changing published V34.
-- This migration is idempotent for databases that already ran V34.

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible, status)
SELECT 108, 1, 100, 'menu', '表单视图设计器', 'SystemFormDesignerPrototype', '/system/form-designer-prototype', 'system/form-designer-prototype/index', 'system:form-config:list', 'edit-pen', 75, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 108 OR route_path = '/system/form-designer-prototype'));

UPDATE sys_menu
SET menu_name = '表单视图设计器', visible = 1, status = 1, deleted = 0
WHERE tenant_id = 1 AND (id = 108 OR route_path = '/system/form-designer-prototype');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id = 108 AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1081, 1, id, 'read', 'system:form-config:list', '查看'
FROM sys_menu WHERE tenant_id = 1 AND id = 108 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1082, 1, id, 'create', 'system:form-config:list:create', '新增'
FROM sys_menu WHERE tenant_id = 1 AND id = 108 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1083, 1, id, 'update', 'system:form-config:list:update', '修改'
FROM sys_menu WHERE tenant_id = 1 AND id = 108 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 1084, 1, id, 'delete', 'system:form-config:list:delete', '删除'
FROM sys_menu WHERE tenant_id = 1 AND id = 108 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 108;
