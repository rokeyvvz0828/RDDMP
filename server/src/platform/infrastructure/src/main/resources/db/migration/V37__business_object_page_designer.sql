-- 业务对象页面设计器：为现有视图补充页面集合、页面角色和表单模式。
-- 只追加字段，不修改 V35/V36 的历史结构和数据语义。

ALTER TABLE biz_form_view
    ADD COLUMN view_group_key VARCHAR(128) NOT NULL DEFAULT '' COMMENT '业务对象页面集合编码',
    ADD COLUMN view_role VARCHAR(16) NOT NULL DEFAULT 'create' COMMENT '页面角色：list、detail、create、edit、approval',
    ADD COLUMN form_mode VARCHAR(16) NOT NULL DEFAULT 'none' COMMENT '表单模式：single、wizard、none';

UPDATE biz_form_view v
JOIN biz_form_scope s ON s.id = v.scope_id AND s.tenant_id = v.tenant_id
SET v.view_group_key = s.scope_key,
    v.view_role = CASE v.view_type
        WHEN 'list' THEN 'list'
        WHEN 'detail' THEN 'detail'
        WHEN 'approval' THEN 'approval'
        WHEN 'wizard' THEN 'create'
        ELSE 'create'
    END,
    v.form_mode = CASE v.view_type
        WHEN 'wizard' THEN 'wizard'
        WHEN 'form' THEN 'single'
        ELSE 'none'
    END
WHERE v.view_group_key = '';

CREATE INDEX idx_biz_form_view_role
    ON biz_form_view (tenant_id, scope_id, view_group_key, view_role, form_mode, enabled, deleted);
