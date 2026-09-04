-- REQ-20260901-057：部署单元完整名称、三种业务类型与双向结构化关联。
-- 当前仍是早期开发阶段：旧显示名称按永久 code 规范化，不保留简称、自由文本关联和登记类型兼容列。

-- 旧约束只允许 APPLICATION/DATABASE/MQ，必须先移除再把遗留 MQ 转换为 WEB。
ALTER TABLE arch_deployment_unit
    DROP CHECK chk_arch_deployment_unit_kind;

ALTER TABLE arch_deployment_unit_version
    DROP CHECK chk_arch_deployment_unit_version_kind;

UPDATE arch_deployment_unit
SET kind = 'WEB'
WHERE kind = 'MQ';

UPDATE arch_deployment_unit_version
SET kind = 'WEB'
WHERE kind = 'MQ';

UPDATE arch_deployment_unit
SET name = CONCAT(
        REGEXP_REPLACE(UPPER(code), '[^A-Z0-9]', ''),
        CASE kind WHEN 'DATABASE' THEN '_DB' WHEN 'WEB' THEN '_WB' ELSE '_AP' END
    );

UPDATE arch_deployment_unit_version version
JOIN arch_deployment_unit unit
  ON unit.tenant_id = version.tenant_id
 AND unit.id = version.unit_id
SET version.name = unit.name,
    version.kind = unit.kind;

ALTER TABLE arch_deployment_unit
    DROP INDEX uk_arch_deployment_unit_name,
    DROP COLUMN short_name,
    DROP COLUMN related_deployment_unit_name,
    DROP COLUMN deployment_unit_type,
    ADD UNIQUE KEY uk_arch_deployment_unit_name (tenant_id, name),
    ADD CONSTRAINT chk_arch_deployment_unit_kind
        CHECK (kind IN ('APPLICATION', 'DATABASE', 'WEB')),
    ADD CONSTRAINT chk_arch_deployment_unit_name
        CHECK (REGEXP_LIKE(name, '^[A-Z0-9]+_[A-Z0-9]{1,8}$', 'c'));

ALTER TABLE arch_deployment_unit_version
    DROP COLUMN short_name,
    DROP COLUMN related_deployment_unit_name,
    DROP COLUMN deployment_unit_type,
    ADD CONSTRAINT chk_arch_deployment_unit_version_kind
        CHECK (kind IN ('APPLICATION', 'DATABASE', 'WEB')),
    ADD CONSTRAINT chk_arch_deployment_unit_version_name
        CHECK (REGEXP_LIKE(name, '^[A-Z0-9]+_[A-Z0-9]{1,8}$', 'c'));

ALTER TABLE arch_resource_request_item
    DROP COLUMN related_deployment_unit_name,
    DROP COLUMN deployment_unit_type;

CREATE TABLE arch_deployment_unit_relation (
    tenant_id BIGINT NOT NULL,
    unit_low_id BIGINT NOT NULL COMMENT '规范化较小部署单元ID',
    unit_high_id BIGINT NOT NULL COMMENT '规范化较大部署单元ID',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, unit_low_id, unit_high_id),
    KEY idx_arch_deployment_unit_relation_high (tenant_id, unit_high_id, unit_low_id),
    CONSTRAINT fk_arch_deployment_unit_relation_low
        FOREIGN KEY (tenant_id, unit_low_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_deployment_unit_relation_high
        FOREIGN KEY (tenant_id, unit_high_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_deployment_unit_relation_order CHECK (unit_low_id < unit_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元双向无向当前关系';

CREATE TABLE arch_deployment_unit_relation_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    source_unit_id BIGINT NOT NULL COMMENT '发起编辑并发布版本的部署单元',
    unit_low_id BIGINT NOT NULL,
    unit_high_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL COMMENT 'LINK/UNLINK',
    changed_by BIGINT NOT NULL,
    source_version_no INT NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_arch_deployment_unit_relation_history_source
        (tenant_id, source_unit_id, changed_at, id),
    KEY idx_arch_deployment_unit_relation_history_pair
        (tenant_id, unit_low_id, unit_high_id, changed_at, id),
    CONSTRAINT fk_arch_deployment_unit_relation_history_source
        FOREIGN KEY (tenant_id, source_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_deployment_unit_relation_history_low
        FOREIGN KEY (tenant_id, unit_low_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_deployment_unit_relation_history_high
        FOREIGN KEY (tenant_id, unit_high_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_deployment_unit_relation_history_order CHECK (unit_low_id < unit_high_id),
    CONSTRAINT chk_arch_deployment_unit_relation_history_action CHECK (action IN ('LINK', 'UNLINK')),
    CONSTRAINT chk_arch_deployment_unit_relation_history_version CHECK (source_version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署单元关联变更历史（只追加）';
