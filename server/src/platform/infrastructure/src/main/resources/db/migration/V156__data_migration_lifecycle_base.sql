-- REQ-20260923-001 批次1·复用接入底座：
-- 1) 注册"数迁生命周期管理"目录菜单（id=745，父节点 699 数据迁移）与权限点；
-- 2) 角色可见性与权限分配复用既有模式（角色 1=SUPER_ADMIN、200=DATA_MIGRATION_ADMIN、201=DATA_MIGRATION_DEVELOPER）；
-- 3) 契约视图：组件选项（dm_component.deleted=0 视为可用，D2）、角色选项（sys_role 启用）；
--    成员选项复用 platform/system 的 SystemReferenceQuery（deleted=0 AND status=1），不重复造数据源。
-- 仅追加，不修改已发布脚本；幂等可重复执行（INSERT IGNORE / CREATE OR REPLACE VIEW）。

-- 1) 生命周期平台目录菜单（生命周期作为数据迁移模块子域，D1）
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (745, 1, 699, 'directory', '数迁生命周期管理', 'DataMigrationLifecycle', '/data-migration/lifecycle', 'LAYOUT', 'data-migration-lifecycle:access', 'timer', 40);

-- 2) 生命周期权限点：access 基础访问、create/update/delete 写操作、review 审核人、dashboard 看板、manage 平台管理
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7451, 1, 745, 'read', 'data-migration-lifecycle:access', '生命周期平台访问'),
    (7452, 1, 745, 'create', 'data-migration-lifecycle:access:create', '创建生命周期任务与配置'),
    (7453, 1, 745, 'update', 'data-migration-lifecycle:access:update', '维护生命周期任务与配置'),
    (7454, 1, 745, 'delete', 'data-migration-lifecycle:access:delete', '删除生命周期任务与配置'),
    (7455, 1, 745, 'review', 'data-migration-lifecycle:review', '生命周期任务审核'),
    (7456, 1, 745, 'dashboard', 'data-migration-lifecycle:dashboard', '生命周期看板访问'),
    (7457, 1, 745, 'manage', 'data-migration-lifecycle:manage', '生命周期平台管理');

-- 3) 角色可见性：超级管理员/数据迁移管理员/数据迁移开发人员可见生命周期菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 745, 1), (200, 745, 1), (201, 745, 1);

-- 4) 权限分配：1 与 200 全部生命周期权限；201 仅 read（与 V87 开发人员只读模式一致）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 745 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 745 AND status = 1;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 201, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 745 AND status = 1 AND action_code = 'read';

-- 5) 契约视图：组件选项（仅 deleted=0 可用组件，附带项目/物理子系统只读信息）
CREATE OR REPLACE VIEW v_data_migration_lifecycle_component_option AS
SELECT c.id, c.tenant_id, c.project_id, p.project_code, p.project_name,
       c.physical_subsystem_code, s.short_name AS system_short_name, s.name AS system_name,
       s.business_group_name, c.owner_id, c.updated_at
FROM dm_component c
JOIN pm_project p ON p.id = c.project_id AND p.tenant_id = c.tenant_id AND p.deleted = 0
LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0
WHERE c.deleted = 0;

-- 6) 契约视图：角色选项（仅启用角色，status=1 AND deleted=0）
CREATE OR REPLACE VIEW v_data_migration_lifecycle_role_option AS
SELECT id, tenant_id, role_code, role_name
FROM sys_role
WHERE status = 1 AND deleted = 0;
