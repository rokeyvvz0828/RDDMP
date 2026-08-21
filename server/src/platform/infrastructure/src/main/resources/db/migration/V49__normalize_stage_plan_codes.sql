-- 将历史阶段计划的人工名称统一回填为“阶段序号-阶段内序号”。
-- 仅处理有效数据，按阶段计划排序号和主键保证编号稳定；不删除计划或改变其所属阶段。
ALTER TABLE pm_project_plan_group
    DROP INDEX uk_pm_plan_group_name_phase;

CREATE TEMPORARY TABLE tmp_project_stage_plan_codes (
    id BIGINT NOT NULL PRIMARY KEY,
    stage_plan_code VARCHAR(128) NOT NULL
);

INSERT INTO tmp_project_stage_plan_codes (id, stage_plan_code)
SELECT g.id,
       CONCAT(
           CASE g.phase
               WHEN 'PLAN_INITIATION' THEN 1
               WHEN 'PLAN_REQUIREMENT' THEN 2
               WHEN 'PLAN_DESIGN_DEVELOPMENT' THEN 3
               WHEN 'PLAN_DATA_MIGRATION' THEN 4
               WHEN 'PLAN_TEST_ACCEPTANCE' THEN 5
               WHEN 'PLAN_TRAINING_PRODUCTION_REHEARSAL' THEN 6
               WHEN 'PLAN_PRODUCTION_LAUNCH' THEN 7
               ELSE 1
           END,
           '-',
           ROW_NUMBER() OVER (PARTITION BY g.tenant_id, g.project_id, g.phase ORDER BY g.sort_no, g.id)
       )
FROM pm_project_plan_group g
WHERE g.deleted = 0;

UPDATE pm_project_plan_group g
JOIN tmp_project_stage_plan_codes c ON c.id = g.id
SET g.group_name = c.stage_plan_code;

DROP TEMPORARY TABLE tmp_project_stage_plan_codes;

ALTER TABLE pm_project_plan_group
    ADD UNIQUE KEY uk_pm_plan_group_name_phase (tenant_id, project_id, phase, group_name, deleted);
