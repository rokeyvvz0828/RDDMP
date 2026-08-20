-- 项目计划固定七阶段并移除已废弃的项目级阶段字段。
-- 计划阶段仍由 PLAN_PHASE 参数管理；历史计划和分组保留并归入可用阶段，不删除业务计划数据。

-- 固定 PLAN_PHASE 的七个参数及其中文名称，停用其他历史或自定义计划阶段参数。
UPDATE sys_config c
JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id
SET c.status = 0,
    c.updated_at = CURRENT_TIMESTAMP
WHERE t.dict_code = 'PLAN_PHASE'
  AND t.deleted = 0
  AND c.deleted = 0
  AND c.config_key NOT IN (
      'PLAN_INITIATION',
      'PLAN_REQUIREMENT',
      'PLAN_DESIGN_DEVELOPMENT',
      'PLAN_DATA_MIGRATION',
      'PLAN_TEST_ACCEPTANCE',
      'PLAN_TRAINING_PRODUCTION_REHEARSAL',
      'PLAN_PRODUCTION_LAUNCH'
  );

UPDATE sys_config c
JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id
SET c.status = 1,
    c.config_value = CASE c.config_key
        WHEN 'PLAN_INITIATION' THEN '立项'
        WHEN 'PLAN_REQUIREMENT' THEN '需求'
        WHEN 'PLAN_DESIGN_DEVELOPMENT' THEN '设计开发'
        WHEN 'PLAN_DATA_MIGRATION' THEN '数据迁移'
        WHEN 'PLAN_TEST_ACCEPTANCE' THEN '测试与验收'
        WHEN 'PLAN_TRAINING_PRODUCTION_REHEARSAL' THEN '培训及投产演练'
        WHEN 'PLAN_PRODUCTION_LAUNCH' THEN '投产上线'
    END,
    c.remark = '计划阶段',
    c.updated_at = CURRENT_TIMESTAMP
WHERE t.dict_code = 'PLAN_PHASE'
  AND t.deleted = 0
  AND c.deleted = 0
  AND c.config_key IN (
      'PLAN_INITIATION',
      'PLAN_REQUIREMENT',
      'PLAN_DESIGN_DEVELOPMENT',
      'PLAN_DATA_MIGRATION',
      'PLAN_TEST_ACCEPTANCE',
      'PLAN_TRAINING_PRODUCTION_REHEARSAL',
      'PLAN_PRODUCTION_LAUNCH'
  );

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT stage.id, 1, dict.id, stage.config_key, stage.config_value, 'string', '计划阶段'
FROM (
    SELECT 4211 AS id, 'PLAN_INITIATION' AS config_key, '立项' AS config_value
    UNION ALL SELECT 4212, 'PLAN_REQUIREMENT', '需求'
    UNION ALL SELECT 4213, 'PLAN_DESIGN_DEVELOPMENT', '设计开发'
    UNION ALL SELECT 4214, 'PLAN_DATA_MIGRATION', '数据迁移'
    UNION ALL SELECT 4215, 'PLAN_TEST_ACCEPTANCE', '测试与验收'
    UNION ALL SELECT 4216, 'PLAN_TRAINING_PRODUCTION_REHEARSAL', '培训及投产演练'
    UNION ALL SELECT 4217, 'PLAN_PRODUCTION_LAUNCH', '投产上线'
) stage
JOIN sys_dict_type dict ON dict.tenant_id = 1 AND dict.dict_code = 'PLAN_PHASE' AND dict.deleted = 0;

-- 仅允许固定七阶段，历史空值和已停用阶段兼容回填为立项。
UPDATE pm_project_plan
SET phase = 'PLAN_INITIATION'
WHERE phase IS NULL OR phase NOT IN (
    'PLAN_INITIATION',
    'PLAN_REQUIREMENT',
    'PLAN_DESIGN_DEVELOPMENT',
    'PLAN_DATA_MIGRATION',
    'PLAN_TEST_ACCEPTANCE',
    'PLAN_TRAINING_PRODUCTION_REHEARSAL',
    'PLAN_PRODUCTION_LAUNCH'
);

UPDATE pm_project_plan_group
SET phase = 'PLAN_INITIATION'
WHERE phase IS NULL OR phase NOT IN (
    'PLAN_INITIATION',
    'PLAN_REQUIREMENT',
    'PLAN_DESIGN_DEVELOPMENT',
    'PLAN_DATA_MIGRATION',
    'PLAN_TEST_ACCEPTANCE',
    'PLAN_TRAINING_PRODUCTION_REHEARSAL',
    'PLAN_PRODUCTION_LAUNCH'
);

-- 项目级阶段与计划阶段重复，项目详情和项目编辑均不再使用该字段。
ALTER TABLE pm_project
    DROP COLUMN phase;
