-- Seek reads are ordered by created_at DESC, id DESC and retain their existing tenant/user/status filters.
ALTER TABLE wf_task ADD KEY idx_wf_task_seek_inbox (tenant_id, assignee_id, status, created_at, id);
ALTER TABLE wf_task_action ADD KEY idx_wf_task_action_seek_done (tenant_id, operator_id, created_at, id);
ALTER TABLE wf_instance ADD KEY idx_wf_instance_seek_history (tenant_id, deleted, created_at, id);
