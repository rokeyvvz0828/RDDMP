-- REQ-20260915-077：隐藏「架构子系统变更工单」（803）与「部署单元初始化导入」（805）两个菜单。
-- 仅置 visible = 0：保留 deleted、permission_code、sys_menu_permission 与 sys_role_menu 授权。
-- 权限解析走 sys_menu_permission（不按 visible 过滤），因此隐藏菜单不会回收 architecture:view/apply/manage
-- 与 architecture:deployment-unit:manage；/auth/routes 只返回 visible = 1 的菜单。

UPDATE sys_menu
SET visible = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 1
  AND id IN (803, 805)
  AND deleted = 0;

-- 失败关闭守卫：两个菜单必须已隐藏且仍在册，关键权限码不得丢失，否则迁移中止。
CREATE TEMPORARY TABLE tmp_req077_menu_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_req077_menu_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_req077_menu_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE tenant_id = 1 AND id = 803 AND visible = 0 AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu
          WHERE tenant_id = 1 AND id = 805 AND visible = 0 AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE tenant_id = 1 AND id = 8032 AND permission_code = 'architecture:apply' AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE tenant_id = 1 AND id = 8033 AND permission_code = 'architecture:manage' AND status = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys_menu_permission
          WHERE tenant_id = 1 AND id = 8042 AND permission_code = 'architecture:deployment-unit:manage' AND status = 1
      );

DROP TEMPORARY TABLE tmp_req077_menu_guard;
