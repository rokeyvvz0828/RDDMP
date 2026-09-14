-- REQ-20260905-064：将用户可见的“具体环境”统一改名为“环境管理”。

UPDATE sys_menu
SET menu_name = '环境管理'
WHERE route_path = '/architecture/environments'
  AND deleted = 0;

UPDATE sys_menu_permission
SET permission_name = CASE permission_code
    WHEN 'architecture:environment:view' THEN '查看环境管理'
    WHEN 'architecture:environment:manage' THEN '维护环境管理'
    ELSE permission_name
END
WHERE permission_code IN ('architecture:environment:view', 'architecture:environment:manage');
