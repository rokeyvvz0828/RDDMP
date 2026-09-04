-- REQ-20260831-057：测试执行与测试缺陷。
-- 执行记录实时引用测试案例；缺陷与执行记录使用平级关联，并在解除或移除后保留业务快照。

CREATE TABLE tm_test_execution_directory (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL,
    cycle_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_tm_execution_directory_name (tenant_id,test_domain,project_id,physical_subsystem_id,cycle_id,parent_id,directory_name,deleted),
    KEY idx_tm_execution_directory_tree (tenant_id,test_domain,project_id,physical_subsystem_id,round_id,cycle_id,parent_id,deleted,sort_no),
    CONSTRAINT fk_tm_execution_directory_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_directory_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_directory_round FOREIGN KEY (round_id) REFERENCES tm_test_round(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_directory_cycle FOREIGN KEY (cycle_id) REFERENCES tm_test_cycle(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_directory_parent FOREIGN KEY (parent_id) REFERENCES tm_test_execution_directory(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试执行目录';

CREATE TABLE tm_test_execution (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL,
    cycle_id BIGINT NOT NULL,
    directory_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    execution_status VARCHAR(24) NOT NULL DEFAULT 'UNEXECUTED',
    actual_result_html MEDIUMTEXT NULL,
    remark_html MEDIUMTEXT NULL,
    executor_id BIGINT NULL,
    executed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_execution_directory_case (tenant_id,directory_id,case_id,deleted),
    KEY idx_tm_execution_list (tenant_id,test_domain,project_id,physical_subsystem_id,round_id,cycle_id,directory_id,execution_status,deleted,updated_at),
    KEY idx_tm_execution_case (tenant_id,case_id,deleted,updated_at),
    CONSTRAINT fk_tm_execution_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_round FOREIGN KEY (round_id) REFERENCES tm_test_round(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_cycle FOREIGN KEY (cycle_id) REFERENCES tm_test_cycle(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_directory FOREIGN KEY (directory_id) REFERENCES tm_test_execution_directory(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_execution_case FOREIGN KEY (case_id) REFERENCES tm_test_case(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试执行记录';

CREATE TABLE tm_test_execution_attachment (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_execution_attachment (tenant_id,execution_id,attachment_id,deleted),
    KEY idx_tm_execution_attachment_list (tenant_id,execution_id,deleted,sort_no),
    CONSTRAINT fk_tm_execution_attachment_execution FOREIGN KEY (execution_id) REFERENCES tm_test_execution(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试执行附件绑定';

CREATE TABLE tm_test_execution_trace (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    detail_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tm_execution_trace (tenant_id,execution_id,created_at),
    CONSTRAINT fk_tm_execution_trace_execution FOREIGN KEY (execution_id) REFERENCES tm_test_execution(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试执行轨迹';

CREATE TABLE tm_test_defect (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    defect_code VARCHAR(128) NOT NULL,
    defect_serial_no INT NOT NULL,
    summary VARCHAR(100) NOT NULL,
    description_html MEDIUMTEXT NOT NULL,
    round_id BIGINT NULL,
    cycle_id BIGINT NULL,
    defect_category VARCHAR(64) NOT NULL,
    severity VARCHAR(64) NOT NULL,
    priority VARCHAR(64) NOT NULL,
    urgency VARCHAR(64) NOT NULL,
    found_version VARCHAR(50) NULL,
    test_environment_code VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RAISED',
    handler_id BIGINT NULL,
    proposer_id BIGINT NOT NULL,
    proposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_defect_code (tenant_id,defect_code),
    UNIQUE KEY uk_tm_defect_system_serial (tenant_id,physical_subsystem_id,defect_serial_no),
    KEY idx_tm_defect_list (tenant_id,test_domain,project_id,physical_subsystem_id,status,deleted,proposed_at),
    KEY idx_tm_defect_handler (tenant_id,handler_id,status,deleted),
    CONSTRAINT fk_tm_defect_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_defect_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_defect_round FOREIGN KEY (round_id) REFERENCES tm_test_round(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_defect_cycle FOREIGN KEY (cycle_id) REFERENCES tm_test_cycle(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试缺陷';

CREATE TABLE tm_test_defect_execution (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    defect_id BIGINT NOT NULL,
    execution_id BIGINT NULL,
    relation_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    snapshot_case_code VARCHAR(160) NULL,
    snapshot_case_name VARCHAR(200) NULL,
    snapshot_scope_code VARCHAR(128) NULL,
    snapshot_scope_name VARCHAR(100) NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_defect_execution_active (tenant_id,defect_id,execution_id,relation_state),
    KEY idx_tm_defect_execution_execution (tenant_id,execution_id,relation_state),
    KEY idx_tm_defect_execution_defect (tenant_id,defect_id,relation_state),
    CONSTRAINT fk_tm_defect_execution_defect FOREIGN KEY (defect_id) REFERENCES tm_test_defect(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_defect_execution_execution FOREIGN KEY (execution_id) REFERENCES tm_test_execution(id) ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缺陷与执行记录关联';

CREATE TABLE tm_test_defect_attachment (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    defect_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    remark VARCHAR(200) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_defect_attachment (tenant_id,defect_id,attachment_id,deleted),
    KEY idx_tm_defect_attachment_list (tenant_id,defect_id,deleted,sort_no),
    CONSTRAINT fk_tm_defect_attachment_defect FOREIGN KEY (defect_id) REFERENCES tm_test_defect(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试缺陷附件绑定';

CREATE TABLE tm_test_defect_trace (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    defect_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    detail_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tm_defect_trace (tenant_id,defect_id,created_at),
    CONSTRAINT fk_tm_defect_trace_defect FOREIGN KEY (defect_id) REFERENCES tm_test_defect(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试缺陷轨迹';

CREATE TEMPORARY TABLE tmp_tm_execution_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL,
    route_name VARCHAR(128) NOT NULL,
    route_path VARCHAR(255) NOT NULL,
    permission_code VARCHAR(128) NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO tmp_tm_execution_menu(id,parent_id,route_name,route_path,permission_code) VALUES
    (964,910,'TestManagementApplicationAssemblyExecution','/test-management/application-assembly/execution','test-management:application-assembly:execution'),
    (965,911,'TestManagementUserTestingExecution','/test-management/user-testing/execution','test-management:user-testing:execution'),
    (966,912,'TestManagementNonFunctionalExecution','/test-management/non-functional/execution','test-management:non-functional:execution'),
    (967,913,'TestManagementSecurityExecution','/test-management/security/execution','test-management:security:execution');

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_type,menu_name,route_name,route_path,component_path,permission_code,icon,sort_no)
SELECT id,1,parent_id,'menu','测试执行',route_name,route_path,'test-management/execution',permission_code,'video-play',45
FROM tmp_tm_execution_menu
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=1 AND m.deleted=0 AND (m.id=tmp_tm_execution_menu.id OR m.route_name=tmp_tm_execution_menu.route_name));

INSERT IGNORE INTO sys_menu_permission(id,tenant_id,menu_id,action_code,permission_code,permission_name)
SELECT m.id*10+a.seq,1,m.id,a.action_code,CASE WHEN a.action_code='read' THEN m.permission_code ELSE CONCAT(m.permission_code,':',a.action_code) END,a.permission_name
FROM sys_menu m JOIN tmp_tm_execution_menu t ON t.id=m.id
CROSS JOIN (SELECT 1 seq,'read' action_code,'查看' permission_name UNION ALL SELECT 2,'create','新增' UNION ALL SELECT 3,'update','修改' UNION ALL SELECT 4,'delete','删除' UNION ALL SELECT 5,'import','导入' UNION ALL SELECT 6,'export','导出') a
WHERE m.tenant_id=1 AND m.deleted=0;

INSERT IGNORE INTO sys_menu_permission(id,tenant_id,menu_id,action_code,permission_code,permission_name)
SELECT m.id*10+a.seq,1,m.id,a.action_code,CONCAT(m.permission_code,':',a.action_code),a.permission_name
FROM sys_menu m JOIN (SELECT 924 id UNION ALL SELECT 931 UNION ALL SELECT 938 UNION ALL SELECT 945) d ON d.id=m.id
CROSS JOIN (SELECT 5 seq,'export' action_code,'导出' permission_name UNION ALL SELECT 6,'restore','恢复' UNION ALL SELECT 7,'associate','关联执行案例' UNION ALL SELECT 8,'transition','状态流转') a
WHERE m.tenant_id=1 AND m.deleted=0;

INSERT IGNORE INTO sys_role_menu(role_id,menu_id,tenant_id)
SELECT 1,id,1 FROM tmp_tm_execution_menu;
INSERT IGNORE INTO sys_role_permission(role_id,permission_id,tenant_id)
SELECT 1,p.id,1 FROM sys_menu_permission p WHERE p.tenant_id=1 AND p.menu_id IN (964,965,966,967,924,931,938,945) AND p.status=1;

DROP TEMPORARY TABLE tmp_tm_execution_menu;
