-- 修复 V47 固定计划阶段参数的历史 ID 冲突。
-- V37 已占用 4211-4213，V47 的 INSERT IGNORE 因此遗漏了前三个固定阶段；本迁移只补齐参数，不删除业务计划数据。

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
    c.config_type = 'string',
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

-- 使用当前最大 ID 之后的序号，避免复用 V37 的历史参数 ID。
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT ids.base_id + stage.seq,
       1,
       dict.id,
       stage.config_key,
       stage.config_value,
       'string',
       '计划阶段'
FROM (
    SELECT 1 AS seq, 'PLAN_INITIATION' AS config_key, '立项' AS config_value
    UNION ALL SELECT 2, 'PLAN_REQUIREMENT', '需求'
    UNION ALL SELECT 3, 'PLAN_DESIGN_DEVELOPMENT', '设计开发'
    UNION ALL SELECT 4, 'PLAN_DATA_MIGRATION', '数据迁移'
    UNION ALL SELECT 5, 'PLAN_TEST_ACCEPTANCE', '测试与验收'
    UNION ALL SELECT 6, 'PLAN_TRAINING_PRODUCTION_REHEARSAL', '培训及投产演练'
    UNION ALL SELECT 7, 'PLAN_PRODUCTION_LAUNCH', '投产上线'
) stage
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_config) ids
JOIN sys_dict_type dict ON dict.tenant_id = 1 AND dict.dict_code = 'PLAN_PHASE' AND dict.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_config existing
    WHERE existing.tenant_id = 1
      AND existing.category_id = dict.id
      AND existing.config_key = stage.config_key
      AND existing.deleted = 0
);
