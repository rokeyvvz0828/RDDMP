-- REQ-20260911-074：物理子系统补充安全节点号/文件传输节点号，并将架构管理菜单拆分为三个子目录。
-- 只追加，不修改既有迁移。MySQL 8.4。

-- ---------------------------------------------------------------------------
-- 1. 物理子系统主记录与变更草稿：两个可选自由文本节点号，存量保持 NULL。
-- ---------------------------------------------------------------------------

ALTER TABLE arch_physical_subsystem
    ADD COLUMN security_node_no VARCHAR(64) NULL COMMENT '安全节点号，自由文本' AFTER remark,
    ADD COLUMN file_transfer_node_no VARCHAR(64) NULL COMMENT '文件传输节点号，自由文本' AFTER security_node_no;

ALTER TABLE arch_subsystem_physical_draft
    ADD COLUMN security_node_no VARCHAR(64) NULL COMMENT '安全节点号，自由文本' AFTER remark,
    ADD COLUMN file_transfer_node_no VARCHAR(64) NULL COMMENT '文件传输节点号，自由文本' AFTER security_node_no;

-- ---------------------------------------------------------------------------
-- 2. 架构管理菜单分组：只改 parent_id 与追加目录，不改路由、组件路径和权限代码。
--    817 架构资产管理 / 818 环境管理 / 819 架构决策管理。
-- ---------------------------------------------------------------------------

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 817, 1, 800, 'directory', '架构资产管理', 'ArchitectureAssetRoot', '/architecture/assets', 'LAYOUT', NULL, 'folder-opened', 10
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 800 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 817);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 818, 1, 800, 'directory', '环境管理', 'ArchitectureEnvironmentRoot', '/architecture/environment-management', 'LAYOUT', NULL, 'folder-opened', 20
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 800 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 818);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 819, 1, 800, 'directory', '架构决策管理', 'ArchitectureDecisionRoot', '/architecture/decision-management', 'LAYOUT', NULL, 'folder-opened', 30
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 800 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 819);

-- 架构资产管理
UPDATE sys_menu SET parent_id = 817, sort_no = 10 WHERE id = 802 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 817, sort_no = 20 WHERE id = 803 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 817, sort_no = 30 WHERE id = 804 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 817, sort_no = 40 WHERE id = 816 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 817, sort_no = 50 WHERE id = 805 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 817, sort_no = 60 WHERE id = 806 AND tenant_id = 1;

-- 环境管理
UPDATE sys_menu SET parent_id = 818, sort_no = 10 WHERE id = 809 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 20 WHERE id = 811 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 30 WHERE id = 814 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 40 WHERE id = 815 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 50 WHERE id = 810 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 60 WHERE id = 812 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 70 WHERE id = 813 AND tenant_id = 1;
UPDATE sys_menu SET parent_id = 818, sort_no = 80 WHERE id = 808 AND tenant_id = 1;

-- 架构决策管理
UPDATE sys_menu SET parent_id = 819, sort_no = 10 WHERE id = 807 AND tenant_id = 1;

-- 按既有子菜单授权逐个目录授予角色可见性；已授权角色（含超级管理员）保持原可见范围。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT rm.role_id, 817, 1
FROM sys_role_menu rm
WHERE rm.tenant_id = 1 AND rm.menu_id IN (802, 803, 804, 805, 806, 816);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT rm.role_id, 818, 1
FROM sys_role_menu rm
WHERE rm.tenant_id = 1 AND rm.menu_id IN (808, 809, 810, 811, 812, 813, 814, 815);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT rm.role_id, 819, 1
FROM sys_role_menu rm
WHERE rm.tenant_id = 1 AND rm.menu_id = 807;

-- ---------------------------------------------------------------------------
-- 3. 失败关闭守卫：稳定 ID 被其他菜单占用或分组未生效时中止迁移。
-- ---------------------------------------------------------------------------

CREATE TEMPORARY TABLE tmp_arch_v204_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v204_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v204_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 817 AND tenant_id = 1 AND parent_id = 800 AND menu_type = 'directory'
            AND route_name = 'ArchitectureAssetRoot' AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 818 AND tenant_id = 1 AND parent_id = 800 AND menu_type = 'directory'
            AND route_name = 'ArchitectureEnvironmentRoot' AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 819 AND tenant_id = 1 AND parent_id = 800 AND menu_type = 'directory'
            AND route_name = 'ArchitectureDecisionRoot' AND deleted = 0
      )
   OR (SELECT COUNT(*) FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id IN (802, 803, 804, 805, 806, 816) AND parent_id = 817) <> 6
   OR (SELECT COUNT(*) FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id IN (808, 809, 810, 811, 812, 813, 814, 815) AND parent_id = 818) <> 8
   OR (SELECT COUNT(*) FROM sys_menu WHERE tenant_id = 1 AND deleted = 0 AND id = 807 AND parent_id = 819) <> 1
   OR NOT EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'arch_physical_subsystem'
            AND column_name = 'security_node_no'
      )
   OR NOT EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'arch_physical_subsystem'
            AND column_name = 'file_transfer_node_no'
      );

DROP TEMPORARY TABLE tmp_arch_v204_seed_guard;
