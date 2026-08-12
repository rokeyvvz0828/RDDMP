-- 字段治理视图层：字段定义仍由 V32 维护，视图只保存引用和页面级属性。
-- 迁移只追加，不改变既有 scope、字段、规则、选项和值的语义。

ALTER TABLE biz_form_field_definition
    ADD COLUMN form_available TINYINT NOT NULL DEFAULT 1 COMMENT '是否纳入业务表单字段集合';

CREATE TABLE biz_form_view (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_id BIGINT NOT NULL,
    view_key VARCHAR(128) NOT NULL,
    view_name VARCHAR(128) NOT NULL,
    view_type VARCHAR(32) NOT NULL COMMENT 'form、detail、list、approval、wizard',
    layout_json JSON NULL,
    published_revision_id BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_view_key (tenant_id, scope_id, view_key, deleted),
    KEY idx_biz_form_view_type (tenant_id, scope_id, view_type, enabled, deleted)
) COMMENT='业务表单视图定义';

CREATE TABLE biz_form_view_field (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    view_id BIGINT NOT NULL,
    field_definition_id BIGINT NOT NULL,
    view_section_key VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    visible TINYINT NOT NULL DEFAULT 1,
    editable TINYINT NOT NULL DEFAULT 1,
    required TINYINT NOT NULL DEFAULT 0,
    column_span TINYINT NOT NULL DEFAULT 12,
    column_width INT,
    fixed_position VARCHAR(16),
    filter_operator VARCHAR(32),
    display_format_json JSON NULL,
    mobile_visible TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_view_field (tenant_id, view_id, field_definition_id, deleted),
    KEY idx_biz_form_view_field_sort (tenant_id, view_id, enabled, deleted, sort_no),
    KEY idx_biz_form_view_field_definition (tenant_id, field_definition_id, deleted)
) COMMENT='表单和列表视图字段引用';

CREATE TABLE biz_form_view_step (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    view_id BIGINT NOT NULL,
    step_key VARCHAR(128) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    validation_mode VARCHAR(32) NOT NULL DEFAULT 'current-step',
    sort_no INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_view_step_key (tenant_id, view_id, step_key, deleted),
    KEY idx_biz_form_view_step_sort (tenant_id, view_id, enabled, deleted, sort_no)
) COMMENT='分步表单步骤';

CREATE TABLE biz_form_view_step_field (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    step_id BIGINT NOT NULL,
    field_definition_id BIGINT NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_view_step_field (tenant_id, step_id, field_definition_id, deleted),
    KEY idx_biz_form_view_step_field_sort (tenant_id, step_id, enabled, deleted, sort_no)
) COMMENT='分步表单字段引用';

CREATE TABLE biz_form_view_revision (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    view_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    revision_status VARCHAR(16) NOT NULL DEFAULT 'draft',
    snapshot_json JSON NOT NULL,
    change_summary VARCHAR(500),
    created_by BIGINT NOT NULL DEFAULT 0,
    published_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    UNIQUE KEY uk_biz_form_view_revision_no (tenant_id, view_id, revision_no),
    KEY idx_biz_form_view_revision_status (tenant_id, view_id, revision_status, revision_no)
) COMMENT='业务表单视图版本';

CREATE TABLE biz_form_page (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    module_key VARCHAR(64) NOT NULL,
    page_key VARCHAR(128) NOT NULL,
    page_name VARCHAR(128) NOT NULL,
    layout_json JSON NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_page_key (tenant_id, page_key, deleted),
    KEY idx_biz_form_page_module (tenant_id, module_key, enabled, deleted)
) COMMENT='业务页面编排定义';

CREATE TABLE biz_form_page_block (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    page_id BIGINT NOT NULL,
    block_key VARCHAR(128) NOT NULL,
    block_type VARCHAR(32) NOT NULL COMMENT 'form、list、wizard、detail、workflow、chart、attachment、timeline',
    module_key VARCHAR(64) NOT NULL,
    ref_key VARCHAR(160) NOT NULL,
    title VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    grid_span TINYINT NOT NULL DEFAULT 24,
    permission_code VARCHAR(160),
    config_json JSON NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_page_block_key (tenant_id, page_id, block_key, deleted),
    KEY idx_biz_form_page_block_sort (tenant_id, page_id, enabled, deleted, sort_no)
) COMMENT='业务页面 Block 编排';
