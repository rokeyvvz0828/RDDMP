-- REQ-20260824-052 纠偏：资源申请登记字段继续收口到物理子系统、部署单元和字典。
-- 不修改历史迁移；本迁移移除明细层物理字段，补齐整数资源规格和技术栈字典。

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES
    (360012, 1, 'ARCH_JDK_VERSION', 'JDK版本', 1),
    (360013, 1, 'ARCH_MIDDLEWARE', '中间件', 1),
    (360014, 1, 'ARCH_OPERATING_SYSTEM', '产品化操作系统', 1);

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000001, 1, dict.id, 'architecture.jdk.jdk8', 'JDK 8',
       'string', 1, '资源申请 JDK 字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_JDK_VERSION' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000002, 1, dict.id, 'architecture.jdk.jdk17', 'JDK 17',
       'string', 1, '资源申请 JDK 字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_JDK_VERSION' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000003, 1, dict.id, 'architecture.jdk.openjdk17', 'OpenJDK 17',
       'string', 1, '资源申请 JDK 字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_JDK_VERSION' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000101, 1, dict.id, 'architecture.middleware.tomcat9', 'Tomcat 9',
       'string', 1, '资源申请中间件字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_MIDDLEWARE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000102, 1, dict.id, 'architecture.middleware.ibm-mq-9-1', 'IBM MQ 9.1',
       'string', 1, '资源申请中间件字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_MIDDLEWARE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000201, 1, dict.id, 'architecture.os.rhel8-5', 'RHEL 8.5',
       'string', 1, '资源申请操作系统字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_OPERATING_SYSTEM' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000202, 1, dict.id, 'architecture.os.suse12-sp5', 'SUSE Linux 12 SP5',
       'string', 1, '资源申请操作系统字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_OPERATING_SYSTEM' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 960000000000203, 1, dict.id, 'architecture.os.kylin-v10-sp2', '麒麟 V10 SP2',
       'string', 1, '资源申请操作系统字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_OPERATING_SYSTEM' AND dict.deleted = 0;

ALTER TABLE arch_resource_request_item
    DROP CHECK chk_arch_resource_request_item_cpu,
    DROP CHECK chk_arch_resource_request_item_memory,
    DROP CHECK chk_arch_resource_request_item_storage,
    DROP CHECK chk_arch_resource_request_item_db_storage,
    DROP CHECK chk_arch_resource_request_item_sidecar_cpu,
    DROP CHECK chk_arch_resource_request_item_sidecar_memory,
    DROP CHECK chk_arch_resource_request_item_extra_cbs,
    DROP CHECK chk_arch_resource_request_item_local_disk;

UPDATE arch_resource_request_item
SET database_storage_gb = CEILING(database_storage_gb),
    storage_gb = CEILING(storage_gb),
    cpu_cores = CEILING(cpu_cores),
    memory_gb = CEILING(memory_gb),
    sidecar_cpu_cores = CEILING(sidecar_cpu_cores),
    sidecar_memory_gb = CEILING(sidecar_memory_gb),
    extra_cbs_gb = CEILING(extra_cbs_gb),
    local_disk_gb = CEILING(local_disk_gb);

ALTER TABLE arch_resource_request_item
    MODIFY COLUMN database_storage_gb BIGINT NOT NULL DEFAULT 0 COMMENT '数据库存储需求（G）',
    MODIFY COLUMN storage_gb BIGINT NOT NULL DEFAULT 0 COMMENT '文件存储需求（G）',
    MODIFY COLUMN cpu_cores BIGINT NOT NULL DEFAULT 0 COMMENT 'CPU',
    MODIFY COLUMN memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT '内存（G）',
    MODIFY COLUMN sidecar_cpu_cores BIGINT NOT NULL DEFAULT 0 COMMENT '总边车CPU（已乘以节点数）',
    MODIFY COLUMN sidecar_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT '总边车内存（已乘以节点数）',
    MODIFY COLUMN extra_cbs_gb BIGINT NOT NULL DEFAULT 0 COMMENT '额外的CBS容量C',
    MODIFY COLUMN local_disk_gb BIGINT NOT NULL DEFAULT 0 COMMENT '本地盘需求（G）',
    DROP COLUMN business_continuity_level,
    DROP COLUMN collected_system_level,
    DROP COLUMN business_group_name,
    DROP COLUMN deployment_platform,
    DROP COLUMN disaster_recovery_mode,
    ADD CONSTRAINT chk_arch_resource_request_item_cpu CHECK (cpu_cores >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_memory CHECK (memory_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_storage CHECK (storage_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_db_storage CHECK (database_storage_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_sidecar_cpu CHECK (sidecar_cpu_cores >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_sidecar_memory CHECK (sidecar_memory_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_extra_cbs CHECK (extra_cbs_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_local_disk CHECK (local_disk_gb >= 0);

UPDATE arch_resource_request_item
SET jdk_version = CASE
        WHEN LOWER(jdk_version) IN ('jdk1.8', 'jdk 1.8', 'jdk8', 'jdk 8') THEN 'architecture.jdk.jdk8'
        WHEN LOWER(jdk_version) IN ('jdk17', 'jdk 17') THEN 'architecture.jdk.jdk17'
        WHEN LOWER(jdk_version) IN ('openjdk17', 'openjdk 17', 'openjdk 17.0.4') THEN 'architecture.jdk.openjdk17'
        ELSE jdk_version
    END,
    middleware = CASE
        WHEN LOWER(middleware) LIKE 'tomcat%' THEN 'architecture.middleware.tomcat9'
        WHEN LOWER(middleware) LIKE 'ibm_mq 9.1%' OR LOWER(middleware) LIKE 'ibm mq 9.1%' THEN 'architecture.middleware.ibm-mq-9-1'
        ELSE middleware
    END,
    operating_system = CASE
        WHEN LOWER(operating_system) IN ('rhel8.5', 'rhel 8.5') THEN 'architecture.os.rhel8-5'
        WHEN LOWER(operating_system) IN ('suse linux12 sp5', 'suse linux 12 sp5', 'suse12 sp5') THEN 'architecture.os.suse12-sp5'
        WHEN LOWER(operating_system) IN ('麒麟v10 sp2', '麒麟 v10 sp2') THEN 'architecture.os.kylin-v10-sp2'
        ELSE operating_system
    END
WHERE jdk_version IS NOT NULL
   OR middleware IS NOT NULL
   OR operating_system IS NOT NULL;

CREATE TEMPORARY TABLE tmp_arch_v96_registration_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v96_registration_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v96_registration_guard (marker)
SELECT 1
WHERE EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request_item'
            AND column_name IN (
                'business_continuity_level',
                'collected_system_level',
                'business_group_name',
                'deployment_platform',
                'disaster_recovery_mode'
            )
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request_item'
            AND column_name IN (
                'database_storage_gb',
                'storage_gb',
                'cpu_cores',
                'memory_gb',
                'sidecar_cpu_cores',
                'sidecar_memory_gb',
                'extra_cbs_gb',
                'local_disk_gb'
            )
            AND data_type NOT IN ('bigint')
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict
            ON dict.tenant_id = config.tenant_id
           AND dict.id = config.category_id
          WHERE config.tenant_id = 1
            AND dict.dict_code IN ('ARCH_JDK_VERSION', 'ARCH_MIDDLEWARE', 'ARCH_OPERATING_SYSTEM')
            AND config.status = 1
            AND config.deleted = 0
          GROUP BY config.tenant_id
          HAVING COUNT(DISTINCT dict.dict_code) = 3
      );

DROP TEMPORARY TABLE tmp_arch_v96_registration_guard;
