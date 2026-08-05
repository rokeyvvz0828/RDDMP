ALTER TABLE sys_config ADD COLUMN category_id BIGINT NULL AFTER tenant_id;
ALTER TABLE sys_config ADD KEY idx_sys_config_category (tenant_id, category_id);

CREATE TABLE sys_menu_permission (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    menu_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    permission_code VARCHAR(160) NOT NULL,
    permission_name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_menu_permission_action (tenant_id, menu_id, action_code),
    UNIQUE KEY uk_sys_menu_permission_code (tenant_id, permission_code),
    KEY idx_sys_menu_permission_menu (tenant_id, menu_id)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission (tenant_id, permission_id)
);

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES
    (4001, 1, 'SYSTEM_PARAM', '系统参数', 1),
    (4002, 1, 'WORKFLOW_PARAM', '流程参数', 1),
    (4003, 1, 'AI_PARAM', 'AI 参数', 1);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
VALUES
    (4101, 1, 4001, 'security.jwt.access-ttl-millis', '900000', 'number', '访问令牌有效期，单位毫秒'),
    (4102, 1, 4001, 'security.jwt.refresh-ttl-millis', '604800000', 'number', '刷新令牌有效期，单位毫秒');

UPDATE sys_menu
SET menu_name = '参数管理', route_name = 'SystemParams', route_path = '/system/params',
    component_path = 'system/params/index', permission_code = 'system:param:list'
WHERE tenant_id = 1 AND id = 105;

UPDATE sys_menu
SET status = 0, visible = 0, deleted = 1
WHERE tenant_id = 1 AND id = 106;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 1, m.tenant_id, m.id, 'read', m.permission_code, '查看'
FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 2, m.tenant_id, m.id, 'create', CONCAT(m.permission_code, ':create'), '新增'
FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 3, m.tenant_id, m.id, 'update', CONCAT(m.permission_code, ':update'), '修改'
FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + 4, m.tenant_id, m.id, 'delete', CONCAT(m.permission_code, ':delete'), '删除'
FROM sys_menu m WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1;
