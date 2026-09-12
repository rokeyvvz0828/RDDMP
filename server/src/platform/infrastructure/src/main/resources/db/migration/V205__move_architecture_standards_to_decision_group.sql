-- REQ-20260911-074：架构规范归入架构决策管理分组（V204 之后追加，不修改已执行脚本）。

UPDATE sys_menu SET parent_id = 819, sort_no = 20
WHERE id = 806 AND tenant_id = 1 AND deleted = 0;

UPDATE sys_menu SET sort_no = 10
WHERE id = 807 AND tenant_id = 1 AND deleted = 0;

-- 移动后清理不再拥有任何子菜单的“架构资产管理”角色授权，避免出现空分组。
DELETE FROM sys_role_menu
WHERE tenant_id = 1
  AND menu_id = 817
  AND role_id NOT IN (
      SELECT role_id FROM (
          SELECT DISTINCT role_id
          FROM sys_role_menu
          WHERE tenant_id = 1 AND menu_id IN (802, 803, 804, 805, 816)
      ) asset_roles
  );

-- 持有“架构规范”的角色必须能看到“架构决策管理”目录。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT DISTINCT role_menu.role_id, 819, 1
FROM sys_role_menu role_menu
WHERE role_menu.tenant_id = 1 AND role_menu.menu_id = 806;

-- 失败关闭守卫：分组未生效时中止迁移。
CREATE TEMPORARY TABLE tmp_arch_v205_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v205_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v205_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 806 AND tenant_id = 1 AND parent_id = 819 AND sort_no = 20 AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE id = 807 AND tenant_id = 1 AND parent_id = 819 AND sort_no = 10 AND deleted = 0
      );

DROP TEMPORARY TABLE tmp_arch_v205_guard;
