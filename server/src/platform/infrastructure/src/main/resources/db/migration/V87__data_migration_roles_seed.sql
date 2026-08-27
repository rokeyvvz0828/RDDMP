-- 新增数据迁移角色：数据迁移管理员 / 数据迁移开发人员。
-- 兼容现有角色体系：角色 id 200/201（1=SUPER_ADMIN，100/101=需求角色）；
-- 幂等可重复执行：角色用 INSERT...WHERE NOT EXISTS，菜单/权限分配用 INSERT IGNORE；
-- 不修改已发布脚本，仅追加。

-- 1) sys_role 增加 role_description 列（幂等，兼容已有库；不改动任何 Java 代码）
SET @dm_role_desc_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'role_description'
);
SET @dm_role_desc_ddl = IF(@dm_role_desc_col = 0,
    'ALTER TABLE sys_role ADD COLUMN role_description VARCHAR(255) NULL COMMENT ''角色描述'' AFTER role_name',
    'SELECT 1');
PREPARE dm_role_desc_stmt FROM @dm_role_desc_ddl;
EXECUTE dm_role_desc_stmt;
DEALLOCATE PREPARE dm_role_desc_stmt;

-- 2) 数据迁移管理员（id=200）：创建、监控与管理权限
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_description, status, deleted)
SELECT 200, 1, 'DATA_MIGRATION_ADMIN', '数据迁移管理员',
       '负责数据迁移任务的创建、监控与管理，具备数据迁移模块全部菜单与动作权限', 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'DATA_MIGRATION_ADMIN' AND deleted = 0
);

-- 3) 数据迁移开发人员（id=201）：执行与调试权限
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_description, status, deleted)
SELECT 201, 1, 'DATA_MIGRATION_DEVELOPER', '数据迁移开发人员',
       '负责迁移任务的执行与调试，具备数据迁移模块查看与只读权限', 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'DATA_MIGRATION_DEVELOPER' AND deleted = 0
);

-- 4) 菜单可见性：两个角色均可见全部数据迁移菜单（700-744 现存菜单；741 项目清单已由 V86 清理）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu WHERE tenant_id = 1 AND (id BETWEEN 700 AND 744 OR route_name = 'DataMigration') AND deleted = 0;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu WHERE tenant_id = 1 AND (id BETWEEN 700 AND 744 OR route_name = 'DataMigration') AND deleted = 0;

-- 5) 权限分配
-- 5.1 管理员：全部 data-migration 动作权限（read/create/update/delete）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND status = 1 AND permission_code LIKE 'data-migration:%';

-- 5.2 开发人员：仅 read 动作权限（查看 / 执行 / 调试）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND status = 1 AND action_code = 'read' AND permission_code LIKE 'data-migration:%';
