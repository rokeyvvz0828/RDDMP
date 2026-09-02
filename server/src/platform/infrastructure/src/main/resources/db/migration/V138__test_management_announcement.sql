-- REQ-20260831-057：测试管理项目级公告板；只追加，不修改 V123。
CREATE TABLE tm_test_announcement (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL, content_html MEDIUMTEXT NOT NULL, pinned TINYINT NOT NULL DEFAULT 0, pinned_at TIMESTAMP NULL,
    published_by BIGINT NOT NULL, published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_by BIGINT NULL, last_edited_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), KEY idx_tm_announcement_current (tenant_id,test_domain,project_id,deleted,pinned,pinned_at,published_at),
    CONSTRAINT fk_tm_announcement_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理公告';

CREATE TABLE tm_test_announcement_attachment (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, announcement_id BIGINT NOT NULL, attachment_id BIGINT NOT NULL,
    attachment_type VARCHAR(16) NOT NULL DEFAULT 'FILE', sort_no INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_tm_announcement_attachment (tenant_id,announcement_id,attachment_id,deleted),
    KEY idx_tm_announcement_attachment (tenant_id,announcement_id,attachment_type,sort_no,deleted),
    CONSTRAINT fk_tm_announcement_attachment_announcement FOREIGN KEY (announcement_id) REFERENCES tm_test_announcement(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理公告附件映射';

CREATE TABLE tm_test_announcement_audit (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, test_domain VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL, action_code VARCHAR(32) NOT NULL, operator_id BIGINT NOT NULL, detail_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
    KEY idx_tm_announcement_audit (tenant_id,test_domain,project_id,announcement_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试管理公告操作审计';

INSERT IGNORE INTO sys_menu_permission (id,tenant_id,menu_id,action_code,permission_code,permission_name)
SELECT m.id*10+a.seq,1,m.id,a.action_code,CONCAT(m.permission_code,':',a.action_code),a.permission_name
FROM sys_menu m CROSS JOIN (SELECT 2 seq,'create' action_code,'发布公告' permission_name UNION ALL SELECT 3,'update','编辑公告' UNION ALL SELECT 4,'delete','删除公告') a
WHERE m.tenant_id=1 AND m.id IN (920,927,934,941) AND m.deleted=0;
INSERT IGNORE INTO sys_role_permission (role_id,permission_id,tenant_id)
SELECT 1,p.id,1 FROM sys_menu_permission p WHERE p.tenant_id=1 AND p.menu_id IN (920,927,934,941) AND p.status=1;
