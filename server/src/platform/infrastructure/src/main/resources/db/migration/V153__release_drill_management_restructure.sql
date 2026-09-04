-- REQ-20260903-059：投产演练管理模型重构。只追加，不修改历史迁移。

CREATE TABLE rel_release_plan (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    plan_code VARCHAR(64) NOT NULL,
    description VARCHAR(2000) NULL,
    version_no VARCHAR(64) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_plan_name (tenant_id, project_id, plan_name, deleted),
    UNIQUE KEY uk_rel_plan_code (tenant_id, project_id, plan_code, deleted),
    KEY idx_rel_plan_project (tenant_id, project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产方案';

CREATE TABLE rel_release_plan_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    item_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/ROLLBACK',
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
    UNIQUE KEY uk_rel_plan_item_seq (tenant_id, plan_id, item_type, seq_no, deleted),
    KEY idx_rel_plan_item_project (tenant_id, project_id, plan_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产方案步骤';

CREATE TABLE rel_release_drill_environment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    environment_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    carry_data_line_environment VARCHAR(2000) NULL,
    infrastructure_deployment VARCHAR(2000) NULL,
    hardware_check VARCHAR(2000) NULL,
    network_opening VARCHAR(2000) NULL,
    middleware_check VARCHAR(2000) NULL,
    component_check VARCHAR(2000) NULL,
    database_check VARCHAR(2000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_drill_environment_name (tenant_id, project_id, environment_name, deleted),
    KEY idx_rel_drill_environment_project (tenant_id, project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产演练环境';

CREATE TABLE rel_release_drill_step (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    drill_round_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    owner_id BIGINT NULL,
    owner_name VARCHAR(128) NULL,
    planned_start DATETIME(6) NULL,
    planned_end DATETIME(6) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    result_content VARCHAR(2000) NULL,
    description VARCHAR(2000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rel_drill_step_seq (tenant_id, drill_round_id, seq_no, deleted),
    KEY idx_rel_drill_step_project (tenant_id, project_id, drill_round_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产演练步骤';

ALTER TABLE rel_release_drill_round
    MODIFY COLUMN drill_plan_id BIGINT NULL,
    ADD COLUMN release_plan_id BIGINT NULL AFTER drill_plan_id,
    ADD COLUMN environment_id BIGINT NULL AFTER release_plan_id,
    ADD KEY idx_rel_drill_round_plan (tenant_id, project_id, release_plan_id, environment_id, deleted);

ALTER TABLE rel_release_issue
    ADD COLUMN drill_round_id BIGINT NULL AFTER project_id,
    ADD KEY idx_rel_issue_drill_round (tenant_id, project_id, drill_round_id, deleted);

-- 将历史单方案及项目级正向/回退时序转换为默认方案和默认环境。
INSERT INTO rel_release_plan
    (id, tenant_id, project_id, plan_name, plan_code, description, version_no, status, created_by, updated_by)
SELECT p.id + 100000000, p.tenant_id, p.project_id, CONCAT('默认投产方案-', p.project_id), CONCAT('LEGACY-', p.id),
       COALESCE(p.scenario_content, '历史投产演练方案'), NULL, 'DRAFT', p.created_by, p.updated_by
FROM rel_release_drill_plan p
WHERE p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_plan n WHERE n.id = p.id + 100000000);

INSERT INTO rel_release_drill_environment
    (id, tenant_id, project_id, environment_name, description, carry_data_line_environment, infrastructure_deployment,
     hardware_check, network_opening, middleware_check, component_check, database_check, created_by, updated_by)
SELECT p.id + 200000000, p.tenant_id, p.project_id, CONCAT('默认演练环境-', p.project_id), '由历史演练环境说明回填',
       COALESCE(p.environment_content, '沿用数据线专项环境'), '沿用历史环境搭建说明', '待检查', '待检查', '待检查', '待检查', '待检查', p.created_by, p.updated_by
FROM rel_release_drill_plan p
WHERE p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_drill_environment n WHERE n.id = p.id + 200000000);

INSERT INTO rel_release_plan_item
    (id, tenant_id, project_id, plan_id, item_type, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, created_by, updated_by)
SELECT i.id + 300000000, i.tenant_id, i.project_id, p.id + 100000000, t.timeline_type, i.seq_no, i.item_name,
       i.planned_start, i.planned_end, i.owner_id, i.owner_name, i.status, i.description, i.created_by, i.updated_by
FROM rel_release_timeline_item i
JOIN rel_release_timeline t ON t.id = i.timeline_id AND t.tenant_id = i.tenant_id AND t.project_id = i.project_id AND t.deleted = 0
JOIN rel_release_drill_plan p ON p.tenant_id = t.tenant_id AND p.project_id = t.project_id AND p.deleted = 0
WHERE i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rel_release_plan_item n WHERE n.id = i.id + 300000000);

UPDATE rel_release_drill_round r
JOIN rel_release_drill_plan p ON p.id = r.drill_plan_id AND p.tenant_id = r.tenant_id AND p.project_id = r.project_id
SET r.release_plan_id = p.id + 100000000, r.environment_id = p.id + 200000000
WHERE r.release_plan_id IS NULL AND p.deleted = 0;

UPDATE rel_release_issue i
JOIN rel_release_drill_round r ON r.project_id = i.project_id AND r.tenant_id = i.tenant_id AND r.deleted = 0
SET i.drill_round_id = r.id
WHERE i.drill_round_id IS NULL
  AND r.round_no = (SELECT MIN(r2.round_no) FROM rel_release_drill_round r2 WHERE r2.project_id = i.project_id AND r2.tenant_id = i.tenant_id AND r2.deleted = 0);

-- 菜单变更：保留投产演练计划、问题和组织，新增环境与投产演练，逻辑下线两条独立时序菜单。
UPDATE sys_menu SET deleted = 1 WHERE tenant_id = 1 AND id IN (1002, 1003);
UPDATE sys_menu_permission SET status = 0
WHERE tenant_id = 1 AND id IN (10021, 10022, 10031, 10032);
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
VALUES
    (1006, 1, 1000, 'menu', '投产演练环境', 'ReleaseOperationsEnvironments', '/release-operations/environments', 'release/operations', 'release-operations:environment:view', 'setting', 20),
    (1007, 1, 1000, 'menu', '投产演练', 'ReleaseOperationsDrills', '/release-operations/drills', 'release/operations', 'release-operations:drill:view', 'video-play', 30)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), route_name = VALUES(route_name), route_path = VALUES(route_path),
    component_path = VALUES(component_path), permission_code = VALUES(permission_code), icon = VALUES(icon), sort_no = VALUES(sort_no), deleted = 0;

UPDATE sys_menu SET menu_name = '投产演练计划', route_name = 'ReleaseOperationsDrillPlans', permission_code = 'release-operations:plan:view', sort_no = 10, deleted = 0
WHERE tenant_id = 1 AND id = 1001;
UPDATE sys_menu_permission SET permission_code = 'release-operations:plan:view', permission_name = '查看投产演练计划' WHERE tenant_id = 1 AND id = 10011;
UPDATE sys_menu_permission SET permission_code = 'release-operations:plan:manage', permission_name = '维护投产演练计划' WHERE tenant_id = 1 AND id = 10012;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name) VALUES
    (10061, 1, 1006, 'read', 'release-operations:environment:view', '查看投产演练环境'),
    (10062, 1, 1006, 'manage', 'release-operations:environment:manage', '维护投产演练环境'),
    (10071, 1, 1007, 'read', 'release-operations:drill:view', '查看投产演练'),
    (10072, 1, 1007, 'manage', 'release-operations:drill:manage', '维护投产演练');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (1, 1006, 1), (1, 1007, 1);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id IN (10061, 10062, 10071, 10072) AND status = 1;
