-- V41：req_stage_log 增加审批结果与关联审批实例字段
-- approval_result: PENDING/APPROVED/REJECTED/MANUAL（MANUAL 仅用于未来手工直写场景）
-- workflow_instance_id: 关联 wf_instance.id，便于从阶段记录跳转查看完整审批动作
ALTER TABLE req_stage_log
    ADD COLUMN approval_result VARCHAR(16) NULL COMMENT '审批结果：PENDING/APPROVED/REJECTED/MANUAL',
    ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT '关联审批实例 id';

-- 回填历史 stage_log：comment 含"审批流回写：APPROVED/REJECTED"的可识别回填
UPDATE req_stage_log SET approval_result = 'APPROVED' WHERE approval_result IS NULL AND comment LIKE '%审批流回写：APPROVED%';
UPDATE req_stage_log SET approval_result = 'REJECTED' WHERE approval_result IS NULL AND comment LIKE '%审批流回写：REJECTED%';
UPDATE req_stage_log SET approval_result = 'MANUAL' WHERE approval_result IS NULL;
