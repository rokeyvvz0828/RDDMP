-- REQ-20260823-049：部署单元、发布版本、编号分配与初始化导入批次。
-- 只追加，不修改 V1-V84。MySQL 8.4。

CREATE TABLE arch_deployment_unit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '部署单元编号 D<物理编号><三位序号>；首次发布时分配，之后不可修改',
    physical_subsystem_id BIGINT NOT NULL COMMENT '归属物理子系统；发布后不可变更',
    short_name VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL COMMENT '显示名称，租户+物理子系统内永久唯一',
    kind VARCHAR(32) NOT NULL COMMENT 'APPLICATION/DATABASE/MQ',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/VOIDED',
    current_version INT NOT NULL DEFAULT 0 COMMENT '当前版本号，首次发布为 1',
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_deployment_unit_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_deployment_unit_code (tenant_id, code),
    UNIQUE KEY uk_arch_deployment_unit_name (tenant_id, physical_subsystem_id, name),
    KEY idx_arch_deployment_unit_physical (tenant_id, physical_subsystem_id, status, id),
    KEY idx_arch_deployment_unit_kind (tenant_id, kind, status, id),
    CONSTRAINT fk_arch_deployment_unit_physical
        FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_deployment_unit_kind CHECK (kind IN ('APPLICATION', 'DATABASE', 'MQ')),
    CONSTRAINT chk_arch_deployment_unit_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'VOIDED')),
    CONSTRAINT chk_arch_deployment_unit_code CHECK (
        code IS NULL OR (LENGTH(code) BETWEEN 8 AND 32 AND code LIKE 'D%')
    ),
    CONSTRAINT chk_arch_deployment_unit_version CHECK (current_version >= 0),
    CONSTRAINT chk_arch_deployment_unit_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元主记录';

CREATE TABLE arch_deployment_unit_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    version_no INT NOT NULL COMMENT '从 1 递增，发布后不可改写',
    short_name VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    published_by BIGINT NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_deployment_unit_version_tenant_unit (tenant_id, unit_id, version_no),
    KEY idx_arch_deployment_unit_version_published (tenant_id, unit_id, published_at),
    CONSTRAINT fk_arch_deployment_unit_version_unit
        FOREIGN KEY (tenant_id, unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_deployment_unit_version_no CHECK (version_no > 0),
    CONSTRAINT chk_arch_deployment_unit_version_kind CHECK (kind IN ('APPLICATION', 'DATABASE', 'MQ'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元发布版本快照（不可改写）';

CREATE TABLE arch_deployment_unit_number_seq (
    tenant_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    next_ordinal INT NOT NULL COMMENT '该物理子系统下待分配序号 1..999',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, physical_subsystem_id),
    CONSTRAINT fk_arch_deployment_unit_number_seq_physical
        FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_deployment_unit_number_seq_next CHECK (next_ordinal BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元编号分配序号（行锁分配，永久占用）';

CREATE TABLE arch_deployment_unit_import_batch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0 COMMENT '确认时发现已存在的幂等跳过行',
    status VARCHAR(16) NOT NULL DEFAULT 'PREVIEW' COMMENT 'PREVIEW/SUCCESS/PARTIAL/FAILED',
    error_message VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_deployment_unit_import_batch_tenant (tenant_id, id),
    KEY idx_arch_deployment_unit_import_batch_created (tenant_id, created_at),
    CONSTRAINT chk_arch_deployment_unit_import_batch_status CHECK (status IN ('PREVIEW', 'SUCCESS', 'PARTIAL', 'FAILED')),
    CONSTRAINT chk_arch_deployment_unit_import_batch_rows CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND success_rows >= 0 AND failed_rows >= 0 AND skipped_rows >= 0
    ),
    CONSTRAINT chk_arch_deployment_unit_import_batch_size CHECK (file_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元导入批次';

CREATE TABLE arch_deployment_unit_import_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    line_no INT NOT NULL COMMENT 'Excel 数据行号（从 1 起）',
    raw_json JSON NULL COMMENT '原始行快照',
    row_status VARCHAR(16) NOT NULL COMMENT 'VALID/INVALID/SUCCESS/FAILED/SKIPPED',
    error_message VARCHAR(1000) NULL COMMENT '失败原因或预览说明（如已存在将跳过）',
    note VARCHAR(500) NULL COMMENT '非错误的状态说明',
    unit_id BIGINT NULL COMMENT '写入成功的部署单元',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_deployment_unit_import_item_tenant_line (tenant_id, batch_id, line_no),
    KEY idx_arch_deployment_unit_import_item_batch (tenant_id, batch_id, row_status),
    CONSTRAINT fk_arch_deployment_unit_import_item_batch
        FOREIGN KEY (tenant_id, batch_id)
        REFERENCES arch_deployment_unit_import_batch (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_deployment_unit_import_item_unit
        FOREIGN KEY (tenant_id, unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_deployment_unit_import_item_status CHECK (row_status IN ('VALID', 'INVALID', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_arch_deployment_unit_import_item_line CHECK (line_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元导入行明细';
