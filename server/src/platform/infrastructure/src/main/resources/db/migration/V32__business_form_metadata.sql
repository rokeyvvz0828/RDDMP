-- 业务表单元数据基础模型。
-- 目的：为所有业务模块提供可配置的分区、字段、规则、选项、值和配置版本能力。
-- 原则：核心字段仍在业务主表；本迁移只新增平台公共表，不修改既有业务表。

CREATE TABLE biz_form_scope (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_key VARCHAR(128) NOT NULL,
    scope_name VARCHAR(128) NOT NULL,
    module_key VARCHAR(64) NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    form_key VARCHAR(64) NOT NULL DEFAULT 'default',
    status_field VARCHAR(128),
    permission_prefix VARCHAR(160) NOT NULL,
    published_revision_id BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_scope_key (tenant_id, scope_key, deleted),
    KEY idx_biz_form_scope_module (tenant_id, module_key, enabled),
    KEY idx_biz_form_scope_entity (tenant_id, entity_type, form_key)
) COMMENT='业务表单配置范围';

CREATE TABLE biz_form_section (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_id BIGINT NOT NULL,
    section_key VARCHAR(128) NOT NULL,
    title VARCHAR(128) NOT NULL,
    layout_mode VARCHAR(16) NOT NULL DEFAULT 'left',
    show_title TINYINT NOT NULL DEFAULT 1,
    collapsed TINYINT NOT NULL DEFAULT 0,
    sort_no INT NOT NULL DEFAULT 0,
    is_builtin TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_section_key (tenant_id, scope_id, section_key, deleted),
    KEY idx_biz_form_section_sort (tenant_id, scope_id, enabled, deleted, sort_no)
) COMMENT='业务表单分区布局';

CREATE TABLE biz_form_field_definition (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_id BIGINT NOT NULL,
    section_id BIGINT NULL,
    field_key VARCHAR(128) NOT NULL,
    label VARCHAR(128) NOT NULL,
    field_kind VARCHAR(16) NOT NULL DEFAULT 'extension',
    input_type VARCHAR(32) NOT NULL DEFAULT 'text',
    value_type VARCHAR(32) NOT NULL DEFAULT 'string',
    source_type VARCHAR(32) NOT NULL DEFAULT 'none',
    source_key VARCHAR(128),
    component_key VARCHAR(160),
    native_column VARCHAR(128),
    multiple TINYINT NOT NULL DEFAULT 0,
    column_span TINYINT NOT NULL DEFAULT 12,
    visible TINYINT NOT NULL DEFAULT 1,
    list_visible TINYINT NOT NULL DEFAULT 0,
    filterable TINYINT NOT NULL DEFAULT 0,
    sortable TINYINT NOT NULL DEFAULT 0,
    dashboard_dimension TINYINT NOT NULL DEFAULT 0,
    placeholder VARCHAR(255),
    help_text VARCHAR(500),
    default_value_json JSON NULL,
    sort_no INT NOT NULL DEFAULT 0,
    is_builtin TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_field_key (tenant_id, scope_id, field_key, deleted),
    KEY idx_biz_form_field_render (tenant_id, scope_id, enabled, deleted, visible, sort_no),
    KEY idx_biz_form_field_list (tenant_id, scope_id, list_visible, filterable, sortable),
    KEY idx_biz_form_field_section (tenant_id, section_id, sort_no)
) COMMENT='业务表单字段定义';

CREATE TABLE biz_form_field_rule (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    field_definition_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    condition_type VARCHAR(32) NOT NULL DEFAULT 'status',
    condition_key VARCHAR(128) NOT NULL,
    required TINYINT NOT NULL DEFAULT 0,
    editable TINYINT NOT NULL DEFAULT 1,
    visible TINYINT NOT NULL DEFAULT 1,
    validation_json JSON NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_field_rule (tenant_id, field_definition_id, action_code, condition_type, condition_key, deleted),
    KEY idx_biz_form_field_rule_lookup (tenant_id, field_definition_id, action_code, enabled, deleted)
) COMMENT='业务表单条件规则';

CREATE TABLE biz_form_field_option (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    field_definition_id BIGINT NOT NULL,
    option_value VARCHAR(128) NOT NULL,
    option_label VARCHAR(128) NOT NULL,
    option_group VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_field_option (tenant_id, field_definition_id, option_value, deleted),
    KEY idx_biz_form_field_option_sort (tenant_id, field_definition_id, enabled, deleted, sort_no)
) COMMENT='业务表单静态选项';

CREATE TABLE biz_form_field_value (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_id BIGINT NOT NULL,
    field_definition_id BIGINT NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    entity_id BIGINT NOT NULL,
    ordinal INT NOT NULL DEFAULT 0,
    value_text TEXT,
    value_code VARCHAR(128),
    value_number DECIMAL(20, 6),
    value_date DATE,
    value_datetime DATETIME,
    value_boolean TINYINT,
    value_ref_type VARCHAR(64),
    value_ref_id BIGINT,
    value_json JSON NULL,
    value_label_snapshot VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_form_field_value (tenant_id, scope_id, entity_type, entity_id, field_definition_id, ordinal),
    KEY idx_biz_form_field_value_entity (tenant_id, scope_id, entity_type, entity_id),
    KEY idx_biz_form_field_value_code (tenant_id, field_definition_id, value_code),
    KEY idx_biz_form_field_value_number (tenant_id, field_definition_id, value_number),
    KEY idx_biz_form_field_value_date (tenant_id, field_definition_id, value_date),
    KEY idx_biz_form_field_value_ref (tenant_id, field_definition_id, value_ref_type, value_ref_id)
) COMMENT='业务表单扩展字段值';

CREATE TABLE biz_form_config_revision (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    revision_status VARCHAR(16) NOT NULL DEFAULT 'draft',
    snapshot_json JSON NOT NULL,
    change_summary VARCHAR(500),
    created_by BIGINT NOT NULL DEFAULT 0,
    published_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    UNIQUE KEY uk_biz_form_revision_no (tenant_id, scope_id, revision_no),
    KEY idx_biz_form_revision_status (tenant_id, scope_id, revision_status, revision_no)
) COMMENT='业务表单配置版本';

ALTER TABLE biz_form_scope
    ADD KEY idx_biz_form_scope_published_revision (tenant_id, published_revision_id);

