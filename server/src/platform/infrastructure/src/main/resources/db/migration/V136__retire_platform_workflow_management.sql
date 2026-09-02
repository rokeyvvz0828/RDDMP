-- 平台流程退出日常管理：保留存量数据供历史实例和运行时兼容，只生成可编辑的模板/项目草稿。
-- 保留号段：913600000000000-913699999999999，每个存量平台流程占用 4 个连续 ID。

CREATE TEMPORARY TABLE tmp_wf_platform_source AS
SELECT
    d.id AS source_definition_id,
    d.tenant_id,
    d.code,
    d.name,
    d.model_schema_version,
    v.definition_json,
    ROW_NUMBER() OVER (ORDER BY d.tenant_id, d.id) AS sequence_no
FROM wf_definition d
JOIN wf_version v
  ON v.tenant_id = d.tenant_id
 AND v.definition_id = d.id
 AND v.version_no = (
     SELECT MAX(latest.version_no)
     FROM wf_version latest
     WHERE latest.tenant_id = d.tenant_id
       AND latest.definition_id = d.id
 )
WHERE d.scope_type = 'PLATFORM'
  AND d.deleted = 0;

CREATE TEMPORARY TABLE tmp_wf_platform_nodes AS
SELECT source_definition_id, tenant_id, node_ordinal, node_json
FROM tmp_wf_platform_source,
     JSON_TABLE(
         definition_json,
         '$.nodes[*]' COLUMNS (
             node_ordinal FOR ORDINALITY,
             node_json JSON PATH '$'
         )
     ) nodes;

CREATE TEMPORARY TABLE tmp_wf_sanitized_source AS
SELECT
    source.source_definition_id,
    source.tenant_id,
    source.code,
    source.name,
    source.model_schema_version,
    source.sequence_no,
    JSON_SET(
        source.definition_json,
        '$.nodes',
        COALESCE(nodes.sanitized_nodes, JSON_ARRAY())
    ) AS definition_json
FROM tmp_wf_platform_source source
LEFT JOIN (
    SELECT
        expanded.source_definition_id,
        expanded.tenant_id,
        JSON_ARRAYAGG(
            CASE JSON_UNQUOTE(JSON_EXTRACT(expanded.node_json, '$.type'))
                WHEN 'APPROVAL' THEN JSON_SET(
                    JSON_REMOVE(
                        expanded.node_json,
                        '$.config.roleIds',
                        '$.config.assigneeVariable',
                        '$.config.fieldName',
                        '$.config.expression',
                        '$.config.organizationId'
                    ),
                    '$.config.assigneeType',
                    IF(
                        JSON_UNQUOTE(JSON_EXTRACT(expanded.node_json, '$.config.assigneeType')) = 'STARTER',
                        'STARTER',
                        'TEMPLATE_PLACEHOLDER'
                    ),
                    '$.config.assigneeIds', JSON_ARRAY()
                )
                WHEN 'CC' THEN JSON_SET(
                    expanded.node_json,
                    '$.config.userIds', JSON_ARRAY(),
                    '$.config.templatePlaceholder', CAST('true' AS JSON)
                )
                ELSE expanded.node_json
            END
        ) AS sanitized_nodes
    FROM (
        SELECT source_definition_id, tenant_id, node_json
        FROM tmp_wf_platform_nodes
        ORDER BY source_definition_id, node_ordinal
    ) expanded
    GROUP BY expanded.source_definition_id, expanded.tenant_id
) nodes
  ON nodes.source_definition_id = source.source_definition_id
 AND nodes.tenant_id = source.tenant_id;

INSERT INTO wf_definition
    (id, tenant_id, code, name, scope_type, project_id, status, current_version,
     model_schema_version, bpmn_xml, deployment_id, node_mapping_json, deleted)
SELECT
    913600000000000 + source.sequence_no * 4,
    source.tenant_id,
    source.code,
    source.name,
    'TEMPLATE',
    NULL,
    'DRAFT',
    0,
    source.model_schema_version,
    NULL,
    NULL,
    NULL,
    0
FROM tmp_wf_sanitized_source source
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_definition existing
    WHERE existing.tenant_id = source.tenant_id
      AND existing.scope_type = 'TEMPLATE'
      AND existing.project_id IS NULL
      AND existing.code = source.code
      AND existing.deleted = 0
);

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, status,
     model_schema_version, bpmn_xml, deployment_id, node_mapping_json)
SELECT
    913600000000001 + source.sequence_no * 4,
    source.tenant_id,
    913600000000000 + source.sequence_no * 4,
    1,
    source.definition_json,
    'DRAFT',
    source.model_schema_version,
    NULL,
    NULL,
    NULL
FROM tmp_wf_sanitized_source source
JOIN wf_definition generated_definition
  ON generated_definition.id = 913600000000000 + source.sequence_no * 4
 AND generated_definition.tenant_id = source.tenant_id
 AND generated_definition.scope_type = 'TEMPLATE'
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_version existing
    WHERE existing.tenant_id = source.tenant_id
      AND existing.definition_id = generated_definition.id
      AND existing.version_no = 1
);

INSERT INTO wf_definition
    (id, tenant_id, code, name, scope_type, project_id, status, current_version,
     model_schema_version, bpmn_xml, deployment_id, node_mapping_json, deleted)
SELECT
    913600000000002 + source.sequence_no * 4,
    source.tenant_id,
    source.code,
    source.name,
    'PROJECT',
    project.id,
    'DRAFT',
    0,
    source.model_schema_version,
    NULL,
    NULL,
    NULL,
    0
FROM tmp_wf_sanitized_source source
JOIN pm_project project
  ON project.tenant_id = source.tenant_id
 AND project.id = 910000000003001
 AND project.project_code = 'RDDMP-PLATFORM'
 AND project.project_name = '平台能力升级项目'
 AND project.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_definition existing
    WHERE existing.tenant_id = source.tenant_id
      AND existing.scope_type = 'PROJECT'
      AND existing.project_id = project.id
      AND existing.code = source.code
      AND existing.deleted = 0
);

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, status,
     model_schema_version, bpmn_xml, deployment_id, node_mapping_json)
SELECT
    913600000000003 + source.sequence_no * 4,
    source.tenant_id,
    913600000000002 + source.sequence_no * 4,
    1,
    source.definition_json,
    'DRAFT',
    source.model_schema_version,
    NULL,
    NULL,
    NULL
FROM tmp_wf_sanitized_source source
JOIN wf_definition generated_definition
  ON generated_definition.id = 913600000000002 + source.sequence_no * 4
 AND generated_definition.tenant_id = source.tenant_id
 AND generated_definition.scope_type = 'PROJECT'
 AND generated_definition.project_id = 910000000003001
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_version existing
    WHERE existing.tenant_id = source.tenant_id
      AND existing.definition_id = generated_definition.id
      AND existing.version_no = 1
);

DROP TEMPORARY TABLE tmp_wf_sanitized_source;
DROP TEMPORARY TABLE tmp_wf_platform_nodes;
DROP TEMPORARY TABLE tmp_wf_platform_source;
