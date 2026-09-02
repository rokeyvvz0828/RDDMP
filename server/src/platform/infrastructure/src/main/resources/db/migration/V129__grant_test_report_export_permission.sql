-- 补齐既有测试报告菜单的导出动作权限。菜单可能由早期迁移预先创建，故按实际菜单记录授权。
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 5, m.tenant_id, m.id, 'export', CONCAT(m.permission_code, ':export'), '导出'
FROM sys_menu m
WHERE m.tenant_id = 1
  AND m.deleted = 0
  AND m.route_path IN (
    '/test-management/application-assembly/reports',
    '/test-management/user-testing/reports',
    '/test-management/non-functional/reports',
    '/test-management/security/reports'
  );

-- 已具备报告查看权限的角色可下载该报告，保持菜单访问与导出能力一致。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT rp.role_id, export_permission.id, rp.tenant_id
FROM sys_role_permission rp
JOIN sys_menu_permission read_permission
  ON read_permission.id = rp.permission_id
 AND read_permission.tenant_id = rp.tenant_id
 AND read_permission.action_code = 'read'
JOIN sys_menu_permission export_permission
  ON export_permission.menu_id = read_permission.menu_id
 AND export_permission.tenant_id = read_permission.tenant_id
 AND export_permission.action_code = 'export'
WHERE read_permission.permission_code LIKE 'test-management:%:reports';
