INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (200, 1, 0, 'directory', 'Workflow', 'WorkflowRoot', '/workflow', 'LAYOUT', 'workflow:access', 'grid', 200);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (201, 1, 200, 'menu', 'Definitions', 'WorkflowDefinitions', '/workflow/definitions', 'workflow/index', 'workflow:access', 'grid', 10);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (202, 1, 200, 'menu', 'Inbox', 'WorkflowInbox', '/workflow/inbox', 'workflow/index', 'workflow:access', 'grid', 20);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (300, 1, 0, 'directory', 'AI Control', 'AiRoot', '/ai', 'LAYOUT', 'ai:access', 'setting', 300);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (301, 1, 300, 'menu', 'Providers', 'AiProviders', '/ai/providers', 'ai/index', 'ai:access', 'setting', 10);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (302, 1, 300, 'menu', 'Models', 'AiModels', '/ai/models', 'ai/index', 'ai:access', 'setting', 20);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES (303, 1, 300, 'menu', 'Capability Routes', 'AiRoutes', '/ai/routes', 'ai/index', 'ai:access', 'setting', 30);

INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id >= 200;
