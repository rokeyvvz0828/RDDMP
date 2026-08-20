-- 持久业务附件元数据、绑定、清理状态和不可变操作日志。
CREATE TABLE att_file (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NULL,
    file_size BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    file_extension VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'TEMP',
    uploader_id BIGINT NOT NULL,
    business_type VARCHAR(64) NULL,
    business_key VARCHAR(128) NULL,
    project_ref VARCHAR(64) NULL,
    bound_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    cleanup_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    cleanup_attempts INT NOT NULL DEFAULT 0,
    cleanup_error VARCHAR(1000) NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_att_file_object_key (object_key),
    KEY idx_att_file_owner (tenant_id, uploader_id, status),
    KEY idx_att_file_business (tenant_id, business_type, business_key, status),
    KEY idx_att_file_cleanup (status, cleanup_status, expires_at, id)
) COMMENT='持久业务附件';

CREATE TABLE att_operation_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    operation_code VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    business_type VARCHAR(64) NULL,
    business_key VARCHAR(128) NULL,
    detail_text VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_att_operation_attachment (tenant_id, attachment_id, created_at),
    KEY idx_att_operation_business (tenant_id, business_type, business_key, created_at)
) COMMENT='附件不可变操作日志';
