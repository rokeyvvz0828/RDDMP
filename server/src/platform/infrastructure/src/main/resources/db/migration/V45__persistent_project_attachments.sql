-- 项目持久附件元数据；对象文件保存于 MinIO，object_key 只在服务端使用。
CREATE TABLE sys_attachment (
    id BIGINT PRIMARY KEY COMMENT '附件主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型，例如 PROJECT',
    business_id BIGINT NOT NULL COMMENT '业务主键，例如项目主键',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(255) NOT NULL COMMENT '文件媒体类型',
    file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
    object_key VARCHAR(512) NOT NULL COMMENT 'MinIO 对象键，仅服务端使用',
    uploader_id BIGINT NOT NULL COMMENT '上传人用户主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0否、1是',
    KEY idx_sys_attachment_scope (tenant_id, business_type, business_id, deleted, created_at, id),
    KEY idx_sys_attachment_uploader (tenant_id, uploader_id, deleted),
    UNIQUE KEY uk_sys_attachment_object_key (object_key)
) COMMENT='平台持久附件元数据表';
