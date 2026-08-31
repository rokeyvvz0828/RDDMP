-- REQ-20260830-056：搭建计划模板、环节模板、任务模板与版本快照。
-- 模板草稿结构可编辑；发布时将完整结构快照写入版本表（不可变），生成计划只读取已发布版本快照。

CREATE TABLE arch_plan_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/INACTIVE',
    latest_version_no INT NOT NULL DEFAULT 0 COMMENT '已发布最新版本号，0 表示未发布',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_template_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_template_name (tenant_id, name),
    KEY idx_arch_plan_template_status (tenant_id, status, id),
    CONSTRAINT chk_arch_plan_template_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_plan_template_version CHECK (latest_version_no >= 0),
    CONSTRAINT chk_arch_plan_template_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建计划模板主记录';

CREATE TABLE arch_plan_template_stage (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_template_stage_tenant_id (tenant_id, id),
    KEY idx_arch_plan_template_stage_template (tenant_id, template_id, sort_no, id),
    CONSTRAINT fk_arch_plan_template_stage_template
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES arch_plan_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_template_stage_sort CHECK (sort_no >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划模板环节（草稿结构，发布时随版本快照）';

CREATE TABLE arch_task_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    dimension VARCHAR(32) NOT NULL COMMENT 'NONE/PHYSICAL_SUBSYSTEM/DEPLOYMENT_UNIT',
    description VARCHAR(2000) NULL,
    check_items_json JSON NULL COMMENT '草稿检查项 [{"name":"...","sortNo":1}]',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/INACTIVE',
    latest_version_no INT NOT NULL DEFAULT 0 COMMENT '已发布最新版本号，0 表示未发布',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_task_template_tenant_id (tenant_id, id),
    KEY idx_arch_task_template_stage (tenant_id, stage_id, status, id),
    KEY idx_arch_task_template_template (tenant_id, template_id, id),
    CONSTRAINT fk_arch_task_template_stage
        FOREIGN KEY (tenant_id, stage_id)
        REFERENCES arch_plan_template_stage (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_task_template_template
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES arch_plan_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_task_template_dimension CHECK (dimension IN ('NONE', 'PHYSICAL_SUBSYSTEM', 'DEPLOYMENT_UNIT')),
    CONSTRAINT chk_arch_task_template_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_task_template_version CHECK (latest_version_no >= 0),
    CONSTRAINT chk_arch_task_template_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务模板（草稿结构，发布时随版本快照）';

CREATE TABLE arch_plan_template_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content_json JSON NOT NULL COMMENT '环节/任务模板结构快照 [{"stageName":"...","sortNo":1,"tasks":[{"taskTemplateId":1,"taskTemplateVersionNo":1,"name":"...","dimension":"NONE","checkItems":[{"name":"...","sortNo":1}]}]}]',
    note VARCHAR(1000) NULL,
    published_by BIGINT NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_template_version_tenant_ver (tenant_id, template_id, version_no),
    KEY idx_arch_plan_template_version_published (tenant_id, template_id, published_at),
    CONSTRAINT fk_arch_plan_template_version_template
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES arch_plan_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_template_version_no CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划模板已发布版本（不可变快照）';

CREATE TABLE arch_task_template_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    dimension VARCHAR(32) NOT NULL,
    check_items_json JSON NOT NULL COMMENT '检查项快照',
    note VARCHAR(1000) NULL,
    published_by BIGINT NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_task_template_version_tenant_ver (tenant_id, task_template_id, version_no),
    KEY idx_arch_task_template_version_published (tenant_id, task_template_id, published_at),
    CONSTRAINT fk_arch_task_template_version_template
        FOREIGN KEY (tenant_id, task_template_id)
        REFERENCES arch_task_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT chk_arch_task_template_version_no CHECK (version_no > 0),
    CONSTRAINT chk_arch_task_template_version_dimension CHECK (dimension IN ('NONE', 'PHYSICAL_SUBSYSTEM', 'DEPLOYMENT_UNIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务模板已发布版本（不可变快照）';
