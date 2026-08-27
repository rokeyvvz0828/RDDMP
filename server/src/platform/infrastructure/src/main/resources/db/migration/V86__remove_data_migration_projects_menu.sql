-- 废弃数迁模块独立的"项目清单"菜单。
-- 原因：T9 决策将"所属项目"数据源收敛到平台"项目管理"(pm_project)，
-- 数迁自身不再维护项目清单，对应前端页面及 API 已移除。
-- 本脚本幂等清理 sys_menu / sys_menu_permission / sys_role_permission / sys_role_menu
-- 中残留的 DataMigrationProjects 记录。
-- 仅追加、不修改已发布脚本。

SET @dm_projects_menu_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationProjects'
    ORDER BY id LIMIT 1
);

-- 1) 清理角色-权限绑定（permission_id 来自 sys_menu_permission）
DELETE FROM sys_role_permission
WHERE tenant_id = 1
  AND permission_id IN (
      SELECT id FROM sys_menu_permission
      WHERE tenant_id = 1 AND menu_id = @dm_projects_menu_id
  );

-- 2) 清理菜单动作权限
DELETE FROM sys_menu_permission
WHERE tenant_id = 1 AND menu_id = @dm_projects_menu_id;

-- 3) 清理角色-菜单绑定
DELETE FROM sys_role_menu
WHERE tenant_id = 1 AND menu_id = @dm_projects_menu_id;

-- 4) 清理菜单本身
DELETE FROM sys_menu
WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationProjects';
