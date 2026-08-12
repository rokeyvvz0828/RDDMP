-- Expose the platform form designer prototype under system settings.
-- The page is an in-memory frontend prototype and does not change form metadata persistence.

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 108, 1, 100, 'menu', '表单设计器原型', 'SystemFormDesignerPrototype', '/system/form-designer-prototype', 'system/form-designer-prototype/index', 'system:form-config:list', 'edit-pen', 75
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 108 OR route_path = '/system/form-designer-prototype'));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id = 108 AND deleted = 0;
