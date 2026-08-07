-- 本地 workflow 演示数据，仅使用 DEMO_ 前缀编码和独立 ID。
-- 数据用于验证流程定义、审批待办、流程已办和流程监控页面，不代表真实业务。

INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000001, 1, 'DEMO_EXPENSE_APPROVAL', '研发费用报销审批', 'PUBLISHED', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE tenant_id = 1 AND code = 'DEMO_EXPENSE_APPROVAL' AND deleted = 0);

INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000011, 1, 900000000000001, 1,
       '{"schemaVersion":2,"variables":[{"name":"amount","type":"NUMBER","required":true}],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-finance","type":"APPROVAL","label":"费用审批","position":{"x":380,"y":160},"config":{"assigneeType":"USER","assigneeIds":[1],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"cc-owner","type":"CC","label":"抄送申请人","position":{"x":660,"y":160},"config":{"userIds":[1]}},{"id":"end","type":"END","label":"结束","position":{"x":940,"y":160},"config":{}}],"edges":[{"id":"edge-start-finance","source":"start","target":"approval-finance","label":null,"condition":null,"default":false},{"id":"edge-finance-cc","source":"approval-finance","target":"cc-owner","label":null,"condition":null,"default":false},{"id":"edge-cc-end","source":"cc-owner","target":"end","label":null,"condition":null,"default":false}],"formBindings":[]}',
       1, 'PUBLISHED'
WHERE NOT EXISTS (SELECT 1 FROM wf_version WHERE tenant_id = 1 AND id = 900000000000011);

INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000002, 1, 'DEMO_RELEASE_APPROVAL', '版本投产审批', 'PUBLISHED', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE tenant_id = 1 AND code = 'DEMO_RELEASE_APPROVAL' AND deleted = 0);

INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000012, 1, 900000000000002, 1,
       '{"schemaVersion":2,"variables":[{"name":"releaseVersion","type":"STRING","required":true}],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-product","type":"APPROVAL","label":"产品审批","position":{"x":380,"y":160},"config":{"assigneeType":"USER","assigneeIds":[1],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"approval-ops","type":"APPROVAL","label":"运维审批","position":{"x":660,"y":160},"config":{"assigneeType":"USER","assigneeIds":[1],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":940,"y":160},"config":{}}],"edges":[{"id":"edge-start-product","source":"start","target":"approval-product","label":null,"condition":null,"default":false},{"id":"edge-product-ops","source":"approval-product","target":"approval-ops","label":null,"condition":null,"default":false},{"id":"edge-ops-end","source":"approval-ops","target":"end","label":null,"condition":null,"default":false}],"formBindings":[]}',
       1, 'PUBLISHED'
WHERE NOT EXISTS (SELECT 1 FROM wf_version WHERE tenant_id = 1 AND id = 900000000000012);

INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000003, 1, 'DEMO_CHANGE_DRAFT', '系统变更审批示例（草稿）', 'DRAFT', 1, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE tenant_id = 1 AND code = 'DEMO_CHANGE_DRAFT' AND deleted = 0);

INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000013, 1, 900000000000003, 1,
       '{"schemaVersion":2,"variables":[{"name":"changeType","type":"STRING","required":true},{"name":"riskLevel","type":"STRING","required":true}],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"condition-risk","type":"CONDITION","label":"风险判断","position":{"x":380,"y":160},"config":{"defaultEdgeId":"edge-risk-normal"}},{"id":"approval-change","type":"APPROVAL","label":"变更审批","position":{"x":660,"y":160},"config":{"assigneeType":"USER","assigneeIds":[1],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":940,"y":160},"config":{}}],"edges":[{"id":"edge-start-risk","source":"start","target":"condition-risk","label":null,"condition":null,"default":false},{"id":"edge-risk-high","source":"condition-risk","target":"approval-change","label":"高风险","condition":"riskLevel == ''HIGH''","default":false},{"id":"edge-risk-normal","source":"condition-risk","target":"approval-change","label":"普通","condition":null,"default":true},{"id":"edge-change-end","source":"approval-change","target":"end","label":null,"condition":null,"default":false}],"formBindings":[]}',
       2, 'DRAFT'
WHERE NOT EXISTS (SELECT 1 FROM wf_version WHERE tenant_id = 1 AND id = 900000000000013);

INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at)
SELECT 900000000000101, 1, 900000000000001, 1, 'DEMO-EXP-2026-001', 'RUNNING', 0, 1,
       '{"amount":6800,"applicant":"管理员"}', CURRENT_TIMESTAMP - INTERVAL 2 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_instance WHERE id = 900000000000101);

INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at)
SELECT 900000000000102, 1, 900000000000001, 1, 'DEMO-EXP-2026-002', 'APPROVED', 0, 1,
       '{"amount":1280,"applicant":"管理员"}', CURRENT_TIMESTAMP - INTERVAL 2 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_instance WHERE id = 900000000000102);

INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at)
SELECT 900000000000103, 1, 900000000000002, 1, 'DEMO-REL-2026-001', 'REJECTED', 0, 1,
       '{"releaseVersion":"v2.6.0","riskLevel":"HIGH"}', CURRENT_TIMESTAMP - INTERVAL 4 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_instance WHERE id = 900000000000103);

INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, deleted, starter_id, variables_json, created_at)
SELECT 900000000000104, 1, 900000000000002, 1, 'DEMO-REL-2026-002', 'RUNNING', 0, 1,
       '{"releaseVersion":"v2.6.1","riskLevel":"NORMAL"}', CURRENT_TIMESTAMP - INTERVAL 5 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_instance WHERE id = 900000000000104);

INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, created_at)
SELECT 900000000000201, 1, 900000000000101, 'approval-finance', 'approval-finance', 'APPROVAL', 'demo-exp-001', 'USER', '管理员', 1, 'PENDING', CURRENT_TIMESTAMP - INTERVAL 2 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 900000000000201);

INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, comment, completed_at, created_at)
SELECT 900000000000202, 1, 900000000000102, 'approval-finance', 'approval-finance', 'APPROVAL', 'demo-exp-002', 'USER', '管理员', 1, 'APPROVED', '费用合理，同意报销', CURRENT_TIMESTAMP - INTERVAL 1 DAY, CURRENT_TIMESTAMP - INTERVAL 2 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 900000000000202);

INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, comment, completed_at, created_at)
SELECT 900000000000203, 1, 900000000000102, 'cc-owner', 'cc-owner', 'CC', 'demo-exp-002-cc', 'USER', '管理员', 1, 'SENT', '审批结果抄送', CURRENT_TIMESTAMP - INTERVAL 23 HOUR, CURRENT_TIMESTAMP - INTERVAL 23 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 900000000000203);

INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, comment, completed_at, created_at)
SELECT 900000000000204, 1, 900000000000103, 'approval-product', 'approval-product', 'APPROVAL', 'demo-rel-001', 'USER', '管理员', 1, 'REJECTED', '投产窗口与当前版本冲突', CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP - INTERVAL 4 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 900000000000204);

INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, created_at)
SELECT 900000000000205, 1, 900000000000104, 'approval-product', 'approval-product', 'APPROVAL', 'demo-rel-002', 'USER', '管理员', 1, 'PENDING', CURRENT_TIMESTAMP - INTERVAL 5 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 900000000000205);

INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, created_at)
SELECT 900000000000206, 1, 900000000000104, 'approval-ops', 'approval-ops', 'APPROVAL', 'demo-rel-002-next', 'USER', '管理员', 1, 'CANCELLED', CURRENT_TIMESTAMP - INTERVAL 5 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 900000000000206);

INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, comment, payload_json, created_at)
SELECT 900000000000301, 1, 900000000000102, 900000000000202, 'APPROVE', 1, '费用合理，同意报销', '{}', CURRENT_TIMESTAMP - INTERVAL 1 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_task_action WHERE id = 900000000000301);

INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, target_user_id, comment, payload_json, created_at)
SELECT 900000000000302, 1, 900000000000102, 900000000000203, 'CC', 1, 1, '审批结果抄送', '{"userIds":[1]}', CURRENT_TIMESTAMP - INTERVAL 23 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_task_action WHERE id = 900000000000302);

INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, comment, payload_json, created_at)
SELECT 900000000000303, 1, 900000000000103, 900000000000204, 'REJECT', 1, '投产窗口与当前版本冲突', '{}', CURRENT_TIMESTAMP - INTERVAL 3 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_task_action WHERE id = 900000000000303);

INSERT INTO wf_audit_event (id, tenant_id, definition_id, version_no, instance_id, task_id, event_type, operator_id, reason, payload_json, created_at)
SELECT 900000000000401, 1, 900000000000001, 1, 900000000000101, NULL, 'INSTANCE_STARTED', 1, '演示流程已发起', '{"businessKey":"DEMO-EXP-2026-001"}', CURRENT_TIMESTAMP - INTERVAL 2 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_audit_event WHERE id = 900000000000401);

INSERT INTO wf_audit_event (id, tenant_id, definition_id, version_no, instance_id, task_id, event_type, operator_id, reason, payload_json, created_at)
SELECT 900000000000402, 1, 900000000000001, 1, 900000000000102, NULL, 'INSTANCE_STARTED', 1, '演示流程已发起', '{"businessKey":"DEMO-EXP-2026-002"}', CURRENT_TIMESTAMP - INTERVAL 2 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_audit_event WHERE id = 900000000000402);

INSERT INTO wf_audit_event (id, tenant_id, definition_id, version_no, instance_id, task_id, event_type, operator_id, reason, payload_json, created_at)
SELECT 900000000000403, 1, 900000000000001, 1, 900000000000102, 900000000000202, 'TASK_APPROVED', 1, '费用合理，同意报销', '{"action":"APPROVE"}', CURRENT_TIMESTAMP - INTERVAL 1 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_audit_event WHERE id = 900000000000403);

INSERT INTO wf_audit_event (id, tenant_id, definition_id, version_no, instance_id, task_id, event_type, operator_id, reason, payload_json, created_at)
SELECT 900000000000404, 1, 900000000000002, 1, 900000000000103, NULL, 'INSTANCE_STARTED', 1, '演示流程已发起', '{"businessKey":"DEMO-REL-2026-001"}', CURRENT_TIMESTAMP - INTERVAL 4 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_audit_event WHERE id = 900000000000404);

INSERT INTO wf_audit_event (id, tenant_id, definition_id, version_no, instance_id, task_id, event_type, operator_id, reason, payload_json, created_at)
SELECT 900000000000405, 1, 900000000000002, 1, 900000000000103, 900000000000204, 'TASK_REJECTED', 1, '投产窗口与当前版本冲突', '{"action":"REJECT"}', CURRENT_TIMESTAMP - INTERVAL 3 DAY
WHERE NOT EXISTS (SELECT 1 FROM wf_audit_event WHERE id = 900000000000405);

INSERT INTO wf_audit_event (id, tenant_id, definition_id, version_no, instance_id, task_id, event_type, operator_id, reason, payload_json, created_at)
SELECT 900000000000406, 1, 900000000000002, 1, 900000000000104, NULL, 'INSTANCE_STARTED', 1, '演示流程已发起', '{"businessKey":"DEMO-REL-2026-002"}', CURRENT_TIMESTAMP - INTERVAL 5 HOUR
WHERE NOT EXISTS (SELECT 1 FROM wf_audit_event WHERE id = 900000000000406);
