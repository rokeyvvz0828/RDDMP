-- 问题清单独立存储：从 dm_asset(asset_type='ISSUE') 切换到 dm_issue。
-- 历史 ISSUE 数据按已确认决策不迁移、不归档；迁移只清理旧关系和旧行。
-- dm_asset 的 structured_data、报告字段、附件字段和通用审计列保持不变。

CREATE TABLE IF NOT EXISTS dm_issue (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT NOT NULL,
    issue_code VARCHAR(96) NOT NULL,
    issue_name VARCHAR(255) NOT NULL,
    granularity VARCHAR(16) NULL,
    system_code VARCHAR(96) NULL,
    system_name VARCHAR(160) NULL,
    issue_source VARCHAR(32) NULL,
    defect_type VARCHAR(32) NULL,
    issue_description TEXT NULL,
    solution TEXT NULL,
    meeting_conclusion TEXT NULL,
    processing_steps TEXT NULL,
    business_scenario VARCHAR(500) NULL,
    handler VARCHAR(160) NULL,
    responsible_party VARCHAR(160) NULL,
    keywords VARCHAR(500) NULL,
    frequency VARCHAR(16) NULL,
    owner_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    deleted_by BIGINT NULL,
    deleted_at TIMESTAMP NULL,
    UNIQUE KEY uk_dm_issue_code (tenant_id, project_id, issue_code, deleted),
    KEY idx_dm_issue_query (tenant_id, project_id, deleted, updated_at),
    KEY idx_dm_issue_owner (tenant_id, owner_id, deleted),
    KEY idx_dm_issue_filters (tenant_id, project_id, granularity, issue_source, defect_type, frequency, deleted),
    KEY idx_dm_issue_system (tenant_id, project_id, system_code, deleted)
) COMMENT '数据迁移问题清单';

-- 不保留旧问题及其多态关系；非 ISSUE 资产和关系不受影响。
DELETE FROM dm_asset_relation
WHERE source_asset_type = 'ISSUE';

DELETE FROM dm_asset
WHERE asset_type = 'ISSUE';
