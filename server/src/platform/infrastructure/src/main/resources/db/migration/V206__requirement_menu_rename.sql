-- 需求管理菜单名称统一为"需求列表"
-- id=701: 新建项目 → 需求列表
-- id=702: 存量项目 → 需求列表

UPDATE sys_menu
SET menu_name = '需求列表',
    updated_at = '2026-09-08 14:42:50'
WHERE id = 701 AND tenant_id = 1;

UPDATE sys_menu
SET menu_name = '需求列表',
    updated_at = '2026-09-08 14:42:50'
WHERE id = 702 AND tenant_id = 1;