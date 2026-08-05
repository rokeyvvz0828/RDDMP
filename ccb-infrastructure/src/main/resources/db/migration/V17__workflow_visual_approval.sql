ALTER TABLE wf_task
    ADD COLUMN node_id VARCHAR(64) NULL AFTER task_key,
    ADD COLUMN task_type VARCHAR(16) NOT NULL DEFAULT 'APPROVAL' AFTER node_id,
    ADD COLUMN task_group_key VARCHAR(64) NULL AFTER task_type,
    ADD COLUMN parent_task_id BIGINT NULL AFTER task_group_key,
    ADD COLUMN assignee_type VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER parent_task_id,
    ADD COLUMN assignee_name VARCHAR(128) NULL AFTER assignee_type;

ALTER TABLE wf_task
    ADD KEY idx_wf_task_group (tenant_id, instance_id, task_group_key, status),
    ADD KEY idx_wf_task_parent (tenant_id, parent_task_id, status);

CREATE TABLE wf_task_action (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    action_code VARCHAR(16) NOT NULL,
    operator_id BIGINT NOT NULL,
    target_user_id BIGINT NULL,
    comment VARCHAR(500),
    payload_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wf_task_action_task (tenant_id, task_id, created_at),
    KEY idx_wf_task_action_instance (tenant_id, instance_id, created_at),
    KEY idx_wf_task_action_operator (tenant_id, operator_id, created_at)
);