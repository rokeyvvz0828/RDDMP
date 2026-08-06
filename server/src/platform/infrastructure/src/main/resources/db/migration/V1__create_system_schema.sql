CREATE TABLE sys_org (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_org_code (tenant_id, org_code, deleted),
    KEY idx_sys_org_parent (tenant_id, parent_id)
);

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    org_id BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_username (tenant_id, username, deleted),
    KEY idx_sys_user_org (tenant_id, org_id)
);

CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_code (tenant_id, role_code, deleted)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
);

CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_type VARCHAR(16) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    route_name VARCHAR(128),
    route_path VARCHAR(255),
    component_path VARCHAR(255),
    permission_code VARCHAR(128),
    icon VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    visible TINYINT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_sys_menu_parent (tenant_id, parent_id),
    UNIQUE KEY uk_sys_menu_route (tenant_id, route_name, deleted)
);

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE sys_dict_type (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_dict_code (tenant_id, dict_code, deleted)
);

CREATE TABLE sys_dict_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    dict_type_id BIGINT NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_sys_dict_item_type (tenant_id, dict_type_id)
);

CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    config_type VARCHAR(32) NOT NULL DEFAULT 'string',
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_config_key (tenant_id, config_key, deleted)
);

CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    success TINYINT NOT NULL,
    failure_reason VARCHAR(255),
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_login_log_created (tenant_id, created_at)
);

CREATE TABLE sys_operation_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    operator_id BIGINT NOT NULL DEFAULT 0,
    operation_code VARCHAR(128) NOT NULL,
    request_method VARCHAR(16),
    request_path VARCHAR(255),
    success TINYINT NOT NULL,
    error_message VARCHAR(255),
    client_ip VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_operation_log_created (tenant_id, created_at),
    KEY idx_sys_operation_log_operator (tenant_id, operator_id)
);

INSERT INTO sys_org (id, tenant_id, parent_id, org_code, org_name, sort_no) VALUES (1, 1, 0, 'ROOT', 'Root Organization', 0);
INSERT INTO sys_role (id, tenant_id, role_code, role_name) VALUES (1, 1, 'SUPER_ADMIN', 'Super Administrator');
INSERT INTO sys_user (id, tenant_id, username, password_hash, display_name, org_id) VALUES
    (1, 1, 'admin', '${bootstrap_admin_password_hash}', 'System Administrator', 1);
