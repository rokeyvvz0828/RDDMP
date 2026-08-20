-- 项目管理数据与权限初始化。
-- 所有业务表均保留租户、逻辑删除和审计时间，避免项目数据跨租户泄漏。

CREATE TABLE pm_project (
    id BIGINT PRIMARY KEY COMMENT '项目主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编号',
    project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    description VARCHAR(1000) NULL COMMENT '项目描述',
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNING' COMMENT '项目状态：PLANNING计划中、RUNNING进行中、COMPLETED已完成、SUSPENDED已暂停',
    owner_id BIGINT NOT NULL COMMENT '项目负责人用户主键',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    actual_end_date DATE NULL COMMENT '实际结束日期',
    created_by BIGINT NOT NULL COMMENT '创建人用户主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted),
    KEY idx_pm_project_owner (tenant_id, owner_id, deleted),
    KEY idx_pm_project_status (tenant_id, status, deleted)
) COMMENT='项目主表';

CREATE TABLE pm_project_plan (
    id BIGINT PRIMARY KEY COMMENT '项目计划主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父计划主键，0表示根计划',
    plan_name VARCHAR(128) NOT NULL COMMENT '计划名称',
    description VARCHAR(1000) NULL COMMENT '计划描述',
    owner_id BIGINT NULL COMMENT '计划负责人用户主键',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '完成进度百分比',
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '计划状态：NOT_STARTED未开始、IN_PROGRESS进行中、COMPLETED已完成、BLOCKED已阻塞',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '同级计划排序号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    KEY idx_pm_plan_project (tenant_id, project_id, parent_id, deleted),
    KEY idx_pm_plan_owner (tenant_id, owner_id, deleted)
) COMMENT='项目计划任务表';

CREATE TABLE pm_project_role (
    id BIGINT PRIMARY KEY COMMENT '项目角色主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    role_code VARCHAR(64) NOT NULL COMMENT '项目角色编码',
    role_name VARCHAR(128) NOT NULL COMMENT '项目角色名称',
    description VARCHAR(500) NULL COMMENT '项目角色描述',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    UNIQUE KEY uk_pm_role_code (tenant_id, project_id, role_code, deleted),
    KEY idx_pm_role_project (tenant_id, project_id, deleted)
) COMMENT='项目角色表';

CREATE TABLE pm_project_member (
    id BIGINT PRIMARY KEY COMMENT '项目成员关系主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    user_id BIGINT NOT NULL COMMENT '用户主键',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '成员状态：0停用、1有效',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    UNIQUE KEY uk_pm_member (tenant_id, project_id, user_id, deleted),
    KEY idx_pm_member_user (tenant_id, user_id, status, deleted),
    KEY idx_pm_member_project (tenant_id, project_id, status, deleted)
) COMMENT='项目成员表';

CREATE TABLE pm_project_member_role (
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    member_id BIGINT NOT NULL COMMENT '项目成员关系主键',
    role_id BIGINT NOT NULL COMMENT '项目角色主键',
    PRIMARY KEY (member_id, role_id),
    KEY idx_pm_member_role_tenant (tenant_id, role_id)
) COMMENT='项目成员角色关联表';

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 500, 1, 0, 'menu', '项目管理', 'ProjectManagement', '/projects', 'project/index', 'project:access', 'tickets', 500
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 500 OR route_path = '/projects'));

-- 权限节点隐藏于导航，仅用于角色权限维护，左侧只显示一个项目管理菜单。
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible)
SELECT 501, 1, 500, 'button', '项目基础信息权限', 'ProjectPermissionProject', NULL, NULL, 'project:project:list', 'briefcase', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 501);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible)
SELECT 502, 1, 500, 'button', '项目计划权限', 'ProjectPermissionPlan', NULL, NULL, 'project:plan:list', 'calendar', 2, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 502);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible)
SELECT 503, 1, 500, 'button', '项目成员权限', 'ProjectPermissionMember', NULL, NULL, 'project:member:list', 'user', 3, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 503);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible)
SELECT 504, 1, 500, 'button', '项目角色权限', 'ProjectPermissionRole', NULL, NULL, 'project:role:list', 'user-filled', 4, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 504);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id IN (500, 501, 502, 503, 504) AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5011, 1, 501, 'read', 'project:project:list', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 501 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5012, 1, 501, 'create', 'project:project:list:create', '新增'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 501 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5013, 1, 501, 'update', 'project:project:list:update', '修改'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 501 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5014, 1, 501, 'delete', 'project:project:list:delete', '删除'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 501 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5021, 1, 502, 'read', 'project:plan:list', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 502 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5022, 1, 502, 'create', 'project:plan:list:create', '新增'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 502 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5023, 1, 502, 'update', 'project:plan:list:update', '修改'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 502 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5024, 1, 502, 'delete', 'project:plan:list:delete', '删除'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 502 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5031, 1, 503, 'read', 'project:member:list', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 503 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5032, 1, 503, 'create', 'project:member:list:create', '新增'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 503 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5033, 1, 503, 'update', 'project:member:list:update', '修改'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 503 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5034, 1, 503, 'delete', 'project:member:list:delete', '删除'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 503 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5041, 1, 504, 'read', 'project:role:list', '查看'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 504 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5042, 1, 504, 'create', 'project:role:list:create', '新增'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 504 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5043, 1, 504, 'update', 'project:role:list:update', '修改'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 504 AND tenant_id = 1 AND deleted = 0);
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 5044, 1, 504, 'delete', 'project:role:list:delete', '删除'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 504 AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id IN (501, 502, 503, 504);
