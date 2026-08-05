INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (400, 1, 0, 'menu', '组件示例', 'ComponentShowcase', '/components', 'examples/components', 'system:access', 'grid', 400);

INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 400, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 400 AND tenant_id = 1);