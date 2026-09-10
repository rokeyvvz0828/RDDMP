-- REQ-20260910-073：架构管理交付单元、编号序列与部署单元无方向关联，并追加菜单与权限种子。
-- 只追加，不修改既有迁移。MySQL 8.4。

-- 关联表的复合外键要求父表在 (tenant_id, physical_subsystem_id, id) 上唯一；
-- 该组合由主键 id 蕴含，不改变既有语义，也不改写存量数据。
ALTER TABLE arch_deployment_unit
    ADD UNIQUE KEY uk_arch_deployment_unit_tenant_physical_id (tenant_id, physical_subsystem_id, id);

CREATE TABLE arch_delivery_unit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交付单元编号 DU<物理编号><三位序号>；创建时分配，创建后不可修改',
    physical_subsystem_id BIGINT NOT NULL COMMENT '归属物理子系统；创建后不可变更',
    name VARCHAR(200) NOT NULL COMMENT '交付单元名称，同租户同项目同物理子系统内唯一',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_delivery_unit_tenant_code (tenant_id, code),
    UNIQUE KEY uk_arch_delivery_unit_name (tenant_id, project_id, physical_subsystem_id, name),
    UNIQUE KEY uk_arch_delivery_unit_tenant_physical_id (tenant_id, physical_subsystem_id, id),
    KEY idx_arch_delivery_unit_project (tenant_id, project_id, deleted, status, id),
    KEY idx_arch_delivery_unit_physical (tenant_id, project_id, physical_subsystem_id, deleted, id),
    CONSTRAINT fk_arch_delivery_unit_physical
        FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_delivery_unit_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_delivery_unit_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_arch_delivery_unit_code CHECK (code LIKE 'DU%' AND LENGTH(code) BETWEEN 8 AND 32),
    CONSTRAINT chk_arch_delivery_unit_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付单元主记录';

CREATE TABLE arch_delivery_unit_number_seq (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    next_ordinal INT NOT NULL COMMENT '该物理子系统下待分配序号 1..1000',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, project_id, physical_subsystem_id),
    CONSTRAINT fk_arch_delivery_unit_number_seq_physical
        FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_delivery_unit_number_seq_next CHECK (next_ordinal BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付单元编号分配序号（行锁分配，永久占用）';

CREATE TABLE arch_delivery_unit_deployment_unit (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL COMMENT '关联双方共同归属的物理子系统',
    delivery_unit_id BIGINT NOT NULL,
    deployment_unit_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, delivery_unit_id, deployment_unit_id),
    KEY idx_arch_delivery_unit_deployment_reverse (tenant_id, deployment_unit_id, delivery_unit_id),
    CONSTRAINT fk_arch_du_relation_delivery
        FOREIGN KEY (tenant_id, physical_subsystem_id, delivery_unit_id)
        REFERENCES arch_delivery_unit (tenant_id, physical_subsystem_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_du_relation_deployment
        FOREIGN KEY (tenant_id, physical_subsystem_id, deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, physical_subsystem_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付单元与部署单元的无方向关联';

-- ---------------------------------------------------------------------------
-- 菜单与权限种子：交付单元。只补充新授权，不删除或改写既有 800-815 权限记录。
-- 816 为 800 下第一个未占用菜单 ID；8161/8162 沿用 <菜单ID><序号> 约定。
-- ---------------------------------------------------------------------------

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 816, 1, 800, 'menu', '交付单元', 'ArchitectureDeliveryUnits', '/architecture/delivery-units',
       'architecture/delivery-units/index', 'architecture:delivery-unit:view', 'tickets', 45
WHERE EXISTS (
    SELECT 1
    FROM sys_menu parent_menu
    WHERE parent_menu.id = 800
      AND parent_menu.tenant_id = 1
      AND parent_menu.deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 816);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8161, 1, 816, 'view', 'architecture:delivery-unit:view', '查看交付单元'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 816 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8161);

INSERT INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8162, 1, 816, 'manage', 'architecture:delivery-unit:manage', '维护交付单元与部署单元关联'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 816 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8162);

-- 超级管理员（角色 1）与技术架构师（角色 111）：菜单与两级权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT role_seed.role_id, 816, 1
FROM (SELECT 1 AS role_id UNION ALL SELECT 111) role_seed
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = role_seed.role_id AND tenant_id = 1 AND deleted = 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT role_seed.role_id, permission_seed.permission_id, 1
FROM (SELECT 1 AS role_id UNION ALL SELECT 111) role_seed
JOIN (SELECT 8161 AS permission_id UNION ALL SELECT 8162) permission_seed
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = role_seed.role_id AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (
      SELECT 1
      FROM sys_menu_permission permission
      WHERE permission.id = permission_seed.permission_id
        AND permission.tenant_id = 1
        AND permission.status = 1
  );

-- 本地 tenant 1 管理员加入技术架构师角色（保留既有超级管理员授权）。
INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id)
SELECT 1, 111, 1
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1 AND tenant_id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 111 AND tenant_id = 1 AND deleted = 0);

-- 既有架构权限（architecture:view/apply/manage = 8031/8032/8033）与部署单元查看权限（8041）
-- 的持有角色获得交付单元查看权限与菜单，避免升级后入口不可见。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 8161, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id IN (8031, 8032, 8033, 8041);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_permission.role_id, 816, role_permission.tenant_id
FROM sys_role_permission role_permission
WHERE role_permission.tenant_id = 1
  AND role_permission.permission_id = 8161;

-- 对已存在但不符合本迁移身份的稳定 ID 失败关闭，避免静默复用其他菜单、角色或权限。
CREATE TEMPORARY TABLE tmp_arch_v202_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v202_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v202_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 816
            AND tenant_id = 1
            AND parent_id = 800
            AND menu_type = 'menu'
            AND route_name = 'ArchitectureDeliveryUnits'
            AND route_path = '/architecture/delivery-units'
            AND component_path = 'architecture/delivery-units/index'
            AND permission_code = 'architecture:delivery-unit:view'
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8161
            AND tenant_id = 1
            AND menu_id = 816
            AND action_code = 'view'
            AND permission_code = 'architecture:delivery-unit:view'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE id = 8162
            AND tenant_id = 1
            AND menu_id = 816
            AND action_code = 'manage'
            AND permission_code = 'architecture:delivery-unit:manage'
            AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 1 AND permission_id = 8161 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 1 AND permission_id = 8162 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 111 AND permission_id = 8161 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_permission
          WHERE role_id = 111 AND permission_id = 8162 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 1 AND menu_id = 816 AND tenant_id = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_role_menu
          WHERE role_id = 111 AND menu_id = 816 AND tenant_id = 1
      );

DROP TEMPORARY TABLE tmp_arch_v202_seed_guard;
