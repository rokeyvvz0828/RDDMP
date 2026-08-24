-- REQ-20260823-050：网络专项工单的菜单、权限、办理角色与固定审批流程。
-- 只补充新授权及兼容映射，绝不删除既有 80x/81x 权限记录。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 808, 1, 800, 'menu', '网络专项工单', 'ArchitectureNetworkWorkOrders',
       '/architecture/network-work-orders', 'architecture/network-work-orders/index',
       'architecture:network-work-order:view', 'tickets', 40
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 808);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted)
SELECT 113, 1, 'NETWORK_MANAGER', '网络办理人员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 113);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8081, 1, 808, 'view', 'architecture:network-work-order:view', '查看网络专项工单'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 808 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8081);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8082, 1, 808, 'apply', 'architecture:network-work-order:apply', '发起和维护网络专项工单'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 808 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8082);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8083, 1, 808, 'manage', 'architecture:network-work-order:manage', '办理和审批网络专项工单'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 808 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8083);

-- 网络办理人员拥有网络专项工单查看、发起与办理能力，并保留架构目录可见性。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 113, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 808);

-- 网络办理人员仅复用工作流根和收件箱，以取得 workflow:access；不授予流程定义、监控或已办菜单。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 113, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (200, 202);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 113, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8081, 8082, 8083)
  AND permission.status = 1;

-- 本地 tenant 1 管理员既保留超级管理员授权，也加入固定 ROLE 审批节点可解析的角色。
INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id)
SELECT 1, 113, 1
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 113 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 808);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8081, 8082, 8083)
  AND permission.status = 1;

-- 存量兼容映射：持有 architecture:view 的角色获得网络工单查看，持有 architecture:apply
-- 的角色获得发起能力；办理与审批（manage）只授予 NETWORK_MANAGER 与管理员。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8081, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8031;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8082, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8032;

-- 获得新三级权限的存量角色同时获得网络工单菜单与其架构父目录。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 808, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id IN (8081, 8082, 8083);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_menu.role_id, 800, role_menu.tenant_id
FROM sys_role_menu role_menu
WHERE role_menu.tenant_id = 1
  AND role_menu.menu_id = 808;

-- 固定审批流程草稿：必须通过平台既有发布入口生成 Flowable deployment 后才能提交。
INSERT INTO wf_definition
    (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000032, 1, 'architecture.network.work-order', '网络专项工单审批', 'DRAFT', 1, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE id = 900000000000032);

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000033, 1, 900000000000032, 1,
       '{"schemaVersion":2,"variables":[],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-network-manager","type":"APPROVAL","label":"网络专项工单审批","position":{"x":380,"y":160},"config":{"assigneeType":"ROLE","assigneeIds":[113],"roleIds":[113],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","RETURN","REJECT"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-approval","source":"start","target":"approval-network-manager","label":null,"condition":null,"default":false},{"id":"edge-approval-end","source":"approval-network-manager","target":"end","label":null,"condition":null,"default":false}]}',
       2, 'DRAFT'
WHERE EXISTS (
    SELECT 1
    FROM wf_definition
    WHERE id = 900000000000032
      AND tenant_id = 1
      AND code = 'architecture.network.work-order'
      AND deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM wf_version WHERE id = 900000000000033);

-- 稳定 ID 或流程编码若被不同记录占用，必须停止迁移，不能静默覆盖其他已发布流程或角色授权。
CREATE TEMPORARY TABLE tmp_arch_v88_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v88_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v88_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 808
            AND tenant_id = 1
            AND parent_id = 800
            AND menu_type = 'menu'
            AND route_name = 'ArchitectureNetworkWorkOrders'
            AND route_path = '/architecture/network-work-orders'
            AND component_path = 'architecture/network-work-orders/index'
            AND permission_code = 'architecture:network-work-order:view'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role
          WHERE id = 113
            AND tenant_id = 1
            AND role_code = 'NETWORK_MANAGER'
            AND status = 1
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 200
            AND tenant_id = 1
            AND parent_id = 0
            AND menu_type = 'directory'
            AND permission_code = 'workflow:access'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
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
          WHERE id = 8081
            AND tenant_id = 1
            AND menu_id = 808
            AND action_code = 'view'
            AND permission_code = 'architecture:network-work-order:view'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8082
            AND tenant_id = 1
            AND menu_id = 808
            AND action_code = 'apply'
            AND permission_code = 'architecture:network-work-order:apply'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8083
            AND tenant_id = 1
            AND menu_id = 808
            AND action_code = 'manage'
            AND permission_code = 'architecture:network-work-order:manage'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 113 AND permission_id = 8081 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 113 AND permission_id = 8082 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 113 AND permission_id = 8083 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 113 AND menu_id = 200 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 113 AND menu_id = 202 AND tenant_id = 1
      )
   OR EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 113 AND menu_id IN (201, 203, 204) AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_user_role
          WHERE user_id = 1 AND role_id = 113 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 1 AND permission_id = 8083 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1
          FROM wf_definition
          WHERE id = 900000000000032
            AND tenant_id = 1
            AND code = 'architecture.network.work-order'
            AND name = '网络专项工单审批'
            AND status = 'DRAFT'
            AND current_version = 1
            AND model_schema_version = 2
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM wf_version
          WHERE id = 900000000000033
            AND tenant_id = 1
            AND definition_id = 900000000000032
            AND version_no = 1
            AND model_schema_version = 2
            AND status = 'DRAFT'
      );

DROP TEMPORARY TABLE tmp_arch_v88_seed_guard;
