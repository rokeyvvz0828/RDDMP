UPDATE sys_menu
SET menu_name = CASE id
    WHEN 100 THEN '系统管理'
    WHEN 101 THEN '用户管理'
    WHEN 102 THEN '角色权限'
    WHEN 103 THEN '组织架构'
    WHEN 104 THEN '菜单路由'
    WHEN 105 THEN '参数管理'
    WHEN 200 THEN '工作流'
    WHEN 201 THEN '流程定义'
    WHEN 202 THEN '审批待办'
    WHEN 300 THEN '智能能力'
    WHEN 301 THEN '服务商配置'
    WHEN 302 THEN '模型管理'
    WHEN 303 THEN '能力路由'
    WHEN 400 THEN '组件示例'
    ELSE menu_name
END,
status = CASE WHEN id = 300 THEN 1 ELSE status END
WHERE tenant_id = 1 AND id IN (100,101,102,103,104,105,200,201,202,300,301,302,303,400);
