-- V184: 投产及演练专属域模型与系统参数初始化。
-- 仅追加、条件式执行；颗粒度与资料类型归属系统管理/参数管理，不新建业务字典表。

-- 1. dm_release_drill 元数据（兼容历史表与低基线）。
SET @dm_release_drill_granularity_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_release_drill' AND column_name = 'granularity'),
    'SELECT 1',
    'ALTER TABLE dm_release_drill ADD COLUMN granularity VARCHAR(16) NOT NULL DEFAULT ''PROJECT'' COMMENT ''颗粒度 PROJECT/COMPONENT（参数管理）'' AFTER project_id'
);
PREPARE dm_release_drill_granularity_stmt FROM @dm_release_drill_granularity_sql;
EXECUTE dm_release_drill_granularity_stmt;
DEALLOCATE PREPARE dm_release_drill_granularity_stmt;

SET @dm_release_drill_type_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_release_drill' AND column_name = 'material_type_code'),
    'SELECT 1',
    'ALTER TABLE dm_release_drill ADD COLUMN material_type_code VARCHAR(64) NOT NULL DEFAULT ''RELEASE_PLAN'' COMMENT ''资料类型参数编码（系统管理/参数管理）'' AFTER granularity'
);
PREPARE dm_release_drill_type_stmt FROM @dm_release_drill_type_sql;
EXECUTE dm_release_drill_type_stmt;
DEALLOCATE PREPARE dm_release_drill_type_stmt;

SET @dm_release_drill_round_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_release_drill' AND column_name = 'drill_round'),
    'SELECT 1',
    'ALTER TABLE dm_release_drill ADD COLUMN drill_round VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''所属轮次（手工输入）'' AFTER material_type_code'
);
PREPARE dm_release_drill_round_stmt FROM @dm_release_drill_round_sql;
EXECUTE dm_release_drill_round_stmt;
DEALLOCATE PREPARE dm_release_drill_round_stmt;

-- 显式回填，确保重复执行和历史行均满足新字段不变量。
UPDATE dm_release_drill SET granularity = 'PROJECT' WHERE granularity IS NULL OR granularity = '';
UPDATE dm_release_drill SET material_type_code = 'RELEASE_PLAN' WHERE material_type_code IS NULL OR material_type_code = '';
UPDATE dm_release_drill SET drill_round = '' WHERE drill_round IS NULL;

-- 2. 查询索引。
SET @dm_release_drill_granularity_idx_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_release_drill' AND index_name = 'idx_dm_release_drill_granularity'),
    'SELECT 1',
    'ALTER TABLE dm_release_drill ADD KEY idx_dm_release_drill_granularity (tenant_id, project_id, granularity, deleted, updated_at)'
);
PREPARE dm_release_drill_granularity_idx_stmt FROM @dm_release_drill_granularity_idx_sql;
EXECUTE dm_release_drill_granularity_idx_stmt;
DEALLOCATE PREPARE dm_release_drill_granularity_idx_stmt;

SET @dm_release_drill_type_idx_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_release_drill' AND index_name = 'idx_dm_release_drill_type'),
    'SELECT 1',
    'ALTER TABLE dm_release_drill ADD KEY idx_dm_release_drill_type (tenant_id, project_id, material_type_code, deleted)'
);
PREPARE dm_release_drill_type_idx_stmt FROM @dm_release_drill_type_idx_sql;
EXECUTE dm_release_drill_type_idx_stmt;
DEALLOCATE PREPARE dm_release_drill_type_idx_stmt;

-- 3. 参数管理类别：管理员后续可在“系统管理/参数管理”修改名称、排序和状态。
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES
    (5812, 1, 'DM_RELEASE_DRILL_GRANULARITY', '投产及演练颗粒度', 1, 0),
    (5813, 1, 'DM_RELEASE_DRILL_TYPE', '投产及演练资料类型', 1, 0);

-- 4. 初始参数项。config_key 为稳定编码，config_value 为展示名称；INSERT IGNORE 不覆盖管理员既有配置。
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '投产及演练业务码值（由系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58145 id, 'DM_RELEASE_DRILL_GRANULARITY.PROJECT' config_key, '项目级' config_value, 'DM_RELEASE_DRILL_GRANULARITY' category_code UNION ALL
    SELECT 58146, 'DM_RELEASE_DRILL_GRANULARITY.COMPONENT', '组件级', 'DM_RELEASE_DRILL_GRANULARITY' UNION ALL
    SELECT 58147, 'DM_RELEASE_DRILL_TYPE.RELEASE_PLAN', '投产方案', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58148, 'DM_RELEASE_DRILL_TYPE.EMERGENCY_PLAN', '应急方案', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58149, 'DM_RELEASE_DRILL_TYPE.RELEASE_SUMMARY', '投产总结', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58150, 'DM_RELEASE_DRILL_TYPE.BRIEFING_NOTICE', '宣讲通知', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58151, 'DM_RELEASE_DRILL_TYPE.SCHEDULE_TIMELINE', '调度时序', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58152, 'DM_RELEASE_DRILL_TYPE.CRITICAL_PATH', '关键路径', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58153, 'DM_RELEASE_DRILL_TYPE.MILESTONE_REPORT_INSTRUCTION', '里程碑汇报指令', 'DM_RELEASE_DRILL_TYPE' UNION ALL
    SELECT 58154, 'DM_RELEASE_DRILL_TYPE.DRILL_ENV_INFO', '数迁环境信息', 'DM_RELEASE_DRILL_TYPE'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = v.category_code AND t.deleted = 0;
