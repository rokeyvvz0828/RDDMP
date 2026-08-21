-- 项目组织架构：与系统组织架构完全独立，仅在项目范围内生效。
CREATE TABLE pm_project_org (
    id BIGINT PRIMARY KEY COMMENT '项目组织主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '上级项目组织主键，0表示根节点',
    org_code VARCHAR(64) NOT NULL COMMENT '项目组织编码',
    org_name VARCHAR(128) NOT NULL COMMENT '项目组织名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '同级排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用、1启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    UNIQUE KEY uk_pm_project_org_code (tenant_id, project_id, org_code, deleted),
    KEY idx_pm_project_org_parent (tenant_id, project_id, parent_id, deleted),
    KEY idx_pm_project_org_status (tenant_id, project_id, status, deleted)
) COMMENT='项目独立组织架构表';

ALTER TABLE pm_project_member
    ADD COLUMN org_id BIGINT NULL COMMENT '所属项目组织主键，NULL表示未分配机构' AFTER user_id,
    ADD KEY idx_pm_member_project_org (tenant_id, project_id, org_id, deleted);
