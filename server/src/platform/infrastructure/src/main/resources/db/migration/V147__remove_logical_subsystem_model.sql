-- REQ-20260831-001：移除独立逻辑子系统模型，物理子系统成为架构主数据根对象。

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES (360015, 1, 'ARCH_BUSINESS_COMPONENT', '业务组件编号', 1);

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 970000000000001, 1, dict.id, 'architecture.business-component.employee-portal',
       '员工门户组件', 'string', 1, '业务组件编号字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_BUSINESS_COMPONENT' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 970000000000002, 1, dict.id, 'architecture.business-component.mobile-channel',
       '移动渠道组件', 'string', 1, '业务组件编号字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_BUSINESS_COMPONENT' AND dict.deleted = 0;

INSERT IGNORE INTO sys_config
    (id, tenant_id, category_id, config_key, config_value, config_type, status, remark)
SELECT 970000000000003, 1, dict.id, 'architecture.business-component.delivery-operations',
       '交付运营组件', 'string', 1, '业务组件编号字典选项'
FROM sys_dict_type dict
WHERE dict.tenant_id = 1 AND dict.dict_code = 'ARCH_BUSINESS_COMPONENT' AND dict.deleted = 0;

ALTER TABLE arch_physical_subsystem
    ADD COLUMN logical_subsystem_name VARCHAR(200) NULL COMMENT '逻辑子系统名称，物理侧可选文本' AFTER name,
    ADD COLUMN business_component_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'ARCH_BUSINESS_COMPONENT 字典 config_key' AFTER logical_subsystem_name;

UPDATE arch_physical_subsystem physical_subsystem
LEFT JOIN arch_logical_subsystem logical_subsystem
  ON logical_subsystem.tenant_id = physical_subsystem.tenant_id
 AND logical_subsystem.id = physical_subsystem.logical_subsystem_id
SET physical_subsystem.logical_subsystem_name = logical_subsystem.name
WHERE physical_subsystem.logical_subsystem_name IS NULL;

ALTER TABLE arch_subsystem_physical_draft
    ADD COLUMN code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '申请人填写的物理子系统编号' AFTER source_physical_subsystem_id,
    ADD COLUMN logical_subsystem_name VARCHAR(200) NULL COMMENT '逻辑子系统名称，物理侧可选文本' AFTER code,
    ADD COLUMN business_component_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'ARCH_BUSINESS_COMPONENT 字典 config_key' AFTER logical_subsystem_name;

UPDATE arch_subsystem_physical_draft physical_draft
LEFT JOIN arch_physical_subsystem physical_subsystem
  ON physical_subsystem.tenant_id = physical_draft.tenant_id
 AND physical_subsystem.id = physical_draft.source_physical_subsystem_id
LEFT JOIN arch_logical_subsystem logical_subsystem
  ON logical_subsystem.tenant_id = physical_draft.tenant_id
 AND logical_subsystem.id = COALESCE(physical_draft.target_logical_subsystem_id, physical_subsystem.logical_subsystem_id)
SET physical_draft.code = COALESCE(
        physical_subsystem.code,
        CASE
            WHEN physical_draft.reserved_number_slot IS NOT NULL AND logical_subsystem.number_sequence IS NOT NULL
            THEN CONCAT('W', LPAD(logical_subsystem.number_sequence, 4, '0'), physical_draft.reserved_number_slot)
            ELSE NULL
        END
    ),
    physical_draft.logical_subsystem_name = COALESCE(logical_subsystem.name, physical_subsystem.logical_subsystem_name),
    physical_draft.business_component_code = physical_subsystem.business_component_code
WHERE physical_draft.code IS NULL
   OR physical_draft.logical_subsystem_name IS NULL
   OR physical_draft.business_component_code IS NULL;

ALTER TABLE arch_subsystem_physical_draft
    ADD KEY idx_arch_subsystem_physical_draft_code (tenant_id, code);

ALTER TABLE arch_subsystem_physical_draft
    DROP FOREIGN KEY fk_arch_subsystem_physical_draft_target;

ALTER TABLE arch_subsystem_physical_draft
    DROP INDEX idx_arch_subsystem_physical_draft_target,
    DROP CHECK chk_arch_subsystem_physical_draft_slot,
    DROP COLUMN target_logical_subsystem_id,
    DROP COLUMN reserved_number_slot,
    DROP COLUMN business_continuity_level,
    DROP COLUMN collected_system_level;

ALTER TABLE arch_physical_subsystem
    DROP FOREIGN KEY fk_arch_physical_logical;

ALTER TABLE arch_physical_subsystem
    DROP INDEX idx_arch_physical_logical,
    DROP INDEX uk_arch_physical_parent_slot,
    DROP INDEX idx_arch_physical_status_parent,
    DROP CHECK chk_arch_physical_number_slot,
    DROP COLUMN logical_subsystem_id,
    DROP COLUMN number_slot,
    DROP COLUMN business_continuity_level,
    DROP COLUMN collected_system_level,
    ADD KEY idx_arch_physical_status_business_component (tenant_id, status, business_component_code, deleted, id),
    ADD KEY idx_arch_physical_logical_text (tenant_id, logical_subsystem_name, deleted, id);

DROP TABLE arch_subsystem_logical_draft;

DROP TABLE arch_subsystem_number_reservation;

DROP TABLE arch_subsystem_number_recycled;

DROP TABLE arch_subsystem_number_namespace;

DROP TABLE arch_logical_subsystem;

UPDATE sys_menu
SET visible = 0,
    status = 0,
    deleted = 1
WHERE tenant_id = 1
  AND (id = 801
       OR route_name = 'ArchitectureLogicalSubsystems'
       OR route_path = '/architecture/logical-subsystems');

UPDATE sys_menu_permission
SET status = 0
WHERE tenant_id = 1
  AND permission_code IN (
      'architecture:logical:list',
      'architecture:logical:create',
      'architecture:logical:update',
      'architecture:logical:delete'
  );

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_menu_permission menu_permission
  ON menu_permission.tenant_id = role_permission.tenant_id
 AND menu_permission.id = role_permission.permission_id
WHERE role_permission.tenant_id = 1
  AND menu_permission.permission_code IN (
      'architecture:logical:list',
      'architecture:logical:create',
      'architecture:logical:update',
      'architecture:logical:delete'
  );

DELETE FROM sys_role_menu
WHERE tenant_id = 1
  AND menu_id = 801;

CREATE TEMPORARY TABLE tmp_arch_v147_remove_logical_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v147_remove_logical_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v147_remove_logical_guard (marker)
SELECT 1
WHERE EXISTS (
          SELECT 1
          FROM information_schema.tables
          WHERE table_schema = DATABASE()
            AND table_name IN (
                'arch_logical_subsystem',
                'arch_subsystem_logical_draft',
                'arch_subsystem_number_namespace',
                'arch_subsystem_number_recycled',
                'arch_subsystem_number_reservation'
            )
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_physical_subsystem'
            AND column_name IN ('logical_subsystem_id', 'number_slot',
                                'business_continuity_level', 'collected_system_level')
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_physical_subsystem'
            AND column_name = 'logical_subsystem_name'
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_physical_subsystem'
            AND column_name = 'business_component_code'
      )
   OR EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_subsystem_physical_draft'
            AND column_name IN ('target_logical_subsystem_id', 'reserved_number_slot',
                                'business_continuity_level', 'collected_system_level')
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_subsystem_physical_draft'
            AND column_name IN ('code', 'logical_subsystem_name', 'business_component_code')
          GROUP BY table_name
          HAVING COUNT(*) = 3
      )
   OR NOT EXISTS (
          SELECT 1
          FROM sys_dict_type
          WHERE tenant_id = 1
            AND dict_code = 'ARCH_BUSINESS_COMPONENT'
            AND status = 1
            AND deleted = 0
      )
   OR EXISTS (
          SELECT 1
          FROM sys_menu
          WHERE tenant_id = 1
            AND (id = 801 OR route_name = 'ArchitectureLogicalSubsystems' OR route_path = '/architecture/logical-subsystems')
            AND deleted = 0
      );

DROP TEMPORARY TABLE tmp_arch_v147_remove_logical_guard;
