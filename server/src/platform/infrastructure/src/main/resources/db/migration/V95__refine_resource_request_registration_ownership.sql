-- REQ-20260824-052 纠偏：资源申请字段归属回到物理子系统、部署单元和系统字典。
-- 资源申请只填写申请联系人与资源需求；实际资源分配仍留给后续批次。

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES
    (360010, 1, 'ARCH_SERVER_TYPE', '服务器类型', 1),
    (360011, 1, 'ARCH_DISASTER_RECOVERY_MODE', '灾备模式', 1);

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000001, 1, dict.id, 'architecture.server-type.container', '容器',
       'string', 1, '服务器类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_SERVER_TYPE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000002, 1, dict.id, 'architecture.server-type.physical-machine', '物理机',
       'string', 1, '服务器类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_SERVER_TYPE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000003, 1, dict.id, 'architecture.server-type.virtual-machine', '虚拟机',
       'string', 1, '服务器类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_SERVER_TYPE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000101, 1, dict.id, 'architecture.disaster-recovery.active-standby', '主备',
       'string', 1, '灾备模式字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_DISASTER_RECOVERY_MODE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000102, 1, dict.id, 'architecture.disaster-recovery.active-active', '双活',
       'string', 1, '灾备模式字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_DISASTER_RECOVERY_MODE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000103, 1, dict.id, 'architecture.disaster-recovery.cold-standby', '冷备',
       'string', 1, '灾备模式字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_DISASTER_RECOVERY_MODE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 950000000000104, 1, dict.id, 'architecture.disaster-recovery.none', '无灾备',
       'string', 1, '灾备模式字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_DISASTER_RECOVERY_MODE' AND dict.deleted = 0;

ALTER TABLE arch_physical_subsystem
    ADD COLUMN business_continuity_level VARCHAR(32) NULL COMMENT '农信业务连续性等级' AFTER business_group_name,
    ADD COLUMN collected_system_level VARCHAR(32) NULL COMMENT '项目组收集系统等级' AFTER business_continuity_level,
    ADD COLUMN deployment_platform VARCHAR(64) NULL COMMENT '部署平台' AFTER collected_system_level,
    ADD COLUMN disaster_recovery_mode VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'ARCH_DISASTER_RECOVERY_MODE 字典 config_key' AFTER deployment_platform;

ALTER TABLE arch_subsystem_physical_draft
    ADD COLUMN business_continuity_level VARCHAR(32) NULL COMMENT '农信业务连续性等级' AFTER business_group_name,
    ADD COLUMN collected_system_level VARCHAR(32) NULL COMMENT '项目组收集系统等级' AFTER business_continuity_level,
    ADD COLUMN deployment_platform VARCHAR(64) NULL COMMENT '部署平台' AFTER collected_system_level,
    ADD COLUMN disaster_recovery_mode VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'ARCH_DISASTER_RECOVERY_MODE 字典 config_key' AFTER deployment_platform;

ALTER TABLE arch_deployment_unit
    ADD COLUMN related_deployment_unit_name VARCHAR(500) NULL COMMENT '关联部署单元名称' AFTER name,
    ADD COLUMN deployment_unit_type VARCHAR(32) NULL COMMENT '资源登记表部署单元类型：DB/AP/WB/PL' AFTER related_deployment_unit_name;

UPDATE arch_deployment_unit
SET deployment_unit_type = CASE
    WHEN kind = 'DATABASE' THEN 'DB'
    ELSE 'AP'
END
WHERE deployment_unit_type IS NULL;

ALTER TABLE arch_deployment_unit
    MODIFY COLUMN deployment_unit_type VARCHAR(32) NOT NULL COMMENT '资源登记表部署单元类型：DB/AP/WB/PL';

ALTER TABLE arch_deployment_unit_version
    ADD COLUMN related_deployment_unit_name VARCHAR(500) NULL COMMENT '关联部署单元名称' AFTER name,
    ADD COLUMN deployment_unit_type VARCHAR(32) NULL COMMENT '资源登记表部署单元类型：DB/AP/WB/PL' AFTER related_deployment_unit_name;

UPDATE arch_deployment_unit_version version
JOIN arch_deployment_unit unit
  ON unit.tenant_id = version.tenant_id
 AND unit.id = version.unit_id
SET version.related_deployment_unit_name = unit.related_deployment_unit_name,
    version.deployment_unit_type = unit.deployment_unit_type
WHERE version.deployment_unit_type IS NULL;

ALTER TABLE arch_deployment_unit_version
    MODIFY COLUMN deployment_unit_type VARCHAR(32) NOT NULL COMMENT '资源登记表部署单元类型：DB/AP/WB/PL';

ALTER TABLE arch_resource_request
    ADD COLUMN contact_user_id BIGINT NULL COMMENT '资源申请联系人用户ID' AFTER applicant_id;

UPDATE arch_resource_request
SET contact_user_id = applicant_id
WHERE contact_user_id IS NULL;

ALTER TABLE arch_resource_request
    MODIFY COLUMN contact_user_id BIGINT NOT NULL COMMENT '资源申请联系人用户ID',
    ADD KEY idx_arch_resource_request_contact (tenant_id, contact_user_id, status, updated_at),
    DROP COLUMN source_task_id;

UPDATE arch_resource_request_item
SET server_type = CASE server_type
    WHEN '容器' THEN 'architecture.server-type.container'
    WHEN '物理机' THEN 'architecture.server-type.physical-machine'
    WHEN '虚拟机' THEN 'architecture.server-type.virtual-machine'
    ELSE server_type
END
WHERE server_type IN ('容器', '物理机', '虚拟机');

UPDATE arch_resource_request_item
SET disaster_recovery_mode = CASE disaster_recovery_mode
    WHEN '主备' THEN 'architecture.disaster-recovery.active-standby'
    WHEN '双活' THEN 'architecture.disaster-recovery.active-active'
    WHEN '冷备' THEN 'architecture.disaster-recovery.cold-standby'
    WHEN '主备（冷备）' THEN 'architecture.disaster-recovery.cold-standby'
    WHEN '无灾备' THEN 'architecture.disaster-recovery.none'
    ELSE disaster_recovery_mode
END
WHERE disaster_recovery_mode IN ('主备', '双活', '冷备', '主备（冷备）', '无灾备');

ALTER TABLE arch_resource_request_item
    DROP COLUMN confirmer_name,
    DROP COLUMN confirmer_contact;

CREATE TEMPORARY TABLE tmp_arch_v95_registration_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v95_registration_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v95_registration_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict
            ON dict.tenant_id = config.tenant_id
           AND dict.id = config.category_id
          WHERE config.tenant_id = 1
            AND dict.dict_code = 'ARCH_SERVER_TYPE'
            AND config.config_key IN (
                'architecture.server-type.container',
                'architecture.server-type.physical-machine',
                'architecture.server-type.virtual-machine'
            )
            AND config.status = 1
            AND config.deleted = 0
          GROUP BY config.tenant_id
          HAVING COUNT(*) = 3
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict
            ON dict.tenant_id = config.tenant_id
           AND dict.id = config.category_id
          WHERE config.tenant_id = 1
            AND dict.dict_code = 'ARCH_DISASTER_RECOVERY_MODE'
            AND config.config_key IN (
                'architecture.disaster-recovery.active-standby',
                'architecture.disaster-recovery.active-active',
                'architecture.disaster-recovery.cold-standby',
                'architecture.disaster-recovery.none'
            )
            AND config.status = 1
            AND config.deleted = 0
          GROUP BY config.tenant_id
          HAVING COUNT(*) = 4
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request'
            AND column_name = 'contact_user_id'
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request'
            AND column_name = 'source_task_id'
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request_item'
            AND column_name IN ('confirmer_name', 'confirmer_contact')
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_physical_subsystem'
            AND column_name = 'disaster_recovery_mode'
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_deployment_unit'
            AND column_name = 'deployment_unit_type'
      );

DROP TEMPORARY TABLE tmp_arch_v95_registration_guard;
