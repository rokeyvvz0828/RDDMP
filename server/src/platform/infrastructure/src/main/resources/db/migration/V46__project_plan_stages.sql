-- 项目计划阶段与分组层级重构。
-- 阶段继续由 PLAN_PHASE 参数管理，分组持久化所属阶段，历史数据按原阶段兼容回填。

-- 首次尝试可能已完成结构变更后才在数据回填阶段失败；重试时跳过已完成的结构变更。
SET @pm_plan_group_phase_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'pm_project_plan_group'
      AND column_name = 'phase'
);
SET @pm_plan_group_structure_sql = IF(
    @pm_plan_group_phase_column_exists = 0,
    'ALTER TABLE pm_project_plan_group ADD COLUMN phase VARCHAR(128) NULL COMMENT ''计划分组所属阶段参数键，关联 PLAN_PHASE'' AFTER project_id, DROP INDEX uk_pm_plan_group_name, ADD UNIQUE KEY uk_pm_plan_group_name_phase (tenant_id, project_id, phase, group_name, deleted), ADD KEY idx_pm_plan_group_phase (tenant_id, project_id, phase, deleted, sort_no)',
    'SELECT 1'
);
PREPARE pm_plan_group_structure_statement FROM @pm_plan_group_structure_sql;
EXECUTE pm_plan_group_structure_statement;
DEALLOCATE PREPARE pm_plan_group_structure_statement;

-- 将历史计划阶段映射到新的七阶段编码；未知的自定义参数值继续保留。
UPDATE pm_project_plan
SET phase = CASE
    WHEN phase IS NULL THEN 'PLAN_INITIATION'
    WHEN phase = 'ANALYSIS' THEN 'PLAN_REQUIREMENT'
    WHEN phase = 'DEVELOPMENT' THEN 'PLAN_DESIGN_DEVELOPMENT'
    WHEN phase = 'DELIVERY' THEN 'PLAN_TEST_ACCEPTANCE'
    ELSE phase
END
WHERE phase IS NULL OR phase IN ('ANALYSIS', 'DEVELOPMENT', 'DELIVERY');

-- 旧分组没有阶段时，优先使用已有主计划阶段；无阶段数据归入立项。
UPDATE pm_project_plan_group g
LEFT JOIN (
    SELECT project_id, tenant_id, group_id, MAX(phase) AS phase
    FROM pm_project_plan
    WHERE group_id IS NOT NULL AND parent_id = 0 AND deleted = 0
    GROUP BY project_id, tenant_id, group_id
) p ON p.group_id = g.id AND p.project_id = g.project_id AND p.tenant_id = g.tenant_id
SET g.phase = COALESCE(NULLIF(p.phase, ''), 'PLAN_INITIATION')
WHERE g.phase IS NULL OR g.phase = '';

ALTER TABLE pm_project_plan_group
    MODIFY COLUMN phase VARCHAR(128) NOT NULL DEFAULT 'PLAN_INITIATION' COMMENT '计划分组所属阶段参数键，关联 PLAN_PHASE';

-- 已归组计划统一继承分组阶段，保证阶段、分组和主子计划树一致。
UPDATE pm_project_plan p
JOIN pm_project_plan_group g ON g.id = p.group_id AND g.project_id = p.project_id AND g.tenant_id = p.tenant_id AND g.deleted = 0
SET p.phase = g.phase
WHERE p.deleted = 0;

-- sys_config 的租户级编码唯一，因此计划阶段使用 PLAN_ 前缀与项目阶段编码区分。
UPDATE sys_config
SET config_key = 'PLAN_INITIATION', config_value = '立项', remark = '计划阶段'
WHERE id = 4211 AND tenant_id = 1 AND deleted = 0;
UPDATE sys_config
SET config_key = 'PLAN_REQUIREMENT', config_value = '需求', remark = '计划阶段'
WHERE id = 4212 AND tenant_id = 1 AND deleted = 0;
UPDATE sys_config
SET config_key = 'PLAN_DESIGN_DEVELOPMENT', config_value = '设计开发', remark = '计划阶段'
WHERE id = 4213 AND tenant_id = 1 AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT stage.id, 1, dict.id, stage.config_key, stage.config_value, 'string', '计划阶段'
FROM (
    SELECT 4214 AS id, 'PLAN_DATA_MIGRATION' AS config_key, '数据迁移' AS config_value
    UNION ALL SELECT 4215, 'PLAN_TEST_ACCEPTANCE', '测试与验收'
    UNION ALL SELECT 4216, 'PLAN_TRAINING_PRODUCTION_REHEARSAL', '培训及投产演练'
    UNION ALL SELECT 4217, 'PLAN_PRODUCTION_LAUNCH', '投产上线'
) stage
JOIN sys_dict_type dict ON dict.tenant_id = 1 AND dict.dict_code = 'PLAN_PHASE' AND dict.deleted = 0;
