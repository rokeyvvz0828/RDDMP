-- 项目级计划阶段；阶段名称和顺序按项目隔离，计划 phase 保存 stage_code。
CREATE TABLE pm_project_stage (
    id BIGINT PRIMARY KEY COMMENT '项目阶段主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    stage_code VARCHAR(64) NOT NULL COMMENT '项目内阶段编码',
    stage_name VARCHAR(128) NOT NULL COMMENT '阶段名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '阶段排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用、1启用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_pm_project_stage_code (tenant_id, project_id, stage_code, deleted),
    KEY idx_pm_project_stage_list (tenant_id, project_id, status, deleted, sort_no, id)
) COMMENT='项目级可配置计划阶段';

-- 为存量活动项目补齐默认七阶段；INSERT IGNORE 使迁移可重复执行且不覆盖已有配置。
INSERT IGNORE INTO pm_project_stage
    (id, tenant_id, project_id, stage_code, stage_name, sort_no)
SELECT (p.id * 10) + s.sort_no, p.tenant_id, p.id, s.stage_code, s.stage_name, s.sort_no
FROM pm_project p
JOIN (
    SELECT 'PLAN_INITIATION' AS stage_code, '立项' AS stage_name, 1 AS sort_no
    UNION ALL SELECT 'PLAN_REQUIREMENT', '需求', 2
    UNION ALL SELECT 'PLAN_DESIGN_DEVELOPMENT', '设计开发', 3
    UNION ALL SELECT 'PLAN_DATA_MIGRATION', '数据迁移', 4
    UNION ALL SELECT 'PLAN_TEST_ACCEPTANCE', '测试与验收', 5
    UNION ALL SELECT 'PLAN_TRAINING_PRODUCTION_REHEARSAL', '培训及投产演练', 6
    UNION ALL SELECT 'PLAN_PRODUCTION_LAUNCH', '投产上线', 7
) s
WHERE p.deleted = 0;
