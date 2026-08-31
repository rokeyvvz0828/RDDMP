-- REQ-20260823-049：部署单元菜单、权限与技术架构师角色种子。
-- 只补充新授权，绝不删除或改写既有 800-803 权限记录。

-- 菜单 804：部署单元（查看用户可见；维护权限控制写操作）。
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 804, 1, 800, 'menu', '部署单元', 'ArchitectureDeploymentUnits',
       '/architecture/deployment-units', 'architecture/deployment-units/index',
       'architecture:deployment-unit:view', 'box', 40
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 804);

-- 菜单 805：部署单元初始化导入（仅维护权限可见）。
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 805, 1, 800, 'menu', '部署单元初始化导入', 'ArchitectureDeploymentUnitImports',
       '/architecture/deployment-unit-imports', 'architecture/deployment-unit-imports/index',
       'architecture:deployment-unit:manage', 'upload', 50
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 805);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted)
SELECT 111, 1, 'ARCHITECTURE_TECHNICAL_MANAGER', '技术架构师', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 111);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8041, 1, 804, 'view', 'architecture:deployment-unit:view', '查看部署单元'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 804 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8041);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8042, 1, 804, 'manage', 'architecture:deployment-unit:manage', '维护部署单元与初始化导入'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 804 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8042);

-- 技术架构师角色：部署单元菜单（804、805）与架构目录（800），并持有查看与维护权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 111, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 804, 805);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 111, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8041, 8042)
  AND permission.status = 1;

-- 本地 tenant 1 管理员加入技术架构师角色（同时保留超级管理员授权）。
INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id)
SELECT 1, 111, 1
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 111 AND tenant_id = 1 AND deleted = 0);

-- 超级管理员（角色 1）：部署单元菜单与两级权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (804, 805);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8041, 8042)
  AND permission.status = 1;

-- 已有三级架构权限（architecture:view/apply/manage）的存量角色获得部署单元查看权限与菜单 804。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8041, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id IN (8031, 8032, 8033);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 804, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8041;

-- 旧八项 CRUD 权限按最小能力迁移：读取或写入旧权限都得到部署单元查看权限。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT old_role_permission.role_id, 8041, old_role_permission.tenant_id
FROM sys_role_permission old_role_permission
JOIN sys_menu_permission old_permission
  ON old_permission.id = old_role_permission.permission_id
 AND old_permission.tenant_id = old_role_permission.tenant_id
WHERE old_role_permission.tenant_id = 1
  AND old_permission.status = 1
  AND old_permission.id IN (8011, 8012, 8013, 8014, 8021, 8022, 8023, 8024);

-- 对已存在但不符合本迁移身份的稳定 ID 失败关闭，避免静默复用其他菜单、角色或权限。
CREATE TEMPORARY TABLE tmp_arch_v86_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v86_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v86_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 804
            AND tenant_id = 1
            AND parent_id = 800
            AND menu_type = 'menu'
            AND route_name = 'ArchitectureDeploymentUnits'
            AND route_path = '/architecture/deployment-units'
            AND component_path = 'architecture/deployment-units/index'
            AND permission_code = 'architecture:deployment-unit:view'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 805
            AND tenant_id = 1
            AND parent_id = 800
            AND menu_type = 'menu'
            AND route_name = 'ArchitectureDeploymentUnitImports'
            AND route_path = '/architecture/deployment-unit-imports'
            AND component_path = 'architecture/deployment-unit-imports/index'
            AND permission_code = 'architecture:deployment-unit:manage'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role
          WHERE id = 111
            AND tenant_id = 1
            AND role_code = 'ARCHITECTURE_TECHNICAL_MANAGER'
            AND status = 1
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8041
            AND tenant_id = 1
            AND menu_id = 804
            AND action_code = 'view'
            AND permission_code = 'architecture:deployment-unit:view'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8042
            AND tenant_id = 1
            AND menu_id = 804
            AND action_code = 'manage'
            AND permission_code = 'architecture:deployment-unit:manage'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 111 AND permission_id = 8041 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 111 AND permission_id = 8042 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 111 AND menu_id = 800 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 111 AND menu_id = 804 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 111 AND menu_id = 805 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_user_role
          WHERE user_id = 1 AND role_id = 111 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 1 AND permission_id = 8041 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 1 AND permission_id = 8042 AND tenant_id = 1
      )
   OR EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 111 AND menu_id NOT IN (800, 804, 805) AND tenant_id = 1
      );

DROP TEMPORARY TABLE tmp_arch_v86_seed_guard;
