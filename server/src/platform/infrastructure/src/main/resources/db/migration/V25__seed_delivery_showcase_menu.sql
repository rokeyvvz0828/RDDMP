INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 500, 1, 0, 'menu', '交付示范中心', 'DeliveryShowcase', '/delivery-showcase', 'examples/delivery-showcase', 'system:access', 'tickets', 500
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 500 OR route_path = '/delivery-showcase'));

INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 500, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 500 AND tenant_id = 1);
