-- REQ-20260911-074: normalize delivery-unit artifact types for release-management integration.
-- Keep the two canonical codes aligned with release ArtifactType: IMAGE and BINARY.

CREATE TEMPORARY TABLE tmp_arch_v213_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v213_guard CHECK (marker = 0)
) ENGINE=InnoDB;

-- Fail closed if the stable V212 rows were repurposed or canonical keys are already owned elsewhere.
INSERT INTO tmp_arch_v213_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id
          WHERE config.id = 360107 AND config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ARTIFACT_TYPE'
            AND config.config_key = 'architecture.artifact-type.container'
            AND config.deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id
          WHERE config.id = 360108 AND config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ARTIFACT_TYPE'
            AND config.config_key = 'architecture.artifact-type.archive'
            AND config.deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id
          WHERE config.id = 360109 AND config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ARTIFACT_TYPE'
            AND config.config_key = 'architecture.artifact-type.script'
            AND config.deleted = 0
      )
   OR EXISTS (
          SELECT 1 FROM sys_config
          WHERE tenant_id = 1 AND config_key IN ('IMAGE', 'BINARY')
            AND id NOT IN (360107, 360108) AND deleted = 0
      );

UPDATE arch_delivery_unit
SET artifact_type_code = CASE
    WHEN LOWER(artifact_type_code) IN ('architecture.artifact-type.container', 'container', 'image') THEN 'IMAGE'
    WHEN LOWER(artifact_type_code) IN ('architecture.artifact-type.archive', 'architecture.artifact-type.script',
                                      'archive', 'script', 'binary') THEN 'BINARY'
    ELSE artifact_type_code
END
WHERE artifact_type_code IS NOT NULL;

UPDATE sys_config
SET config_key = 'IMAGE', config_value = '镜像', config_type = 'string', status = 1,
    remark = '交付单元制品类型选项'
WHERE id = 360107 AND tenant_id = 1 AND deleted = 0;

UPDATE sys_config
SET config_key = 'BINARY', config_value = '二进制', config_type = 'string', status = 1,
    remark = '交付单元制品类型选项'
WHERE id = 360108 AND tenant_id = 1 AND deleted = 0;

UPDATE sys_config
SET status = 0, remark = '已归并至 BINARY，保留用于历史迁移审计'
WHERE id = 360109 AND tenant_id = 1 AND deleted = 0;

DROP TEMPORARY TABLE tmp_arch_v213_guard;
