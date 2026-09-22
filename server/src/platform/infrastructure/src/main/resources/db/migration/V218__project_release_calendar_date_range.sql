ALTER TABLE pm_project_release_calendar
    ADD COLUMN release_start_date DATE NULL COMMENT '投产开始日期' AFTER release_date,
    ADD COLUMN release_end_date DATE NULL COMMENT '投产结束日期' AFTER release_start_date;

UPDATE pm_project_release_calendar
SET release_start_date = release_date,
    release_end_date = release_date
WHERE release_start_date IS NULL OR release_end_date IS NULL;

ALTER TABLE pm_project_release_calendar
    MODIFY COLUMN release_start_date DATE NOT NULL COMMENT '投产开始日期',
    MODIFY COLUMN release_end_date DATE NOT NULL COMMENT '投产结束日期',
    ADD KEY idx_pm_project_release_calendar_range (tenant_id, project_id, release_start_date, release_end_date, deleted);
