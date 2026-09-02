-- REQ-20260831-057：测试报告与分析统计。报告事实快照不可变，章节补充按版本独立维护。

CREATE TABLE tm_test_report (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NULL, report_name VARCHAR(100) NOT NULL, report_type VARCHAR(16) NOT NULL,
    round_id BIGINT NULL, cycle_id BIGINT NULL, source_type VARCHAR(16) NOT NULL DEFAULT 'LIVE',
    selected_sections JSON NOT NULL, current_version_no INT NOT NULL DEFAULT 0, generated_by BIGINT NULL,
    generated_at TIMESTAMP NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_tm_report_list (tenant_id,test_domain,project_id,physical_subsystem_id,generated_at),
    CONSTRAINT fk_tm_report_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_report_system FOREIGN KEY (physical_subsystem_id) REFERENCES arch_physical_subsystem(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_report_round FOREIGN KEY (round_id) REFERENCES tm_test_round(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tm_report_cycle FOREIGN KEY (cycle_id) REFERENCES tm_test_cycle(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试报告对象';

CREATE TABLE tm_test_report_version (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, report_id BIGINT NOT NULL, version_no INT NOT NULL,
    snapshot_json JSON NOT NULL, generated_by BIGINT NOT NULL, generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_tm_report_version (tenant_id,report_id,version_no),
    KEY idx_tm_report_version_history (tenant_id,report_id,generated_at),
    CONSTRAINT fk_tm_report_version_report FOREIGN KEY (report_id) REFERENCES tm_test_report(id) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试报告不可变版本快照';

CREATE TABLE tm_test_report_supplement (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, report_version_id BIGINT NOT NULL, chapter_code VARCHAR(32) NOT NULL,
    content_html MEDIUMTEXT NULL, updated_by BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_tm_report_supplement (tenant_id,report_version_id,chapter_code),
    CONSTRAINT fk_tm_report_supplement_version FOREIGN KEY (report_version_id) REFERENCES tm_test_report_version(id) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告章节人工补充';

CREATE TABLE tm_test_report_trace (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL, version_id BIGINT NULL, action_code VARCHAR(32) NOT NULL, operator_id BIGINT NOT NULL,
    detail_json JSON NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_tm_report_trace (tenant_id,report_id,created_at),
    CONSTRAINT fk_tm_report_trace_report FOREIGN KEY (report_id) REFERENCES tm_test_report(id) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试报告操作轨迹';

CREATE TABLE tm_test_analytics_report (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    report_name VARCHAR(50) NOT NULL, report_key VARCHAR(64) NOT NULL, owner_id BIGINT NOT NULL,
    shared TINYINT NOT NULL DEFAULT 0, published_by BIGINT NULL, config_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_tm_analytics_report_name (tenant_id,test_domain,project_id,owner_id,report_name),
    KEY idx_tm_analytics_report_list (tenant_id,test_domain,project_id,shared,owner_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析统计自定义报表';

CREATE TABLE tm_test_analytics_snapshot (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL, report_key VARCHAR(64) NOT NULL, snapshot_json JSON NOT NULL,
    archived_by BIGINT NOT NULL, archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_tm_analytics_snapshot (tenant_id,test_domain,project_id,round_id,report_key),
    KEY idx_tm_analytics_snapshot_list (tenant_id,test_domain,project_id,round_id,archived_at),
    CONSTRAINT fk_tm_analytics_snapshot_round FOREIGN KEY (round_id) REFERENCES tm_test_round(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮次分析统计快照';

CREATE TABLE tm_test_analytics_trace (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    analytics_report_id BIGINT NULL, action_code VARCHAR(32) NOT NULL, operator_id BIGINT NOT NULL, detail_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id),
    KEY idx_tm_analytics_trace (tenant_id,test_domain,project_id,analytics_report_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析统计操作轨迹';

CREATE TEMPORARY TABLE tmp_tm_report_menu (id BIGINT PRIMARY KEY,parent_id BIGINT NOT NULL,route_name VARCHAR(128) NOT NULL,route_path VARCHAR(255) NOT NULL,permission_code VARCHAR(128) NOT NULL) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO tmp_tm_report_menu(id,parent_id,route_name,route_path,permission_code) VALUES
 (968,910,'TestManagementApplicationAssemblyReports','/test-management/application-assembly/reports','test-management:application-assembly:reports'),
 (969,911,'TestManagementUserTestingReports','/test-management/user-testing/reports','test-management:user-testing:reports'),
 (970,912,'TestManagementNonFunctionalReports','/test-management/non-functional/reports','test-management:non-functional:reports'),
 (971,913,'TestManagementSecurityReports','/test-management/security/reports','test-management:security:reports'),
 (972,910,'TestManagementApplicationAssemblyAnalytics','/test-management/application-assembly/analytics','test-management:application-assembly:analytics'),
 (973,911,'TestManagementUserTestingAnalytics','/test-management/user-testing/analytics','test-management:user-testing:analytics'),
 (974,912,'TestManagementNonFunctionalAnalytics','/test-management/non-functional/analytics','test-management:non-functional:analytics'),
 (975,913,'TestManagementSecurityAnalytics','/test-management/security/analytics','test-management:security:analytics');
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_type,menu_name,route_name,route_path,component_path,permission_code,icon,sort_no)
SELECT id,1,parent_id,'menu',CASE WHEN id<972 THEN '测试报告' ELSE '分析统计' END,route_name,route_path,CASE WHEN id<972 THEN 'test-management/report' ELSE 'test-management/analytics' END,permission_code,CASE WHEN id<972 THEN 'document' ELSE 'data-analysis' END,CASE WHEN id<972 THEN 60 ELSE 70 END FROM tmp_tm_report_menu
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=1 AND m.deleted=0 AND (m.id=tmp_tm_report_menu.id OR m.route_name=tmp_tm_report_menu.route_name));
INSERT IGNORE INTO sys_menu_permission(id,tenant_id,menu_id,action_code,permission_code,permission_name)
SELECT m.id*10+a.seq,1,m.id,a.action_code,CASE WHEN a.action_code='read' THEN m.permission_code ELSE CONCAT(m.permission_code,':',a.action_code) END,a.permission_name FROM sys_menu m JOIN tmp_tm_report_menu t ON t.id=m.id
CROSS JOIN (SELECT 1 seq,'read' action_code,'查看' permission_name UNION ALL SELECT 2,'create','生成' UNION ALL SELECT 3,'update','修改' UNION ALL SELECT 4,'delete','删除' UNION ALL SELECT 5,'export','导出' UNION ALL SELECT 6,'publish','发布共享' UNION ALL SELECT 7,'archive','归档快照') a WHERE m.tenant_id=1 AND m.deleted=0;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id,tenant_id) SELECT 1,m.id,1 FROM sys_menu m JOIN tmp_tm_report_menu t ON t.id=m.id WHERE m.tenant_id=1 AND m.deleted=0;
INSERT IGNORE INTO sys_role_permission(role_id,permission_id,tenant_id) SELECT 1,p.id,1 FROM sys_menu_permission p JOIN tmp_tm_report_menu t ON t.id=p.menu_id WHERE p.tenant_id=1 AND p.status=1;
DROP TEMPORARY TABLE tmp_tm_report_menu;
