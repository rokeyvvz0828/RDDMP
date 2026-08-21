-- Data migration menu hierarchy correction. Resolve data-migration nodes by their
-- stable routes so a menu ID already owned by another module is never repurposed.

SET @dm_root_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0
      AND (route_name = 'DataMigration' OR route_path = '/data-migration')
    ORDER BY id LIMIT 1
);
SET @dm_next_menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no)
SELECT @dm_next_menu_id, 1, 0, 'directory', '数据迁移', 'DataMigration',
       '/data-migration', 'LAYOUT', 'data-migration:access', 'takeaway-box', 80
WHERE @dm_root_id IS NULL;

SET @dm_root_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0
      AND (route_name = 'DataMigration' OR route_path = '/data-migration')
    ORDER BY id LIMIT 1
);
SET @dm_dashboard_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0
      AND (route_name = 'DataMigrationDashboard'
           OR route_path = '/data-migration/dashboard')
    ORDER BY id LIMIT 1
);
SET @dm_next_menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu);

-- V35 may have skipped its fixed dashboard ID when that ID belonged to an
-- existing platform menu. Create the missing directory without overwriting it.
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no)
SELECT @dm_next_menu_id, 1, @dm_root_id, 'directory', '数迁资产看板',
       'DataMigrationDashboard', '/data-migration/dashboard', 'LAYOUT',
       'data-migration:access', 'data-analysis', 10
WHERE @dm_dashboard_id IS NULL;

SET @dm_dashboard_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0
      AND (route_name = 'DataMigrationDashboard'
           OR route_path = '/data-migration/dashboard')
    ORDER BY id LIMIT 1
);

UPDATE sys_menu
SET parent_id = @dm_root_id,
    sort_no = CASE route_name
        WHEN 'DataMigrationDashboard' THEN 10
        WHEN 'DataMigrationContent' THEN 20
        WHEN 'DataMigrationBase' THEN 30
        ELSE sort_no
    END,
    menu_name = CASE route_name
        WHEN 'DataMigrationDashboard' THEN '数迁资产看板'
        WHEN 'DataMigrationContent' THEN '数迁资产内容'
        WHEN 'DataMigrationBase' THEN '基础资料管理'
        ELSE menu_name
    END
WHERE tenant_id = 1 AND deleted = 0
  AND route_name IN ('DataMigrationDashboard', 'DataMigrationContent', 'DataMigrationBase');

-- Repair dashboard children that V35 may have attached to an unrelated menu
-- when its fixed dashboard ID was already occupied.
UPDATE sys_menu
SET parent_id = @dm_dashboard_id,
    sort_no = CASE route_name
        WHEN 'DataMigrationOverall' THEN 10
        WHEN 'DataMigrationComponent' THEN 20
        ELSE sort_no
    END
WHERE tenant_id = 1 AND deleted = 0
  AND route_name IN ('DataMigrationOverall', 'DataMigrationComponent');

-- Keep administrator bindings intact and grant access to directories created
-- by this correction without relying on their dynamically allocated IDs.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1
FROM sys_menu
WHERE tenant_id = 1 AND deleted = 0
  AND route_name IN ('DataMigration', 'DataMigrationDashboard');

SET @dm_next_permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu_permission);
INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT @dm_next_permission_id, 1, @dm_root_id, 'read',
       'data-migration:access', '查看'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu_permission
    WHERE tenant_id = 1 AND menu_id = @dm_root_id AND action_code = 'read'
);

SET @dm_next_permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu_permission);
INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT @dm_next_permission_id, 1, @dm_dashboard_id, 'read',
       'data-migration:access', '查看'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu_permission
    WHERE tenant_id = 1 AND menu_id = @dm_dashboard_id AND action_code = 'read'
);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
JOIN sys_menu menu ON menu.id = permission.menu_id AND menu.tenant_id = permission.tenant_id
WHERE permission.tenant_id = 1 AND permission.action_code = 'read'
  AND menu.deleted = 0
  AND menu.route_name IN ('DataMigration', 'DataMigrationDashboard');
