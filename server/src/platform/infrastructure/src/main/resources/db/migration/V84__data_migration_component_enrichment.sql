-- 数据迁移基础资料：系统/组件清单增量。
-- 1) 幂等补齐 dm_project / dm_component 建表（源目录历史迁移缺失的既有缺口）。
-- 2) dm_component 增列：physical_subsystem_code（系统编号，关联 arch_physical_subsystem.code）、
--    total_check（是否涉及总分核对）、created_by / updated_by（创建人 / 更新人）。
-- 3) 字段清理：component_code / component_name 为多余字段（组件身份由物理子系统编号承担），
--    幂等删除这两列及其唯一键 uk_dm_component_code，并以 uk_dm_component_subsystem
--    （系统编号项目内唯一）替代；列/索引存在性判断遵循 V62 既有模式。
-- 4) 幂等补齐"系统/组件清单"菜单 742 及其权限码（read/create/update/delete），
--    保证全新库可直接访问该菜单，旧库不受影响（INSERT IGNORE / WHERE NOT EXISTS）。
-- 仅追加、不修改已发布脚本；本脚本为当前分支未发布迁移，字段清理随迁移一次完成。

-- V82/V83 已从当前分支基线移除，但模块现有服务仍依赖这些基础表和菜单。
-- 在本次未发布迁移中幂等补齐，保证全新库和已有库都能沿当前契约启动。
CREATE TABLE IF NOT EXISTS dm_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT NOT NULL,
    component_id BIGINT,
    asset_type VARCHAR(32) NOT NULL,
    asset_code VARCHAR(96) NOT NULL,
    asset_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(160),
    file_size BIGINT,
    object_key VARCHAR(512),
    checksum_md5 CHAR(32),
    structured_data JSON,
    owner_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dm_asset_code (tenant_id, project_id, asset_type, asset_code, deleted),
    KEY idx_dm_asset_query (tenant_id, project_id, asset_type, deleted, updated_at),
    KEY idx_dm_asset_owner (tenant_id, owner_id, deleted)
);

CREATE TABLE IF NOT EXISTS dm_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    actor_id BIGINT NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT,
    result_code VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    trace_id VARCHAR(64),
    detail_json JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_dm_operation_log (tenant_id, created_at, actor_id)
);

CREATE TABLE IF NOT EXISTS dm_dashboard_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    snapshot_date DATE NOT NULL,
    project_id BIGINT,
    component_id BIGINT,
    metric_code VARCHAR(64) NOT NULL,
    metric_value DECIMAL(20,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dm_snapshot (tenant_id, snapshot_date, project_id, component_id, metric_code)
);

-- 数据迁移菜单层级（使用稳定编号，重复执行安全；V86/V88 后续迁移按路由/编号修正存量数据）。
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (699,1,0,'directory','数据迁移','DataMigration','/data-migration','LAYOUT','data-migration:access','takeaway-box',80),
    (700,1,699,'directory','数迁资产看板','DataMigrationDashboard','/data-migration/dashboard','LAYOUT','data-migration:access','data-analysis',10),
    (701,1,700,'menu','整体看板','DataMigrationOverall','/data-migration/dashboard/overall','data-migration','data-migration:dashboard:overall','dashboard',10),
    (702,1,700,'menu','组件看板','DataMigrationComponent','/data-migration/dashboard/components','data-migration','data-migration:dashboard:components','pie-chart',20),
    (720,1,699,'directory','数迁资产内容管理','DataMigrationContent','/data-migration/content','LAYOUT','data-migration:access','folder-opened',20),
    (721,1,720,'menu','汇报材料','DataMigrationReports','/data-migration/content/reports','data-migration','data-migration:content:reports','document',10),
    (722,1,720,'menu','会议纪要','DataMigrationMeetings','/data-migration/content/meetings','data-migration','data-migration:content:meetings','document',20),
    (723,1,720,'menu','迁移方案','DataMigrationPlans','/data-migration/content/plans','data-migration','data-migration:content:plans','document',30),
    (724,1,720,'menu','迁移映射','DataMigrationMappings','/data-migration/content/mappings','data-migration','data-migration:content:mappings','document',40),
    (725,1,720,'menu','迁移检核规则','DataMigrationValidationRules','/data-migration/content/validation-rules','data-migration','data-migration:content:validation-rules','document-checked',50),
    (726,1,720,'menu','迁移参数','DataMigrationParameters','/data-migration/content/parameters','data-migration','data-migration:content/parameters','setting',60),
    (727,1,720,'menu','迁移过程依赖文件','DataMigrationDependencies','/data-migration/content/dependencies','data-migration','data-migration:content/dependencies','files',70),
    (728,1,720,'menu','迁移程序','DataMigrationPrograms','/data-migration/content/programs','data-migration','data-migration:content/programs','cpu',80),
    (729,1,720,'menu','专题材料','DataMigrationTopics','/data-migration/content/topics','data-migration','data-migration:content/topics','collection',90),
    (730,1,720,'menu','投产及演练','DataMigrationReleaseDrills','/data-migration/content/release-drills','data-migration','data-migration:content/release-drills','promotion',100),
    (731,1,720,'menu','问题清单','DataMigrationIssues','/data-migration/content/issues','data-migration','data-migration:content/issues','warning',110),
    (740,1,699,'directory','基础资料管理','DataMigrationBase','/data-migration/base','LAYOUT','data-migration:manage','database',30),
    (741,1,740,'menu','项目清单','DataMigrationProjects','/data-migration/base/projects','data-migration','data-migration:base:projects','folder',10),
    (742,1,740,'menu','系统/组件清单','DataMigrationComponents','/data-migration/base/components','data-migration','data-migration:base:components','box',20),
    (743,1,740,'menu','目标表结构','DataMigrationTargetTables','/data-migration/base/target-tables','data-migration','data-migration:base:target-tables','table',30),
    (744,1,740,'menu','中间表结构','DataMigrationIntermediateTables','/data-migration/base/intermediate-tables','data-migration','data-migration:base:intermediate-tables','table-2',40);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu
WHERE tenant_id = 1 AND deleted = 0 AND (id BETWEEN 700 AND 744 OR route_name = 'DataMigration');
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 1, 1, id, 'read', permission_code, '查看'
FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id BETWEEN 700 AND 744 AND permission_code IS NOT NULL AND permission_code <> '';
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 2, 1, id, 'create', CONCAT(permission_code, ':create'), '新增'
FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id BETWEEN 700 AND 744 AND permission_code IS NOT NULL AND permission_code <> '';
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 3, 1, id, 'update', CONCAT(permission_code, ':update'), '修改'
FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id BETWEEN 700 AND 744 AND permission_code IS NOT NULL AND permission_code <> '';
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 4, 1, id, 'delete', CONCAT(permission_code, ':delete'), '删除'
FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id BETWEEN 700 AND 744 AND permission_code IS NOT NULL AND permission_code <> '';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND menu_id IN (SELECT id FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id BETWEEN 700 AND 744);

CREATE TABLE IF NOT EXISTS dm_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    owner_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dm_project_code (tenant_id, project_code, deleted),
    KEY idx_dm_project_owner (tenant_id, owner_id, deleted)
);

-- 若表已存在则跳过建表，仅由下方动态补列/清理逻辑处理增量变化。
CREATE TABLE IF NOT EXISTS dm_component (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    physical_subsystem_code VARCHAR(64) NULL COMMENT '系统编号（arch_physical_subsystem.code），项目内唯一',
    total_check TINYINT NOT NULL DEFAULT 0 COMMENT '是否涉及总分核对 0否 1是',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code, deleted),
    KEY idx_dm_component_project (tenant_id, project_id, deleted),
    KEY idx_dm_component_list (tenant_id, project_id, deleted, updated_at)
);

-- 动态补列：表已存在但缺列时补齐（幂等）。
SET @dm_component_physical_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'physical_subsystem_code'
);
SET @dm_component_physical_code_sql = IF(
    @dm_component_physical_code_exists = 0,
    'ALTER TABLE dm_component ADD COLUMN physical_subsystem_code VARCHAR(64) NULL COMMENT ''系统编号（arch_physical_subsystem.code）'' AFTER updated_at',
    'SELECT 1'
);
PREPARE dm_component_physical_code_stmt FROM @dm_component_physical_code_sql;
EXECUTE dm_component_physical_code_stmt;
DEALLOCATE PREPARE dm_component_physical_code_stmt;

SET @dm_component_total_check_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'total_check'
);
SET @dm_component_total_check_sql = IF(
    @dm_component_total_check_exists = 0,
    'ALTER TABLE dm_component ADD COLUMN total_check TINYINT NOT NULL DEFAULT 0 COMMENT ''是否涉及总分核对 0否 1是'' AFTER physical_subsystem_code',
    'SELECT 1'
);
PREPARE dm_component_total_check_stmt FROM @dm_component_total_check_sql;
EXECUTE dm_component_total_check_stmt;
DEALLOCATE PREPARE dm_component_total_check_stmt;

SET @dm_component_created_by_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'created_by'
);
SET @dm_component_created_by_sql = IF(
    @dm_component_created_by_exists = 0,
    'ALTER TABLE dm_component ADD COLUMN created_by BIGINT NULL COMMENT ''创建人'' AFTER total_check',
    'SELECT 1'
);
PREPARE dm_component_created_by_stmt FROM @dm_component_created_by_sql;
EXECUTE dm_component_created_by_stmt;
DEALLOCATE PREPARE dm_component_created_by_stmt;

SET @dm_component_updated_by_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'updated_by'
);
SET @dm_component_updated_by_sql = IF(
    @dm_component_updated_by_exists = 0,
    'ALTER TABLE dm_component ADD COLUMN updated_by BIGINT NULL COMMENT ''更新人'' AFTER created_by',
    'SELECT 1'
);
PREPARE dm_component_updated_by_stmt FROM @dm_component_updated_by_sql;
EXECUTE dm_component_updated_by_stmt;
DEALLOCATE PREPARE dm_component_updated_by_stmt;

-- 字段清理（幂等）：先删唯一键，再删多余列，避免索引依赖报错。
-- 清理范围：component_code / component_name（组件编号/名称）与 description（组件简介），
-- 组件身份由系统编号承担、系统信息由 arch_physical_subsystem 动态带出，三者均为多余字段；
-- dm_project.description（项目简介）不在清理范围。
SET @dm_component_code_key_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'uk_dm_component_code'
);
SET @dm_component_code_key_sql = IF(
    @dm_component_code_key_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_component DROP INDEX uk_dm_component_code'
);
PREPARE dm_component_code_key_stmt FROM @dm_component_code_key_sql;
EXECUTE dm_component_code_key_stmt;
DEALLOCATE PREPARE dm_component_code_key_stmt;

SET @dm_component_code_col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'component_code'
);
SET @dm_component_code_col_sql = IF(
    @dm_component_code_col_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_component DROP COLUMN component_code'
);
PREPARE dm_component_code_col_stmt FROM @dm_component_code_col_sql;
EXECUTE dm_component_code_col_stmt;
DEALLOCATE PREPARE dm_component_code_col_stmt;

SET @dm_component_name_col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'component_name'
);
SET @dm_component_name_col_sql = IF(
    @dm_component_name_col_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_component DROP COLUMN component_name'
);
PREPARE dm_component_name_col_stmt FROM @dm_component_name_col_sql;
EXECUTE dm_component_name_col_stmt;
DEALLOCATE PREPARE dm_component_name_col_stmt;

SET @dm_component_desc_col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'description'
);
SET @dm_component_desc_col_sql = IF(
    @dm_component_desc_col_exists = 0,
    'SELECT 1',
    'ALTER TABLE dm_component DROP COLUMN description'
);
PREPARE dm_component_desc_col_stmt FROM @dm_component_desc_col_sql;
EXECUTE dm_component_desc_col_stmt;
DEALLOCATE PREPARE dm_component_desc_col_stmt;

-- 系统编号项目内唯一（替代原组件编号唯一约束；存在重复历史数据时跳过，由服务层兜底校验）。
SET @dm_component_subsystem_key_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'uk_dm_component_subsystem'
);
SET @dm_component_subsystem_dup = (
    SELECT COUNT(*) FROM (
        SELECT 1 FROM dm_component
        WHERE physical_subsystem_code IS NOT NULL
        GROUP BY tenant_id, project_id, physical_subsystem_code, deleted
        HAVING COUNT(*) > 1
    ) t
);
SET @dm_component_subsystem_key_sql = IF(
    @dm_component_subsystem_key_exists = 0 AND @dm_component_subsystem_dup = 0,
    'ALTER TABLE dm_component ADD UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code, deleted)',
    'SELECT 1'
);
PREPARE dm_component_subsystem_key_stmt FROM @dm_component_subsystem_key_sql;
EXECUTE dm_component_subsystem_key_stmt;
DEALLOCATE PREPARE dm_component_subsystem_key_stmt;

-- 动态补索引：列表分页索引（幂等）。
SET @dm_component_list_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'idx_dm_component_list'
);
SET @dm_component_list_index_sql = IF(
    @dm_component_list_index_exists = 0,
    'ALTER TABLE dm_component ADD KEY idx_dm_component_list (tenant_id, project_id, deleted, updated_at)',
    'SELECT 1'
);
PREPARE dm_component_list_index_stmt FROM @dm_component_list_index_sql;
EXECUTE dm_component_list_index_stmt;
DEALLOCATE PREPARE dm_component_list_index_stmt;

-- 幂等补齐"系统/组件清单"菜单（742）与动作权限；旧库已有则不重复。
SET @dm_components_menu_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationComponents'
    ORDER BY id LIMIT 1
);
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no)
SELECT 642, 1,
       (SELECT id FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationBase' ORDER BY id LIMIT 1),
       'menu', '系统/组件清单', 'DataMigrationComponents', '/data-migration/base/components',
       'data-migration', 'data-migration:base:components', 'box', 20
WHERE @dm_components_menu_id IS NULL;

SET @dm_components_menu_id = (
    SELECT id FROM sys_menu
    WHERE tenant_id = 1 AND deleted = 0 AND route_name = 'DataMigrationComponents'
    ORDER BY id LIMIT 1
);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (@dm_components_menu_id * 10 + 1, 1, @dm_components_menu_id, 'read', 'data-migration:base:components', '查看'),
    (@dm_components_menu_id * 10 + 2, 1, @dm_components_menu_id, 'create', 'data-migration:base:components:create', '新增'),
    (@dm_components_menu_id * 10 + 3, 1, @dm_components_menu_id, 'update', 'data-migration:base:components:update', '修改'),
    (@dm_components_menu_id * 10 + 4, 1, @dm_components_menu_id, 'delete', 'data-migration:base:components:delete', '删除');
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND menu_id = @dm_components_menu_id;
