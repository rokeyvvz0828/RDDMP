INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 203, 1, 200, 'menu', '流程监控', 'WorkflowMonitor', '/workflow/monitor', 'workflow/index', 'workflow:access', 'monitor', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 203);
INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 203, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 203 AND tenant_id = 1);