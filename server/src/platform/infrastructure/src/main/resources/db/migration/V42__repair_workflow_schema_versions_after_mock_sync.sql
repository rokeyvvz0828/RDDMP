-- V34 repaired the initial metadata, but local mock synchronization could later
-- upsert schema-version 2 definitions without their matching metadata value.
UPDATE wf_version
SET model_schema_version = 2
WHERE model_schema_version <> 2
  AND JSON_UNQUOTE(JSON_EXTRACT(definition_json, '$.schemaVersion')) = '2';

UPDATE wf_definition d
JOIN wf_version v
  ON v.definition_id = d.id
 AND v.tenant_id = d.tenant_id
 AND v.version_no = COALESCE(NULLIF(d.current_version, 0), (
     SELECT MAX(v2.version_no)
     FROM wf_version v2
     WHERE v2.definition_id = d.id
       AND v2.tenant_id = d.tenant_id
 ))
SET d.model_schema_version = 2
WHERE d.model_schema_version <> 2
  AND JSON_UNQUOTE(JSON_EXTRACT(v.definition_json, '$.schemaVersion')) = '2';
