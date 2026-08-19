CREATE TABLE pm_project (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    owner_user_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_pm_project_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted),
    KEY idx_pm_project_status (tenant_id, status, updated_at),
    KEY idx_pm_project_owner (tenant_id, owner_user_id)
);

CREATE TABLE pm_project_member (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    owner_project_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN role = 'OWNER' THEN project_id ELSE NULL END
    ) STORED,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, project_id, user_id),
    CONSTRAINT chk_pm_project_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    UNIQUE KEY uk_pm_project_member_owner (tenant_id, owner_project_id),
    KEY idx_pm_project_member_user (tenant_id, user_id, project_id),
    KEY idx_pm_project_member_role (tenant_id, project_id, role)
);

CREATE TABLE pm_project_audit_event (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    change_summary VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pm_project_audit_result CHECK (result IN ('SUCCESS', 'REJECTED')),
    KEY idx_pm_project_audit_project (tenant_id, project_id, created_at),
    KEY idx_pm_project_audit_actor (tenant_id, actor_user_id, created_at)
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path,
     component_path, permission_code, icon, sort_no)
VALUES (600, 1, 0, 'menu', '项目管理', 'ProjectManagement', '/projects',
        'project/index', 'project:list', 'folder-kanban', 600);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 600, 1
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 600 AND deleted = 0);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (6001, 1, 600, 'read', 'project:list', '查看'),
    (6002, 1, 600, 'create', 'project:list:create', '新增'),
    (6003, 1, 600, 'update', 'project:list:update', '修改'),
    (6004, 1, 600, 'member', 'project:list:member', '成员管理'),
    (6005, 1, 600, 'archive', 'project:list:archive', '归档恢复');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1
FROM sys_menu_permission
WHERE tenant_id = 1 AND menu_id = 600;
