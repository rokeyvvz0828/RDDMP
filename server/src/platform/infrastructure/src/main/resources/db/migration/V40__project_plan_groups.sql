-- 项目计划分组：分组只属于项目，删除分组不会删除计划。
CREATE TABLE pm_project_plan_group (
    id BIGINT PRIMARY KEY COMMENT '项目计划分组主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '所属项目主键',
    group_name VARCHAR(128) NOT NULL COMMENT '分组名称',
    description VARCHAR(500) NULL COMMENT '分组说明',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '分组排序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    UNIQUE KEY uk_pm_plan_group_name (tenant_id, project_id, group_name, deleted),
    KEY idx_pm_plan_group_project (tenant_id, project_id, deleted, sort_no)
) COMMENT='项目计划分组表';

ALTER TABLE pm_project_plan
    ADD COLUMN group_id BIGINT NULL COMMENT '所属项目计划分组主键，空值表示未分组' AFTER project_id,
    ADD KEY idx_pm_plan_group (tenant_id, project_id, group_id, deleted);
