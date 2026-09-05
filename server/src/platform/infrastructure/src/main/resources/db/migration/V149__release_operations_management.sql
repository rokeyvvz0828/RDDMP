-- REQ-20260901-057：按项目隔离的投产管理数据、菜单和权限。
-- 只追加迁移；业务记录通过正式 API 创建，不在迁移中写入演示业务数据。

CREATE TABLE rel_release_drill_plan (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    scenario_content TEXT NULL,
    environment_content TEXT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_drill_plan_project (tenant_id, project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产演练计划';

CREATE TABLE rel_release_drill_round (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    drill_plan_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    round_name VARCHAR(128) NOT NULL,
    planned_at DATETIME(6) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    result_content TEXT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_drill_round_no (tenant_id, drill_plan_id, round_no, deleted),
    KEY idx_rel_drill_round_project (tenant_id, project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产演练轮次';

CREATE TABLE rel_release_timeline (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    timeline_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/ROLLBACK',
    timeline_name VARCHAR(128) NOT NULL,
    description VARCHAR(2000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_timeline_project_type (tenant_id, project_id, timeline_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产时序';

CREATE TABLE rel_release_timeline_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    timeline_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    planned_start DATETIME(6) NULL,
    planned_end DATETIME(6) NULL,
    owner_id BIGINT NULL,
    owner_name VARCHAR(128) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(2000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_timeline_item_seq (tenant_id, timeline_id, seq_no, deleted),
    KEY idx_rel_timeline_item_project (tenant_id, project_id, timeline_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产时序明细';

CREATE TABLE rel_release_issue (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    issue_no VARCHAR(64) NOT NULL,
    issue_title VARCHAR(256) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    issue_status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    discovered_at DATETIME(6) NULL,
    owner_id BIGINT NULL,
    owner_name VARCHAR(128) NULL,
    issue_description TEXT NULL,
    analysis_content TEXT NULL,
    action_content TEXT NULL,
    follow_up_content TEXT NULL,
    closed_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_issue_no (tenant_id, project_id, issue_no, deleted),
    KEY idx_rel_issue_filter (tenant_id, project_id, issue_status, priority, updated_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产问题分析及跟踪';

CREATE TABLE rel_release_group (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    group_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_group_name (tenant_id, project_id, group_name, deleted),
    KEY idx_rel_group_project (tenant_id, project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产组织';

CREATE TABLE rel_release_group_member (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    project_member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_name VARCHAR(128) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_group_member (tenant_id, group_id, project_member_id, deleted),
    KEY idx_rel_group_member_project (tenant_id, project_id, group_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产组项目成员';

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 1000, 1, 0, 'directory', '投产管理', 'ReleaseOperationsRoot', '/release-operations', 'LAYOUT', NULL, 'promotion', 1000
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND deleted = 0
      AND (id = 1000 OR route_name = 'ReleaseOperationsRoot' OR route_path = '/release-operations')
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (1001, 1, 1000, 'menu', '投产演练计划', 'ReleaseOperationsDrillPlans', '/release-operations/drill-plans', 'release/operations', 'release-operations:drill:view', 'calendar', 10),
    (1002, 1, 1000, 'menu', '投产时序', 'ReleaseOperationsTimelines', '/release-operations/timelines', 'release/operations', 'release-operations:timeline:view', 'sort', 20),
    (1003, 1, 1000, 'menu', '投产回退时序', 'ReleaseOperationsRollbackTimelines', '/release-operations/rollback-timelines', 'release/operations', 'release-operations:rollback-timeline:view', 'refresh-left', 30),
    (1004, 1, 1000, 'menu', '投产问题分析及跟踪', 'ReleaseOperationsIssues', '/release-operations/issues', 'release/operations', 'release-operations:issue:view', 'warning', 40),
    (1005, 1, 1000, 'menu', '投产组织', 'ReleaseOperationsOrganization', '/release-operations/organization', 'release/operations', 'release-operations:organization:view', 'user', 50)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), route_name = VALUES(route_name),
    route_path = VALUES(route_path), component_path = VALUES(component_path), permission_code = VALUES(permission_code),
    icon = VALUES(icon), sort_no = VALUES(sort_no), deleted = 0;

INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (10011, 1, 1001, 'read', 'release-operations:drill:view', '查看投产演练计划'),
    (10012, 1, 1001, 'manage', 'release-operations:drill:manage', '维护投产演练计划'),
    (10021, 1, 1002, 'read', 'release-operations:timeline:view', '查看投产时序'),
    (10022, 1, 1002, 'manage', 'release-operations:timeline:manage', '维护投产时序'),
    (10031, 1, 1003, 'read', 'release-operations:rollback-timeline:view', '查看投产回退时序'),
    (10032, 1, 1003, 'manage', 'release-operations:rollback-timeline:manage', '维护投产回退时序'),
    (10041, 1, 1004, 'read', 'release-operations:issue:view', '查看投产问题分析及跟踪'),
    (10042, 1, 1004, 'manage', 'release-operations:issue:manage', '维护投产问题分析及跟踪'),
    (10051, 1, 1005, 'read', 'release-operations:organization:view', '查看投产组织'),
    (10052, 1, 1005, 'manage', 'release-operations:organization:manage', '维护投产组织');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 1000 AND 1005 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND id IN (10011,10012,10021,10022,10031,10032,10041,10042,10051,10052) AND status = 1;
