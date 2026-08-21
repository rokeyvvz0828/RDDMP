-- 项目子计划编号规则与父计划独立子序号。
ALTER TABLE pm_project
    ADD COLUMN child_plan_number_rule VARCHAR(128) NULL COMMENT '子计划编号规则，支持父计划编号和子序号占位符' AFTER plan_number_rule;

ALTER TABLE pm_project_plan
    ADD COLUMN next_child_plan_sequence BIGINT NOT NULL DEFAULT 1 COMMENT '当前计划下一个子计划编号序号，不复用已删除子计划编号' AFTER plan_code;
