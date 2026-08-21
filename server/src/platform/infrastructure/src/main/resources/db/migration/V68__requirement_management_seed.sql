-- 需求管理平台菜单、权限种子（幂等，仅追加）。审批流定义待审批接入需求后另行补充。

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 700, 1, 0, 'directory', '需求管理平台', 'RequirementRoot', '/requirements', 'LAYOUT', 'requirement:access', 'document', 700
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 700);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 701, 1, 700, 'menu', '新建项目', 'RequirementNewProject', '/requirements/new-project', 'requirements/index', 'requirement:project:read', 'document', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 701);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 702, 1, 700, 'menu', '存量项目', 'RequirementLegacy', '/requirements/legacy', 'requirements/index', 'requirement:legacy:read', 'document', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 702);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 703, 1, 700, 'menu', '系统清单', 'RequirementSystems', '/requirements/systems', 'requirements/index', 'requirement:system:read', 'document', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 703);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7001, 1, 700, 'read', 'requirement:access', '需求管理访问'),
    (7002, 1, 700, 'create', 'requirement:access:create', '需求管理新增'),
    (7003, 1, 700, 'update', 'requirement:access:update', '需求管理修改'),
    (7004, 1, 700, 'delete', 'requirement:access:delete', '需求管理删除'),
    (7011, 1, 701, 'read', 'requirement:project:read', '项目查看'),
    (7012, 1, 701, 'create', 'requirement:project:create', '项目新增'),
    (7013, 1, 701, 'update', 'requirement:project:update', '项目修改'),
    (7014, 1, 701, 'delete', 'requirement:project:delete', '项目删除'),
    (7021, 1, 702, 'read', 'requirement:legacy:read', '存量需求查看'),
    (7022, 1, 702, 'create', 'requirement:legacy:create', '存量需求新增'),
    (7023, 1, 702, 'update', 'requirement:legacy:update', '存量需求修改'),
    (7024, 1, 702, 'delete', 'requirement:legacy:delete', '存量需求删除'),
    (7031, 1, 703, 'read', 'requirement:system:read', '系统清单查看'),
    (7032, 1, 703, 'create', 'requirement:system:create', '系统清单新增'),
    (7033, 1, 703, 'update', 'requirement:system:update', '系统清单修改'),
    (7034, 1, 703, 'delete', 'requirement:system:delete', '系统清单删除'),
    (7101, 1, 701, 'review', 'requirement:diff:review', '差异评审处理'),
    (7102, 1, 701, 'baseline', 'requirement:baseline:create', '形成基线'),
    (7103, 1, 701, 'import', 'requirement:import:create', '需求导入'),
    (7104, 1, 700, 'admin', 'requirement:admin', '需求统筹管理（数据范围豁免）'),
    (7105, 1, 700, 'read', 'requirement:changelog:read', '修改记录查看');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 700 AND 703;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id BETWEEN 7001 AND 7105;
