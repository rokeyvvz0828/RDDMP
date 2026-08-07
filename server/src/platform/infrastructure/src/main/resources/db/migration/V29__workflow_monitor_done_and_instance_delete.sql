-- 流程实例软删除、流程已办菜单及菜单授权。
ALTER TABLE wf_instance
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：1已删除，0正常' AFTER status,
    ADD KEY idx_wf_instance_deleted (tenant_id, deleted, id);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 204, 1, 200, 'menu', '流程已办', 'WorkflowDone', '/workflow/done', 'workflow/index', 'workflow:access', 'document', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 204);

INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, 204, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 204 AND tenant_id = 1);
