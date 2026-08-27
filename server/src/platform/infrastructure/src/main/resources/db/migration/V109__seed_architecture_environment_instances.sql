-- REQ-20260825-053：环境部署实例菜单、权限与角色绑定。
-- 补充 811 菜单与权限，授予超管及环境资源办理角色。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 811, 1, 800, 'menu', '环境部署实例', 'ArchitectureInstances',
       '/architecture/instances', 'architecture/instances/index',
       'architecture:instance:view', 'cpu', 57
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 811);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8111, 1, 811, 'view', 'architecture:instance:view', '查看环境部署实例'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 811 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8111);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8112, 1, 811, 'manage', 'architecture:instance:manage', '维护环境部署实例与灾备关系'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 811 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8112);

-- 环境资源办理人员赋予实例菜单与权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 114, 811, 1
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = 114 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 811 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 114, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8111, 8112)
  AND permission.status = 1;

-- 超管赋予实例菜单与权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 811, 1
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 811 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8111, 8112)
  AND permission.status = 1;
