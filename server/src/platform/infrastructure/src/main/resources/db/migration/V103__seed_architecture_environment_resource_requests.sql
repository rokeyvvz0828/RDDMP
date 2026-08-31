-- REQ-20260824-052：具体环境和资源申请菜单、权限、办理角色与固定审批流程。
-- 只追加新授权和兼容映射，不删除既有 80x 权限记录。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 809, 1, 800, 'menu', '具体环境', 'ArchitectureEnvironments',
       '/architecture/environments', 'architecture/environments/index',
       'architecture:environment:view', 'server', 55
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 809);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 810, 1, 800, 'menu', '资源申请', 'ArchitectureResourceRequests',
       '/architecture/resource-requests', 'architecture/resource-requests/index',
       'architecture:resource-request:view', 'clipboard-list', 56
WHERE EXISTS (
    SELECT 1 FROM sys_menu parent_menu
    WHERE parent_menu.id = 800 AND parent_menu.tenant_id = 1 AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 810);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted)
SELECT 114, 1, 'ENVIRONMENT_RESOURCE_MANAGER', '环境资源办理人员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 114);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8091, 1, 809, 'view', 'architecture:environment:view', '查看具体环境'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 809 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8091);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8092, 1, 809, 'manage', 'architecture:environment:manage', '维护环境类型和具体环境'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 809 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8092);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8101, 1, 810, 'view', 'architecture:resource-request:view', '查看资源申请'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 810 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8101);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8102, 1, 810, 'apply', 'architecture:resource-request:apply', '发起和维护本人资源申请'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 810 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8102);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8103, 1, 810, 'manage', 'architecture:resource-request:manage', '办理和审批资源申请'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 810 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8103);

-- 环境资源办理人员：架构目录、具体环境、资源申请，以及工作流收件箱。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 114, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (800, 809, 810, 200, 202);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 114, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8091, 8092, 8101, 8102, 8103)
  AND permission.status = 1;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 114, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.permission_code = 'workflow:access'
  AND permission.status = 1;

INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id)
SELECT 1, 114, 1
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 114 AND tenant_id = 1 AND deleted = 0);

-- 超级管理员获得全部新菜单和权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (809, 810);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8091, 8092, 8101, 8102, 8103)
  AND permission.status = 1;

-- 存量兼容：架构查看可看环境/资源申请；架构申请可发起资源申请；架构管理可维护环境和办理资源申请。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8091, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8031;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8101, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8031;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8102, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8032;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, granted.permission_id, role_permission.tenant_id
FROM sys_role_permission role_permission
JOIN (
    SELECT 8092 AS permission_id
    UNION ALL SELECT 8103
) granted
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8033;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, menu_id, role_permission.tenant_id
FROM sys_role_permission role_permission
JOIN (
    SELECT 809 AS menu_id, 8091 AS permission_id
    UNION ALL SELECT 810, 8101
    UNION ALL SELECT 810, 8102
    UNION ALL SELECT 809, 8092
    UNION ALL SELECT 810, 8103
) granted
  ON granted.permission_id = role_permission.permission_id
WHERE role_permission.tenant_id = 1;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_menu.role_id, 800, role_menu.tenant_id
FROM sys_role_menu role_menu
WHERE role_menu.tenant_id = 1
  AND role_menu.menu_id IN (809, 810);

-- 固定资源申请审批流程草稿：需要通过平台发布入口发布后才能在真实运行中提交。
INSERT INTO wf_definition
    (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000050, 1, 'architecture.resource-request', '资源申请审批', 'DRAFT', 1, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE id = 900000000000050);

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000051, 1, 900000000000050, 1,
       '{"schemaVersion":2,"variables":[],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-resource-manager","type":"APPROVAL","label":"资源申请审批","position":{"x":380,"y":160},"config":{"assigneeType":"ROLE","assigneeIds":[114],"roleIds":[114],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","RETURN","REJECT"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-approval","source":"start","target":"approval-resource-manager","label":null,"condition":null,"default":false},{"id":"edge-approval-end","source":"approval-resource-manager","target":"end","label":null,"condition":null,"default":false}]}',
       2, 'DRAFT'
WHERE EXISTS (
    SELECT 1 FROM wf_definition
    WHERE id = 900000000000050
      AND tenant_id = 1
      AND code = 'architecture.resource-request'
      AND deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM wf_version WHERE id = 900000000000051);

CREATE TEMPORARY TABLE tmp_arch_v92_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v92_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v92_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 809 AND tenant_id = 1 AND parent_id = 800
            AND route_name = 'ArchitectureEnvironments'
            AND route_path = '/architecture/environments'
            AND component_path = 'architecture/environments/index'
            AND permission_code = 'architecture:environment:view'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 810 AND tenant_id = 1 AND parent_id = 800
            AND route_name = 'ArchitectureResourceRequests'
            AND route_path = '/architecture/resource-requests'
            AND component_path = 'architecture/resource-requests/index'
            AND permission_code = 'architecture:resource-request:view'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role
          WHERE id = 114 AND tenant_id = 1
            AND role_code = 'ENVIRONMENT_RESOURCE_MANAGER'
            AND status = 1 AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id IN (8091, 8092, 8101, 8102, 8103)
            AND tenant_id = 1 AND status = 1
          GROUP BY tenant_id HAVING COUNT(*) = 5
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_user_role
          WHERE user_id = 1 AND role_id = 114 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM wf_definition
          WHERE id = 900000000000050
            AND tenant_id = 1
            AND code = 'architecture.resource-request'
            AND name = '资源申请审批'
            AND status = 'DRAFT'
            AND current_version = 1
            AND model_schema_version = 2
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM wf_version
          WHERE id = 900000000000051
            AND tenant_id = 1
            AND definition_id = 900000000000050
            AND version_no = 1
            AND model_schema_version = 2
            AND status = 'DRAFT'
      );

DROP TEMPORARY TABLE tmp_arch_v92_seed_guard;
