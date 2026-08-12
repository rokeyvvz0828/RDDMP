-- 为尚未配置模块编码的菜单按路由地址补齐编码；已有编码不覆盖。
UPDATE sys_menu
SET module_key = LOWER(REPLACE(TRIM(BOTH '/' FROM route_path), '/', '.'))
WHERE tenant_id = 1
  AND deleted = 0
  AND (module_key IS NULL OR module_key = '')
  AND route_path IS NOT NULL
  AND route_path <> '';
