-- V103: 会议纪要编号列
-- 背景：统一回收站信封 asset_code 需要真实业务编号；dm_meeting 只有代理主键 meeting_id，
-- 与 dm_report.doc_code / dm_issue.issue_code 不同构，导致回收站 MEETING 行「编号」为空。
-- 方案 C：为 dm_meeting 追加真实业务编号列 meeting_code，向统一信封提供来源。
-- 参照 V94（问题编号活动域唯一）的做法，仅约束未删除记录的唯一性，允许同编号经历软删-重建。

ALTER TABLE dm_meeting
    ADD COLUMN meeting_code VARCHAR(96) NULL COMMENT '会议编号' AFTER meeting_id;

-- 存量数据回填：使用可复现的 MEET-{meeting_id}；开发/生产历史行都获得稳定编号。
UPDATE dm_meeting
   SET meeting_code = CONCAT('MEET-', meeting_id)
 WHERE meeting_code IS NULL OR meeting_code = '';

ALTER TABLE dm_meeting
    MODIFY COLUMN meeting_code VARCHAR(96) NOT NULL COMMENT '会议编号';

-- 活动域唯一索引：软删记录不参与唯一约束，允许恢复/重建。
ALTER TABLE dm_meeting
    ADD COLUMN active_meeting_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN meeting_code ELSE NULL END) STORED AFTER meeting_code,
    ADD UNIQUE KEY uk_dm_meeting_active_code (tenant_id, project_id, active_meeting_code),
    ADD KEY idx_dm_meeting_code (tenant_id, meeting_code);
