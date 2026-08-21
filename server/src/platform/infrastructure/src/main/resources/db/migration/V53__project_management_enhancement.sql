ALTER TABLE pm_project
    ADD COLUMN phase VARCHAR(128) NULL COMMENT '项目阶段参数键' AFTER status;

ALTER TABLE pm_project_plan
    ADD COLUMN phase VARCHAR(128) NULL COMMENT '计划阶段参数键' AFTER status;

CREATE TABLE pm_project_plan_org (
    plan_id BIGINT NOT NULL COMMENT '计划主键',
    org_id BIGINT NOT NULL COMMENT '组织主键',
    party_type VARCHAR(20) NOT NULL COMMENT '组织类型：LEAD牵头方、COOPERATING配合方',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (plan_id, org_id, party_type),
    KEY idx_pm_plan_org_plan (tenant_id, plan_id, party_type),
    KEY idx_pm_plan_org_org (tenant_id, org_id, party_type)
) COMMENT='项目计划组织关系表';

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES
    (4004, 1, 'PROJECT_PHASE', '项目阶段', 1),
    (4005, 1, 'PLAN_PHASE', '计划阶段', 1);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4201, 1, id, 'INITIATION', '项目立项', 'string', '项目阶段'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'PROJECT_PHASE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4202, 1, id, 'EXECUTION', '项目执行', 'string', '项目阶段'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'PROJECT_PHASE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4203, 1, id, 'CLOSURE', '项目收尾', 'string', '项目阶段'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'PROJECT_PHASE' AND deleted = 0;

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4211, 1, id, 'ANALYSIS', '需求分析', 'string', '计划阶段'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'PLAN_PHASE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4212, 1, id, 'DEVELOPMENT', '开发实施', 'string', '计划阶段'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'PLAN_PHASE' AND deleted = 0;
INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
SELECT 4213, 1, id, 'DELIVERY', '交付验收', 'string', '计划阶段'
FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'PLAN_PHASE' AND deleted = 0;
