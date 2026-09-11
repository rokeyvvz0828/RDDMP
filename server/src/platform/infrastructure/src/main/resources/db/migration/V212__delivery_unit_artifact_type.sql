-- REQ-20260910-073：交付单元制品类型字段，并接入平台统一字典（参数管理）。
-- 只追加，不修改既有迁移。MySQL 8.4。
-- config_key 使用命名空间：sys_config 存在租户级唯一键 (tenant_id, config_key, deleted)，
-- 与既有 architecture.* 参数保持一致；字典项可在“系统管理 → 参数管理”内维护。

ALTER TABLE arch_delivery_unit
    ADD COLUMN artifact_type_code VARCHAR(64) NULL COMMENT '制品类型字典 code（sys_dict_type.dict_code = ARCH_ARTIFACT_TYPE）' AFTER name;

INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
SELECT 360016, 1, 'ARCH_ARTIFACT_TYPE', '制品类型', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE id = 360016);

INSERT INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 360107, 1, dict.id, 'architecture.artifact-type.container', '容器', 'string', 1, '交付单元制品类型选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ARTIFACT_TYPE' AND dict.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 360107);

INSERT INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 360108, 1, dict.id, 'architecture.artifact-type.archive', '压缩包', 'string', 1, '交付单元制品类型选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ARTIFACT_TYPE' AND dict.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 360108);

INSERT INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 360109, 1, dict.id, 'architecture.artifact-type.script', '脚本', 'string', 1, '交付单元制品类型选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ARTIFACT_TYPE' AND dict.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 360109);

-- 身份不符时失败关闭，避免静默复用其他字典类别或参数项。
CREATE TEMPORARY TABLE tmp_arch_v203_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v203_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v203_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_dict_type
          WHERE id = 360016
            AND tenant_id = 1
            AND dict_code = 'ARCH_ARTIFACT_TYPE'
            AND dict_name = '制品类型'
            AND status = 1
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_config config
          JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id
          WHERE config.id = 360107
            AND config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ARTIFACT_TYPE'
            AND config.config_key = 'architecture.artifact-type.container'
            AND config.config_value = '容器'
            AND config.status = 1
            AND config.deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_config config
          JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id
          WHERE config.id = 360108
            AND config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ARTIFACT_TYPE'
            AND config.config_key = 'architecture.artifact-type.archive'
            AND config.config_value = '压缩包'
            AND config.status = 1
            AND config.deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_config config
          JOIN sys_dict_type dict ON dict.id = config.category_id AND dict.tenant_id = config.tenant_id
          WHERE config.id = 360109
            AND config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ARTIFACT_TYPE'
            AND config.config_key = 'architecture.artifact-type.script'
            AND config.config_value = '脚本'
            AND config.status = 1
            AND config.deleted = 0
      );

DROP TEMPORARY TABLE tmp_arch_v203_seed_guard;
