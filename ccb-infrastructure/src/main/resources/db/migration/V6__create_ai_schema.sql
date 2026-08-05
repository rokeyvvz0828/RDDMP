CREATE TABLE ai_provider (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, provider_code VARCHAR(64) NOT NULL, provider_name VARCHAR(128) NOT NULL,
    endpoint VARCHAR(255), status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_provider_code (tenant_id, provider_code, deleted)
);
CREATE TABLE ai_model (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, provider_id BIGINT NOT NULL, model_code VARCHAR(128) NOT NULL, model_name VARCHAR(128) NOT NULL,
    capabilities VARCHAR(1000), credential_secret TEXT, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_model_code (tenant_id, model_code, deleted)
);
CREATE TABLE ai_route (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, capability VARCHAR(128) NOT NULL, model_id BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 100, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_route_capability (tenant_id, capability, status)
);
CREATE TABLE ai_execution (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, operator_id BIGINT NOT NULL, capability VARCHAR(128) NOT NULL, model_id BIGINT NOT NULL,
    input_summary VARCHAR(500), status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_execution_operator (tenant_id, operator_id, created_at)
);
