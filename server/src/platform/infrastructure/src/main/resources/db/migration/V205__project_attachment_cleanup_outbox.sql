CREATE TABLE att_project_cleanup_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    last_error VARCHAR(1000) NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_att_project_cleanup_attachment (tenant_id, attachment_id),
    KEY idx_att_project_cleanup_claim (status, locked_until, id)
) COMMENT='项目附件对象异步清理 Outbox';
