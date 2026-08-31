-- 项目附件分类；分类按租户和项目隔离，未分类附件使用 sys_attachment.category_id=NULL 表示。
CREATE TABLE sys_attachment_category (
    id BIGINT PRIMARY KEY COMMENT '附件分类主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型，例如 PROJECT',
    business_id BIGINT NOT NULL COMMENT '业务主键，例如项目主键',
    category_name VARCHAR(128) NOT NULL COMMENT '附件分类名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '分类排序号',
    created_by BIGINT NOT NULL COMMENT '创建人用户主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    KEY idx_sys_attachment_category_scope (tenant_id, business_type, business_id, deleted, sort_no, id),
    UNIQUE KEY uk_sys_attachment_category_name (tenant_id, business_type, business_id, category_name, deleted)
) COMMENT='平台项目附件分类表';

ALTER TABLE sys_attachment
    ADD COLUMN category_id BIGINT NULL COMMENT '附件分类主键，NULL表示未分类' AFTER business_id,
    ADD KEY idx_sys_attachment_category (tenant_id, category_id, deleted);
