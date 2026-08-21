-- 需求管理平台菜单、权限种子（幂等，仅追加）。审批流定义待审批接入需求后另行补充。

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 600, 1, 0, 'directory', '需求管理平台', 'RequirementRoot', '/requirements', 'LAYOUT', 'requirement:access', 'document', 600
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 600);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 601, 1, 600, 'menu', '新建项目', 'RequirementNewProject', '/requirements/new-project', 'requirements/index', 'requirement:project:read', 'document', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 601);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 602, 1, 600, 'menu', '存量项目', 'RequirementLegacy', '/requirements/legacy', 'requirements/index', 'requirement:legacy:read', 'document', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 602);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 603, 1, 600, 'menu', '系统清单', 'RequirementSystems', '/requirements/systems', 'requirements/index', 'requirement:system:read', 'document', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 603);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (6001, 1, 600, 'read', 'requirement:access', '需求管理访问'),
    (6002, 1, 600, 'create', 'requirement:access:create', '需求管理新增'),
    (6003, 1, 600, 'update', 'requirement:access:update', '需求管理修改'),
    (6004, 1, 600, 'delete', 'requirement:access:delete', '需求管理删除'),
    (6011, 1, 601, 'read', 'requirement:project:read', '项目查看'),
    (6012, 1, 601, 'create', 'requirement:project:create', '项目新增'),
    (6013, 1, 601, 'update', 'requirement:project:update', '项目修改'),
    (6014, 1, 601, 'delete', 'requirement:project:delete', '项目删除'),
    (6021, 1, 602, 'read', 'requirement:legacy:read', '存量需求查看'),
    (6022, 1, 602, 'create', 'requirement:legacy:create', '存量需求新增'),
    (6023, 1, 602, 'update', 'requirement:legacy:update', '存量需求修改'),
    (6024, 1, 602, 'delete', 'requirement:legacy:delete', '存量需求删除'),
    (6031, 1, 603, 'read', 'requirement:system:read', '系统清单查看'),
    (6032, 1, 603, 'create', 'requirement:system:create', '系统清单新增'),
    (6033, 1, 603, 'update', 'requirement:system:update', '系统清单修改'),
    (6034, 1, 603, 'delete', 'requirement:system:delete', '系统清单删除'),
    (6101, 1, 601, 'review', 'requirement:diff:review', '差异评审处理'),
    (6102, 1, 601, 'baseline', 'requirement:baseline:create', '形成基线'),
    (6103, 1, 601, 'import', 'requirement:import:create', '需求导入'),
    (6104, 1, 600, 'admin', 'requirement:admin', '需求统筹管理（数据范围豁免）'),
    (6105, 1, 600, 'read', 'requirement:changelog:read', '修改记录查看');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 600 AND 603;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id BETWEEN 6001 AND 6105;
