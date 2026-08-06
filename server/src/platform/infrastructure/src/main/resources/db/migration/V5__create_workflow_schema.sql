CREATE TABLE wf_definition (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', current_version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_definition_code (tenant_id, code, deleted)
);
CREATE TABLE wf_version (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, definition_id BIGINT NOT NULL, version_no INT NOT NULL,
    definition_json JSON NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_version (tenant_id, definition_id, version_no)
);
CREATE TABLE wf_instance (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, definition_id BIGINT NOT NULL, version_no INT NOT NULL,
    business_key VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL, starter_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wf_instance_business (tenant_id, business_key)
);
CREATE TABLE wf_task (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, instance_id BIGINT NOT NULL, task_key VARCHAR(64) NOT NULL,
    assignee_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', comment VARCHAR(500), completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_wf_task_inbox (tenant_id, assignee_id, status)
);
