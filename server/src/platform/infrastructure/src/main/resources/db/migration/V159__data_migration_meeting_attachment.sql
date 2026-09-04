-- V96: 会议纪要多附件支持
-- 创建 dm_meeting_attachment 表，支持会议纪要关联多个附件，并支持附件回收站

CREATE TABLE dm_meeting_attachment (
    id               BIGINT PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    meeting_id       BIGINT NOT NULL,
    attachment_id    BIGINT NOT NULL COMMENT '关联 att_file.id',
    file_name        VARCHAR(500) NOT NULL COMMENT '附件原始文件名',
    sort_order       INT NOT NULL DEFAULT 0 COMMENT '排序序号',

    -- 逻辑删除（附件回收站）
    deleted          TINYINT(1) NOT NULL DEFAULT 0,
    deleted_by       BIGINT,
    deleted_at       DATETIME(6),

    -- 审计字段
    created_by       BIGINT NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- 索引
    INDEX idx_dm_meeting_att_meeting (meeting_id, deleted),
    INDEX idx_dm_meeting_att_attachment (attachment_id),
    INDEX idx_dm_meeting_att_tenant (tenant_id, deleted)
) COMMENT='会议纪要附件关联表（支持多附件和回收站）';

-- 迁移已有单附件数据到新表
INSERT INTO dm_meeting_attachment (id, tenant_id, meeting_id, attachment_id, file_name, sort_order, created_by, created_at)
SELECT
    meeting_id * 10000 + 1 AS id,
    tenant_id,
    meeting_id,
    attachment_id,
    COALESCE(file_name, '未知文件') AS file_name,
    0 AS sort_order,
    created_by,
    created_at
FROM dm_meeting
WHERE attachment_id IS NOT NULL AND deleted = 0;
