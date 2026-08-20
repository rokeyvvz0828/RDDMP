-- 申请可编辑明细采用前向修订，旧修订保留为非活动记录，禁止物理删除业务历史。
ALTER TABLE rel_application_delivery
    DROP INDEX uk_rel_application_delivery,
    DROP INDEX idx_rel_delivery_conflict,
    ADD COLUMN application_revision BIGINT NOT NULL DEFAULT 0 AFTER artifact_version,
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1 AFTER application_revision,
    ADD UNIQUE KEY uk_rel_application_delivery (tenant_id, application_id, delivery_unit_code, application_revision),
    ADD KEY idx_rel_delivery_conflict (tenant_id, delivery_unit_code, active, application_id);

ALTER TABLE rel_application_requirement
    DROP INDEX uk_rel_application_requirement,
    DROP INDEX idx_rel_requirement_code,
    ADD COLUMN application_revision BIGINT NOT NULL DEFAULT 0 AFTER requirement_code,
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1 AFTER application_revision,
    ADD UNIQUE KEY uk_rel_application_requirement (tenant_id, application_id, requirement_code, application_revision),
    ADD KEY idx_rel_requirement_code (tenant_id, requirement_code, active);

ALTER TABLE rel_application_attachment
    DROP INDEX uk_rel_application_attachment,
    DROP INDEX idx_rel_attachment_id,
    ADD COLUMN application_revision BIGINT NOT NULL DEFAULT 0 AFTER file_name_snapshot,
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1 AFTER application_revision,
    ADD UNIQUE KEY uk_rel_application_attachment (tenant_id, application_id, attachment_id, application_revision),
    ADD KEY idx_rel_attachment_id (tenant_id, attachment_id, active);
