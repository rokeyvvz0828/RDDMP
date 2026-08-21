-- =============================================================================
-- 需求管理平台审批流：审批节点从写死 admin（assigneeType=USER, assigneeIds=[1]）
-- 改造为动态审批人（assigneeType=VARIABLE），由业务层提交时通过 variables.approverIds 指定。
-- -----------------------------------------------------------------------------
-- 改造背景：
--   V37 预置的 requirement.diff.review 与 legacy.stage.transition 两个流程定义，
--   审批节点 config.assigneeType=USER、assigneeIds=[1]，只能由 admin 审批。
--   业务要求"提交评审时指定审批人"，引擎已支持 VARIABLE 类型（从 variables 读取审批人 ID 列表）。
-- 改造范围：
--   1. 差异评审 requirement.diff.review：审批节点 assigneeType=VARIABLE, assigneeVariable=approverIds
--   2. 阶段推进 legacy.stage.transition：审批节点 assigneeType=VARIABLE, assigneeVariable=approverIds
-- 注意：仅更新已发布的 definition_json，不新增版本号；存量 wf_instance 仍按启动时变量回写。
-- 幂等：definition_json 已是 VARIABLE 形态时跳过。
-- =============================================================================

-- 差异评审：审批节点 approval-reviewer 改为 VARIABLE
UPDATE wf_version
SET definition_json = '{"schemaVersion":1,"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-reviewer","type":"APPROVAL","label":"差异评审","position":{"x":380,"y":160},"config":{"assigneeType":"VARIABLE","assigneeVariable":"approverIds","mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-reviewer","source":"start","target":"approval-reviewer"},{"id":"edge-reviewer-end","source":"approval-reviewer","target":"end"}]}'
WHERE tenant_id = 1
  AND definition_id = 900000000000010
  AND version_no = 1
  AND definition_json NOT LIKE '%assigneeType":"VARIABLE"%';

-- 阶段推进：审批节点 approval-leader 改为 VARIABLE
UPDATE wf_version
SET definition_json = '{"schemaVersion":1,"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-leader","type":"APPROVAL","label":"阶段推进审批","position":{"x":380,"y":160},"config":{"assigneeType":"VARIABLE","assigneeVariable":"approverIds","mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-leader","source":"start","target":"approval-leader"},{"id":"edge-leader-end","source":"approval-leader","target":"end"}]}'
WHERE tenant_id = 1
  AND definition_id = 900000000000011
  AND version_no = 1
  AND definition_json NOT LIKE '%assigneeType":"VARIABLE"%';
