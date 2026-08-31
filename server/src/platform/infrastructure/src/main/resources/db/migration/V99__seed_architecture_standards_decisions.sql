-- REQ-20260823-050：架构规范类别、架构事项类型、菜单、权限、角色与决策发布工作流草稿种子。
-- 只补充新授权与新字典，绝不删除或改写既有 800-805 菜单、权限、角色与参数记录。

-- ============================================================
-- 1. 字典类别与参数（平台参数管理维护）
-- ============================================================
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES
    (360007, 1, 'ARCH_STANDARD_CATEGORY', '架构规范类别', 1),
    (360008, 1, 'ARCH_MATTER_TYPE', '架构事项类型', 1);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360201, 1, id, 'DEPLOYMENT_SPEC', '部署规范', 'string', '架构规范类别'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_STANDARD_CATEGORY' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360202, 1, id, 'NETWORK_PLANNING', '网络规划', 'string', '架构规范类别'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_STANDARD_CATEGORY' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360203, 1, id, 'CODING_STANDARD', '编码规范', 'string', '架构规范类别'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_STANDARD_CATEGORY' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360204, 1, id, 'DATABASE_STANDARD', '数据库规范', 'string', '架构规范类别'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_STANDARD_CATEGORY' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360301, 1, id, 'TECHNOLOGY_SELECTION', '技术选型', 'string', '架构事项类型'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_MATTER_TYPE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360302, 1, id, 'ARCHITECTURE_ADJUSTMENT', '架构调整', 'string', '架构事项类型'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_MATTER_TYPE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360303, 1, id, 'STANDARD_DEVIATION', '规范偏差', 'string', '架构事项类型'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_MATTER_TYPE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360304, 1, id, 'SECURITY_COMPLIANCE', '安全合规', 'string', '架构事项类型'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_MATTER_TYPE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 360305, 1, id, 'OTHER', '其他', 'string', '架构事项类型'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'ARCH_MATTER_TYPE' AND deleted = 0;

-- ============================================================
-- 2. 菜单 806 架构规范 / 807 架构决策
-- ============================================================
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 806, 1, 800, 'menu', '架构规范', 'ArchitectureStandards',
       '/architecture/standards', 'architecture/standards/index',
       'architecture:standard:view', 'document', 60
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 806);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 807, 1, 800, 'menu', '架构决策', 'ArchitectureDecisions',
       '/architecture/decisions', 'architecture/decisions/index',
       'architecture:decision:view', 'collection', 70
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 807);

-- ============================================================
-- 3. 权限 8061/8062、8071-8074
-- ============================================================
INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8061, 1, 806, 'view', 'architecture:standard:view', '查阅架构规范'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 806 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8061);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8062, 1, 806, 'manage', 'architecture:standard:manage', '发布和维护架构规范'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 806 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8062);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8071, 1, 807, 'view', 'architecture:decision:view', '查看架构决策事项'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 807 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8071);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8072, 1, 807, 'propose', 'architecture:decision:propose', '提交和维护架构决策事项'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 807 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8072);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8073, 1, 807, 'review', 'architecture:decision:review', '首次处理和评审架构决策事项'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 807 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8073);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8074, 1, 807, 'manage', 'architecture:decision:manage', '管理和发布架构决策结论'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 807 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8074);

-- ============================================================
-- 4. 角色 112 ARCHITECTURE_GROUP 与授权
-- ============================================================
INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted)
SELECT 112, 1, 'ARCHITECTURE_GROUP', '架构组成员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 112);

-- 架构管理人员（110）拥有规范查看/维护与决策查看/提交/评审/发布全部能力。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 110, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (806, 807);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 110, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8061, 8062, 8071, 8072, 8073, 8074)
  AND permission.status = 1;

-- 架构组成员（112）拥有规范查看、决策查看/提交/首次处理/评审，不授予发布结论权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 112, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (806, 807);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 112, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8061, 8071, 8072, 8073)
  AND permission.status = 1;

-- 本地 tenant 1 管理员保留全部新授权，并加入固定 ROLE 审批节点可解析的角色（110 已含）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, menu.id, 1
FROM sys_menu menu
WHERE menu.tenant_id = 1
  AND menu.deleted = 0
  AND menu.id IN (806, 807);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, permission.id, 1
FROM sys_menu_permission permission
WHERE permission.tenant_id = 1
  AND permission.id IN (8061, 8062, 8071, 8072, 8073, 8074)
  AND permission.status = 1;

INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id)
SELECT 1, 112, 1
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 112 AND tenant_id = 1 AND deleted = 0);

-- ============================================================
-- 5. 工作流草稿 architecture.decision.review
-- ============================================================
INSERT INTO wf_definition
    (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000040, 1, 'architecture.decision.review', '架构决策结论发布审批', 'DRAFT', 1, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE id = 900000000000040);

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000041, 1, 900000000000040, 1,
       '{"schemaVersion":2,"variables":[],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-architecture-manager","type":"APPROVAL","label":"架构决策结论发布审批","position":{"x":380,"y":160},"config":{"assigneeType":"ROLE","assigneeIds":[110],"roleIds":[110],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","RETURN","REJECT"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-approval","source":"start","target":"approval-architecture-manager","label":null,"condition":null,"default":false},{"id":"edge-approval-end","source":"approval-architecture-manager","target":"end","label":null,"condition":null,"default":false}]}',
       2, 'DRAFT'
WHERE EXISTS (
    SELECT 1
    FROM wf_definition
    WHERE id = 900000000000040
      AND tenant_id = 1
      AND code = 'architecture.decision.review'
      AND deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM wf_version WHERE id = 900000000000041);

-- 稳定 ID 或流程编码若被不同记录占用，必须停止迁移，不能静默覆盖其他已发布流程。
CREATE TEMPORARY TABLE tmp_arch_v88_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v88_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v88_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM wf_definition
          WHERE id = 900000000000040
            AND tenant_id = 1
            AND code = 'architecture.decision.review'
            AND name = '架构决策结论发布审批'
            AND status = 'DRAFT'
            AND current_version = 1
            AND model_schema_version = 2
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM wf_version
          WHERE id = 900000000000041
            AND tenant_id = 1
            AND definition_id = 900000000000040
            AND version_no = 1
            AND model_schema_version = 2
            AND status = 'DRAFT'
      );

DROP TEMPORARY TABLE tmp_arch_v88_seed_guard;
