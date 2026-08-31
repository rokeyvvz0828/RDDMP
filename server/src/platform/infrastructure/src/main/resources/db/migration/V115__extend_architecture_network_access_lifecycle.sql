-- REQ-20260828-055：网络访问判定与关系生命周期。
-- 只追加，不修改 V100-V103；历史关系保留，生命周期从本迁移后通过申请驱动。

ALTER TABLE arch_network_access_application
    DROP CHECK chk_arch_network_access_app_status;

ALTER TABLE arch_network_access_application
    ADD COLUMN action_type VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/MODIFY/RENEW/CLOSE' AFTER applicant_id,
    ADD COLUMN target_relation_id BIGINT NULL COMMENT '修改、续期、关闭的目标关系' AFTER action_type,
    ADD COLUMN validity_type VARCHAR(16) NOT NULL DEFAULT 'LONG_TERM' COMMENT 'LIMITED/LONG_TERM' AFTER valid_until,
    ADD COLUMN current_business_round INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN current_workflow_definition_id BIGINT NULL AFTER current_business_round,
    ADD COLUMN current_workflow_version_id BIGINT NULL AFTER current_workflow_definition_id,
    ADD COLUMN current_workflow_instance_id BIGINT NULL AFTER current_workflow_version_id,
    ADD COLUMN current_payload_digest CHAR(64) NULL AFTER current_workflow_instance_id,
    ADD COLUMN cancellation_requested TINYINT NOT NULL DEFAULT 0 AFTER current_payload_digest,
    ADD KEY idx_arch_network_access_app_target_relation (tenant_id, target_relation_id),
    ADD KEY idx_arch_network_access_app_workflow (tenant_id, current_workflow_instance_id),
    ADD CONSTRAINT fk_arch_network_access_app_target_relation
        FOREIGN KEY (tenant_id, target_relation_id)
        REFERENCES arch_network_access_relation (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT chk_arch_network_access_app_action CHECK (action_type IN ('OPEN', 'MODIFY', 'RENEW', 'CLOSE')),
    ADD CONSTRAINT chk_arch_network_access_app_validity CHECK (validity_type IN ('LIMITED', 'LONG_TERM')),
    ADD CONSTRAINT chk_arch_network_access_app_status CHECK (
        status IN ('DRAFT', 'RETURNED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    ADD CONSTRAINT chk_arch_network_access_app_round CHECK (current_business_round >= 0),
    ADD CONSTRAINT chk_arch_network_access_app_cancel CHECK (cancellation_requested IN (0, 1));

UPDATE arch_network_access_application
SET valid_from = COALESCE(valid_from, created_at, CURRENT_TIMESTAMP)
WHERE valid_from IS NULL;

UPDATE arch_network_access_application
SET valid_from = DATE_SUB(valid_until, INTERVAL 1 SECOND)
WHERE valid_until IS NOT NULL
  AND valid_from >= valid_until;

UPDATE arch_network_access_application
SET validity_type = CASE WHEN valid_until IS NULL THEN 'LONG_TERM' ELSE 'LIMITED' END;

ALTER TABLE arch_network_access_application
    ADD CONSTRAINT chk_arch_network_access_app_validity_dates CHECK (
        (validity_type = 'LONG_TERM' AND valid_from IS NOT NULL AND valid_until IS NULL)
     OR (validity_type = 'LIMITED' AND valid_from IS NOT NULL AND valid_until IS NOT NULL AND valid_until > valid_from)
    );

ALTER TABLE arch_network_access_relation
    ADD COLUMN replaces_relation_id BIGINT NULL COMMENT '替代的原访问关系' AFTER application_id,
    ADD COLUMN replaced_by_relation_id BIGINT NULL COMMENT '替代本关系的新访问关系' AFTER replaces_relation_id,
    ADD COLUMN closed_application_id BIGINT NULL COMMENT '关闭本关系的访问申请' AFTER replaced_by_relation_id,
    ADD COLUMN validity_type VARCHAR(16) NOT NULL DEFAULT 'LONG_TERM' COMMENT 'LIMITED/LONG_TERM' AFTER valid_until,
    ADD COLUMN close_type VARCHAR(24) NULL COMMENT 'SUPERSEDED/CLOSED_BY_APPLICATION/LEGACY_DIRECT' AFTER close_reason,
    ADD KEY idx_arch_network_access_relation_replace (tenant_id, replaces_relation_id),
    ADD KEY idx_arch_network_access_relation_replaced_by (tenant_id, replaced_by_relation_id),
    ADD KEY idx_arch_network_access_relation_closed_app (tenant_id, closed_application_id),
    ADD CONSTRAINT fk_arch_network_access_relation_replaces
        FOREIGN KEY (tenant_id, replaces_relation_id)
        REFERENCES arch_network_access_relation (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_arch_network_access_relation_replaced_by
        FOREIGN KEY (tenant_id, replaced_by_relation_id)
        REFERENCES arch_network_access_relation (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_arch_network_access_relation_closed_app
        FOREIGN KEY (tenant_id, closed_application_id)
        REFERENCES arch_network_access_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT chk_arch_network_access_relation_validity CHECK (validity_type IN ('LIMITED', 'LONG_TERM')),
    ADD CONSTRAINT chk_arch_network_access_relation_close_type CHECK (
        close_type IS NULL OR close_type IN ('SUPERSEDED', 'CLOSED_BY_APPLICATION', 'LEGACY_DIRECT')
    );

UPDATE arch_network_access_relation
SET valid_from = COALESCE(valid_from, created_at, CURRENT_TIMESTAMP)
WHERE valid_from IS NULL;

UPDATE arch_network_access_relation
SET valid_from = DATE_SUB(valid_until, INTERVAL 1 SECOND)
WHERE valid_until IS NOT NULL
  AND valid_from >= valid_until;

UPDATE arch_network_access_relation
SET validity_type = CASE WHEN valid_until IS NULL THEN 'LONG_TERM' ELSE 'LIMITED' END;

ALTER TABLE arch_network_access_relation
    ADD CONSTRAINT chk_arch_network_access_relation_validity_dates CHECK (
        (validity_type = 'LONG_TERM' AND valid_from IS NOT NULL AND valid_until IS NULL)
     OR (validity_type = 'LIMITED' AND valid_from IS NOT NULL AND valid_until IS NOT NULL AND valid_until > valid_from)
    );

CREATE TABLE arch_network_access_application_history (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NULL,
    business_round INT NOT NULL DEFAULT 0,
    summary VARCHAR(1000) NULL,
    snapshot_json JSON NULL,
    diff_json JSON NULL,
    operator_id BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_arch_network_access_app_history_app (tenant_id, application_id, occurred_at),
    CONSTRAINT fk_arch_network_access_app_history_app
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_network_access_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_access_app_history_round CHECK (business_round >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络访问申请不可变业务历史';

CREATE TABLE arch_network_access_workflow_round (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    workflow_definition_id BIGINT NULL,
    workflow_version_id BIGINT NULL,
    workflow_instance_id BIGINT NULL,
    payload_digest CHAR(64) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_access_workflow_round_app (tenant_id, application_id, round_no),
    UNIQUE KEY uk_arch_network_access_workflow_round_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_arch_network_access_workflow_round_app
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_network_access_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_access_workflow_round_no CHECK (round_no > 0),
    CONSTRAINT chk_arch_network_access_workflow_round_status CHECK (
        status IN ('PENDING', 'STARTED', 'RETURNED', 'APPROVED', 'REJECTED', 'TERMINATED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络访问申请工作流轮次';

CREATE TABLE arch_network_access_workflow_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    subscriber_key VARCHAR(128) NOT NULL,
    application_id BIGINT NULL,
    round_no INT NULL,
    workflow_instance_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    detail VARCHAR(1000) NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_access_workflow_receipt (tenant_id, event_id, subscriber_key),
    KEY idx_arch_network_access_workflow_receipt_app (tenant_id, application_id, received_at),
    CONSTRAINT fk_arch_network_access_workflow_receipt_app
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_network_access_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_access_workflow_receipt_round CHECK (round_no IS NULL OR round_no > 0),
    CONSTRAINT chk_arch_network_access_workflow_receipt_status CHECK (
        processing_status IN ('PROCESSED', 'IGNORED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络访问申请工作流事件回执';

CREATE TABLE arch_network_access_exemption_rule (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    rule_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rule_name VARCHAR(160) NOT NULL,
    source_network_zone_id BIGINT NOT NULL,
    target_network_zone_id BIGINT NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    ports VARCHAR(128) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NULL,
    validity_type VARCHAR(16) NOT NULL DEFAULT 'LONG_TERM',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_access_exemption_rule_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_network_access_exemption_rule_code (tenant_id, rule_code),
    KEY idx_arch_network_access_exemption_rule_match
        (tenant_id, source_network_zone_id, target_network_zone_id, protocol, status),
    CONSTRAINT fk_arch_network_access_exemption_rule_source_zone
        FOREIGN KEY (tenant_id, source_network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_exemption_rule_target_zone
        FOREIGN KEY (tenant_id, target_network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_access_exemption_rule_protocol CHECK (protocol IN ('TCP', 'UDP', 'HTTP', 'HTTPS', 'OTHER')),
    CONSTRAINT chk_arch_network_access_exemption_rule_validity CHECK (validity_type IN ('LIMITED', 'LONG_TERM')),
    CONSTRAINT chk_arch_network_access_exemption_rule_validity_dates CHECK (
        (validity_type = 'LONG_TERM' AND valid_until IS NULL)
     OR (validity_type = 'LIMITED' AND valid_until IS NOT NULL AND valid_until > valid_from)
    ),
    CONSTRAINT chk_arch_network_access_exemption_rule_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_arch_network_access_exemption_rule_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络访问免申请规则';
