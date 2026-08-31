-- REQ-20260826-054：网络分区、外部网络地址、网络访问申请与访问关系。
-- 只追加，不修改 V1-V99。既有 network_zone 文本字段保留兼容显示。

CREATE TABLE arch_network_zone (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(160) NOT NULL,
    restriction_level INT NOT NULL DEFAULT 0 COMMENT '限制级别；子分区不得低于父分区',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    description VARCHAR(1000) NULL,
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_zone_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_network_zone_code (tenant_id, code),
    UNIQUE KEY uk_arch_network_zone_name_parent (tenant_id, parent_id, name),
    KEY idx_arch_network_zone_parent (tenant_id, parent_id, status, id),
    CONSTRAINT fk_arch_network_zone_parent
        FOREIGN KEY (tenant_id, parent_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_zone_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_network_zone_level CHECK (restriction_level >= 0),
    CONSTRAINT chk_arch_network_zone_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络分区树';

CREATE TABLE arch_external_network_address (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    address_type VARCHAR(16) NOT NULL COMMENT 'IP/CIDR/DOMAIN',
    address_value VARCHAR(255) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    purpose VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_external_network_address_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_external_network_address_value (tenant_id, address_type, address_value),
    KEY idx_arch_external_network_address_status (tenant_id, status, id),
    CONSTRAINT chk_arch_external_network_address_type CHECK (address_type IN ('IP', 'CIDR', 'DOMAIN')),
    CONSTRAINT chk_arch_external_network_address_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_external_network_address_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部网络地址';

ALTER TABLE arch_deployment_unit
    ADD COLUMN default_network_zone_id BIGINT NULL COMMENT '默认网络分区',
    ADD COLUMN default_network_zone_name VARCHAR(160) NULL COMMENT '默认网络分区名称快照',
    ADD KEY idx_arch_deployment_unit_network_zone (tenant_id, default_network_zone_id),
    ADD CONSTRAINT fk_arch_deployment_unit_network_zone
        FOREIGN KEY (tenant_id, default_network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE arch_deployment_unit_version
    ADD COLUMN default_network_zone_id BIGINT NULL COMMENT '默认网络分区',
    ADD COLUMN default_network_zone_name VARCHAR(160) NULL COMMENT '默认网络分区名称快照',
    ADD KEY idx_arch_deployment_unit_version_network_zone (tenant_id, default_network_zone_id),
    ADD CONSTRAINT fk_arch_deployment_unit_version_network_zone
        FOREIGN KEY (tenant_id, default_network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE arch_resource_request_item
    ADD COLUMN network_zone_id BIGINT NULL COMMENT '结构化网络分区',
    ADD COLUMN network_zone_name VARCHAR(160) NULL COMMENT '网络分区名称快照',
    ADD KEY idx_arch_resource_request_item_network_zone (tenant_id, network_zone_id),
    ADD CONSTRAINT fk_arch_resource_request_item_network_zone
        FOREIGN KEY (tenant_id, network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE arch_environment_instance
    ADD COLUMN network_zone_id BIGINT NULL COMMENT '实例归属网络分区',
    ADD COLUMN network_zone_name VARCHAR(160) NULL COMMENT '网络分区名称快照',
    ADD KEY idx_arch_env_instance_network_zone (tenant_id, network_zone_id, status),
    ADD CONSTRAINT fk_arch_env_instance_network_zone
        FOREIGN KEY (tenant_id, network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TABLE arch_network_access_application (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applicant_id BIGINT NOT NULL,
    source_kind VARCHAR(16) NOT NULL COMMENT 'MANAGED/EXTERNAL',
    source_physical_subsystem_id BIGINT NULL,
    source_environment_id BIGINT NULL,
    source_deployment_unit_id BIGINT NULL,
    source_external_address_id BIGINT NULL,
    source_snapshot_json JSON NULL,
    target_kind VARCHAR(16) NOT NULL COMMENT 'MANAGED/EXTERNAL',
    target_physical_subsystem_id BIGINT NULL,
    target_environment_id BIGINT NULL,
    target_deployment_unit_id BIGINT NULL,
    target_external_address_id BIGINT NULL,
    target_snapshot_json JSON NULL,
    protocol VARCHAR(16) NOT NULL COMMENT 'TCP/UDP/HTTP/HTTPS/OTHER',
    ports VARCHAR(128) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    process_description VARCHAR(1000) NULL,
    valid_from TIMESTAMP NULL,
    valid_until TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/APPROVED/REJECTED/CANCELLED',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_access_app_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_network_access_app_no (tenant_id, application_no),
    KEY idx_arch_network_access_app_applicant (tenant_id, applicant_id, status, id),
    KEY idx_arch_network_access_app_source (tenant_id, source_kind, source_deployment_unit_id),
    KEY idx_arch_network_access_app_target (tenant_id, target_kind, target_deployment_unit_id),
    CONSTRAINT fk_arch_network_access_app_source_physical
        FOREIGN KEY (tenant_id, source_physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_source_environment
        FOREIGN KEY (tenant_id, source_environment_id)
        REFERENCES arch_environment (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_source_unit
        FOREIGN KEY (tenant_id, source_deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_source_external
        FOREIGN KEY (tenant_id, source_external_address_id)
        REFERENCES arch_external_network_address (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_target_physical
        FOREIGN KEY (tenant_id, target_physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_target_environment
        FOREIGN KEY (tenant_id, target_environment_id)
        REFERENCES arch_environment (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_target_unit
        FOREIGN KEY (tenant_id, target_deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_network_access_app_target_external
        FOREIGN KEY (tenant_id, target_external_address_id)
        REFERENCES arch_external_network_address (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_access_app_source_kind CHECK (source_kind IN ('MANAGED', 'EXTERNAL')),
    CONSTRAINT chk_arch_network_access_app_target_kind CHECK (target_kind IN ('MANAGED', 'EXTERNAL')),
    CONSTRAINT chk_arch_network_access_app_protocol CHECK (protocol IN ('TCP', 'UDP', 'HTTP', 'HTTPS', 'OTHER')),
    CONSTRAINT chk_arch_network_access_app_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_arch_network_access_app_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络访问申请';

CREATE TABLE arch_network_access_relation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    relation_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    application_id BIGINT NOT NULL,
    source_kind VARCHAR(16) NOT NULL,
    source_snapshot_json JSON NULL,
    target_kind VARCHAR(16) NOT NULL,
    target_snapshot_json JSON NULL,
    protocol VARCHAR(16) NOT NULL,
    ports VARCHAR(128) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    process_description VARCHAR(1000) NULL,
    valid_from TIMESTAMP NULL,
    valid_until TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
    close_reason VARCHAR(1000) NULL,
    closed_by BIGINT NULL,
    closed_at TIMESTAMP NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_access_relation_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_network_access_relation_no (tenant_id, relation_no),
    KEY idx_arch_network_access_relation_app (tenant_id, application_id),
    KEY idx_arch_network_access_relation_status (tenant_id, status, id),
    CONSTRAINT fk_arch_network_access_relation_app
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_network_access_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_access_relation_source_kind CHECK (source_kind IN ('MANAGED', 'EXTERNAL')),
    CONSTRAINT chk_arch_network_access_relation_target_kind CHECK (target_kind IN ('MANAGED', 'EXTERNAL')),
    CONSTRAINT chk_arch_network_access_relation_protocol CHECK (protocol IN ('TCP', 'UDP', 'HTTP', 'HTTPS', 'OTHER')),
    CONSTRAINT chk_arch_network_access_relation_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_arch_network_access_relation_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络访问关系快照';
