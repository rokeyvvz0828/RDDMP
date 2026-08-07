-- Keep the workflow directory as a top-level navigation entry for existing tenants.
UPDATE sys_menu
SET parent_id = 0
WHERE tenant_id = 1
  AND id = 200
  AND route_path = '/workflow'
  AND deleted = 0;
