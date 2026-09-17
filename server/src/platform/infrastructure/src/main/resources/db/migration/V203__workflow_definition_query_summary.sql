ALTER TABLE wf_definition
    ADD COLUMN latest_version_no INT NOT NULL DEFAULT 0 COMMENT '最新流程版本号（含草稿）' AFTER current_version,
    ADD COLUMN requires_configuration TINYINT NOT NULL DEFAULT 0 COMMENT '项目流程是否仍有待配置人员' AFTER latest_version_no,
    ADD KEY idx_wf_definition_list_summary (tenant_id, scope_type, project_id, deleted, id);

UPDATE wf_definition d
LEFT JOIN (
    SELECT tenant_id, definition_id, MAX(version_no) AS latest_version_no
    FROM wf_version GROUP BY tenant_id, definition_id
) latest ON latest.tenant_id = d.tenant_id AND latest.definition_id = d.id
LEFT JOIN wf_version v ON v.tenant_id = d.tenant_id AND v.definition_id = d.id AND v.version_no = latest.latest_version_no
SET d.latest_version_no = COALESCE(latest.latest_version_no, 0),
    d.requires_configuration = CASE WHEN d.scope_type = 'PROJECT' AND EXISTS (
        SELECT 1
        FROM JSON_TABLE(v.definition_json, '$.nodes[*]' COLUMNS (
            node_type VARCHAR(32) PATH '$.type',
            assignee_type VARCHAR(64) PATH '$.config.assigneeType',
            template_placeholder BOOLEAN PATH '$.config.templatePlaceholder'
        )) nodes
        WHERE (nodes.node_type = 'APPROVAL' AND nodes.assignee_type = 'TEMPLATE_PLACEHOLDER')
           OR (nodes.node_type = 'CC' AND nodes.template_placeholder = TRUE)
    ) THEN 1 ELSE 0 END;
