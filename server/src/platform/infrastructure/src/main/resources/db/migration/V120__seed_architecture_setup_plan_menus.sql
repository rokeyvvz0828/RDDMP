-- REQ-20260830-056：环境搭建计划与搭建计划模板菜单、权限与角色绑定。
-- 只追加新授权，不删除既有 80x/81x 权限记录。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 814, 1, 800, 'menu', '环境搭建计划', 'ArchitecturePlans',
       '/architecture/plans', 'architecture/plans/index',
       'architecture:plan:view', 'tickets', 58
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 814);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 815, 1, 800, 'menu', '搭建计划模板', 'ArchitecturePlanTemplates',
       '/architecture/plan-templates', 'architecture/plan-templates/index',
       'architecture:plan-template:view', 'document', 59
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 815);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8141, 1, 814, 'view', 'architecture:plan:view', '查看环境搭建计划'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 814 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8141);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8142, 1, 814, 'manage', 'architecture:plan:manage', '管理搭建计划（创建、调整、取消恢复、依赖、阻塞、时间、工单关联）'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 814 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8142);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8151, 1, 815, 'view', 'architecture:plan-template:view', '查看搭建计划模板'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 815 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8151);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8152, 1, 815, 'manage', 'architecture:plan-template:manage', '维护和发布搭建计划模板'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 815 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8152);

-- 环境资源办理人员赋予计划与模板菜单（模板仅查看）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 114, 814, 1
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = 114 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 814 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 114, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8141, 8142)
  AND permission.status = 1;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 114, 815, 1
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = 114 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 815 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 114, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8151)
  AND permission.status = 1;

-- 超管赋予计划与模板菜单及全部权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 814, 1
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 814 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8141, 8142)
  AND permission.status = 1;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 815, 1
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 815 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8151, 8152)
  AND permission.status = 1;
