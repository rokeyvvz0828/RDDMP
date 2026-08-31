-- REQ-20260823-050：CLB、DNS 与证书网络专项工单。
-- 三类工单共享工单引擎（状态机、轮次、回执、历史），kind 专属字段以强类型 DTO
-- 校验后存入 business_payload JSON；平台只登记申请、办理过程与办理结果，不执行任何
-- 外部 CLB/DNS/证书动作。

CREATE TABLE arch_network_work_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL COMMENT 'CLB/DNS/CERT',
    action_type VARCHAR(16) NOT NULL COMMENT 'CLB:OPEN/ADJUST; DNS:ADD/CHANGE/REMOVE; CERT:APPLY/RENEW/REVOKE',
    subject VARCHAR(255) NOT NULL COMMENT '列表主标识：CLB 名称/域名/证书主题，由服务端从载荷投影',
    applicant_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL COMMENT '申请原因/变更说明',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RETURNED/COMPLETED/REJECTED/CANCELLED',
    business_payload JSON NOT NULL COMMENT 'kind 专属字段快照，服务端强类型 DTO 校验后写入',
    attachment_ids JSON NULL COMMENT '申请材料附件 id 列表',
    result_status VARCHAR(16) NULL COMMENT 'SUCCESS/FAILED',
    result_description VARCHAR(2000) NULL,
    result_attachment_ids JSON NULL COMMENT '办理凭证附件 id 列表',
    result_registered_by BIGINT NULL,
    result_registered_at TIMESTAMP NULL,
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
    UNIQUE KEY uk_arch_network_work_order_tenant_id (tenant_id, id),
    KEY idx_arch_network_work_order_applicant (tenant_id, applicant_id, status, updated_at),
    KEY idx_arch_network_work_order_kind_status (tenant_id, kind, status, updated_at),
    KEY idx_arch_network_work_order_workflow (tenant_id, current_workflow_instance_id),
    CONSTRAINT chk_arch_network_work_order_kind CHECK (kind IN ('CLB', 'DNS', 'CERT')),
    CONSTRAINT chk_arch_network_work_order_action CHECK (
        (kind = 'CLB' AND action_type IN ('OPEN', 'ADJUST'))
     OR (kind = 'DNS' AND action_type IN ('ADD', 'CHANGE', 'REMOVE'))
     OR (kind = 'CERT' AND action_type IN ('APPLY', 'RENEW', 'REVOKE'))
    ),
    CONSTRAINT chk_arch_network_work_order_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'RETURNED', 'COMPLETED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT chk_arch_network_work_order_round CHECK (current_business_round >= 0),
    CONSTRAINT chk_arch_network_work_order_cancel CHECK (cancellation_requested IN (0, 1)),
    CONSTRAINT chk_arch_network_work_order_row_version CHECK (row_version >= 0),
    CONSTRAINT chk_arch_network_work_order_result CHECK (
        result_status IS NULL OR result_status IN ('SUCCESS', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLB/DNS/证书网络专项工单';

CREATE TABLE arch_network_work_order_history (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
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
    KEY idx_arch_network_work_order_history_order (tenant_id, work_order_id, occurred_at),
    CONSTRAINT fk_arch_network_work_order_history_order
        FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES arch_network_work_order (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_work_order_history_round CHECK (business_round >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络专项工单不可变业务历史';

CREATE TABLE arch_network_workflow_round (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_arch_network_workflow_round_order (tenant_id, work_order_id, round_no),
    UNIQUE KEY uk_arch_network_workflow_round_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_arch_network_workflow_round_order
        FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES arch_network_work_order (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_workflow_round_no CHECK (round_no > 0),
    CONSTRAINT chk_arch_network_workflow_round_status CHECK (
        status IN ('PENDING', 'STARTED', 'RETURNED', 'APPROVED', 'REJECTED', 'TERMINATED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络专项工单工作流轮次';

CREATE TABLE arch_network_workflow_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    subscriber_key VARCHAR(128) NOT NULL,
    work_order_id BIGINT NULL,
    round_no INT NULL,
    workflow_instance_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    detail VARCHAR(1000) NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_workflow_receipt (tenant_id, event_id, subscriber_key),
    KEY idx_arch_network_workflow_receipt_order (tenant_id, work_order_id, received_at),
    CONSTRAINT fk_arch_network_workflow_receipt_order
        FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES arch_network_work_order (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_workflow_receipt_round CHECK (round_no IS NULL OR round_no > 0),
    CONSTRAINT chk_arch_network_workflow_receipt_status CHECK (
        processing_status IN ('PROCESSED', 'IGNORED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络专项工单工作流事件回执';
