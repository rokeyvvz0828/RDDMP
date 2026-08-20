-- 为需求管理平台业务模块（差异评审、存量项目阶段推进）预置审批流程定义。
-- 流程结构：start → approval（admin 单人审批）→ end；schemaVersion=1。
-- businessKey 约定：
--   差异评审：req-diff:{differenceId}
--   阶段推进：req-legacy:{requirementId}:{stage}:{action}   stage ∈ {ba_review, docking, scheme, tech_review, dev, sit, uat, prod}
--   action ∈ {START, COMPLETE, BACK}

INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000010, 1, 'requirement.diff.review', '需求差异评审审批', 'PUBLISHED', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE tenant_id = 1 AND code = 'requirement.diff.review' AND deleted = 0);

INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000020, 1, 900000000000010, 1,
       '{"schemaVersion":1,"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-reviewer","type":"APPROVAL","label":"差异评审","position":{"x":380,"y":160},"config":{"assigneeType":"USER","assigneeIds":[1],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-reviewer","source":"start","target":"approval-reviewer"},{"id":"edge-reviewer-end","source":"approval-reviewer","target":"end"}]}',
       1, 'PUBLISHED'
WHERE NOT EXISTS (SELECT 1 FROM wf_version WHERE tenant_id = 1 AND id = 900000000000020);

INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000011, 1, 'legacy.stage.transition', '存量需求阶段推进审批', 'PUBLISHED', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE tenant_id = 1 AND code = 'legacy.stage.transition' AND deleted = 0);

INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000021, 1, 900000000000011, 1,
       '{"schemaVersion":1,"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-leader","type":"APPROVAL","label":"阶段推进审批","position":{"x":380,"y":160},"config":{"assigneeType":"USER","assigneeIds":[1],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-leader","source":"start","target":"approval-leader"},{"id":"edge-leader-end","source":"approval-leader","target":"end"}]}',
       1, 'PUBLISHED'
WHERE NOT EXISTS (SELECT 1 FROM wf_version WHERE tenant_id = 1 AND id = 900000000000021);
