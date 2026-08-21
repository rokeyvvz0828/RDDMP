-- Data migration asset library V3. Additive only; application rollback hides menus and leaves data intact.

CREATE TABLE dm_project (
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

CREATE TABLE dm_component (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT NOT NULL,
    component_code VARCHAR(64) NOT NULL,
    component_name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    owner_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dm_component_code (tenant_id, project_id, component_code, deleted),
    KEY idx_dm_component_project (tenant_id, project_id, deleted)
);

CREATE TABLE dm_asset (
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

CREATE TABLE dm_operation_log (
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

CREATE TABLE dm_dashboard_snapshot (
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

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no) VALUES
(600,1,0,'directory','数迁资产看板','DataMigrationDashboard','/data-migration/dashboard','LAYOUT','data-migration:access','data-analysis',80),
(601,1,600,'menu','整体看板','DataMigrationOverall','/data-migration/dashboard/overall','data-migration','data-migration:dashboard:overall','dashboard',10),
(602,1,600,'menu','组件看板','DataMigrationComponent','/data-migration/dashboard/components','data-migration','data-migration:dashboard:components','pie-chart',20),
(620,1,0,'directory','数迁资产内容管理','DataMigrationContent','/data-migration/content','LAYOUT','data-migration:access','folder-opened',90),
(621,1,620,'menu','汇报材料','DataMigrationReports','/data-migration/content/reports','data-migration','data-migration:content:reports','document',10),
(622,1,620,'menu','会议纪要','DataMigrationMeetings','/data-migration/content/meetings','data-migration','data-migration:content:meetings','document',20),
(623,1,620,'menu','迁移方案','DataMigrationPlans','/data-migration/content/plans','data-migration','data-migration:content:plans','document',30),
(624,1,620,'menu','迁移映射','DataMigrationMappings','/data-migration/content/mappings','data-migration','data-migration:content:mappings','document',40),
(625,1,620,'menu','迁移检核规则','DataMigrationValidationRules','/data-migration/content/validation-rules','data-migration','data-migration:content:validation-rules','document-checked',50),
(626,1,620,'menu','迁移参数','DataMigrationParameters','/data-migration/content/parameters','data-migration','data-migration:content:parameters','setting',60),
(627,1,620,'menu','迁移过程依赖文件','DataMigrationDependencies','/data-migration/content/dependencies','data-migration','data-migration:content:dependencies','files',70),
(628,1,620,'menu','迁移程序','DataMigrationPrograms','/data-migration/content/programs','data-migration','data-migration:content:programs','cpu',80),
(629,1,620,'menu','专题材料','DataMigrationTopics','/data-migration/content/topics','data-migration','data-migration:content:topics','collection',90),
(630,1,620,'menu','投产及演练','DataMigrationReleaseDrills','/data-migration/content/release-drills','data-migration','data-migration:content:release-drills','promotion',100),
(631,1,620,'menu','问题清单','DataMigrationIssues','/data-migration/content/issues','data-migration','data-migration:content:issues','warning',110),
(640,1,0,'directory','基础资料管理','DataMigrationBase','/data-migration/base','LAYOUT','data-migration:manage','database',100),
(641,1,640,'menu','项目清单','DataMigrationProjects','/data-migration/base/projects','data-migration','data-migration:base:projects','folder',10),
(642,1,640,'menu','系统/组件清单','DataMigrationComponents','/data-migration/base/components','data-migration','data-migration:base:components','box',20),
(643,1,640,'menu','目标表结构','DataMigrationTargetTables','/data-migration/base/target-tables','data-migration','data-migration:base:target-tables','table',30),
(644,1,640,'menu','中间表结构','DataMigrationIntermediateTables','/data-migration/base/intermediate-tables','data-migration','data-migration:base:intermediate-tables','table-2',40);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 600 AND 644 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 1, 1, id, 'read', permission_code, '查看' FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 600 AND 644 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 2, 1, id, 'create', CONCAT(permission_code, ':create'), '新增' FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 600 AND 644 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 3, 1, id, 'update', CONCAT(permission_code, ':update'), '修改' FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 600 AND 644 AND deleted = 0;
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT id * 10 + 4, 1, id, 'delete', CONCAT(permission_code, ':delete'), '删除' FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 600 AND 644 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id BETWEEN 600 AND 644;
