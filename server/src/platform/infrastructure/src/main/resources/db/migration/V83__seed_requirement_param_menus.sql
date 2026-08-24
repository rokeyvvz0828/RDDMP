-- =============================================================================
-- V83：需求管理平台 - 八大参数管理（二级目录 + 8 个三级菜单）
-- -----------------------------------------------------------------------------
-- 页面内容暂不实施，本脚本仅建立菜单树与查看权限（幂等，仅追加）。
-- 结构：需求管理平台(700) -> 八大参数管理(704, directory)
--       -> 产品目录/定价管理/财务会计/机构员工/员工渠道/参数管理/授权复核/凭证回单(705-712)
-- =============================================================================

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 704, 1, 700, 'directory', '八大参数管理', 'RequirementParams', '/requirements/params', 'LAYOUT', 'requirement:param:access', 'setting', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 704);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 705, 1, 704, 'menu', '产品目录', 'RequirementParamProductCatalog', '/requirements/params/product-catalog', 'requirements/index', 'requirement:param:product:read', 'document', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 705);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 706, 1, 704, 'menu', '定价管理', 'RequirementParamPricing', '/requirements/params/pricing', 'requirements/index', 'requirement:param:pricing:read', 'document', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 706);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 707, 1, 704, 'menu', '财务会计', 'RequirementParamFinanceAccounting', '/requirements/params/finance-accounting', 'requirements/index', 'requirement:param:finance:read', 'document', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 707);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 708, 1, 704, 'menu', '机构员工', 'RequirementParamOrgStaff', '/requirements/params/org-staff', 'requirements/index', 'requirement:param:org-staff:read', 'document', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 708);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 709, 1, 704, 'menu', '员工渠道', 'RequirementParamStaffChannel', '/requirements/params/staff-channel', 'requirements/index', 'requirement:param:channel:read', 'document', 50
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 709);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 710, 1, 704, 'menu', '参数管理', 'RequirementParamParameter', '/requirements/params/parameter', 'requirements/index', 'requirement:param:parameter:read', 'document', 60
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 710);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 711, 1, 704, 'menu', '授权复核', 'RequirementParamAuthReview', '/requirements/params/auth-review', 'requirements/index', 'requirement:param:auth-review:read', 'document', 70
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 711);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 712, 1, 704, 'menu', '凭证回单', 'RequirementParamVoucherReceipt', '/requirements/params/voucher-receipt', 'requirements/index', 'requirement:param:voucher:read', 'document', 80
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND id = 712);

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (7201, 1, 704, 'read', 'requirement:param:access', '八大参数管理访问'),
    (7202, 1, 705, 'read', 'requirement:param:product:read', '产品目录查看'),
    (7203, 1, 706, 'read', 'requirement:param:pricing:read', '定价管理查看'),
    (7204, 1, 707, 'read', 'requirement:param:finance:read', '财务会计查看'),
    (7205, 1, 708, 'read', 'requirement:param:org-staff:read', '机构员工查看'),
    (7206, 1, 709, 'read', 'requirement:param:channel:read', '员工渠道查看'),
    (7207, 1, 710, 'read', 'requirement:param:parameter:read', '参数管理查看'),
    (7208, 1, 711, 'read', 'requirement:param:auth-review:read', '授权复核查看'),
    (7209, 1, 712, 'read', 'requirement:param:voucher:read', '凭证回单查看');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id BETWEEN 704 AND 712;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission WHERE tenant_id = 1 AND id BETWEEN 7201 AND 7209;
