-- =============================================================================
-- REQ-20260919-077 需求管理权限重建
-- 1. 删除需求管理旧权限码（29 个）及其在系统角色、菜单、项目角色三处的绑定；
-- 2. 建立 9 个新权限码（+1 个超管兜底码）并重绑需求菜单；
-- 3. 为每个项目建立三个需求角色的项目角色模板并绑定权限；
-- 4. 演示项目补充角色分配，便于本地验收；
-- 5. 流转日志补 to_user_id / from_user_id 索引，支撑"曾流转给我"的历史可见范围。
-- 说明：用户确认当前均为测试数据，不做存量绑定迁移与兼容；迁移幂等，可重复执行。
-- =============================================================================

-- 1. 删除旧权限码与绑定（系统角色、菜单权限、项目角色权限）
DELETE rp FROM sys_role_permission rp
JOIN sys_menu_permission p ON p.id = rp.permission_id AND p.tenant_id = rp.tenant_id
WHERE p.permission_code LIKE 'requirement:%';

DELETE rp FROM pm_project_role_permission rp
JOIN sys_menu_permission p ON p.id = rp.permission_id AND p.tenant_id = rp.tenant_id
WHERE p.permission_code LIKE 'requirement:%';

DELETE FROM sys_menu_permission WHERE tenant_id = 1 AND permission_code LIKE 'requirement:%';

-- 2. 新建权限码（挂到需求菜单 700/701/702/703）
INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name, status)
VALUES
    (7001, 1, 700, 'access',   'requirement:access',       '进入需求管理', 1),
    (7002, 1, 700, 'read',     'requirement:read-own',     '查看本人相关需求', 1),
    (7003, 1, 700, 'read-all', 'requirement:read-project', '查看项目全部需求', 1),
    (7004, 1, 701, 'create',   'requirement:propose',      '提出需求', 1),
    (7005, 1, 701, 'update',   'requirement:edit',         '编辑需求', 1),
    (7006, 1, 701, 'transfer', 'requirement:transfer',     '流转需求', 1),
    (7007, 1, 701, 'withdraw', 'requirement:withdraw',     '收回需求', 1),
    (7008, 1, 701, 'review',   'requirement:review',       '提交或撤销评审', 1),
    (7009, 1, 703, 'manage',   'requirement:manage',       '需求管理配置', 1),
    (7010, 1, 700, 'admin',    'requirement:admin',        '需求模块兜底管理', 1)
ON DUPLICATE KEY UPDATE permission_code = VALUES(permission_code), permission_name = VALUES(permission_name),
                        menu_id = VALUES(menu_id), action_code = VALUES(action_code), status = 1;

-- 3. 需求菜单绑定新权限码
UPDATE sys_menu SET permission_code = 'requirement:access'   WHERE tenant_id = 1 AND id = 700;
UPDATE sys_menu SET permission_code = 'requirement:read-own' WHERE tenant_id = 1 AND id IN (701, 702);
UPDATE sys_menu SET permission_code = 'requirement:manage'   WHERE tenant_id = 1 AND id = 703;
UPDATE sys_menu SET permission_code = 'requirement:manage'   WHERE tenant_id = 1 AND parent_id = 700 AND id > 703;

-- 4. 三个项目角色模板 + 权限绑定（覆盖所有未删除项目，幂等）
INSERT INTO pm_project_role (id, tenant_id, project_id, role_code, role_name, description)
SELECT 900000000000700000 + ROW_NUMBER() OVER (ORDER BY p.id, t.role_code),
       p.tenant_id, p.id, t.role_code, t.role_name, t.description
FROM pm_project p
CROSS JOIN (
    SELECT 'REQUIREMENT_PROPOSER' AS role_code, '需求提出人' AS role_name, '提出需求、流转、未受理收回' AS description
    UNION ALL SELECT 'REQUIREMENT_ANALYST', '需求分析员', '处理流转到本人名下的需求并继续流转'
    UNION ALL SELECT 'REQUIREMENT_COORDINATOR', '需求统筹管理员', '查看并处理本项目全部需求'
) t
WHERE p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM pm_project_role r
                  WHERE r.tenant_id = p.tenant_id AND r.project_id = p.id
                    AND r.role_code = t.role_code AND r.deleted = 0);

-- 提出人：进入模块 + 提出 + 编辑 + 流转 + 收回 + 评审 + 查看本人
INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, p.id
FROM pm_project_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id AND p.status = 1
WHERE r.deleted = 0 AND r.role_code = 'REQUIREMENT_PROPOSER'
  AND p.permission_code IN ('requirement:access','requirement:propose','requirement:edit',
                            'requirement:transfer','requirement:withdraw','requirement:review',
                            'requirement:read-own');

-- 分析员：进入模块 + 编辑 + 流转 + 评审 + 查看本人（无收回）
INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, p.id
FROM pm_project_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id AND p.status = 1
WHERE r.deleted = 0 AND r.role_code = 'REQUIREMENT_ANALYST'
  AND p.permission_code IN ('requirement:access','requirement:edit','requirement:transfer',
                            'requirement:review','requirement:read-own');

-- 统筹管理员：全部需求权限
INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, p.id
FROM pm_project_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id AND p.status = 1
WHERE r.deleted = 0 AND r.role_code = 'REQUIREMENT_COORDINATOR'
  AND p.permission_code LIKE 'requirement:%';

-- 5. 演示项目角色分配（仅演示项目，便于本地验收；项目负责人/PM 由平台兜底）
INSERT IGNORE INTO pm_project_member_role (tenant_id, member_id, role_id)
SELECT m.tenant_id, m.id, r.id
FROM pm_project_member m
JOIN pm_project_role r ON r.tenant_id = m.tenant_id AND r.project_id = m.project_id AND r.deleted = 0
WHERE m.deleted = 0 AND m.status = 1
  AND m.project_id IN (3001, 3002, 3003)
  AND (
    (r.role_code = 'REQUIREMENT_PROPOSER'    AND (m.project_id, m.user_id) IN ((3001,1002),(3002,1005),(3003,1007)))
    OR (r.role_code = 'REQUIREMENT_ANALYST'  AND (m.project_id, m.user_id) IN ((3001,1003),(3002,1006),(3003,1008)))
    OR (r.role_code = 'REQUIREMENT_COORDINATOR' AND m.user_id = 1)
  );

-- 6. 流转日志索引：支撑"曾流转给我"（to_user_id / from_user_id 双向）可见范围
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_flow_log' AND INDEX_NAME = 'idx_req_flow_to_user');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE req_flow_log ADD KEY idx_req_flow_to_user (tenant_id, to_user_id, deleted), ADD KEY idx_req_flow_from_user (tenant_id, from_user_id, deleted)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_difference_flow_log' AND INDEX_NAME = 'idx_diff_flow_to_user');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE req_difference_flow_log ADD KEY idx_diff_flow_to_user (tenant_id, to_user_id, deleted), ADD KEY idx_diff_flow_from_user (tenant_id, from_user_id, deleted)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
