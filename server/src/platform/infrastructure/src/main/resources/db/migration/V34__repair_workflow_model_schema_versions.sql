-- Repair workflow metadata that was seeded as version 1 while storing schemaVersion 2 JSON.
UPDATE wf_version
SET model_schema_version = 2
WHERE model_schema_version <> 2
  AND JSON_UNQUOTE(JSON_EXTRACT(definition_json, '$.schemaVersion')) = '2';

UPDATE wf_definition d
JOIN wf_version v
  ON v.definition_id = d.id
 AND v.tenant_id = d.tenant_id
 AND v.version_no = d.current_version
SET d.model_schema_version = 2
WHERE d.model_schema_version <> 2
  AND JSON_UNQUOTE(JSON_EXTRACT(v.definition_json, '$.schemaVersion')) = '2';
