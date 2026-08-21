-- Project plan group palette selection.
ALTER TABLE pm_project_plan_group
    ADD COLUMN color_key VARCHAR(32) NULL COMMENT '分组主题配色编码，空值兼容为 ocean' AFTER group_name;
