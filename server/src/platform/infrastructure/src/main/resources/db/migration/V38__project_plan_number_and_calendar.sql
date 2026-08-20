-- 项目计划编号规则、计划编号和项目级序号。
-- 历史项目/计划保留兼容性，空编号不回溯生成。
ALTER TABLE pm_project
    ADD COLUMN plan_number_rule VARCHAR(128) NULL COMMENT '计划编号规则，支持项目编号、序号和日期占位符' AFTER phase,
    ADD COLUMN next_plan_sequence BIGINT NOT NULL DEFAULT 1 COMMENT '下一个计划编号序号，不复用已删除计划编号' AFTER plan_number_rule;

ALTER TABLE pm_project_plan
    ADD COLUMN plan_code VARCHAR(128) NULL COMMENT '计划编号，由项目编号规则服务端生成' AFTER plan_name,
    ADD UNIQUE KEY uk_pm_plan_code (tenant_id, project_id, plan_code);
