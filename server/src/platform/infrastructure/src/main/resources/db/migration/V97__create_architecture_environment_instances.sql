-- REQ-20260825-053：环境部署实例与同部署单元灾备关系。
-- 资源申请办理结果逐台落成环境部署实例及实际资源分配；实例严格归属单一部署单元。

CREATE TABLE arch_environment_instance (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    environment_id BIGINT NOT NULL,
    deployment_unit_id BIGINT NOT NULL,
    deployment_unit_version_id BIGINT NULL,
    deployment_unit_version_no INT NOT NULL DEFAULT 1,
    physical_subsystem_id BIGINT NOT NULL,
    source_request_id BIGINT NOT NULL,
    source_item_id BIGINT NULL,
    machine_name VARCHAR(128) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    server_type VARCHAR(64) NULL COMMENT 'ARCH_SERVER_TYPE 字典 config_key',
    deployment_platform VARCHAR(64) NULL COMMENT '部署平台',
    network_zone VARCHAR(100) NULL COMMENT '网络分区',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/OFFLINE',
    cpu_cores BIGINT NOT NULL DEFAULT 0,
    memory_gb BIGINT NOT NULL DEFAULT 0,
    database_storage_gb BIGINT NOT NULL DEFAULT 0,
    file_storage_gb BIGINT NOT NULL DEFAULT 0,
    extra_cbs_gb BIGINT NOT NULL DEFAULT 0,
    local_disk_gb BIGINT NOT NULL DEFAULT 0,
    database_name VARCHAR(100) NULL,
    database_version VARCHAR(100) NULL,
    jdk_version VARCHAR(128) NULL,
    middleware VARCHAR(128) NULL,
    operating_system VARCHAR(128) NULL,
    needs_nft TINYINT NOT NULL DEFAULT 0,
    needs_fserver TINYINT NOT NULL DEFAULT 0,
    needs_jobexecutor TINYINT NOT NULL DEFAULT 0,
    fulfillment_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTOMATED',
    difference_reason VARCHAR(1000) NULL COMMENT '实际值与申请值差异原因',
    remark VARCHAR(1000) NULL,
    offlined_at TIMESTAMP NULL,
    offlined_by BIGINT NULL,
    offline_reason VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_env_instance_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_env_instance_no (tenant_id, instance_no),
    KEY idx_arch_env_instance_env_unit (tenant_id, environment_id, deployment_unit_id, status),
    KEY idx_arch_env_instance_physical (tenant_id, physical_subsystem_id, status),
    KEY idx_arch_env_instance_request (tenant_id, source_request_id),
    KEY idx_arch_env_instance_machine_ip (tenant_id, environment_id, ip_address, machine_name, status),
    CONSTRAINT fk_arch_env_instance_env
        FOREIGN KEY (tenant_id, environment_id)
        REFERENCES arch_environment (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_env_instance_unit
        FOREIGN KEY (tenant_id, deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_env_instance_physical
        FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_env_instance_request
        FOREIGN KEY (tenant_id, source_request_id)
        REFERENCES arch_resource_request (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_env_instance_status CHECK (status IN ('ACTIVE', 'OFFLINE')),
    CONSTRAINT chk_arch_env_instance_fulfillment_mode CHECK (fulfillment_mode IN ('MANUAL', 'AUTOMATED')),
    CONSTRAINT chk_arch_env_instance_cpu CHECK (cpu_cores >= 0),
    CONSTRAINT chk_arch_env_instance_memory CHECK (memory_gb >= 0),
    CONSTRAINT chk_arch_env_instance_db_storage CHECK (database_storage_gb >= 0),
    CONSTRAINT chk_arch_env_instance_file_storage CHECK (file_storage_gb >= 0),
    CONSTRAINT chk_arch_env_instance_extra_cbs CHECK (extra_cbs_gb >= 0),
    CONSTRAINT chk_arch_env_instance_local_disk CHECK (local_disk_gb >= 0),
    CONSTRAINT chk_arch_env_instance_needs_nft CHECK (needs_nft IN (0, 1)),
    CONSTRAINT chk_arch_env_instance_needs_fserver CHECK (needs_fserver IN (0, 1)),
    CONSTRAINT chk_arch_env_instance_needs_job CHECK (needs_jobexecutor IN (0, 1)),
    CONSTRAINT chk_arch_env_instance_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='环境部署实例';

CREATE TABLE arch_instance_disaster_recovery (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    deployment_unit_id BIGINT NOT NULL,
    primary_instance_id BIGINT NOT NULL,
    standby_instance_id BIGINT NOT NULL,
    dr_mode VARCHAR(64) NOT NULL COMMENT 'PRIMARY_STANDBY/ACTIVE_ACTIVE/COLD_STANDBY',
    description VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_instance_dr_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_instance_dr_pair (tenant_id, primary_instance_id, standby_instance_id),
    KEY idx_arch_instance_dr_standby (tenant_id, standby_instance_id),
    KEY idx_arch_instance_dr_unit (tenant_id, deployment_unit_id),
    CONSTRAINT fk_arch_instance_dr_unit
        FOREIGN KEY (tenant_id, deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_instance_dr_primary
        FOREIGN KEY (tenant_id, primary_instance_id)
        REFERENCES arch_environment_instance (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_instance_dr_standby
        FOREIGN KEY (tenant_id, standby_instance_id)
        REFERENCES arch_environment_instance (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_instance_dr_mode CHECK (dr_mode IN ('PRIMARY_STANDBY', 'ACTIVE_ACTIVE', 'COLD_STANDBY')),
    CONSTRAINT chk_arch_instance_dr_distinct CHECK (primary_instance_id <> standby_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同部署单元环境部署实例灾备拓扑关系';
