CREATE TABLE sys_notification (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    notification_level VARCHAR(16) NOT NULL,
    source_name VARCHAR(128) NOT NULL,
    action_path VARCHAR(512),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_notification_event (tenant_id, business_type, event_id),
    KEY idx_sys_notification_business (tenant_id, business_type, business_key),
    KEY idx_sys_notification_created (tenant_id, created_at, id)
);

CREATE TABLE sys_user_notification (
    notification_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id, user_id),
    KEY idx_sys_user_notification_unread (tenant_id, user_id, is_read, created_at, notification_id)
);
