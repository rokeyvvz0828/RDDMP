-- V192：移除目录菜单的所有权限节点
-- 目录菜单（type='directory'）是纯组织容器，没有独立路由和页面，
-- 菜单可见性由 sys_role_menu 控制，不需要权限节点。
-- 仅追加，不修改已发布迁移。

-- ── 删除目录 700 (数迁资产看板) 的所有权限节点 ──
DELETE FROM sys_role_permission WHERE permission_id IN (7000, 7001, 7002, 7003) AND tenant_id = 1;
DELETE FROM sys_menu_permission WHERE id IN (7000, 7001, 7002, 7003) AND tenant_id = 1;

-- ── 删除目录 720 (数迁资产内容管理) 的所有权限节点 ──
DELETE FROM sys_role_permission WHERE permission_id IN (7200, 7201, 7202, 7203) AND tenant_id = 1;
DELETE FROM sys_menu_permission WHERE id IN (7200, 7201, 7202, 7203) AND tenant_id = 1;

-- ── 删除目录 740 (基础资料管理) 的所有权限节点 ──
DELETE FROM sys_role_permission WHERE permission_id IN (7400, 7401, 7402, 7403) AND tenant_id = 1;
DELETE FROM sys_menu_permission WHERE id IN (7400, 7401, 7402, 7403) AND tenant_id = 1;
