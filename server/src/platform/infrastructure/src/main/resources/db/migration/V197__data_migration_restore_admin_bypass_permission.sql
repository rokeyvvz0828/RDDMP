-- V196: 恢复统一回收站管理所需的 data-migration:manage 管理员豁免权限。
--
-- 历史：V191/V192/V193 为清理目录菜单权限，移除了菜单 720/740 上的 data-migration:manage 节点，
-- 但 DataMigrationPermissionService.isAdmin 仍以 system:admin / data-migration:bypass / data-migration:manage
-- 判定管理员；且 ContentRecycleBinController 也以 data-migration:manage / system:admin 作为守卫。
-- 清理后权限目录中不再存在这些节点，导致统一回收站（恢复/彻底删除）对任何角色都无法通过服务端管理员校验。
-- 本脚本在原"统一回收站"菜单（732）下补一个非页面动作的管理权限节点，并授予超级管理员(1)与数据迁移管理员(200)。
-- 仅追加，不修改历史迁移；全部 INSERT IGNORE，幂等可重跑。

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES (7323, 1, 732, 'bypass', 'data-migration:manage', '数据迁移管理(回收站兼容)');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 200, 732, 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 732;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, 7323, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id = 7323 AND status = 1;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, 7323, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id = 7323 AND status = 1;
