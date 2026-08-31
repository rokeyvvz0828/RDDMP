-- REQ-20260824-052 纠偏：环境类型改由系统字典维护，架构模块只保存字典 code。
-- V91 已建立 arch_environment_type，本迁移负责存量类型迁入 ARCH_ENVIRONMENT_TYPE 并移除自维护表。

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES (360009, 1, 'ARCH_ENVIRONMENT_TYPE', '具体环境类型', 1);

-- 先迁移用户已在旧环境类型表维护过的类型。字典 key 使用命名空间，避免 sys_config 租户级唯一键冲突。
INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 930000000000100 + ROW_NUMBER() OVER (ORDER BY old_type.tenant_id, old_type.id),
       old_type.tenant_id,
       dict.id,
       CONCAT('architecture.environment-type.', LOWER(old_type.code)),
       old_type.name,
       'string',
       CASE WHEN old_type.status = 'ACTIVE' THEN 1 ELSE 0 END,
       '由旧 arch_environment_type 迁入的具体环境类型'
FROM arch_environment_type old_type
JOIN sys_dict_type dict
  ON dict.tenant_id = old_type.tenant_id
 AND dict.dict_code = 'ARCH_ENVIRONMENT_TYPE'
 AND dict.deleted = 0;

-- 初始化常用环境类型；后续增删改停均在系统字典维护。
INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 930000000000001, 1, dict.id, 'architecture.environment-type.dev', '开发环境',
       'string', 1, '具体环境类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ENVIRONMENT_TYPE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 930000000000002, 1, dict.id, 'architecture.environment-type.sit', 'SIT环境',
       'string', 1, '具体环境类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ENVIRONMENT_TYPE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 930000000000003, 1, dict.id, 'architecture.environment-type.uat', 'UAT环境',
       'string', 1, '具体环境类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ENVIRONMENT_TYPE' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 930000000000004, 1, dict.id, 'architecture.environment-type.prod', '生产环境',
       'string', 1, '具体环境类型字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_ENVIRONMENT_TYPE' AND dict.deleted = 0;

ALTER TABLE arch_environment
    ADD COLUMN type_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT 'ARCH_ENVIRONMENT_TYPE 字典 config_key' AFTER type_id;

UPDATE arch_environment environment
JOIN arch_environment_type old_type
  ON old_type.tenant_id = environment.tenant_id
 AND old_type.id = environment.type_id
SET environment.type_code = CONCAT('architecture.environment-type.', LOWER(old_type.code))
WHERE environment.type_code IS NULL;

ALTER TABLE arch_environment
    DROP FOREIGN KEY fk_arch_environment_type;

ALTER TABLE arch_environment
    DROP INDEX idx_arch_environment_type_status;

ALTER TABLE arch_environment
    MODIFY COLUMN type_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'ARCH_ENVIRONMENT_TYPE 字典 config_key';

ALTER TABLE arch_environment
    DROP COLUMN type_id;

ALTER TABLE arch_environment
    ADD KEY idx_arch_environment_type_status (tenant_id, type_code, status, id);

DROP TABLE arch_environment_type;

UPDATE sys_menu_permission
SET permission_name = '维护具体环境'
WHERE tenant_id = 1
  AND id = 8092
  AND permission_code = 'architecture:environment:manage';

CREATE TEMPORARY TABLE tmp_arch_v93_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v93_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v93_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM sys_dict_type
          WHERE tenant_id = 1
            AND dict_code = 'ARCH_ENVIRONMENT_TYPE'
            AND status = 1
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_config config
          JOIN sys_dict_type dict
            ON dict.tenant_id = config.tenant_id
           AND dict.id = config.category_id
          WHERE config.tenant_id = 1
            AND dict.dict_code = 'ARCH_ENVIRONMENT_TYPE'
            AND config.config_key IN (
                'architecture.environment-type.dev',
                'architecture.environment-type.sit',
                'architecture.environment-type.uat',
                'architecture.environment-type.prod'
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
            AND table_name = 'arch_environment'
            AND column_name = 'type_code'
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_environment'
            AND column_name = 'type_id'
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.tables
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_environment_type'
      );

DROP TEMPORARY TABLE tmp_arch_v93_seed_guard;
