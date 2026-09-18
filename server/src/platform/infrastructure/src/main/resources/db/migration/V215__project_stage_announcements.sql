CREATE TABLE pm_project_announcement (
    id BIGINT PRIMARY KEY COMMENT '项目公告主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    stage_code VARCHAR(64) NOT NULL COMMENT '项目阶段编码',
    title VARCHAR(128) NOT NULL COMMENT '公告标题',
    content_html MEDIUMTEXT NOT NULL COMMENT '公告富文本正文',
    pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶',
    row_version BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    CONSTRAINT fk_pm_project_announcement_project FOREIGN KEY (project_id) REFERENCES pm_project(id),
    KEY idx_pm_project_announcement_project_stage (tenant_id, project_id, stage_code, pinned, deleted, created_at)
) COMMENT='项目阶段公告';
