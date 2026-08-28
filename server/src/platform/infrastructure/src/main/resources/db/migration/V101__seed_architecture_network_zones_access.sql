-- REQ-20260826-054：网络分区与网络访问关系菜单、权限、角色授权和本地默认分区。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 812, 1, 800, 'menu', '网络分区', 'ArchitectureNetworkZones',
       '/architecture/network-zones', 'architecture/network-zones/index',
       'architecture:network-zone:view', 'network', 58
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 812);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 813, 1, 800, 'menu', '网络访问关系', 'ArchitectureNetworkAccess',
       '/architecture/network-access', 'architecture/network-access/index',
       'architecture:network-access:view', 'git-branch', 59
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 813);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8121, 1, 812, 'view', 'architecture:network-zone:view', '查看网络分区'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 812 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8121);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8122, 1, 812, 'manage', 'architecture:network-zone:manage', '维护网络分区'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 812 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8122);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8131, 1, 813, 'view', 'architecture:network-access:view', '查看网络访问申请与关系'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 813 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8131);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8132, 1, 813, 'apply', 'architecture:network-access:apply', '发起网络访问申请'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 813 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8132);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8133, 1, 813, 'manage', 'architecture:network-access:manage', '办理网络访问申请与关系'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 813 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8133);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 113, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 812, 813);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 113, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8121, 8122, 8131, 8132, 8133)
  AND permission.status = 1;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 812, 813);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8121, 8122, 8131, 8132, 8133)
  AND permission.status = 1;

-- 存量兼容映射：持有架构 view/apply/manage 的角色获得对应网络访问能力。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8121, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8031;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8131, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8031;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8132, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8032;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8122, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8033;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8133, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8033;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 812, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id IN (8121, 8122);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 813, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id IN (8131, 8132, 8133);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_menu.role_id, 800, role_menu.tenant_id
FROM sys_role_menu role_menu
WHERE role_menu.tenant_id = 1
  AND role_menu.menu_id IN (812, 813);

-- 本地默认分区用于空库演示与资源申请/下发联调；生产可继续维护真实分区。
INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, created_by, updated_by)
SELECT 100000000000100, 1, NULL, 'ZONE_ROOT', '默认网络域', 0, 'ACTIVE', '本地默认网络分区根节点', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'ZONE_ROOT');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, created_by, updated_by)
SELECT 100000000000101, 1, 100000000000100, 'ZONE_APP', '应用区', 1, 'ACTIVE', '默认应用网络区', 1, 1
WHERE EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND id = 100000000000100)
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'ZONE_APP');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, created_by, updated_by)
SELECT 100000000000102, 1, 100000000000100, 'ZONE_DATA', '数据区', 2, 'ACTIVE', '默认数据网络区', 1, 1
WHERE EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND id = 100000000000100)
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'ZONE_DATA');
