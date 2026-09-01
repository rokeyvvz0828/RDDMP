-- REQ-20260831-050：统一回收站此前只注册了前端路由（web/src/router），未在 sys_menu 建节点，
-- 侧边栏按 sys_menu→sys_role_menu→sys_user_role 过滤渲染，故菜单一直不可见。
-- 本迁移幂等补齐"统一回收站"菜单（732，挂在 720 数迁资产内容管理 下，排序 120 位于问题清单之后）、
-- 查看动作权限，并授予管理员角色（role_id=1），保证全新库与已有库都能直接看到该菜单。
-- 仅追加，不修改历史迁移；回收站 API 的实际访问仍由 ContentRecycleBinController 上的
-- data-migration:manage / system:admin 服务端授权守卫，本菜单权限仅用于可见性与角色授权维护。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 732, 1, 720, 'menu', '统一回收站', 'DataMigrationRecycleBin',
       '/data-migration/content/recycle-bin', 'data-migration',
       'data-migration:content:recycle-bin', 'delete', 120, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationRecycleBin'
);

-- 查看权限节点（沿用 V84 的 menu_id*10+action 编号约定：732*10+1=7321）。
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES (7321, 1, 732, 'read', 'data-migration:content:recycle-bin', '查看');

-- 管理员角色可见（sys_role_menu 驱动侧边栏）并持有查看权限（sys_role_permission 驱动接口授权展示）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 732, 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 732;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, 7321, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id = 7321;
