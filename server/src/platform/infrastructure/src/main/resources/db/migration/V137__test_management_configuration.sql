-- REQ-20260831-057：测试管理四大测试大类的管理配置。
-- 项目、用户及物理子系统只读引用；当前候选规则为同租户全部未删除物理子系统。

CREATE TABLE tm_test_participating_system (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_participating_system (tenant_id, test_domain, project_id, physical_subsystem_id, deleted),
    KEY idx_tm_participating_system_list (tenant_id, test_domain, project_id, enabled, deleted),
    CONSTRAINT fk_tm_participating_system_project
        FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_participating_system_physical
        FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理参测系统映射';

CREATE TABLE tm_test_system_role (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_system_role (tenant_id, test_domain, project_id, physical_subsystem_id, user_id, role_code, deleted),
    KEY idx_tm_test_system_role_list (tenant_id, test_domain, project_id, physical_subsystem_id, deleted),
    KEY idx_tm_test_system_role_user (tenant_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理系统角色分配';

CREATE TABLE tm_test_round (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    round_code VARCHAR(64) NOT NULL,
    round_name VARCHAR(128) NOT NULL,
    planned_start_date DATE NULL,
    planned_end_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_round_code (tenant_id, test_domain, project_id, round_code, deleted),
    UNIQUE KEY uk_tm_test_round_name (tenant_id, test_domain, project_id, round_name, deleted),
    KEY idx_tm_test_round_list (tenant_id, test_domain, project_id, status, sort_no, deleted),
    CONSTRAINT fk_tm_test_round_project FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理测试轮次';

CREATE TABLE tm_test_cycle (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL,
    cycle_code VARCHAR(64) NOT NULL,
    cycle_name VARCHAR(128) NOT NULL,
    planned_start_date DATE NULL,
    planned_end_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_cycle_code (tenant_id, round_id, cycle_code, deleted),
    UNIQUE KEY uk_tm_test_cycle_name (tenant_id, round_id, cycle_name, deleted),
    KEY idx_tm_test_cycle_list (tenant_id, round_id, status, sort_no, deleted),
    CONSTRAINT fk_tm_test_cycle_round FOREIGN KEY (round_id) REFERENCES tm_test_round (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理测试周期';

CREATE TABLE tm_test_dictionary (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    dictionary_code VARCHAR(64) NOT NULL,
    dictionary_name VARCHAR(128) NOT NULL,
    source_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_dictionary (tenant_id, test_domain, project_id, dictionary_code, deleted),
    KEY idx_tm_test_dictionary_list (tenant_id, test_domain, project_id, enabled, deleted),
    CONSTRAINT fk_tm_test_dictionary_project FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理本地字典';

CREATE TABLE tm_test_dictionary_option (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    dictionary_id BIGINT NOT NULL,
    option_code VARCHAR(64) NOT NULL,
    option_name VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_dictionary_option (tenant_id, dictionary_id, option_code, deleted),
    KEY idx_tm_test_dictionary_option_list (tenant_id, dictionary_id, enabled, sort_no, deleted),
    CONSTRAINT fk_tm_test_dictionary_option_dictionary FOREIGN KEY (dictionary_id) REFERENCES tm_test_dictionary (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理字典选项';

CREATE TABLE tm_test_configuration_audit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    detail_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tm_test_configuration_audit_entity (tenant_id, test_domain, project_id, entity_type, entity_id, created_at),
    KEY idx_tm_test_configuration_audit_operator (tenant_id, operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理配置审计';

CREATE TEMPORARY TABLE tmp_tm_configuration_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL,
    route_name VARCHAR(128) NOT NULL,
    route_path VARCHAR(255) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO tmp_tm_configuration_menu (id, parent_id, route_name, route_path, permission_code, sort_no) VALUES
    (960, 910, 'TestManagementApplicationAssemblyConfiguration', '/test-management/application-assembly/configuration', 'test-management:application-assembly:configuration', 90),
    (961, 911, 'TestManagementUserTestingConfiguration', '/test-management/user-testing/configuration', 'test-management:user-testing:configuration', 90),
    (962, 912, 'TestManagementNonFunctionalConfiguration', '/test-management/non-functional/configuration', 'test-management:non-functional:configuration', 90),
    (963, 913, 'TestManagementSecurityConfiguration', '/test-management/security/configuration', 'test-management:security:configuration', 90);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT t.id, 1, t.parent_id, 'menu', '管理配置', t.route_name, t.route_path,
       'test-management/configuration', t.permission_code, 'setting', t.sort_no
FROM tmp_tm_configuration_menu t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=1 AND m.deleted=0 AND (m.id=t.id OR m.route_name=t.route_name));

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + a.seq, 1, m.id, a.action_code,
       CASE WHEN a.action_code='read' THEN m.permission_code ELSE CONCAT(m.permission_code, ':', a.action_code) END,
       a.permission_name
FROM sys_menu m
JOIN tmp_tm_configuration_menu t ON t.id=m.id
CROSS JOIN (
    SELECT 1 seq, 'read' action_code, '查看' permission_name
    UNION ALL SELECT 2, 'create', '新增'
    UNION ALL SELECT 3, 'update', '修改'
    UNION ALL SELECT 4, 'delete', '删除'
    UNION ALL SELECT 5, 'import', '导入'
) a
WHERE m.tenant_id=1 AND m.deleted=0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM tmp_tm_configuration_menu;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, p.id, 1 FROM sys_menu_permission p JOIN tmp_tm_configuration_menu t ON t.id=p.menu_id
WHERE p.tenant_id=1 AND p.status=1;

DROP TEMPORARY TABLE tmp_tm_configuration_menu;
