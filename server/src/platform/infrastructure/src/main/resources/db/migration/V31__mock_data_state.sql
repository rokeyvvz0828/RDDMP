-- Local mock data synchronization metadata. The loader is local-profile only and never seeds production data.
CREATE TABLE sys_mock_dataset_state (
    id BIGINT PRIMARY KEY,
    dataset_key VARCHAR(128) NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_mock_dataset_key (dataset_key)
);
