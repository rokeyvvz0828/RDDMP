-- 项目风险进展评论：评论作者由服务端认证用户确定，按租户、项目和风险隔离。
CREATE TABLE pm_project_risk_comment (
    id BIGINT PRIMARY KEY COMMENT '项目风险评论主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    risk_id BIGINT NOT NULL COMMENT '项目风险主键',
    user_id BIGINT NOT NULL COMMENT '评论用户主键',
    comment_text VARCHAR(2000) NOT NULL COMMENT '评论内容',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '评论更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    KEY idx_pm_project_risk_comment_scope (tenant_id, project_id, risk_id, deleted, created_at, id),
    KEY idx_pm_project_risk_comment_user (tenant_id, user_id, deleted)
) COMMENT='项目风险进展评论表';
