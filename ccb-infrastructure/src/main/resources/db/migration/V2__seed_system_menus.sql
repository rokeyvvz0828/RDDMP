INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (100, 1, 0, 'directory', 'System', 'SystemRoot', '/system', 'LAYOUT', 'system:access', 100);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (101, 1, 100, 'menu', 'Users', 'SystemUsers', '/system/users', 'system/users/index', 'system:user:list', 10);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (102, 1, 100, 'menu', 'Roles', 'SystemRoles', '/system/roles', 'system/roles/index', 'system:role:list', 20);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (103, 1, 100, 'menu', 'Organizations', 'SystemOrgs', '/system/orgs', 'system/orgs/index', 'system:org:list', 30);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (104, 1, 100, 'menu', 'Menus', 'SystemMenus', '/system/menus', 'system/menus/index', 'system:menu:list', 40);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (105, 1, 100, 'menu', 'Dictionaries', 'SystemDicts', '/system/dicts', 'system/dicts/index', 'system:dict:list', 50);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, sort_no)
VALUES (106, 1, 100, 'menu', 'Configs', 'SystemConfigs', '/system/configs', 'system/configs/index', 'system:config:list', 60);

INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1;

INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (1, 1, 1);
