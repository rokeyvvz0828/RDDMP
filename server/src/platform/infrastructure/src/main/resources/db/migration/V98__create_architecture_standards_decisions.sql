-- REQ-20260823-050：架构规范文档与架构决策事项的数据结构。
-- 只追加 V87，不修改任何既有迁移。

-- ============================================================
-- 架构规范文档
-- ============================================================
CREATE TABLE arch_standard_document (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL COMMENT '规范标题',
    category_code VARCHAR(64) NOT NULL COMMENT 'ARCH_STANDARD_CATEGORY 字典参数键',
    summary VARCHAR(2000) NULL COMMENT '摘要',
    content LONGTEXT NULL COMMENT '正文',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
    current_version INT NOT NULL DEFAULT 0 COMMENT '当前发布版本号，未发布为 0',
    published_at TIMESTAMP NULL COMMENT '最近发布时间',
    published_by BIGINT NULL COMMENT '最近发布人',
    published_by_name VARCHAR(100) NULL COMMENT '最近发布人姓名快照',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_standard_document_tenant (tenant_id, id),
    KEY idx_arch_standard_document_status_category (tenant_id, status, category_code, deleted, updated_at),
    KEY idx_arch_standard_document_category (tenant_id, category_code, status, deleted),
    CONSTRAINT chk_arch_standard_document_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    CONSTRAINT chk_arch_standard_document_version CHECK (current_version >= 0),
    CONSTRAINT chk_arch_standard_document_row_version CHECK (row_version >= 0),
    CONSTRAINT chk_arch_standard_document_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构规范文档';

CREATE TABLE arch_standard_document_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    summary VARCHAR(2000) NULL,
    content LONGTEXT NULL,
    published_at TIMESTAMP NOT NULL,
    published_by BIGINT NOT NULL,
    published_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_standard_version_tenant (tenant_id, id),
    UNIQUE KEY uk_arch_standard_version_document (tenant_id, document_id, version_no),
    CONSTRAINT fk_arch_standard_version_document
        FOREIGN KEY (tenant_id, document_id)
        REFERENCES arch_standard_document (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_standard_version_no CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构规范文档发布版本快照（不可变）';

-- ============================================================
-- 架构决策事项
-- ============================================================
CREATE TABLE arch_decision_matter (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    matter_no VARCHAR(32) NOT NULL COMMENT 'AD-YYYY-NNNN，租户内永久唯一',
    title VARCHAR(300) NOT NULL COMMENT '事项标题',
    problem LONGTEXT NOT NULL COMMENT '问题或困难描述',
    type_code VARCHAR(64) NULL COMMENT 'ARCH_MATTER_TYPE 参数键；正式决策发布前必填',
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/RETURNED_FOR_INFO/IN_REVIEW/PUBLISHED',
    received_at TIMESTAMP NOT NULL COMMENT '受理时间（首次提交）',
    first_handling_deadline DATE NOT NULL COMMENT '首次处理期限 = 受理时间 + 7 自然日',
    first_handling_outcome VARCHAR(24) NULL COMMENT 'ACCEPTED/REQUESTED_INFO/REVIEW_MODE_SET',
    first_handling_comment VARCHAR(2000) NULL COMMENT '首次处理意见',
    first_handled_at TIMESTAMP NULL,
    first_handler_id BIGINT NULL,
    first_handler_name VARCHAR(100) NULL,
    review_mode VARCHAR(16) NULL COMMENT '异步 ASYNC / 会议 MEETING',
    proposer_id BIGINT NOT NULL COMMENT '事项提出人',
    proposer_name VARCHAR(100) NOT NULL,
    submitter_id BIGINT NOT NULL COMMENT '最近提交人',
    submitter_name VARCHAR(100) NOT NULL,
    publication_prepared_at TIMESTAMP NULL COMMENT '结论发布准备时间',
    publication_prepared_by BIGINT NULL,
    current_business_round INT NOT NULL DEFAULT 0,
    current_workflow_definition_id BIGINT NULL,
    current_workflow_version_id BIGINT NULL,
    current_workflow_instance_id BIGINT NULL,
    current_payload_digest CHAR(64) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_matter_tenant (tenant_id, id),
    UNIQUE KEY uk_arch_decision_matter_no (tenant_id, matter_no),
    KEY idx_arch_decision_matter_proposer (tenant_id, proposer_id, status, updated_at),
    KEY idx_arch_decision_matter_status (tenant_id, status, updated_at),
    KEY idx_arch_decision_matter_type (tenant_id, type_code, status),
    KEY idx_arch_decision_matter_deadline (tenant_id, first_handling_deadline, status),
    KEY idx_arch_decision_matter_workflow (tenant_id, current_workflow_instance_id),
    CONSTRAINT chk_arch_decision_matter_status CHECK (status IN ('SUBMITTED', 'RETURNED_FOR_INFO', 'IN_REVIEW', 'PUBLISHED')),
    CONSTRAINT chk_arch_decision_matter_first_outcome CHECK (
        first_handling_outcome IS NULL
        OR first_handling_outcome IN ('ACCEPTED', 'REQUESTED_INFO', 'REVIEW_MODE_SET')
    ),
    CONSTRAINT chk_arch_decision_matter_review_mode CHECK (review_mode IS NULL OR review_mode IN ('ASYNC', 'MEETING')),
    CONSTRAINT chk_arch_decision_matter_round CHECK (current_business_round >= 0),
    CONSTRAINT chk_arch_decision_matter_row_version CHECK (row_version >= 0),
    CONSTRAINT chk_arch_decision_matter_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策事项';

CREATE TABLE arch_decision_material (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    matter_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL COMMENT 'SOLUTION/IMPACT/DISPUTE/OTHER',
    content LONGTEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_material_tenant (tenant_id, id),
    KEY idx_arch_decision_material_matter (tenant_id, matter_id, created_at),
    CONSTRAINT fk_arch_decision_material_matter
        FOREIGN KEY (tenant_id, matter_id)
        REFERENCES arch_decision_matter (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_decision_material_kind CHECK (kind IN ('SOLUTION', 'IMPACT', 'DISPUTE', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策事项协作补齐材料';

CREATE TABLE arch_decision_review (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    matter_id BIGINT NOT NULL,
    review_no INT NOT NULL,
    method VARCHAR(16) NOT NULL COMMENT 'ASYNC/MEETING',
    reviewed_at TIMESTAMP NOT NULL,
    process_material_summary VARCHAR(2000) NULL COMMENT '过程材料摘要',
    key_opinion LONGTEXT NULL COMMENT '关键意见',
    conclusion_content LONGTEXT NULL COMMENT '正式结论；发布准备前必填',
    conclusion_rationale LONGTEXT NULL COMMENT '理由',
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_review_tenant (tenant_id, id),
    UNIQUE KEY uk_arch_decision_review_matter_no (tenant_id, matter_id, review_no),
    KEY idx_arch_decision_review_matter (tenant_id, matter_id, created_at),
    CONSTRAINT fk_arch_decision_review_matter
        FOREIGN KEY (tenant_id, matter_id)
        REFERENCES arch_decision_matter (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_decision_review_method CHECK (method IN ('ASYNC', 'MEETING')),
    CONSTRAINT chk_arch_decision_review_no CHECK (review_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策评审记录';

CREATE TABLE arch_decision_review_participant (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_participant_tenant_review (tenant_id, review_id, user_id),
    KEY idx_arch_decision_participant_review (tenant_id, review_id),
    CONSTRAINT fk_arch_decision_participant_review
        FOREIGN KEY (tenant_id, review_id)
        REFERENCES arch_decision_review (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策评审参与人';

CREATE TABLE arch_decision_action_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    owner_user_id BIGINT NULL,
    owner_name VARCHAR(100) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/DONE',
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_action_tenant (tenant_id, id),
    KEY idx_arch_decision_action_review (tenant_id, review_id),
    CONSTRAINT fk_arch_decision_action_review
        FOREIGN KEY (tenant_id, review_id)
        REFERENCES arch_decision_review (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_decision_action_status CHECK (status IN ('OPEN', 'DONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策行动项';

CREATE TABLE arch_decision_conclusion (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    matter_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL COMMENT '正式结论内容（不可变）',
    rationale LONGTEXT NULL COMMENT '理由（不可变）',
    published_at TIMESTAMP NOT NULL,
    published_by BIGINT NOT NULL,
    published_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_conclusion_tenant (tenant_id, id),
    UNIQUE KEY uk_arch_decision_conclusion_matter (tenant_id, matter_id),
    KEY idx_arch_decision_conclusion_review (tenant_id, review_id),
    CONSTRAINT fk_arch_decision_conclusion_matter
        FOREIGN KEY (tenant_id, matter_id)
        REFERENCES arch_decision_matter (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_decision_conclusion_review
        FOREIGN KEY (tenant_id, review_id)
        REFERENCES arch_decision_review (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策已发布结论（不可变）';

CREATE TABLE arch_decision_publication_intent (
    matter_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL COMMENT '作为正式结论来源的评审记录',
    supersession_targets_json JSON NULL COMMENT '[{"conclusionId":n,"kind":"SUPERSEDE|PARTIALLY_REVISE"}]',
    payload_digest CHAR(64) NOT NULL COMMENT '发布准备内容的 SHA-256',
    prepared_by BIGINT NOT NULL,
    prepared_by_name VARCHAR(100) NULL,
    prepared_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (matter_id),
    UNIQUE KEY uk_arch_decision_intent_tenant (tenant_id, matter_id),
    CONSTRAINT fk_arch_decision_intent_matter
        FOREIGN KEY (tenant_id, matter_id)
        REFERENCES arch_decision_matter (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_decision_intent_review
        FOREIGN KEY (tenant_id, review_id)
        REFERENCES arch_decision_review (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策结论发布准备意图';

CREATE TABLE arch_decision_supersession (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    conclusion_id BIGINT NOT NULL COMMENT '后续结论',
    superseded_conclusion_id BIGINT NOT NULL COMMENT '被替代/部分修订的既有结论',
    kind VARCHAR(24) NOT NULL COMMENT 'SUPERSEDE/PARTIALLY_REVISE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_supersession_tenant (tenant_id, id),
    UNIQUE KEY uk_arch_decision_supersession_pair (tenant_id, conclusion_id, superseded_conclusion_id),
    KEY idx_arch_decision_supersession_target (tenant_id, superseded_conclusion_id),
    CONSTRAINT fk_arch_decision_supersession_conclusion
        FOREIGN KEY (tenant_id, conclusion_id)
        REFERENCES arch_decision_conclusion (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_decision_supersession_target
        FOREIGN KEY (tenant_id, superseded_conclusion_id)
        REFERENCES arch_decision_conclusion (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_decision_supersession_kind CHECK (kind IN ('SUPERSEDE', 'PARTIALLY_REVISE')),
    CONSTRAINT chk_arch_decision_supersession_distinct CHECK (conclusion_id <> superseded_conclusion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策替代/部分修订关系';

CREATE TABLE arch_decision_number_sequence (
    tenant_id BIGINT NOT NULL,
    seq_year INT NOT NULL,
    next_ordinal INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, seq_year),
    CONSTRAINT chk_arch_decision_number_ordinal CHECK (next_ordinal BETWEEN 1 AND 9999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策事项编号租户内序列';

CREATE TABLE arch_decision_workflow_round (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    matter_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_arch_decision_workflow_round_matter (tenant_id, matter_id, round_no),
    UNIQUE KEY uk_arch_decision_workflow_round_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_arch_decision_workflow_round_matter
        FOREIGN KEY (tenant_id, matter_id)
        REFERENCES arch_decision_matter (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_decision_workflow_round_no CHECK (round_no > 0),
    CONSTRAINT chk_arch_decision_workflow_round_status CHECK (
        status IN ('PENDING', 'STARTED', 'RETURNED', 'APPROVED', 'REJECTED', 'TERMINATED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策结论发布工作流轮次';

CREATE TABLE arch_decision_workflow_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    subscriber_key VARCHAR(128) NOT NULL,
    matter_id BIGINT NULL,
    round_no INT NULL,
    workflow_instance_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    detail VARCHAR(1000) NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_decision_workflow_receipt (tenant_id, event_id, subscriber_key),
    KEY idx_arch_decision_workflow_receipt_matter (tenant_id, matter_id, received_at),
    CONSTRAINT fk_arch_decision_workflow_receipt_matter
        FOREIGN KEY (tenant_id, matter_id)
        REFERENCES arch_decision_matter (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_decision_workflow_receipt_round CHECK (round_no IS NULL OR round_no > 0),
    CONSTRAINT chk_arch_decision_workflow_receipt_status CHECK (processing_status IN ('PROCESSED', 'IGNORED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策工作流事件回执';
