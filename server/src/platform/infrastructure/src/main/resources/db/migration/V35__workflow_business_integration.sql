-- 工作流业务接入上下文、持久生命周期事件及内部电子签名。
ALTER TABLE wf_instance
    ADD COLUMN business_type VARCHAR(64) NULL COMMENT '业务类型' AFTER business_key,
    ADD COLUMN business_title VARCHAR(200) NULL COMMENT '业务标题' AFTER business_type,
    ADD COLUMN business_round INT NULL COMMENT '业务流程轮次' AFTER business_title,
    ADD COLUMN project_ref VARCHAR(64) NULL COMMENT '项目展示标识快照' AFTER business_round,
    ADD COLUMN project_name VARCHAR(128) NULL COMMENT '项目展示名称快照' AFTER project_ref,
    ADD COLUMN action_path VARCHAR(512) NULL COMMENT '站内业务详情路由' AFTER project_name,
    ADD COLUMN data_digest CHAR(64) NULL COMMENT '规范化业务数据SHA-256摘要' AFTER action_path,
    ADD KEY idx_wf_instance_business_context (tenant_id, business_type, business_key, business_round);

CREATE TABLE wf_lifecycle_event (
    id BIGINT PRIMARY KEY,
    event_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    business_round INT NOT NULL,
    business_title VARCHAR(200) NOT NULL,
    project_ref VARCHAR(64) NULL,
    project_name VARCHAR(128) NULL,
    action_path VARCHAR(512) NOT NULL,
    data_digest CHAR(64) NOT NULL,
    operator_id BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_lifecycle_event_id (event_id),
    KEY idx_wf_lifecycle_event_instance (tenant_id, instance_id, occurred_at),
    KEY idx_wf_lifecycle_event_business (tenant_id, business_type, business_key, business_round)
) COMMENT='工作流业务生命周期事件';

CREATE TABLE wf_lifecycle_delivery (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    event_id CHAR(36) NOT NULL,
    subscriber_key VARCHAR(96) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NULL,
    last_error VARCHAR(1000) NULL,
    delivered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_lifecycle_delivery (tenant_id, event_id, subscriber_key),
    KEY idx_wf_lifecycle_delivery_schedule (status, next_attempt_at, id)
) COMMENT='工作流生命周期事件订阅投递';

CREATE TABLE wf_signature (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    business_round INT NOT NULL,
    action_code VARCHAR(24) NOT NULL,
    comment_text VARCHAR(500) NULL,
    data_digest CHAR(64) NOT NULL,
    signer_id BIGINT NOT NULL,
    signer_username VARCHAR(64) NOT NULL,
    signer_display_name VARCHAR(128) NOT NULL,
    signed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_signature_task_action (tenant_id, task_id, action_code),
    KEY idx_wf_signature_instance (tenant_id, instance_id, signed_at)
) COMMENT='平台内部电子签名证据';
