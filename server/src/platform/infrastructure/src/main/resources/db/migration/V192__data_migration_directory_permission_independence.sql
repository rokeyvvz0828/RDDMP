-- V191：数据迁移模块目录权限清理
-- 目标：目录菜单（type='directory'）不需要权限节点，菜单可见性由 sys_role_menu 控制
-- 仅追加，不修改已发布迁移。

-- ── 1) 更新目录菜单的 permission_code（仅作元数据标记） ──

-- 700 数迁资产看板：data-migration:access → data-migration:dashboard:view
UPDATE sys_menu SET permission_code = 'data-migration:dashboard:view'
WHERE tenant_id = 1 AND deleted = 0 AND id = 700 AND permission_code = 'data-migration:access';

-- 720 数迁资产内容管理：data-migration:access → data-migration:content:view
UPDATE sys_menu SET permission_code = 'data-migration:content:view'
WHERE tenant_id = 1 AND deleted = 0 AND id = 720 AND permission_code = 'data-migration:access';

-- 740 基础资料管理：data-migration:manage → data-migration:base:view
UPDATE sys_menu SET permission_code = 'data-migration:base:view'
WHERE tenant_id = 1 AND deleted = 0 AND id = 740 AND permission_code = 'data-migration:manage';
