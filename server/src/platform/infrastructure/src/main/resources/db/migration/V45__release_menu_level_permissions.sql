-- 将配置管理拆分为可按角色独立授权的六个子菜单，并保留存量角色的等价权限。
UPDATE sys_menu
SET menu_type = 'directory',
    menu_name = '配置管理',
    route_name = 'ReleaseManagementRoot',
    route_path = '/release',
    component_path = 'LAYOUT',
    permission_code = 'release:access',
    icon = 'package-check',
    sort_no = 600
WHERE tenant_id = 1 AND id = 600 AND deleted = 0;

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (610, 1, 600, 'menu', '投产窗口', 'ReleaseWindows', '/release/windows', 'release/index', 'release:window:view', 'calendar', 10),
    (611, 1, 600, 'menu', '版本申请', 'ReleaseApplications', '/release/applications', 'release/index', 'release:application:view', 'document', 20),
    (612, 1, 600, 'menu', '投产基线', 'ReleaseProductionBaseline', '/release/production-baseline', 'release/index', 'release:baseline:view', 'tickets', 30),
    (613, 1, 600, 'menu', '生产版本', 'ReleaseProductionVersions', '/release/production-versions', 'release/index', 'release:production-version:view', 'monitor', 40),
    (614, 1, 600, 'menu', '统计分析', 'ReleaseAnalytics', '/release/analytics', 'release/index', 'release:analytics:view', 'data-analysis', 50),
    (615, 1, 600, 'menu', '审批流程配置', 'ReleaseWorkflowBindings', '/release/workflow-bindings', 'release/index', 'release:workflow-config:view', 'setting', 60);

UPDATE sys_menu_permission SET menu_id = 610 WHERE tenant_id = 1 AND id IN (6002, 6003, 6004);
UPDATE sys_menu_permission SET menu_id = 611 WHERE tenant_id = 1 AND id IN (6005, 6006, 6007, 6008, 6009, 6010);
UPDATE sys_menu_permission
SET menu_id = 612,
    action_code = 'baseline-view',
    permission_code = 'release:baseline:view',
    permission_name = '查看投产基线'
WHERE tenant_id = 1 AND id = 6011;
UPDATE sys_menu_permission
SET menu_id = 612,
    action_code = 'baseline-update',
    permission_code = 'release:baseline:update',
    permission_name = '维护投产结果'
WHERE tenant_id = 1 AND id = 6012;
UPDATE sys_menu_permission SET menu_id = 614 WHERE tenant_id = 1 AND id = 6013;
UPDATE sys_menu_permission SET menu_id = 615 WHERE tenant_id = 1 AND id IN (6014, 6015);

INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (6016, 1, 613, 'production-version-view', 'release:production-version:view', '查看生产版本');

-- 父目录不再提供独立勾选项；其访问权限由子菜单的角色菜单关系继承。
UPDATE sys_menu_permission SET status = 0 WHERE tenant_id = 1 AND id = 6001;

-- 原生产查看权限同时覆盖两个页面，迁移后保留基线权限并补授生产版本查看权限。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT role_id, 6016, tenant_id
FROM sys_role_permission
WHERE tenant_id = 1 AND permission_id = 6011;

-- 根据角色动作权限重建新增子菜单关系，并确保父目录只随有效子菜单保留。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT rp.role_id, p.menu_id, rp.tenant_id
FROM sys_role_permission rp
JOIN sys_menu_permission p ON p.id = rp.permission_id AND p.tenant_id = rp.tenant_id
WHERE rp.tenant_id = 1 AND p.menu_id BETWEEN 610 AND 615 AND p.status = 1;

DELETE parent_rm
FROM sys_role_menu parent_rm
LEFT JOIN sys_role_menu child_rm
  ON child_rm.tenant_id = parent_rm.tenant_id
 AND child_rm.role_id = parent_rm.role_id
 AND child_rm.menu_id BETWEEN 610 AND 615
WHERE parent_rm.tenant_id = 1
  AND parent_rm.menu_id = 600
  AND child_rm.role_id IS NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_id, 600, tenant_id
FROM sys_role_menu
WHERE tenant_id = 1 AND menu_id BETWEEN 610 AND 615;
