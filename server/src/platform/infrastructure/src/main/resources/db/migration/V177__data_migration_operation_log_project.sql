-- V176: dm_operation_log 增加项目维度（REQ-20260820-031 9.2 P1「审计缺项目维度」治理）。
-- 背景：审计表只有 entity_type + entity_id，项目级审计查询只能回查实体，清空回收站类操作无实体可回查。
-- 处置：追加 project_id（写入侧从实体项目上下文填充；存量行默认 0，不参与项目级查询，需追溯走补偿迁移）；
--   追加 (tenant_id, project_id, entity_type, created_at) 组合索引支撑项目级审计查询。
-- 约束：Flyway 只追加，不改历史脚本；information_schema 条件式执行，幂等可重跑。

-- 1. 追加 project_id（置于 tenant_id 之后，与实体表口径一致）
SET @dm_operation_log_project_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dm_operation_log' AND column_name = 'project_id'
);
SET @dm_operation_log_add_sql = IF(
    @dm_operation_log_project_id_exists = 0,
    'ALTER TABLE dm_operation_log
        ADD COLUMN project_id BIGINT NOT NULL DEFAULT 0 COMMENT ''所属项目（操作实体归属项目；回收站清空类操作记录操作范围项目）'' AFTER tenant_id',
    'SELECT 1'
);
PREPARE dm_operation_log_add_stmt FROM @dm_operation_log_add_sql;
EXECUTE dm_operation_log_add_stmt;
DEALLOCATE PREPARE dm_operation_log_add_stmt;

-- 2. 项目级审计查询索引
SET @dm_operation_log_project_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'dm_operation_log' AND index_name = 'idx_dm_operation_log_project'
);
SET @dm_operation_log_index_sql = IF(
    @dm_operation_log_project_index_exists = 0,
    'ALTER TABLE dm_operation_log
        ADD KEY idx_dm_operation_log_project (tenant_id, project_id, entity_type, created_at)',
    'SELECT 1'
);
PREPARE dm_operation_log_index_stmt FROM @dm_operation_log_index_sql;
EXECUTE dm_operation_log_index_stmt;
DEALLOCATE PREPARE dm_operation_log_index_stmt;
