ALTER TABLE pm_project_release_calendar
    ADD COLUMN theme_key VARCHAR(16) NOT NULL DEFAULT 'primary' COMMENT '概览主题配色' AFTER remark;
