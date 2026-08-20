-- 审批操作回归业务详情页，工作流菜单仅保留流程定义和流程监控。
DELETE FROM sys_role_menu WHERE menu_id IN (202, 204);
DELETE FROM sys_menu WHERE id IN (202, 204);
