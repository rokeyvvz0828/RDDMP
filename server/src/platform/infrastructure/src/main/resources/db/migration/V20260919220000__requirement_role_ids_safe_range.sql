-- =============================================================================
-- 修正 REQ-20260919-077：三个需求项目角色的 id 落在 9e17 区间，
-- 超过 JavaScript 安全整数上限（2^53-1 = 9007199254740991），前端读取后精度丢失：
--   * 多个角色在 JS 里表现为同一个值 → 角色多选勾一个等于全选；
--   * 保存时回传被舍入的 id → 后端校验报"项目角色不存在"。
-- 处理：删除错误区间的角色及其绑定，改用安全区间 id（8.5e15 起，低于 2^53-1，
--       且高于 nextId() 与演示数据区间，避免碰撞）重建角色、权限绑定与演示分配。
-- 幂等：按 id 区间与 (project_id, role_code) 存在性判断，可重复执行。
-- 说明：不改 V20260919200000（可能已执行，改动会导致校验和不一致）。
-- =============================================================================

-- 1. 清理错误区间的角色绑定与角色本体
DELETE mr FROM pm_project_member_role mr
JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id
WHERE r.id >= 900000000000700000;

DELETE rp FROM pm_project_role_permission rp
JOIN pm_project_role r ON r.id = rp.role_id AND r.tenant_id = rp.tenant_id
WHERE r.id >= 900000000000700000;

DELETE FROM pm_project_role WHERE id >= 900000000000700000;

-- 2. 用安全区间 id 重建三个角色模板
INSERT INTO pm_project_role (id, tenant_id, project_id, role_code, role_name, description)
SELECT 8500000000000000 + ROW_NUMBER() OVER (ORDER BY p.id, t.role_code),
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

-- 3. 重新绑定权限
INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, p.id
FROM pm_project_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id AND p.status = 1
WHERE r.deleted = 0 AND r.role_code = 'REQUIREMENT_PROPOSER'
  AND p.permission_code IN ('requirement:access','requirement:propose','requirement:edit',
                            'requirement:transfer','requirement:withdraw','requirement:review',
                            'requirement:read-own');

INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, p.id
FROM pm_project_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id AND p.status = 1
WHERE r.deleted = 0 AND r.role_code = 'REQUIREMENT_ANALYST'
  AND p.permission_code IN ('requirement:access','requirement:edit','requirement:transfer',
                            'requirement:review','requirement:read-own');

INSERT IGNORE INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id)
SELECT r.tenant_id, r.project_id, r.id, p.id
FROM pm_project_role r
JOIN sys_menu_permission p ON p.tenant_id = r.tenant_id AND p.status = 1
WHERE r.deleted = 0 AND r.role_code = 'REQUIREMENT_COORDINATOR'
  AND p.permission_code LIKE 'requirement:%';

-- 4. 重建演示项目角色分配
INSERT IGNORE INTO pm_project_member_role (tenant_id, member_id, role_id)
SELECT m.tenant_id, m.id, r.id
FROM pm_project_member m
JOIN pm_project_role r ON r.tenant_id = m.tenant_id AND r.project_id = m.project_id AND r.deleted = 0
WHERE m.deleted = 0 AND m.status = 1
  AND m.project_id IN (3001, 3002, 3003)
  AND (
    (r.role_code = 'REQUIREMENT_PROPOSER'       AND (m.project_id, m.user_id) IN ((3001,1002),(3002,1005),(3003,1007)))
    OR (r.role_code = 'REQUIREMENT_ANALYST'     AND (m.project_id, m.user_id) IN ((3001,1003),(3002,1006),(3003,1008)))
    OR (r.role_code = 'REQUIREMENT_COORDINATOR' AND m.user_id = 1)
  );
