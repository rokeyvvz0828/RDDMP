-- V95: 会议纪要独立表
-- 创建 dm_meeting 表用于存储会议纪要和问题提取纪要

CREATE TABLE dm_meeting (
    -- 主键与租户
    meeting_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,

    -- 关联项目
    project_id       BIGINT NOT NULL,
    project_name     VARCHAR(200),

    -- 核心业务字段
    granularity      VARCHAR(50) NOT NULL COMMENT 'PROJECT/COMPONENT/TABLE/FIELD',
    meeting_source   VARCHAR(50) NOT NULL COMMENT 'MEETING_MINUTES/ISSUE_EXTRACT',
    meeting_title    VARCHAR(500) NOT NULL COMMENT '会议主题',
    meeting_content  TEXT COMMENT '会议内容',
    meeting_conclusion TEXT COMMENT '会议结论',
    business_scenario VARCHAR(500) COMMENT '所属业务场景',

    -- 关键字（JSON数组存储多标签）
    keywords         JSON,

    -- 附件
    attachment_id    BIGINT COMMENT '源文件附件ID',
    file_name        VARCHAR(500) COMMENT '源文件原始名称',

    -- 逻辑删除
    deleted          TINYINT(1) NOT NULL DEFAULT 0,
    deleted_by       BIGINT,
    deleted_at       DATETIME(6),

    -- 审计字段
    created_by       BIGINT NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by       BIGINT,
    updated_at       DATETIME(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- 索引
    INDEX idx_dm_meeting_project (project_id, deleted),
    INDEX idx_dm_meeting_source (meeting_source, deleted),
    INDEX idx_dm_meeting_granularity (granularity, deleted),
    INDEX idx_dm_meeting_created (created_at),
    INDEX idx_dm_meeting_deleted (deleted, deleted_at)
) COMMENT='数据迁移会议纪要';
