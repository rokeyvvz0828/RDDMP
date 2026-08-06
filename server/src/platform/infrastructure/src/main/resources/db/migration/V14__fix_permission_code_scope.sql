ALTER TABLE sys_menu_permission DROP INDEX uk_sys_menu_permission_code;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 1, m.tenant_id, m.id, 'read', m.permission_code, '查看'
FROM sys_menu m
WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 2, m.tenant_id, m.id, 'create', CONCAT(m.permission_code, ':create'), '新增'
FROM sys_menu m
WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 3, m.tenant_id, m.id, 'update', CONCAT(m.permission_code, ':update'), '修改'
FROM sys_menu m
WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 4, m.tenant_id, m.id, 'delete', CONCAT(m.permission_code, ':delete'), '删除'
FROM sys_menu m
WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1;
