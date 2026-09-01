-- REQ-20260824-052：具体环境、资源申请工单与工作流轮次。
-- 本批次只维护环境主数据和资源申请态，不创建环境部署实例或实际资源分配。

CREATE TABLE arch_environment_type (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_environment_type_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_environment_type_code (tenant_id, code),
    UNIQUE KEY uk_arch_environment_type_name (tenant_id, name),
    KEY idx_arch_environment_type_status_sort (tenant_id, status, sort_no, id),
    CONSTRAINT chk_arch_environment_type_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_environment_type_sort CHECK (sort_no >= 0),
    CONSTRAINT chk_arch_environment_type_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='环境类型';

CREATE TABLE arch_environment (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(160) NOT NULL,
    type_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_environment_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_environment_code (tenant_id, code),
    UNIQUE KEY uk_arch_environment_name (tenant_id, name),
    KEY idx_arch_environment_type_status (tenant_id, type_id, status, id),
    CONSTRAINT fk_arch_environment_type
        FOREIGN KEY (tenant_id, type_id)
        REFERENCES arch_environment_type (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_environment_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_environment_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='具体环境';

CREATE TABLE arch_resource_request (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    environment_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    request_type VARCHAR(16) NOT NULL COMMENT 'INITIAL/EXPANSION/SHRINK/ADJUSTMENT',
    reason VARCHAR(1000) NULL,
    source_task_id BIGINT NULL COMMENT '后续搭建任务批次接入前仅作可选来源占位，不建外键',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RETURNED/APPROVED/REJECTED/CANCELLED',
    current_business_round INT NOT NULL DEFAULT 0,
    current_workflow_definition_id BIGINT NULL,
    current_workflow_version_id BIGINT NULL,
    current_workflow_instance_id BIGINT NULL,
    current_payload_digest CHAR(64) NULL,
    cancellation_requested TINYINT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_resource_request_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_resource_request_no (tenant_id, request_no),
    KEY idx_arch_resource_request_applicant (tenant_id, applicant_id, status, updated_at),
    KEY idx_arch_resource_request_environment (tenant_id, environment_id, status, updated_at),
    KEY idx_arch_resource_request_physical (tenant_id, physical_subsystem_id, status, updated_at),
    KEY idx_arch_resource_request_workflow (tenant_id, current_workflow_instance_id),
    CONSTRAINT fk_arch_resource_request_physical
        FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_resource_request_environment
        FOREIGN KEY (tenant_id, environment_id)
        REFERENCES arch_environment (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_resource_request_type CHECK (request_type IN ('INITIAL', 'EXPANSION', 'SHRINK', 'ADJUSTMENT')),
    CONSTRAINT chk_arch_resource_request_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT chk_arch_resource_request_round CHECK (current_business_round >= 0),
    CONSTRAINT chk_arch_resource_request_cancel CHECK (cancellation_requested IN (0, 1)),
    CONSTRAINT chk_arch_resource_request_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源申请工单';

CREATE TABLE arch_resource_request_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    item_seq INT NOT NULL,
    deployment_unit_id BIGINT NOT NULL,
    cpu_cores DECIMAL(8,2) NOT NULL DEFAULT 0,
    memory_gb DECIMAL(10,2) NOT NULL DEFAULT 0,
    storage_gb DECIMAL(12,2) NOT NULL DEFAULT 0,
    planned_node_count INT NOT NULL,
    remark VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_resource_request_item_seq (tenant_id, request_id, item_seq),
    KEY idx_arch_resource_request_item_unit (tenant_id, deployment_unit_id, request_id),
    CONSTRAINT fk_arch_resource_request_item_request
        FOREIGN KEY (tenant_id, request_id)
        REFERENCES arch_resource_request (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_resource_request_item_unit
        FOREIGN KEY (tenant_id, deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_resource_request_item_seq CHECK (item_seq > 0),
    CONSTRAINT chk_arch_resource_request_item_cpu CHECK (cpu_cores >= 0),
    CONSTRAINT chk_arch_resource_request_item_memory CHECK (memory_gb >= 0),
    CONSTRAINT chk_arch_resource_request_item_storage CHECK (storage_gb >= 0),
    CONSTRAINT chk_arch_resource_request_item_nodes CHECK (planned_node_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源申请需求规格明细';

CREATE TABLE arch_resource_request_history (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
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
    KEY idx_arch_resource_request_history_request (tenant_id, request_id, occurred_at),
    CONSTRAINT fk_arch_resource_request_history_request
        FOREIGN KEY (tenant_id, request_id)
        REFERENCES arch_resource_request (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_resource_request_history_round CHECK (business_round >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源申请不可变业务历史';

CREATE TABLE arch_resource_request_workflow_round (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_arch_resource_request_workflow_round_order (tenant_id, request_id, round_no),
    UNIQUE KEY uk_arch_resource_request_workflow_round_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_arch_resource_request_workflow_round_request
        FOREIGN KEY (tenant_id, request_id)
        REFERENCES arch_resource_request (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_resource_request_workflow_round_no CHECK (round_no > 0),
    CONSTRAINT chk_arch_resource_request_workflow_round_status CHECK (
        status IN ('PENDING', 'STARTED', 'RETURNED', 'APPROVED', 'REJECTED', 'TERMINATED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源申请工作流轮次';

CREATE TABLE arch_resource_request_workflow_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    subscriber_key VARCHAR(128) NOT NULL,
    request_id BIGINT NULL,
    round_no INT NULL,
    workflow_instance_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    detail VARCHAR(1000) NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_resource_request_workflow_receipt (tenant_id, event_id, subscriber_key),
    KEY idx_arch_resource_request_workflow_receipt_request (tenant_id, request_id, received_at),
    CONSTRAINT fk_arch_resource_request_workflow_receipt_request
        FOREIGN KEY (tenant_id, request_id)
        REFERENCES arch_resource_request (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_resource_request_workflow_receipt_round CHECK (round_no IS NULL OR round_no > 0),
    CONSTRAINT chk_arch_resource_request_workflow_receipt_status CHECK (
        processing_status IN ('PROCESSED', 'IGNORED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源申请工作流事件回执';
