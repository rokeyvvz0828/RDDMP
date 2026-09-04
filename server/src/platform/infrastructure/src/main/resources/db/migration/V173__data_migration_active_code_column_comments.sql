-- V173: 补齐活动编号生成列注释（REQ-20260820-031 9.2 P2「生成列注释不完整」）。
-- dm_issue.active_issue_code（V157）与 dm_meeting.active_meeting_code（V166）建立时缺 COMMENT，
-- 与 dm_plan/dm_report 等同型生成列注释口径不一致；本迁移仅补注释，不改语义。
-- 仅追加，不修改已发布脚本；MODIFY 保留原生成表达式与类型。

ALTER TABLE dm_issue
    MODIFY COLUMN active_issue_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN issue_code ELSE NULL END) STORED
        COMMENT '活动问题编号（仅未删除记录取值）';

ALTER TABLE dm_meeting
    MODIFY COLUMN active_meeting_code VARCHAR(96)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN meeting_code ELSE NULL END) STORED
        COMMENT '活动会议编号（仅未删除记录取值）';
