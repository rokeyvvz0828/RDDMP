-- REQ-20260907-064：将投产演练计划菜单统一命名为投产方案。
-- 路由、权限编码和业务数据保持不变，仅调整菜单及权限的展示名称。
UPDATE sys_menu
SET menu_name = '投产方案'
WHERE tenant_id = 1
  AND route_name = 'ReleaseOperationsDrillPlans'
  AND route_path = '/release-operations/drill-plans'
  AND deleted = 0;

UPDATE sys_menu_permission p
JOIN sys_menu m
  ON m.id = p.menu_id
 AND m.tenant_id = p.tenant_id
 AND m.route_name = 'ReleaseOperationsDrillPlans'
 AND m.route_path = '/release-operations/drill-plans'
SET p.permission_name = CASE p.action_code
    WHEN 'read' THEN '查看投产方案'
    WHEN 'manage' THEN '维护投产方案'
    ELSE p.permission_name
END
WHERE p.tenant_id = 1
  AND m.deleted = 0
  AND p.action_code IN ('read', 'manage');
