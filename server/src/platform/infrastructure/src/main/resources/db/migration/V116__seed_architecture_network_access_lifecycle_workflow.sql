-- REQ-20260828-055：网络访问判定与生命周期种子数据。
-- 免申请规则只是本地可验收示例；真实生产规则需由网络办理人员维护。

INSERT INTO arch_network_access_exemption_rule
    (id, tenant_id, rule_code, rule_name, source_network_zone_id, target_network_zone_id,
     protocol, ports, valid_from, valid_until, validity_type, status, remark, created_by, updated_by)
SELECT 100000000000201, 1, 'EXEMPT_APP_SELF_HTTPS', '应用区内部 HTTPS 免申请',
       source_zone.id, target_zone.id, 'HTTPS', '443',
       '2026-08-28 00:00:00', NULL, 'LONG_TERM', 'ACTIVE',
       '本地 UAT 示例：应用区到应用区 HTTPS 访问可判定为不需要申请', 1, 1
FROM arch_network_zone source_zone
JOIN arch_network_zone target_zone
  ON target_zone.tenant_id = source_zone.tenant_id
WHERE source_zone.tenant_id = 1
  AND source_zone.code = 'ZONE_APP'
  AND target_zone.code = 'ZONE_APP'
  AND NOT EXISTS (
      SELECT 1
      FROM arch_network_access_exemption_rule
      WHERE tenant_id = 1 AND rule_code = 'EXEMPT_APP_SELF_HTTPS'
  );

-- 独立访问申请审批流程草稿：必须通过平台发布入口生成 deployment 后才能支持真实提交。
INSERT INTO wf_definition
    (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000104, 1, 'architecture.network-access-application',
       '网络访问申请审批', 'DRAFT', 1, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE id = 900000000000104)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_definition
      WHERE tenant_id = 1
        AND code = 'architecture.network-access-application'
        AND deleted = 0
  );

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000105, 1, 900000000000104, 1,
       '{"schemaVersion":2,"variables":[],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-network-access-manager","type":"APPROVAL","label":"网络访问申请审批","position":{"x":380,"y":160},"config":{"assigneeType":"ROLE","assigneeIds":[113],"roleIds":[113],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","RETURN","REJECT"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-approval","source":"start","target":"approval-network-access-manager","label":null,"condition":null,"default":false},{"id":"edge-approval-end","source":"approval-network-access-manager","target":"end","label":null,"condition":null,"default":false}]}',
       2, 'DRAFT'
WHERE EXISTS (
    SELECT 1
    FROM wf_definition
    WHERE id = 900000000000104
      AND tenant_id = 1
      AND code = 'architecture.network-access-application'
      AND deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM wf_version WHERE id = 900000000000105);

CREATE TEMPORARY TABLE tmp_arch_v105_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v105_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v105_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM arch_network_access_exemption_rule
          WHERE tenant_id = 1
            AND rule_code = 'EXEMPT_APP_SELF_HTTPS'
            AND protocol = 'HTTPS'
            AND ports = '443'
            AND validity_type = 'LONG_TERM'
            AND status = 'ACTIVE'
      )
   OR NOT EXISTS (
          SELECT 1
          FROM wf_definition
          WHERE id = 900000000000104
            AND tenant_id = 1
            AND code = 'architecture.network-access-application'
            AND name = '网络访问申请审批'
            AND status = 'DRAFT'
            AND current_version = 1
            AND model_schema_version = 2
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM wf_version
          WHERE id = 900000000000105
            AND tenant_id = 1
            AND definition_id = 900000000000104
            AND version_no = 1
            AND model_schema_version = 2
            AND status = 'DRAFT'
      );

DROP TEMPORARY TABLE tmp_arch_v105_seed_guard;
