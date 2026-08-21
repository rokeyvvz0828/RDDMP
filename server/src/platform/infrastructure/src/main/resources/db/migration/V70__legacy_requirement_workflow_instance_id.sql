-- 为存量需求表补加 workflow_instance_id 列，承接阶段推进审批流实例 ID。
-- 与 req_difference.workflow_instance_id 口径一致，便于运维查询与状态回写幂等保护。
ALTER TABLE req_legacy_requirement
    ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT '审批流实例 ID：审批中阶段对应 wf_instance.id' AFTER launch_stage_status;
