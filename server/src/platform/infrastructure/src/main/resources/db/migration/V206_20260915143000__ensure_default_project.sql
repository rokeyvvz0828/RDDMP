-- Prerequisite backfill before V207/V209. Keep all published migration checksums.
-- Run with application writers stopped. Completed V209 databases are unchanged.
-- All permanent inserts are transactional; never restore deleted projects or users.
DROP PROCEDURE IF EXISTS pm_init_default_project_20260915143000;
DELIMITER $$
CREATE PROCEDURE pm_init_default_project_20260915143000()
main: BEGIN
    DECLARE project_base BIGINT;
    DECLARE role_base BIGINT;
    DECLARE member_base BIGINT;
    DECLARE stage_base BIGINT;
    DECLARE new_count BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_pm_default_candidates;
        DROP TEMPORARY TABLE IF EXISTS tmp_pm_default_tenants;
        RESIGNAL;
    END;

    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '209' AND success = 1) THEN
        LEAVE main;
    END IF;

    CREATE TEMPORARY TABLE tmp_pm_default_tenants (tenant_id BIGINT PRIMARY KEY);
    INSERT INTO tmp_pm_default_tenants (tenant_id)
    SELECT 1
    UNION SELECT tenant_id FROM arch_physical_subsystem
    UNION SELECT tenant_id FROM arch_deployment_unit
    UNION SELECT tenant_id FROM arch_environment
    UNION SELECT tenant_id FROM arch_resource_request
    UNION SELECT tenant_id FROM arch_environment_instance
    UNION SELECT tenant_id FROM arch_setup_plan
    UNION SELECT tenant_id FROM arch_network_work_order
    UNION SELECT tenant_id FROM arch_network_zone
    UNION SELECT tenant_id FROM arch_external_network_address
    UNION SELECT tenant_id FROM arch_network_access_application
    UNION SELECT tenant_id FROM arch_network_access_relation
    UNION SELECT tenant_id FROM arch_network_access_exemption_rule
    UNION SELECT tenant_id FROM arch_decision_matter;

    IF EXISTS (
        SELECT t.tenant_id FROM tmp_pm_default_tenants t
        JOIN pm_project p ON p.tenant_id = t.tenant_id
            AND p.project_code = 'RDDMP-PLATFORM' AND p.deleted = 0
        GROUP BY t.tenant_id HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default project initialization: ambiguous active default project';
    END IF;

    CREATE TEMPORARY TABLE tmp_pm_default_candidates (
        tenant_id BIGINT PRIMARY KEY,
        owner_id BIGINT NULL,
        project_id BIGINT NULL,
        role_id BIGINT NULL,
        member_id BIGINT NULL
    );
    INSERT INTO tmp_pm_default_candidates (tenant_id)
    SELECT t.tenant_id FROM tmp_pm_default_tenants t
    WHERE NOT EXISTS (
        SELECT 1 FROM pm_project p WHERE p.tenant_id = t.tenant_id
            AND p.project_code = 'RDDMP-PLATFORM' AND p.deleted = 0
    );

    -- Preflight every tenant before writing any permanent row.
    IF EXISTS (
        SELECT 1 FROM tmp_pm_default_candidates c
        JOIN pm_project p ON p.tenant_id = c.tenant_id
            AND p.project_code = 'RDDMP-PLATFORM' AND p.deleted <> 0
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default project initialization: deleted default project requires owner review';
    END IF;
    IF EXISTS (
        SELECT 1 FROM tmp_pm_default_candidates c
        WHERE (SELECT COUNT(*) FROM sys_user u WHERE u.tenant_id = c.tenant_id
            AND u.username = 'admin' AND u.status = 1 AND u.deleted = 0) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default project initialization requires one active same-tenant admin';
    END IF;
    UPDATE tmp_pm_default_candidates c JOIN sys_user u ON u.tenant_id = c.tenant_id
        AND u.username = 'admin' AND u.status = 1 AND u.deleted = 0
    SET c.owner_id = u.id;

    SELECT COUNT(*) INTO new_count FROM tmp_pm_default_candidates;
    SELECT GREATEST(COALESCE(MAX(id), 0), 0) INTO project_base FROM pm_project;
    SELECT GREATEST(COALESCE(MAX(id), 0), 0) INTO role_base FROM pm_project_role;
    SELECT GREATEST(COALESCE(MAX(id), 0), 0) INTO member_base FROM pm_project_member;
    SELECT GREATEST(COALESCE(MAX(id), 0), 0) INTO stage_base FROM pm_project_stage;
    IF project_base > 9223372036854775807 - new_count
        OR role_base > 9223372036854775807 - new_count
        OR member_base > 9223372036854775807 - new_count
        OR stage_base > 9223372036854775807 - new_count * 7 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default project initialization: insufficient identifier space';
    END IF;

    START TRANSACTION;
    INSERT INTO pm_project (id, tenant_id, project_code, project_name, description,
                            status, creation_type, owner_id, created_by)
    SELECT project_base + ROW_NUMBER() OVER (ORDER BY tenant_id), tenant_id,
           'RDDMP-PLATFORM', '平台能力升级项目', '历史架构数据默认归属项目',
           'RUNNING', 'NEW', owner_id, owner_id
    FROM tmp_pm_default_candidates;

    UPDATE tmp_pm_default_candidates c JOIN pm_project p ON p.tenant_id = c.tenant_id
        AND p.project_code = 'RDDMP-PLATFORM' AND p.deleted = 0
    SET c.project_id = p.id;

    INSERT INTO pm_project_role (id, tenant_id, project_id, role_code, role_name, description)
    SELECT role_base + ROW_NUMBER() OVER (ORDER BY tenant_id), tenant_id, project_id,
           'PM', '项目负责人', '默认项目初始化的项目负责人角色'
    FROM tmp_pm_default_candidates;
    INSERT INTO pm_project_member (id, tenant_id, project_id, user_id)
    SELECT member_base + ROW_NUMBER() OVER (ORDER BY tenant_id), tenant_id, project_id, owner_id
    FROM tmp_pm_default_candidates;

    UPDATE tmp_pm_default_candidates c
    JOIN pm_project_role r ON r.tenant_id = c.tenant_id AND r.project_id = c.project_id
        AND r.role_code = 'PM' AND r.deleted = 0
    JOIN pm_project_member m ON m.tenant_id = c.tenant_id AND m.project_id = c.project_id
        AND m.user_id = c.owner_id AND m.deleted = 0
    SET c.role_id = r.id, c.member_id = m.id;
    INSERT INTO pm_project_member_role (tenant_id, member_id, role_id)
    SELECT tenant_id, member_id, role_id FROM tmp_pm_default_candidates;

    INSERT INTO pm_project_stage (id, tenant_id, project_id, stage_code, stage_name, sort_no)
    SELECT stage_base + ROW_NUMBER() OVER (ORDER BY c.tenant_id, s.sort_no),
           c.tenant_id, c.project_id, s.stage_code, s.stage_name, s.sort_no
    FROM tmp_pm_default_candidates c
    CROSS JOIN (
        SELECT 'PLAN_INITIATION' AS stage_code, '立项' AS stage_name, 1 AS sort_no
        UNION ALL SELECT 'PLAN_REQUIREMENT', '需求', 2
        UNION ALL SELECT 'PLAN_DESIGN_DEVELOPMENT', '设计开发', 3
        UNION ALL SELECT 'PLAN_DATA_MIGRATION', '数据迁移', 4
        UNION ALL SELECT 'PLAN_TEST_ACCEPTANCE', '测试与验收', 5
        UNION ALL SELECT 'PLAN_TRAINING_PRODUCTION_REHEARSAL', '培训及投产演练', 6
        UNION ALL SELECT 'PLAN_PRODUCTION_LAUNCH', '投产上线', 7
    ) s;
    COMMIT;

    DROP TEMPORARY TABLE tmp_pm_default_candidates;
    DROP TEMPORARY TABLE tmp_pm_default_tenants;
END$$
DELIMITER ;
CALL pm_init_default_project_20260915143000();
DROP PROCEDURE pm_init_default_project_20260915143000;
