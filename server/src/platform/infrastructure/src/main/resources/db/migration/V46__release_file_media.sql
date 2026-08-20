-- Extend release delivery rows with a stable item identity so ordinary units and path-only file media can coexist.
ALTER TABLE rel_application_delivery
    ADD COLUMN item_type VARCHAR(24) NULL AFTER delivery_unit_name,
    ADD COLUMN file_path VARCHAR(1024) NULL AFTER artifact_version,
    ADD COLUMN item_key VARCHAR(128) NULL AFTER file_path;

UPDATE rel_application_delivery
SET item_type = 'DELIVERY_UNIT',
    item_key = CONCAT('UNIT:', delivery_unit_code)
WHERE item_type IS NULL OR item_key IS NULL;

ALTER TABLE rel_application_delivery
    DROP INDEX uk_rel_application_delivery,
    DROP INDEX idx_rel_delivery_conflict,
    MODIFY COLUMN item_type VARCHAR(24) NOT NULL,
    MODIFY COLUMN artifact_version VARCHAR(128) NULL,
    MODIFY COLUMN item_key VARCHAR(128) NOT NULL,
    ADD UNIQUE KEY uk_rel_application_delivery (tenant_id, application_id, item_key, application_revision),
    ADD KEY idx_rel_delivery_conflict (tenant_id, item_key, active, application_id),
    ADD CONSTRAINT chk_rel_application_delivery_item CHECK (
        (item_type = 'DELIVERY_UNIT' AND artifact_version IS NOT NULL AND file_path IS NULL)
        OR
        (item_type = 'FILE_MEDIA' AND delivery_unit_id = 'FILE' AND delivery_unit_code = 'FILE'
            AND delivery_unit_name = '文件介质' AND artifact_type = 'FILE'
            AND artifact_version IS NULL AND file_path IS NOT NULL AND CHAR_LENGTH(file_path) > 0)
    );

ALTER TABLE rel_application_relation
    ADD COLUMN item_type VARCHAR(24) NULL AFTER delivery_unit_code,
    ADD COLUMN item_key VARCHAR(128) NULL AFTER item_type,
    ADD COLUMN file_path VARCHAR(1024) NULL AFTER item_key;

UPDATE rel_application_relation
SET item_type = 'DELIVERY_UNIT',
    item_key = CONCAT('UNIT:', delivery_unit_code)
WHERE item_type IS NULL OR item_key IS NULL;

ALTER TABLE rel_application_relation
    DROP INDEX uk_rel_application_relation,
    MODIFY COLUMN item_type VARCHAR(24) NOT NULL,
    MODIFY COLUMN item_key VARCHAR(128) NOT NULL,
    ADD UNIQUE KEY uk_rel_application_relation (tenant_id, application_id, related_application_id, item_key, relation_type),
    ADD CONSTRAINT chk_rel_application_relation_item CHECK (
        (item_type = 'DELIVERY_UNIT' AND file_path IS NULL AND item_key LIKE 'UNIT:%')
        OR
        (item_type = 'FILE_MEDIA' AND delivery_unit_code = 'FILE'
            AND file_path IS NOT NULL AND CHAR_LENGTH(file_path) > 0 AND item_key LIKE 'FILE:%')
    );

ALTER TABLE rel_production_entry
    ADD COLUMN item_type VARCHAR(24) NULL AFTER delivery_unit_name,
    ADD COLUMN file_path VARCHAR(1024) NULL AFTER artifact_version,
    ADD COLUMN item_key VARCHAR(128) NULL AFTER file_path;

UPDATE rel_production_entry
SET item_type = 'DELIVERY_UNIT',
    item_key = CONCAT('UNIT:', delivery_unit_code)
WHERE item_type IS NULL OR item_key IS NULL;

ALTER TABLE rel_production_entry
    DROP INDEX uk_rel_production_source,
    DROP INDEX idx_rel_production_candidate,
    DROP INDEX idx_rel_production_current,
    MODIFY COLUMN item_type VARCHAR(24) NOT NULL,
    MODIFY COLUMN artifact_version VARCHAR(128) NULL,
    MODIFY COLUMN item_key VARCHAR(128) NOT NULL,
    ADD UNIQUE KEY uk_rel_production_source (tenant_id, window_id, application_id, item_key),
    ADD KEY idx_rel_production_candidate (tenant_id, window_id, subsystem_code, item_key, active_candidate),
    ADD KEY idx_rel_production_current (tenant_id, subsystem_code, item_key, production_result, production_at),
    ADD CONSTRAINT chk_rel_production_entry_item CHECK (
        (item_type = 'DELIVERY_UNIT' AND artifact_version IS NOT NULL AND file_path IS NULL)
        OR
        (item_type = 'FILE_MEDIA' AND delivery_unit_id = 'FILE' AND delivery_unit_code = 'FILE'
            AND delivery_unit_name = '文件介质' AND artifact_type = 'FILE'
            AND artifact_version IS NULL AND file_path IS NOT NULL AND CHAR_LENGTH(file_path) > 0)
    );
