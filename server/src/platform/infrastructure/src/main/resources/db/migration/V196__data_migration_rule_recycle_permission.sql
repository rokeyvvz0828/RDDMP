-- V195: 迁移检核规则增量（REQ-20260820-031）——统一回收站"管理"权限落账。
--
-- 背景：ContentRecycleBinController 服务端守卫为 data-migration:manage / system:admin，
-- 但权限目录中不存在这两个节点（V191 已把 740 manage 迁移为 base:view），且 V165 仅授予
-- "查看"权限，导致回收站恢复/彻底删除接口对管理员角色也 403。
-- 本脚本补齐与"统一回收站"菜单绑定的 manage 权限，并授权超级管理员(1)、数据迁移管理员(200)；
-- 202 开发人员在现有权限体系下仍只读，不授予恢复/彻底删除权限。
-- 仅追加，不修改历史迁移；全部 INSERT IGNORE / 存在性守卫，幂等可重跑。

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES (7322, 1, 732, 'manage', 'data-migration:content:recycle-bin:manage', '恢复/彻底删除');

-- 超级管理员（1）：已具备菜单可见，补管理权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, 7322, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id = 7322 AND status = 1;

-- 数据迁移管理员（200）：可见回收站菜单 + 管理权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 200, 732, 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 732;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, 7322, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id = 7322 AND status = 1;
