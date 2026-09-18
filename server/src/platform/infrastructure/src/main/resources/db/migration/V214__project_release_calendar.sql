CREATE TABLE pm_project_release_calendar (
    id BIGINT PRIMARY KEY COMMENT '项目投产日历主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    title VARCHAR(128) NOT NULL COMMENT '投产标题',
    release_date DATE NOT NULL COMMENT '投产日期',
    remark VARCHAR(1000) NULL COMMENT '备注',
    row_version BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    CONSTRAINT fk_pm_project_release_calendar_project FOREIGN KEY (project_id) REFERENCES pm_project(id),
    KEY idx_pm_project_release_calendar_month (tenant_id, project_id, release_date, deleted)
) COMMENT='项目独立投产日历';
