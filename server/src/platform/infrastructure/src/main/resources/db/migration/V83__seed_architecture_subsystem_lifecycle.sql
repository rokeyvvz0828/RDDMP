-- REQ-20260822-048：架构子系统变更工单的菜单、权限和审批角色。
-- 只补充新授权及兼容映射，绝不删除既有 801*/802* 权限记录。

-- V37 移除了旧收件箱菜单；本需求仍复用其既有路由作为只导航到业务详情的待办入口。
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 202, 1, 200, 'menu', '审批待办', 'WorkflowInbox', '/workflow/inbox', 'workflow/index',
       'workflow:access', 'tickets', 20
WHERE EXISTS (
    SELECT 1
    FROM sys_menu workflow_root
    WHERE workflow_root.id = 200
      AND workflow_root.tenant_id = 1
      AND workflow_root.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 202);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 803, 1, 800, 'menu', '架构子系统变更工单', 'ArchitectureSubsystemChanges',
       '/architecture/subsystem-change-applications', 'architecture/subsystem-change-applications/index',
       'architecture:view', 'tickets', 30
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 803);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted)
SELECT 110, 1, 'ARCHITECTURE_MANAGER', '架构子系统管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 110);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8031, 1, 803, 'view', 'architecture:view', '查看架构子系统'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 803 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8031);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8032, 1, 803, 'apply', 'architecture:apply', '发起和维护架构子系统申请'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 803 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8032);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8033, 1, 803, 'manage', 'architecture:manage', '管理和审批架构子系统申请'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 803 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8033);

-- 新架构管理员拥有发布数据查看、本人申请及审批三层能力，并保留架构目录可见性。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 110, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 801, 802, 803);

-- 架构管理员仅复用工作流根和收件箱，以取得 workflow:access；不授予流程定义、监控或已办菜单。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 110, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (200, 202);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 110, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8031, 8032, 8033)
  AND permission.status = 1;

-- 本地 tenant 1 管理员既保留超级管理员授权，也加入固定 ROLE 审批节点可解析的角色。
INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id)
SELECT 1, 110, 1
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 110 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 801, 802, 803);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8031, 8032, 8033)
  AND permission.status = 1;

-- 旧八项 CRUD 权限按最小能力迁移：读取或写入旧权限都得到 view；旧写入再得到 apply。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT old_role_permission.role_id, 8031, old_role_permission.tenant_id
FROM sys_role_permission old_role_permission
JOIN sys_menu_permission old_permission
  ON old_permission.id = old_role_permission.permission_id
 AND old_permission.tenant_id = old_role_permission.tenant_id
WHERE old_role_permission.tenant_id = 1
  AND old_permission.status = 1
  AND old_permission.id IN (8011, 8012, 8013, 8014, 8021, 8022, 8023, 8024);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT old_role_permission.role_id, 8032, old_role_permission.tenant_id
FROM sys_role_permission old_role_permission
JOIN sys_menu_permission old_permission
  ON old_permission.id = old_role_permission.permission_id
 AND old_permission.tenant_id = old_role_permission.tenant_id
WHERE old_role_permission.tenant_id = 1
  AND old_permission.status = 1
  AND old_permission.id IN (8012, 8013, 8014, 8022, 8023, 8024);

-- 获得新三级权限的存量角色同时获得工单菜单与其架构父目录。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 803, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id IN (8031, 8032, 8033);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_menu.role_id, 800, role_menu.tenant_id
FROM sys_role_menu role_menu
WHERE role_menu.tenant_id = 1
  AND role_menu.menu_id = 803;

-- 对已存在但不符合本迁移身份的稳定 ID 失败关闭，避免静默复用其他菜单、角色或权限。
CREATE TEMPORARY TABLE tmp_arch_v83_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v83_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v83_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 803
            AND tenant_id = 1
            AND parent_id = 800
            AND menu_type = 'menu'
            AND route_name = 'ArchitectureSubsystemChanges'
            AND route_path = '/architecture/subsystem-change-applications'
            AND component_path = 'architecture/subsystem-change-applications/index'
            AND permission_code = 'architecture:view'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role
          WHERE id = 110
            AND tenant_id = 1
            AND role_code = 'ARCHITECTURE_MANAGER'
            AND status = 1
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_menu
          WHERE id = 200
            AND tenant_id = 1
            AND parent_id = 0
            AND menu_type = 'directory'
            AND permission_code = 'workflow:access'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_menu
          WHERE id = 202
            AND tenant_id = 1
            AND parent_id = 200
            AND menu_type = 'menu'
            AND route_name = 'WorkflowInbox'
            AND route_path = '/workflow/inbox'
            AND component_path = 'workflow/index'
            AND permission_code = 'workflow:access'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8031
            AND tenant_id = 1
            AND menu_id = 803
            AND action_code = 'view'
            AND permission_code = 'architecture:view'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8032
            AND tenant_id = 1
            AND menu_id = 803
            AND action_code = 'apply'
            AND permission_code = 'architecture:apply'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8033
            AND tenant_id = 1
            AND menu_id = 803
            AND action_code = 'manage'
            AND permission_code = 'architecture:manage'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 110 AND permission_id = 8031 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 110 AND permission_id = 8032 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 110 AND permission_id = 8033 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 110 AND menu_id = 200 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 110 AND menu_id = 202 AND tenant_id = 1
      )
   OR EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 110 AND menu_id IN (201, 203, 204) AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_user_role
          WHERE user_id = 1 AND role_id = 110 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 1 AND permission_id = 8033 AND tenant_id = 1
      );

DROP TEMPORARY TABLE tmp_arch_v83_seed_guard;
