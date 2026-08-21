-- 修复项目管理菜单与交付示范中心的历史 ID 冲突。
-- V35 已执行，不能修改历史迁移；本迁移将项目权限节点重新挂到独立的项目菜单下。
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
                      component_path, permission_code, icon, sort_no, visible)
SELECT 505, 1, 0, 'menu', '项目管理', 'ProjectManagement', '/projects', 'project/index',
       'project:access', 'briefcase', 505, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 505);

UPDATE sys_menu
SET parent_id = 505
WHERE tenant_id = 1
  AND id IN (501, 502, 503, 504)
  AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1
FROM sys_menu
WHERE tenant_id = 1
  AND id IN (505, 501, 502, 503, 504)
  AND deleted = 0;
