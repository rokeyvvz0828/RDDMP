-- V190：修复数据迁移模块权限硬编码问题
-- 1) 修复 V84 中 permission_code 使用斜杠的菜单（改为冒号格式，与 sys_menu_permission 一致）
-- 2) 补齐"迁移过程依赖文件"菜单（750）及查看权限
-- 3) 补齐"迁移检核规则"菜单（751）及查看权限
-- 4) 补齐"迁移参数"菜单（752）及查看权限
-- 5) 为三个新菜单创建细粒度权限节点（read/create/update/delete）
-- 6) 授予数据迁移管理员（200）和开发人员（201）新权限
-- 仅追加，不修改已发布迁移。

-- ── 1) 修复斜杠格式的 permission_code ──
UPDATE sys_menu SET permission_code = 'data-migration:content:parameters' WHERE tenant_id = 1 AND deleted = 0 AND permission_code = 'data-migration:content/parameters';
UPDATE sys_menu SET permission_code = 'data-migration:content:dependencies' WHERE tenant_id = 1 AND deleted = 0 AND permission_code = 'data-migration:content/dependencies';
UPDATE sys_menu SET permission_code = 'data-migration:content:programs' WHERE tenant_id = 1 AND deleted = 0 AND permission_code = 'data-migration:content/programs';
UPDATE sys_menu SET permission_code = 'data-migration:content:topics' WHERE tenant_id = 1 AND deleted = 0 AND permission_code = 'data-migration:content/topics';
UPDATE sys_menu SET permission_code = 'data-migration:content:release-drills' WHERE tenant_id = 1 AND deleted = 0 AND permission_code = 'data-migration:content/release-drills';
UPDATE sys_menu SET permission_code = 'data-migration:content:issues' WHERE tenant_id = 1 AND deleted = 0 AND permission_code = 'data-migration:content/issues';

-- 同步修正 sys_menu_permission 中可能残留的斜杠格式权限码
UPDATE sys_menu_permission SET permission_code = 'data-migration:content:parameters' WHERE tenant_id = 1 AND permission_code = 'data-migration:content/parameters';
UPDATE sys_menu_permission SET permission_code = 'data-migration:content:dependencies' WHERE tenant_id = 1 AND permission_code = 'data-migration:content/dependencies';
UPDATE sys_menu_permission SET permission_code = 'data-migration:content:programs' WHERE tenant_id = 1 AND permission_code = 'data-migration:content/programs';
UPDATE sys_menu_permission SET permission_code = 'data-migration:content:topics' WHERE tenant_id = 1 AND permission_code = 'data-migration:content/topics';
UPDATE sys_menu_permission SET permission_code = 'data-migration:content:release-drills' WHERE tenant_id = 1 AND permission_code = 'data-migration:content/release-drills';
UPDATE sys_menu_permission SET permission_code = 'data-migration:content:issues' WHERE tenant_id = 1 AND permission_code = 'data-migration:content/issues';

-- ── 2) 补齐"迁移过程依赖文件"菜单 750 ──
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 750, 1, 720, 'menu', '迁移过程依赖文件', 'DataMigrationDependencies',
       '/data-migration/content/dependencies', 'data-migration',
       'data-migration:content:dependencies', 'files', 70, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationDependencies'
);

-- ── 3) 补齐"迁移检核规则"菜单 751 ──
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 751, 1, 720, 'menu', '迁移检核规则', 'DataMigrationValidationRules',
       '/data-migration/content/validation-rules', 'data-migration',
       'data-migration:content:validation-rules', 'document-checked', 50, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationValidationRules'
);

-- ── 4) 补齐"迁移参数"菜单 752 ──
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no, visible, status, deleted)
SELECT 752, 1, 720, 'menu', '迁移参数', 'DataMigrationParameters',
       '/data-migration/content/parameters', 'data-migration',
       'data-migration:content:parameters', 'setting', 60, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationParameters'
);

-- ── 5) 为三个新菜单创建细粒度权限节点 ──

-- 迁移过程依赖文件（750）：read
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7500, 1, 750, 'read', 'data-migration:content:dependencies', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 750);

-- 迁移检核规则（751）：read / create / update / delete
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7510, 1, 751, 'read', 'data-migration:content:validation-rules', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 751);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7511, 1, 751, 'create', 'data-migration:content:validation-rules:create', '新增'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 751);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7512, 1, 751, 'update', 'data-migration:content:validation-rules:update', '编辑'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 751);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7513, 1, 751, 'delete', 'data-migration:content:validation-rules:delete', '删除'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 751);

-- 迁移参数（752）：read / create / update / delete
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7520, 1, 752, 'read', 'data-migration:content:parameters', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 752);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7521, 1, 752, 'create', 'data-migration:content:parameters:create', '新增'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 752);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7522, 1, 752, 'update', 'data-migration:content:parameters:update', '编辑'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 752);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 7523, 1, 752, 'delete', 'data-migration:content:parameters:delete', '删除'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 752);

-- ── 6) 角色授权 ──

-- 管理员（200）：新菜单可见 + 全部权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 200, m.id, 1 FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.id IN (750, 751, 752);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, p.id, 1 FROM sys_menu_permission p WHERE p.tenant_id = 1 AND p.status = 1 AND p.menu_id IN (750, 751, 752);

-- 开发人员（201）：新菜单可见 + 仅查看权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 201, m.id, 1 FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.id IN (750, 751, 752);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, p.id, 1 FROM sys_menu_permission p WHERE p.tenant_id = 1 AND p.status = 1 AND p.action_code = 'read' AND p.menu_id IN (750, 751, 752);

-- 超级管理员（1）：新菜单可见 + 全部权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, m.id, 1 FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.id IN (750, 751, 752);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, p.id, 1 FROM sys_menu_permission p WHERE p.tenant_id = 1 AND p.status = 1 AND p.menu_id IN (750, 751, 752);
