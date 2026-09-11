-- V199: 迁移过程依赖文件专属域优化（REQ-20260910-068）
-- 背景：dm_dependency 原为通用文件型资产薄表（doc_code/doc_name + 附件），不满足"迁移参数与使用方系统关系管理"需求。
--   本脚本将其改造为依赖关系表：删除文档字段，新增 parameter_id，以 parameter_id + system_code 作为关系唯一键，
--   并补齐菜单 750 的 create/update/delete 细粒度权限。
-- 存量：本地/开发库 dm_dependency 为 0 行；脚本在任何结构修改前断言表为空，非空以 SIGNAL 失败，禁止静默丢弃数据。
-- 约束：Flyway 只追加，不改历史脚本；information_schema 条件式执行，幂等可重跑。

-- 1. 断言 dm_dependency 为空，避免删除 doc_code/doc_name 后丢失历史数据
DELIMITER $$
CREATE PROCEDURE dm_v199_assert_dependency_empty()
BEGIN
    DECLARE row_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO row_count FROM dm_dependency;
    IF row_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V199 失败：dm_dependency 存在存量行，未确认数据重建前禁止删列改造';
    END IF;
END$$
DELIMITER ;

CALL dm_v199_assert_dependency_empty();
DROP PROCEDURE dm_v199_assert_dependency_empty;

-- 2. 删除旧通用信封列与相关索引（幂等：列/索引不存在则跳过）

-- 2.1 删除活动唯一键（依赖 active_doc_code 生成列，需先删生成列再删主键列）
SET @dm_dep_drop_uk_active_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'uk_dm_dependency_active_code');
SET @dm_dep_drop_uk_active_sql = IF(
    @dm_dep_drop_uk_active_exists > 0,
    'ALTER TABLE dm_dependency DROP INDEX uk_dm_dependency_active_code',
    'SELECT 1');
PREPARE dm_dep_drop_uk_active_stmt FROM @dm_dep_drop_uk_active_sql;
EXECUTE dm_dep_drop_uk_active_stmt;
DEALLOCATE PREPARE dm_dep_drop_uk_active_stmt;

-- 2.2 删除 active_doc_code 生成列
SET @dm_dep_drop_active_doc_code = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND column_name = 'active_doc_code');
SET @dm_dep_drop_active_doc_code_sql = IF(
    @dm_dep_drop_active_doc_code > 0,
    'ALTER TABLE dm_dependency DROP COLUMN active_doc_code',
    'SELECT 1');
PREPARE dm_dep_drop_active_doc_code_stmt FROM @dm_dep_drop_active_doc_code_sql;
EXECUTE dm_dep_drop_active_doc_code_stmt;
DEALLOCATE PREPARE dm_dep_drop_active_doc_code_stmt;

-- 2.3 删除 doc_code 列
SET @dm_dep_drop_doc_code = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND column_name = 'doc_code');
SET @dm_dep_drop_doc_code_sql = IF(
    @dm_dep_drop_doc_code > 0,
    'ALTER TABLE dm_dependency DROP COLUMN doc_code',
    'SELECT 1');
PREPARE dm_dep_drop_doc_code_stmt FROM @dm_dep_drop_doc_code_sql;
EXECUTE dm_dep_drop_doc_code_stmt;
DEALLOCATE PREPARE dm_dep_drop_doc_code_stmt;

-- 2.4 删除 doc_name 列
SET @dm_dep_drop_doc_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND column_name = 'doc_name');
SET @dm_dep_drop_doc_name_sql = IF(
    @dm_dep_drop_doc_name > 0,
    'ALTER TABLE dm_dependency DROP COLUMN doc_name',
    'SELECT 1');
PREPARE dm_dep_drop_doc_name_stmt FROM @dm_dep_drop_doc_name_sql;
EXECUTE dm_dep_drop_doc_name_stmt;
DEALLOCATE PREPARE dm_dep_drop_doc_name_stmt;

-- 2.5 删除 owner 索引（新模型无 owner 语义，但保留 owner_id 列做兼容；索引不再有查询价值）
SET @dm_dep_drop_owner_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'idx_dm_dependency_owner');
SET @dm_dep_drop_owner_idx_sql = IF(
    @dm_dep_drop_owner_idx_exists > 0,
    'ALTER TABLE dm_dependency DROP INDEX idx_dm_dependency_owner',
    'SELECT 1');
PREPARE dm_dep_drop_owner_idx_stmt FROM @dm_dep_drop_owner_idx_sql;
EXECUTE dm_dep_drop_owner_idx_stmt;
DEALLOCATE PREPARE dm_dep_drop_owner_idx_stmt;

-- 2.6 删除 V178 建立的文档唯一键（引用 doc_code，删列前必须移除）
SET @dm_dep_drop_uk_code_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'uk_dm_dependency_code');
SET @dm_dep_drop_uk_code_sql = IF(
    @dm_dep_drop_uk_code_exists > 0,
    'ALTER TABLE dm_dependency DROP INDEX uk_dm_dependency_code',
    'SELECT 1');
PREPARE dm_dep_drop_uk_code_stmt FROM @dm_dep_drop_uk_code_sql;
EXECUTE dm_dep_drop_uk_code_stmt;
DEALLOCATE PREPARE dm_dep_drop_uk_code_stmt;

-- 2.7 删除旧查询索引（列组合已随新模型变化，3.4 重建为参数/使用方查询索引）
SET @dm_dep_drop_query_idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'idx_dm_dependency_query');
SET @dm_dep_drop_query_idx_sql = IF(
    @dm_dep_drop_query_idx_exists > 0,
    'ALTER TABLE dm_dependency DROP INDEX idx_dm_dependency_query',
    'SELECT 1');
PREPARE dm_dep_drop_query_idx_stmt FROM @dm_dep_drop_query_idx_sql;
EXECUTE dm_dep_drop_query_idx_stmt;
DEALLOCATE PREPARE dm_dep_drop_query_idx_stmt;

-- 3. 新增 parameter_id 列与关系唯一键（幂等）

-- 3.1 新增 parameter_id
SET @dm_dep_add_parameter_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND column_name = 'parameter_id');
SET @dm_dep_add_parameter_id_sql = IF(
    @dm_dep_add_parameter_id = 0,
    'ALTER TABLE dm_dependency ADD COLUMN parameter_id BIGINT NOT NULL COMMENT ''关联迁移参数ID（dm_parameter.id）'' AFTER project_id',
    'SELECT 1');
PREPARE dm_dep_add_parameter_id_stmt FROM @dm_dep_add_parameter_id_sql;
EXECUTE dm_dep_add_parameter_id_stmt;
DEALLOCATE PREPARE dm_dep_add_parameter_id_stmt;

-- 3.2 新增关系唯一键（含已删除记录也不允许重复，需恢复或彻底删除后才能重新新增）
SET @dm_dep_add_uk_relation = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'uk_dm_dependency_relation');
SET @dm_dep_add_uk_relation_sql = IF(
    @dm_dep_add_uk_relation = 0,
    'ALTER TABLE dm_dependency ADD UNIQUE KEY uk_dm_dependency_relation (tenant_id, project_id, parameter_id, system_code)',
    'SELECT 1');
PREPARE dm_dep_add_uk_relation_stmt FROM @dm_dep_add_uk_relation_sql;
EXECUTE dm_dep_add_uk_relation_stmt;
DEALLOCATE PREPARE dm_dep_add_uk_relation_stmt;

-- 3.3 重建查询索引（按项目 + 参数 + 使用方系统 + 删除状态）
SET @dm_dep_add_query_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'idx_dm_dependency_query');
SET @dm_dep_add_query_idx_sql = IF(
    @dm_dep_add_query_idx = 0,
    'ALTER TABLE dm_dependency ADD KEY idx_dm_dependency_query (tenant_id, project_id, parameter_id, system_code, deleted)',
    'SELECT 1');
PREPARE dm_dep_add_query_idx_stmt FROM @dm_dep_add_query_idx_sql;
EXECUTE dm_dep_add_query_idx_stmt;
DEALLOCATE PREPARE dm_dep_add_query_idx_stmt;

-- 3.5 按使用方系统反查索引
SET @dm_dep_add_system_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_dependency' AND index_name = 'idx_dm_dependency_system');
SET @dm_dep_add_system_idx_sql = IF(
    @dm_dep_add_system_idx = 0,
    'ALTER TABLE dm_dependency ADD KEY idx_dm_dependency_system (tenant_id, project_id, system_code, deleted)',
    'SELECT 1');
PREPARE dm_dep_add_system_idx_stmt FROM @dm_dep_add_system_idx_sql;
EXECUTE dm_dep_add_system_idx_stmt;
DEALLOCATE PREPARE dm_dep_add_system_idx_stmt;

-- 4. 菜单 750 细粒度权限：新增 create / update / delete（幂等，INSERT IGNORE）
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7501, 1, 750, 'create', 'data-migration:content:dependencies:create', '新增'),
    (7502, 1, 750, 'update', 'data-migration:content:dependencies:update', '编辑'),
    (7503, 1, 750, 'delete', 'data-migration:content:dependencies:delete', '删除');

-- 5. 角色授权：超级管理员(1) 和 数据迁移管理员(200) 获得全部写权限；数据迁移开发(202) 只获查看
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, p.id, 1 FROM sys_menu_permission p WHERE p.tenant_id = 1 AND p.menu_id = 750 AND p.status = 1;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 200, p.id, 1 FROM sys_menu_permission p WHERE p.tenant_id = 1 AND p.menu_id = 750 AND p.status = 1;

-- 数据迁移开发(202)：仅查看（read 权限 7500 已在 V190 授权，此处确保幂等）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 202, 7500, 1 FROM sys_menu_permission p WHERE p.tenant_id = 1 AND p.id = 7500 AND p.status = 1;
