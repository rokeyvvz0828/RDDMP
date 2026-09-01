-- 审批待办已经统一到任务中心。V94 误将 V37 删除的旧菜单重新引入，
-- 此迁移保留历史地址兼容和审批权限，仅清理重复导航入口。

-- 显式持有旧菜单动作权限的角色迁移到工作流根的等价动作权限。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT old_grant.role_id, old_grant.permission_id - 20, old_grant.tenant_id
FROM sys_role_permission old_grant
JOIN sys_menu_permission target_permission
  ON target_permission.id = old_grant.permission_id - 20
 AND target_permission.tenant_id = old_grant.tenant_id
 AND target_permission.menu_id = 200
 AND target_permission.status = 1
WHERE old_grant.permission_id IN (2021, 2022, 2023, 2024);

-- 仅通过旧待办菜单取得 workflow:access 的角色改为显式持有根查看权限。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT legacy_menu_grant.role_id, root_permission.id, legacy_menu_grant.tenant_id
FROM sys_role_menu legacy_menu_grant
JOIN sys_menu_permission root_permission
  ON root_permission.id = 2001
 AND root_permission.tenant_id = legacy_menu_grant.tenant_id
 AND root_permission.menu_id = 200
 AND root_permission.permission_code = 'workflow:access'
 AND root_permission.status = 1
WHERE legacy_menu_grant.menu_id = 202;

DELETE FROM sys_role_menu
WHERE menu_id = 202;

DELETE FROM sys_role_permission
WHERE permission_id IN (2021, 2022, 2023, 2024);

DELETE FROM sys_menu_permission
WHERE menu_id = 202;

DELETE FROM sys_menu
WHERE id = 202;

-- 没有流程定义、流程监控等有效子菜单的角色不应看到空的“工作流”目录。
DELETE root_grant
FROM sys_role_menu root_grant
LEFT JOIN (
    SELECT DISTINCT child_grant.role_id, child_grant.tenant_id
    FROM sys_role_menu child_grant
    JOIN sys_menu child_menu
      ON child_menu.id = child_grant.menu_id
     AND child_menu.tenant_id = child_grant.tenant_id
     AND child_menu.parent_id = 200
     AND child_menu.status = 1
     AND child_menu.visible = 1
     AND child_menu.deleted = 0
) active_child
  ON active_child.role_id = root_grant.role_id
 AND active_child.tenant_id = root_grant.tenant_id
WHERE root_grant.menu_id = 200
  AND active_child.role_id IS NULL;
