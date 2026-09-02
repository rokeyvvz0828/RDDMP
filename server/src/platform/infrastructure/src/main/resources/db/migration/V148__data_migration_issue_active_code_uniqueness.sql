-- 问题编号仅约束活动记录唯一。
-- 已删除记录的生成列为 NULL，允许同一问题编号经历多次删除和重建。

ALTER TABLE dm_issue
    DROP INDEX uk_dm_issue_code,
    ADD COLUMN active_issue_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN issue_code ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_dm_issue_active_code (tenant_id, project_id, active_issue_code);
