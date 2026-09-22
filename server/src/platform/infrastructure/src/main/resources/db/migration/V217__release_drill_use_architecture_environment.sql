-- REQ-20260918-084: discard the retired private drill-environment capability.
-- The approved data policy is destructive: legacy rounds, steps and related issues are not migrated.

DELETE issue_row
FROM rel_release_issue issue_row
JOIN rel_release_drill_round drill
  ON drill.id = issue_row.drill_round_id
 AND drill.tenant_id = issue_row.tenant_id
 AND drill.project_id = issue_row.project_id;

DELETE FROM rel_release_drill_step;
DELETE FROM rel_release_drill_round;
DROP TABLE rel_release_drill_environment;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_menu_permission permission
  ON permission.id = role_permission.permission_id
 AND permission.tenant_id = role_permission.tenant_id
WHERE permission.tenant_id = 1
  AND permission.menu_id = 1006;

DELETE FROM sys_role_menu WHERE tenant_id = 1 AND menu_id = 1006;
UPDATE sys_menu_permission SET status = 0 WHERE tenant_id = 1 AND menu_id = 1006;
UPDATE sys_menu SET deleted = 1 WHERE tenant_id = 1 AND id = 1006;
