-- REQ-20260822-048：架构子系统生命周期、工单和全局编号基座。
-- 容量校验必须先于任何永久 DDL，避免 Flyway 失败后出现半创建的 V82 结构。
CREATE TEMPORARY TABLE tmp_arch_v82_capacity_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v82_capacity_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v82_capacity_guard (marker)
SELECT 1
WHERE (SELECT COUNT(*) FROM arch_logical_subsystem) > 9999
   OR EXISTS (
       SELECT 1
       FROM arch_physical_subsystem
       GROUP BY tenant_id, logical_subsystem_id
       HAVING COUNT(*) > 35
   );

DROP TEMPORARY TABLE tmp_arch_v82_capacity_guard;

ALTER TABLE arch_logical_subsystem
    ADD COLUMN number_sequence INT NULL COMMENT '全系统逻辑编号内部序号' AFTER code,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/OFFLINE/VOIDED' AFTER remark,
    ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT '稳定排序号' AFTER status,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER sort_no;

ALTER TABLE arch_physical_subsystem
    ADD COLUMN number_slot CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '物理编号槽位 1-9,A-Z' AFTER code,
    ADD COLUMN english_name VARCHAR(200) NULL COMMENT '英文名称，租户内永久唯一' AFTER name,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/OFFLINE/VOIDED' AFTER remark,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER status;

-- 既有编号文本保持不变；内部序号按租户、创建时间和主键的稳定全局顺序回填。
UPDATE arch_logical_subsystem logical_subsystem
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (ORDER BY tenant_id, created_at, id) AS assigned_number_sequence
    FROM arch_logical_subsystem
) numbered ON numbered.id = logical_subsystem.id
SET logical_subsystem.number_sequence = numbered.assigned_number_sequence;

-- 已软删除的物理记录同样永久占用槽位，避免后续重复编号。
UPDATE arch_physical_subsystem physical_subsystem
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, logical_subsystem_id
               ORDER BY created_at, id
           ) AS assigned_slot_ordinal
    FROM arch_physical_subsystem
) numbered ON numbered.id = physical_subsystem.id
SET physical_subsystem.number_slot = CASE
    WHEN numbered.assigned_slot_ordinal <= 9 THEN CAST(numbered.assigned_slot_ordinal AS CHAR(1))
    ELSE CHAR(ASCII('A') + numbered.assigned_slot_ordinal - 10)
END;

ALTER TABLE arch_logical_subsystem
    ADD UNIQUE KEY uk_arch_logical_number_sequence (number_sequence),
    ADD KEY idx_arch_logical_status_sort (tenant_id, status, sort_no, deleted, id),
    ADD CONSTRAINT chk_arch_logical_status CHECK (status IN ('ACTIVE', 'OFFLINE', 'VOIDED')),
    ADD CONSTRAINT chk_arch_logical_number_sequence CHECK (number_sequence IS NULL OR number_sequence BETWEEN 1 AND 9999),
    ADD CONSTRAINT chk_arch_logical_row_version CHECK (row_version >= 0);

ALTER TABLE arch_physical_subsystem
    ADD UNIQUE KEY uk_arch_physical_tenant_id (tenant_id, id),
    ADD UNIQUE KEY uk_arch_physical_parent_slot (tenant_id, logical_subsystem_id, number_slot),
    ADD UNIQUE KEY uk_arch_physical_english_name (tenant_id, english_name),
    ADD KEY idx_arch_physical_status_parent (tenant_id, status, logical_subsystem_id, deleted, id),
    ADD CONSTRAINT chk_arch_physical_status CHECK (status IN ('ACTIVE', 'OFFLINE', 'VOIDED')),
    ADD CONSTRAINT chk_arch_physical_number_slot CHECK (
        number_slot IS NULL OR number_slot IN (
            '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
        )
    ),
    ADD CONSTRAINT chk_arch_physical_row_version CHECK (row_version >= 0);

CREATE TABLE arch_subsystem_change_application (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    target_kind VARCHAR(16) NOT NULL COMMENT 'LOGICAL/PHYSICAL',
    action_type VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE/OFFLINE/REACTIVATE/VOID/REPLACE',
    target_id BIGINT NULL COMMENT '已发布目标；新增逻辑可为空',
    applicant_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
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
    UNIQUE KEY uk_arch_subsystem_application_tenant_id (tenant_id, id),
    KEY idx_arch_subsystem_application_applicant (tenant_id, applicant_id, status, updated_at),
    KEY idx_arch_subsystem_application_target (tenant_id, target_kind, target_id, status),
    KEY idx_arch_subsystem_application_workflow (tenant_id, current_workflow_instance_id),
    CONSTRAINT chk_arch_subsystem_application_target_kind CHECK (target_kind IN ('LOGICAL', 'PHYSICAL')),
    CONSTRAINT chk_arch_subsystem_application_action_type CHECK (action_type IN ('CREATE', 'UPDATE', 'OFFLINE', 'REACTIVATE', 'VOID', 'REPLACE')),
    CONSTRAINT chk_arch_subsystem_application_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_arch_subsystem_application_round CHECK (current_business_round >= 0),
    CONSTRAINT chk_arch_subsystem_application_cancel CHECK (cancellation_requested IN (0, 1)),
    CONSTRAINT chk_arch_subsystem_application_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统变更申请';

CREATE TABLE arch_subsystem_logical_draft (
    application_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_logical_subsystem_id BIGINT NULL,
    short_name VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    business_org_id BIGINT NOT NULL,
    deployment_platform_code VARCHAR(64) NULL,
    system_type_code VARCHAR(64) NULL,
    system_ownership_code VARCHAR(64) NULL,
    contact_user_id BIGINT NOT NULL,
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    reserved_number_sequence INT NULL,
    source_row_version BIGINT NULL,
    draft_revision INT NOT NULL DEFAULT 0,
    submitted_snapshot_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id),
    UNIQUE KEY uk_arch_subsystem_logical_draft_tenant_application (tenant_id, application_id),
    KEY idx_arch_subsystem_logical_draft_source (tenant_id, source_logical_subsystem_id),
    CONSTRAINT fk_arch_subsystem_logical_draft_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_subsystem_logical_draft_source
        FOREIGN KEY (tenant_id, source_logical_subsystem_id)
        REFERENCES arch_logical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_logical_draft_number CHECK (reserved_number_sequence IS NULL OR reserved_number_sequence BETWEEN 1 AND 9999),
    CONSTRAINT chk_arch_subsystem_logical_draft_revision CHECK (draft_revision >= 0),
    CONSTRAINT chk_arch_subsystem_logical_draft_source_version CHECK (source_row_version IS NULL OR source_row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑子系统变更草稿';

CREATE TABLE arch_subsystem_physical_draft (
    application_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_physical_subsystem_id BIGINT NULL,
    target_logical_subsystem_id BIGINT NULL,
    short_name VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    english_name VARCHAR(200) NULL,
    business_group_name VARCHAR(100) NULL,
    responsible_team_org_id BIGINT NOT NULL,
    responsible_team_name_snapshot VARCHAR(200) NOT NULL,
    runtime_code VARCHAR(64) NULL,
    system_level_code VARCHAR(64) NULL,
    development_framework_code VARCHAR(64) NULL,
    owner_user_id BIGINT NULL,
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    reserved_number_slot CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_row_version BIGINT NULL,
    draft_revision INT NOT NULL DEFAULT 0,
    submitted_snapshot_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id, line_no),
    UNIQUE KEY uk_arch_subsystem_physical_draft_tenant_line (tenant_id, application_id, line_no),
    KEY idx_arch_subsystem_physical_draft_source (tenant_id, source_physical_subsystem_id),
    KEY idx_arch_subsystem_physical_draft_target (tenant_id, target_logical_subsystem_id),
    CONSTRAINT fk_arch_subsystem_physical_draft_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_subsystem_physical_draft_source
        FOREIGN KEY (tenant_id, source_physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_subsystem_physical_draft_target
        FOREIGN KEY (tenant_id, target_logical_subsystem_id)
        REFERENCES arch_logical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_physical_draft_line CHECK (line_no > 0),
    CONSTRAINT chk_arch_subsystem_physical_draft_slot CHECK (
        reserved_number_slot IS NULL OR reserved_number_slot IN (
            '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
        )
    ),
    CONSTRAINT chk_arch_subsystem_physical_draft_revision CHECK (draft_revision >= 0),
    CONSTRAINT chk_arch_subsystem_physical_draft_source_version CHECK (source_row_version IS NULL OR source_row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物理子系统变更草稿';

CREATE TABLE arch_subsystem_change_history (
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
    KEY idx_arch_subsystem_change_history_application (tenant_id, application_id, occurred_at),
    CONSTRAINT fk_arch_subsystem_change_history_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_change_history_round CHECK (business_round >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统变更历史';

CREATE TABLE arch_subsystem_change_lock (
    tenant_id BIGINT NOT NULL,
    target_kind VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, target_kind, target_id),
    UNIQUE KEY uk_arch_subsystem_change_lock_application (tenant_id, application_id),
    CONSTRAINT fk_arch_subsystem_change_lock_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_change_lock_target_kind CHECK (target_kind IN ('LOGICAL', 'PHYSICAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统变更排他锁';

CREATE TABLE arch_subsystem_value_reservation (
    tenant_id BIGINT NOT NULL,
    reservation_scope VARCHAR(32) NOT NULL,
    normalized_value VARCHAR(255) NOT NULL,
    application_id BIGINT NOT NULL,
    line_no INT NOT NULL DEFAULT 0,
    reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, reservation_scope, normalized_value),
    UNIQUE KEY uk_arch_subsystem_value_reservation_application_line (tenant_id, application_id, reservation_scope, line_no),
    CONSTRAINT fk_arch_subsystem_value_reservation_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_value_reservation_line CHECK (line_no >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统活动值保留';

CREATE TABLE arch_subsystem_replacement (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    old_physical_subsystem_id BIGINT NOT NULL,
    new_physical_subsystem_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    approved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_subsystem_replacement_old (tenant_id, old_physical_subsystem_id),
    UNIQUE KEY uk_arch_subsystem_replacement_new (tenant_id, new_physical_subsystem_id),
    UNIQUE KEY uk_arch_subsystem_replacement_application (tenant_id, application_id),
    CONSTRAINT fk_arch_subsystem_replacement_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_subsystem_replacement_old
        FOREIGN KEY (tenant_id, old_physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_subsystem_replacement_new
        FOREIGN KEY (tenant_id, new_physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_replacement_distinct CHECK (old_physical_subsystem_id <> new_physical_subsystem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物理子系统替换关系';

CREATE TABLE arch_subsystem_workflow_round (
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
    UNIQUE KEY uk_arch_subsystem_workflow_round_application (tenant_id, application_id, round_no),
    UNIQUE KEY uk_arch_subsystem_workflow_round_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_arch_subsystem_workflow_round_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_workflow_round_no CHECK (round_no > 0),
    CONSTRAINT chk_arch_subsystem_workflow_round_status CHECK (status IN ('PENDING', 'STARTED', 'RETURNED', 'APPROVED', 'REJECTED', 'TERMINATED', 'IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统工作流轮次';

CREATE TABLE arch_subsystem_workflow_receipt (
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
    UNIQUE KEY uk_arch_subsystem_workflow_receipt (tenant_id, event_id, subscriber_key),
    KEY idx_arch_subsystem_workflow_receipt_application (tenant_id, application_id, received_at),
    CONSTRAINT fk_arch_subsystem_workflow_receipt_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_workflow_receipt_round CHECK (round_no IS NULL OR round_no > 0),
    CONSTRAINT chk_arch_subsystem_workflow_receipt_status CHECK (processing_status IN ('PROCESSED', 'IGNORED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统工作流事件回执';

CREATE TABLE arch_subsystem_number_namespace (
    allocation_scope BIGINT NOT NULL DEFAULT 0 COMMENT '首期固定为全局分配域 0',
    namespace_code VARCHAR(64) NOT NULL COMMENT 'LOGICAL 或 PHYSICAL:<逻辑序号>',
    next_ordinal INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_scope, namespace_code),
    CONSTRAINT chk_arch_subsystem_number_namespace_scope CHECK (allocation_scope = 0),
    CONSTRAINT chk_arch_subsystem_number_namespace_next CHECK (next_ordinal > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统全局编号命名空间';

CREATE TABLE arch_subsystem_number_recycled (
    allocation_scope BIGINT NOT NULL DEFAULT 0,
    namespace_code VARCHAR(64) NOT NULL,
    ordinal INT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    reservation_kind VARCHAR(16) NOT NULL,
    line_no INT NOT NULL DEFAULT 0,
    release_reason VARCHAR(16) NOT NULL,
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_scope, namespace_code, ordinal),
    KEY idx_arch_subsystem_number_recycled_application (tenant_id, application_id, reservation_kind, line_no),
    CONSTRAINT fk_arch_subsystem_number_recycled_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_number_recycled_scope CHECK (allocation_scope = 0),
    CONSTRAINT chk_arch_subsystem_number_recycled_ordinal CHECK (ordinal > 0),
    CONSTRAINT chk_arch_subsystem_number_recycled_line CHECK (line_no >= 0),
    CONSTRAINT chk_arch_subsystem_number_recycled_kind CHECK (reservation_kind IN ('LOGICAL', 'PHYSICAL')),
    CONSTRAINT chk_arch_subsystem_number_recycled_reason CHECK (release_reason IN ('REJECTED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统可复用编号池';

CREATE TABLE arch_subsystem_number_reservation (
    allocation_scope BIGINT NOT NULL DEFAULT 0,
    namespace_code VARCHAR(64) NOT NULL,
    ordinal INT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    reservation_kind VARCHAR(16) NOT NULL,
    line_no INT NOT NULL DEFAULT 0,
    reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_scope, namespace_code, ordinal),
    UNIQUE KEY uk_arch_subsystem_number_reservation_application_line (tenant_id, application_id, reservation_kind, line_no),
    CONSTRAINT fk_arch_subsystem_number_reservation_application
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES arch_subsystem_change_application (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_subsystem_number_reservation_scope CHECK (allocation_scope = 0),
    CONSTRAINT chk_arch_subsystem_number_reservation_ordinal CHECK (ordinal > 0),
    CONSTRAINT chk_arch_subsystem_number_reservation_line CHECK (line_no >= 0),
    CONSTRAINT chk_arch_subsystem_number_reservation_kind CHECK (reservation_kind IN ('LOGICAL', 'PHYSICAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构子系统活动编号保留';

-- 已发布主记录的历史内部序号和槽位永久占用；旧 code 保留，由后续编号存储在分配时额外检查。
INSERT INTO arch_subsystem_number_namespace (allocation_scope, namespace_code, next_ordinal)
SELECT 0, 'LOGICAL', COALESCE(MAX(number_sequence), 0) + 1
FROM arch_logical_subsystem;

INSERT INTO arch_subsystem_number_namespace (allocation_scope, namespace_code, next_ordinal)
SELECT 0,
       CONCAT('PHYSICAL:', logical_subsystem.number_sequence),
       COUNT(physical_subsystem.id) + 1
FROM arch_logical_subsystem logical_subsystem
LEFT JOIN arch_physical_subsystem physical_subsystem
    ON physical_subsystem.tenant_id = logical_subsystem.tenant_id
   AND physical_subsystem.logical_subsystem_id = logical_subsystem.id
GROUP BY logical_subsystem.number_sequence;
