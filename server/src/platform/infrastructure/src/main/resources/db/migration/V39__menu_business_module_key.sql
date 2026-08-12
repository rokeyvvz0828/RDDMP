-- 一级菜单同时作为业务模块入口，模块编码由菜单路由统一维护。
ALTER TABLE sys_menu
    ADD COLUMN module_key VARCHAR(64) NULL COMMENT '业务模块编码，仅一级菜单维护';

ALTER TABLE sys_menu
    ADD KEY idx_sys_menu_module_key (tenant_id, parent_id, module_key, status, visible, deleted);

UPDATE sys_menu
SET module_key = CASE id
    WHEN 100 THEN 'system'
    WHEN 200 THEN 'workflow'
    WHEN 300 THEN 'ai'
    WHEN 500 THEN 'delivery'
    ELSE module_key
END
WHERE tenant_id = 1
  AND parent_id = 0
  AND id IN (100, 200, 300, 500)
  AND deleted = 0;
