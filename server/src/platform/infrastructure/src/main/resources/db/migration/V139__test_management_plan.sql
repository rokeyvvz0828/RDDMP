-- REQ-20260831-057：测试方案；逻辑方案与版本分离，附件由平台模块拥有。
CREATE TABLE tm_test_plan_special_node (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    node_name VARCHAR(100) NOT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), KEY idx_tm_plan_special_node (tenant_id,test_domain,project_id,deleted,node_name),
    CONSTRAINT fk_tm_plan_special_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试方案专项节点';

CREATE TABLE tm_test_plan (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    node_type VARCHAR(16) NOT NULL, physical_subsystem_id BIGINT NULL, special_node_id BIGINT NULL,
    plan_name VARCHAR(100) NOT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0, deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id), KEY idx_tm_plan_node (tenant_id,test_domain,project_id,node_type,physical_subsystem_id,special_node_id,deleted,updated_at),
    KEY idx_tm_plan_name (tenant_id,test_domain,project_id,node_type,physical_subsystem_id,special_node_id,plan_name,deleted),
    CONSTRAINT fk_tm_plan_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_plan_special_node FOREIGN KEY (special_node_id) REFERENCES tm_test_plan_special_node(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理逻辑测试方案';

CREATE TABLE tm_test_plan_version (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, plan_id BIGINT NOT NULL, version_no INT NOT NULL,
    version_note VARCHAR(200) NOT NULL, attachment_id BIGINT NOT NULL, file_name VARCHAR(255) NOT NULL,
    file_extension VARCHAR(16) NOT NULL, file_size BIGINT NOT NULL, uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_tm_plan_version (tenant_id,plan_id,version_no,deleted),
    KEY idx_tm_plan_version_current (tenant_id,plan_id,deleted,version_no),
    CONSTRAINT fk_tm_plan_version_plan FOREIGN KEY (plan_id) REFERENCES tm_test_plan(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试方案版本';

CREATE TABLE tm_test_plan_audit (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    plan_id BIGINT NULL, version_id BIGINT NULL, action_code VARCHAR(32) NOT NULL, operator_id BIGINT NOT NULL,
    detail_json JSON NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_tm_plan_audit (tenant_id,test_domain,project_id,plan_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试方案操作审计';

INSERT IGNORE INTO sys_menu_permission (id,tenant_id,menu_id,action_code,permission_code,permission_name)
SELECT m.id*10+a.seq,1,m.id,a.action_code,CONCAT(m.permission_code,':',a.action_code),a.permission_name
FROM sys_menu m CROSS JOIN (
    SELECT 2 seq,'create' action_code,'上传方案' permission_name
    UNION ALL SELECT 3,'update','维护方案'
    UNION ALL SELECT 4,'delete','删除方案'
) a
WHERE m.tenant_id=1 AND m.id IN (921,928,935,942) AND m.deleted=0;

INSERT IGNORE INTO sys_role_permission (role_id,permission_id,tenant_id)
SELECT 1,p.id,1 FROM sys_menu_permission p
WHERE p.tenant_id=1 AND p.menu_id IN (921,928,935,942) AND p.status=1;
