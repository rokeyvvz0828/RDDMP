-- REQ-20260831-057：测试范围与测试案例。
-- 两棵目录树独立；所有业务记录由测试大类、项目和参测系统共同隔离。

CREATE TABLE tm_test_scope_directory (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    directory_name VARCHAR(100) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_scope_directory_name (tenant_id, test_domain, project_id, physical_subsystem_id, parent_id, directory_name, deleted),
    KEY idx_tm_scope_directory_tree (tenant_id, test_domain, project_id, physical_subsystem_id, parent_id, deleted, sort_no),
    CONSTRAINT fk_tm_scope_directory_project FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_scope_directory_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_scope_directory_parent FOREIGN KEY (parent_id) REFERENCES tm_test_scope_directory (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试范围目录';

CREATE TABLE tm_test_scope (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    directory_id BIGINT NULL,
    scope_code VARCHAR(128) NOT NULL,
    scope_name VARCHAR(100) NOT NULL,
    leaf_menu VARCHAR(200) NULL,
    function_type VARCHAR(64) NULL,
    change_status VARCHAR(64) NULL,
    importance VARCHAR(64) NULL,
    accounting_flag VARCHAR(64) NULL,
    invalidated TINYINT NOT NULL DEFAULT 0,
    invalidated_by BIGINT NULL,
    invalidated_at TIMESTAMP NULL,
    invalid_reason VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_scope_code (tenant_id, test_domain, project_id, scope_code),
    KEY idx_tm_test_scope_list (tenant_id, test_domain, project_id, physical_subsystem_id, directory_id, invalidated, deleted, updated_at),
    CONSTRAINT fk_tm_test_scope_project FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_test_scope_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_test_scope_directory FOREIGN KEY (directory_id) REFERENCES tm_test_scope_directory (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试范围';

CREATE TABLE tm_test_case_directory (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    directory_name VARCHAR(100) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_case_directory_name (tenant_id, test_domain, project_id, physical_subsystem_id, parent_id, directory_name, deleted),
    KEY idx_tm_case_directory_tree (tenant_id, test_domain, project_id, physical_subsystem_id, parent_id, deleted, sort_no),
    CONSTRAINT fk_tm_case_directory_project FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_case_directory_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_case_directory_parent FOREIGN KEY (parent_id) REFERENCES tm_test_case_directory (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试案例目录';

CREATE TABLE tm_test_case (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    directory_id BIGINT NULL,
    case_code VARCHAR(160) NOT NULL,
    case_serial_no INT NOT NULL,
    case_name VARCHAR(200) NOT NULL,
    case_type VARCHAR(64) NULL,
    test_level VARCHAR(64) NULL,
    priority VARCHAR(64) NULL,
    invalidated TINYINT NOT NULL DEFAULT 0,
    invalidated_by BIGINT NULL,
    invalidated_at TIMESTAMP NULL,
    invalid_reason VARCHAR(500) NULL,
    accounting_result VARCHAR(64) NULL,
    accounting_confirmed TINYINT NOT NULL DEFAULT 0,
    accounting_confirmed_by BIGINT NULL,
    accounting_confirmed_at TIMESTAMP NULL,
    precondition_html MEDIUMTEXT NULL,
    steps_html MEDIUMTEXT NOT NULL,
    expected_result_html MEDIUMTEXT NOT NULL,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_case_code (tenant_id, test_domain, project_id, case_code),
    UNIQUE KEY uk_tm_test_case_serial (tenant_id, scope_id, case_serial_no),
    KEY idx_tm_test_case_list (tenant_id, test_domain, project_id, physical_subsystem_id, scope_id, directory_id, invalidated, deleted, updated_at),
    CONSTRAINT fk_tm_test_case_project FOREIGN KEY (project_id) REFERENCES pm_project (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_test_case_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_test_case_scope FOREIGN KEY (scope_id) REFERENCES tm_test_scope (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_test_case_directory FOREIGN KEY (directory_id) REFERENCES tm_test_case_directory (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试案例';

CREATE TABLE tm_test_case_attachment (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_test_case_attachment (tenant_id, case_id, attachment_id, deleted),
    KEY idx_tm_test_case_attachment_list (tenant_id, case_id, deleted, sort_no),
    CONSTRAINT fk_tm_test_case_attachment_case FOREIGN KEY (case_id) REFERENCES tm_test_case (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试案例附件绑定';

CREATE TABLE tm_test_scope_case_audit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    detail_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tm_scope_case_audit_entity (tenant_id, test_domain, project_id, entity_type, entity_id, created_at),
    KEY idx_tm_scope_case_audit_operator (tenant_id, operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试范围与案例操作审计';

-- V80 已创建查看、新增、修改和删除动作；为范围/案例补充导入导出权限。
INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT m.id * 10 + a.seq, 1, m.id, a.action_code, CONCAT(m.permission_code, ':', a.action_code), a.permission_name
FROM sys_menu m
CROSS JOIN (
    SELECT 5 AS seq, 'import' AS action_code, '导入' AS permission_name
    UNION ALL SELECT 6, 'export', '导出'
) a
WHERE m.tenant_id=1 AND m.id IN (922, 923, 929, 930, 936, 937, 943, 944) AND m.deleted=0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, p.id, 1
FROM sys_menu_permission p
WHERE p.tenant_id=1 AND p.menu_id IN (922, 923, 929, 930, 936, 937, 943, 944) AND p.status=1;
