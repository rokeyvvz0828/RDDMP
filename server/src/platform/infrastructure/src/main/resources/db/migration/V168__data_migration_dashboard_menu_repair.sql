-- 修复数迁资产看板菜单缺失。
-- V84 使用的 700/701/702 已被 V68 的需求管理菜单占用，INSERT IGNORE
-- 会静默跳过看板记录。本迁移使用未占用编号，并按路由幂等补齐菜单和 RBAC 绑定。
-- 仅追加，不修改已发布迁移；菜单仍挂在数据迁移（699）下。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 1350, 1, 699, 'directory', '数迁资产看板', 'DataMigrationDashboard',
       '/data-migration/dashboard', 'LAYOUT', 'data-migration:access',
       'data-analysis', 10, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDashboard'
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 1351, 1,
       (SELECT id FROM sys_menu
        WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDashboard'
        ORDER BY id LIMIT 1),
       'menu', '整体看板', 'DataMigrationOverall',
       '/data-migration/dashboard/overall', 'data-migration',
       'data-migration:dashboard:overall', 'dashboard', 10, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationOverall'
)
  AND EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDashboard'
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 1352, 1,
       (SELECT id FROM sys_menu
        WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDashboard'
        ORDER BY id LIMIT 1),
       'menu', '组件看板', 'DataMigrationComponent',
       '/data-migration/dashboard/components', 'data-migration',
       'data-migration:dashboard:components', 'pie-chart', 20, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationComponent'
)
  AND EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDashboard'
);

SET @dm_dashboard_menu_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDashboard'
    ORDER BY id LIMIT 1
);
SET @dm_overall_menu_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationOverall'
    ORDER BY id LIMIT 1
);
SET @dm_component_menu_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationComponent'
    ORDER BY id LIMIT 1
);

-- 看板子菜单只需要查看权限；目录复用 data-migration:access，避免重复注册全局权限码。
INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT @dm_overall_menu_id * 10 + 1, 1, @dm_overall_menu_id, 'read',
       'data-migration:dashboard:overall', '查看'
WHERE @dm_overall_menu_id IS NOT NULL;

INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT @dm_component_menu_id * 10 + 1, 1, @dm_component_menu_id, 'read',
       'data-migration:dashboard:components', '查看'
WHERE @dm_component_menu_id IS NOT NULL;

-- 1=超级管理员，200=数据迁移管理员，201=数据迁移开发人员，保持 V87 的授权范围。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT r.id, m.id, 1
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = 1 AND m.deleted = 0
WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.status = 1
  AND r.id IN (1, 200, 201)
  AND m.id IN (@dm_dashboard_menu_id, @dm_overall_menu_id, @dm_component_menu_id);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT r.id, p.id, 1
FROM sys_role r
JOIN sys_menu_permission p ON p.tenant_id = 1 AND p.status = 1
WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.status = 1
  AND r.id IN (1, 200, 201)
  AND p.menu_id IN (@dm_overall_menu_id, @dm_component_menu_id);
