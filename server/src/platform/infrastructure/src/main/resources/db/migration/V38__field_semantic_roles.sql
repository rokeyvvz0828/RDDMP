-- 字段语义角色归入字段定义，业务对象的 status_field 仅保留用于历史兼容。
ALTER TABLE biz_form_field_definition
    ADD COLUMN field_role VARCHAR(32) NOT NULL DEFAULT 'normal' COMMENT '字段语义角色：normal 普通字段、status 业务状态字段';

ALTER TABLE biz_form_field_definition
    ADD KEY idx_biz_form_field_role (tenant_id, scope_id, field_role, enabled, deleted);
