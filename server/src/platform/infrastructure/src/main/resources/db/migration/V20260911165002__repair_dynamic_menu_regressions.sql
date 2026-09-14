-- Repair the permission-maintenance label without depending on the SQL client's connection charset.
UPDATE sys_menu
SET menu_name = CONVERT(0xE69D83E99990E7BBB4E68AA4 USING utf8mb4)
WHERE tenant_id = 1
  AND id = 102
  AND deleted = 0;
