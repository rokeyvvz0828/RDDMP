-- V193：数据迁移目录菜单权限清理与权限码格式补齐
-- 平台设计：type='directory' 的目录菜单是纯组织容器，权限管理页不应显示任何动作选项。
-- 背景：
--   1) V84 对菜单区间 700-744 统一生成了 read/create/update/delete 权限节点，
--      其中目录 720（/data-migration/content）、740（/data-migration/base）被误生成了动作节点；
--   2) V192 只清理了 menu_id*10+1 ~ +3 的节点，遗漏了 menu_id*10+4（删除）节点，
--      实际残留为 7204、7404，导致权限管理页仍显示“删除”；
--   3) V190 只修复了 sys_menu_permission 的基础权限码斜杠，未覆盖 :create/:update/:delete 变体。
-- 本迁移仅追加、幂等清理，不修改任何已发布脚本。

-- ── 1) 删除数据迁移目录菜单的角色权限关联 ──
DELETE rp
FROM sys_role_permission rp
JOIN sys_menu_permission mp ON mp.id = rp.permission_id AND mp.tenant_id = rp.tenant_id
JOIN sys_menu m ON m.id = mp.menu_id AND m.tenant_id = mp.tenant_id
WHERE rp.tenant_id = 1
  AND m.deleted = 0
  AND m.menu_type = 'directory'
  AND m.route_path LIKE '/data-migration%';

-- ── 2) 删除数据迁移目录菜单的权限节点 ──
DELETE mp
FROM sys_menu_permission mp
JOIN sys_menu m ON m.id = mp.menu_id AND m.tenant_id = mp.tenant_id
WHERE mp.tenant_id = 1
  AND m.deleted = 0
  AND m.menu_type = 'directory'
  AND m.route_path LIKE '/data-migration%';

-- ── 3) 补齐 V190 遗漏的动作权限码斜杠格式（data-migration:content/…:create/update/delete） ──
UPDATE sys_menu_permission
SET permission_code = REPLACE(permission_code, 'data-migration:content/', 'data-migration:content:')
WHERE tenant_id = 1 AND permission_code LIKE 'data-migration:content/%';
