ALTER TABLE sys_notification
    ADD COLUMN module_code VARCHAR(64) NULL COMMENT '业务板块编码' AFTER event_id,
    ADD COLUMN module_name VARCHAR(128) NULL COMMENT '业务板块名称' AFTER module_code;

UPDATE sys_notification SET
    module_code = CASE WHEN business_type IN ('release', 'release_application') THEN 'release' WHEN business_type = 'delivery' THEN 'delivery' WHEN business_type = 'system' THEN 'system' ELSE LEFT(CONCAT('business_', COALESCE(NULLIF(REGEXP_REPLACE(LOWER(TRIM(business_type)), '[^a-z0-9_-]', '_'), ''), 'legacy')), 64) END,
    module_name = CASE WHEN business_type IN ('release', 'release_application') THEN '配置管理' WHEN business_type = 'delivery' THEN '交付示范中心' WHEN business_type = 'system' THEN '系统管理' ELSE source_name END
WHERE module_code IS NULL OR module_name IS NULL;

ALTER TABLE sys_notification
    MODIFY COLUMN module_code VARCHAR(64) NOT NULL COMMENT '业务板块编码',
    MODIFY COLUMN module_name VARCHAR(128) NOT NULL COMMENT '业务板块名称',
    ADD KEY idx_sys_notification_module (tenant_id, module_code, created_at, id);

ALTER TABLE wf_instance
    ADD COLUMN business_module_code VARCHAR(64) NULL COMMENT '业务板块编码' AFTER business_type,
    ADD COLUMN business_module_name VARCHAR(128) NULL COMMENT '业务板块名称' AFTER business_module_code;

UPDATE wf_instance SET
    business_module_code = CASE WHEN business_type IN ('release', 'release_application') THEN 'release' WHEN business_type = 'delivery' THEN 'delivery' WHEN business_type = 'system' THEN 'system' ELSE LEFT(CONCAT('business_', COALESCE(NULLIF(REGEXP_REPLACE(LOWER(TRIM(business_type)), '[^a-z0-9_-]', '_'), ''), 'legacy')), 64) END,
    business_module_name = CASE WHEN business_type IN ('release', 'release_application') THEN '配置管理' WHEN business_type = 'delivery' THEN '交付示范中心' WHEN business_type = 'system' THEN '系统管理' ELSE business_type END
WHERE business_type IS NOT NULL AND (business_module_code IS NULL OR business_module_name IS NULL);

ALTER TABLE wf_lifecycle_event
    ADD COLUMN business_module_code VARCHAR(64) NULL COMMENT '业务板块编码' AFTER business_type,
    ADD COLUMN business_module_name VARCHAR(128) NULL COMMENT '业务板块名称' AFTER business_module_code;

UPDATE wf_lifecycle_event SET
    business_module_code = CASE WHEN business_type IN ('release', 'release_application') THEN 'release' WHEN business_type = 'delivery' THEN 'delivery' WHEN business_type = 'system' THEN 'system' ELSE LEFT(CONCAT('business_', COALESCE(NULLIF(REGEXP_REPLACE(LOWER(TRIM(business_type)), '[^a-z0-9_-]', '_'), ''), 'legacy')), 64) END,
    business_module_name = CASE WHEN business_type IN ('release', 'release_application') THEN '配置管理' WHEN business_type = 'delivery' THEN '交付示范中心' WHEN business_type = 'system' THEN '系统管理' ELSE business_type END
WHERE business_module_code IS NULL OR business_module_name IS NULL;
