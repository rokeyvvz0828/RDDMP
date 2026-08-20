-- REQ-20260820-036：测试管理三级菜单与管理员占位权限。
-- 本迁移不创建业务表；后续正式测试数据能力必须使用独立需求和追加迁移。

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 600, 1, 0, 'menu', '测试管理', 'TestManagement', '/test-management', 'test-management/index', 'test-management:access', 'tickets', 600
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 600 OR route_name = 'TestManagement') AND deleted = 0);

CREATE TEMPORARY TABLE tmp_test_management_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    route_name VARCHAR(128) NOT NULL,
    route_path VARCHAR(255) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    icon VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL,
    is_leaf TINYINT NOT NULL
);

INSERT INTO tmp_test_management_menu
    (id, parent_id, menu_name, route_name, route_path, permission_code, icon, sort_no, is_leaf)
VALUES
    (610, 600, '应用组装测试', 'TestManagementApplicationAssembly', '/test-management/application-assembly', 'test-management:application-assembly:access', 'folder', 10, 0),
    (611, 600, '用户测试', 'TestManagementUserTesting', '/test-management/user-testing', 'test-management:user-testing:access', 'folder', 20, 0),
    (612, 600, '非功能测试', 'TestManagementNonFunctional', '/test-management/non-functional', 'test-management:non-functional:access', 'folder', 30, 0),
    (613, 600, '安全测试', 'TestManagementSecurity', '/test-management/security', 'test-management:security:access', 'folder', 40, 0),
    (614, 600, '营业日管理', 'TestManagementBusinessDay', '/test-management/business-day', 'test-management:business-day:access', 'folder', 50, 0),

    (620, 610, '测试公告板', 'TestManagementApplicationAssemblyDashboard', '/test-management/application-assembly/dashboard', 'test-management:application-assembly:dashboard', 'dashboard', 10, 1),
    (621, 610, '测试方案', 'TestManagementApplicationAssemblyPlans', '/test-management/application-assembly/plans', 'test-management:application-assembly:plans', 'document', 20, 1),
    (622, 610, '测试范围', 'TestManagementApplicationAssemblyScope', '/test-management/application-assembly/scope', 'test-management:application-assembly:scope', 'collection', 30, 1),
    (623, 610, '测试案例', 'TestManagementApplicationAssemblyCases', '/test-management/application-assembly/cases', 'test-management:application-assembly:cases', 'tickets', 40, 1),
    (624, 610, '测试缺陷', 'TestManagementApplicationAssemblyDefects', '/test-management/application-assembly/defects', 'test-management:application-assembly:defects', 'operation', 50, 1),
    (625, 610, '测试报告', 'TestManagementApplicationAssemblyReports', '/test-management/application-assembly/reports', 'test-management:application-assembly:reports', 'document', 60, 1),
    (626, 610, '分析统计', 'TestManagementApplicationAssemblyAnalytics', '/test-management/application-assembly/analytics', 'test-management:application-assembly:analytics', 'dashboard', 70, 1),

    (627, 611, '测试公告板', 'TestManagementUserTestingDashboard', '/test-management/user-testing/dashboard', 'test-management:user-testing:dashboard', 'dashboard', 10, 1),
    (628, 611, '测试方案', 'TestManagementUserTestingPlans', '/test-management/user-testing/plans', 'test-management:user-testing:plans', 'document', 20, 1),
    (629, 611, '测试范围', 'TestManagementUserTestingScope', '/test-management/user-testing/scope', 'test-management:user-testing:scope', 'collection', 30, 1),
    (630, 611, '测试案例', 'TestManagementUserTestingCases', '/test-management/user-testing/cases', 'test-management:user-testing:cases', 'tickets', 40, 1),
    (631, 611, '测试缺陷', 'TestManagementUserTestingDefects', '/test-management/user-testing/defects', 'test-management:user-testing:defects', 'operation', 50, 1),
    (632, 611, '测试报告', 'TestManagementUserTestingReports', '/test-management/user-testing/reports', 'test-management:user-testing:reports', 'document', 60, 1),
    (633, 611, '分析统计', 'TestManagementUserTestingAnalytics', '/test-management/user-testing/analytics', 'test-management:user-testing:analytics', 'dashboard', 70, 1),

    (634, 612, '测试公告板', 'TestManagementNonFunctionalDashboard', '/test-management/non-functional/dashboard', 'test-management:non-functional:dashboard', 'dashboard', 10, 1),
    (635, 612, '测试方案', 'TestManagementNonFunctionalPlans', '/test-management/non-functional/plans', 'test-management:non-functional:plans', 'document', 20, 1),
    (636, 612, '测试范围', 'TestManagementNonFunctionalScope', '/test-management/non-functional/scope', 'test-management:non-functional:scope', 'collection', 30, 1),
    (637, 612, '测试案例', 'TestManagementNonFunctionalCases', '/test-management/non-functional/cases', 'test-management:non-functional:cases', 'tickets', 40, 1),
    (638, 612, '测试缺陷', 'TestManagementNonFunctionalDefects', '/test-management/non-functional/defects', 'test-management:non-functional:defects', 'operation', 50, 1),
    (639, 612, '测试报告', 'TestManagementNonFunctionalReports', '/test-management/non-functional/reports', 'test-management:non-functional:reports', 'document', 60, 1),
    (640, 612, '分析统计', 'TestManagementNonFunctionalAnalytics', '/test-management/non-functional/analytics', 'test-management:non-functional:analytics', 'dashboard', 70, 1),

    (641, 613, '测试公告板', 'TestManagementSecurityDashboard', '/test-management/security/dashboard', 'test-management:security:dashboard', 'dashboard', 10, 1),
    (642, 613, '测试方案', 'TestManagementSecurityPlans', '/test-management/security/plans', 'test-management:security:plans', 'document', 20, 1),
    (643, 613, '测试范围', 'TestManagementSecurityScope', '/test-management/security/scope', 'test-management:security:scope', 'collection', 30, 1),
    (644, 613, '测试案例', 'TestManagementSecurityCases', '/test-management/security/cases', 'test-management:security:cases', 'tickets', 40, 1),
    (645, 613, '测试缺陷', 'TestManagementSecurityDefects', '/test-management/security/defects', 'test-management:security:defects', 'operation', 50, 1),
    (646, 613, '测试报告', 'TestManagementSecurityReports', '/test-management/security/reports', 'test-management:security:reports', 'document', 60, 1),
    (647, 613, '分析统计', 'TestManagementSecurityAnalytics', '/test-management/security/analytics', 'test-management:security:analytics', 'dashboard', 70, 1),

    (648, 614, '日历概览', 'TestManagementBusinessDayCalendarOverview', '/test-management/business-day/calendar-overview', 'test-management:business-day:calendar-overview', 'dashboard', 10, 1),
    (649, 614, '日历安排', 'TestManagementBusinessDayCalendarSchedule', '/test-management/business-day/calendar-schedule', 'test-management:business-day:calendar-schedule', 'collection', 20, 1),
    (650, 614, '跑批需求', 'TestManagementBusinessDayBatchRequirements', '/test-management/business-day/batch-requirements', 'test-management:business-day:batch-requirements', 'operation', 30, 1),
    (651, 614, '测试环境管理', 'TestManagementBusinessDayTestEnvironments', '/test-management/business-day/test-environments', 'test-management:business-day:test-environments', 'monitor', 40, 1);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT t.id, 1, t.parent_id, 'menu', t.menu_name, t.route_name, t.route_path,
       CASE WHEN t.is_leaf = 1 THEN 'test-management/list' ELSE 'test-management/index' END,
       t.permission_code, t.icon, t.sort_no
FROM tmp_test_management_menu t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m
    WHERE m.tenant_id = 1 AND m.deleted = 0 AND (m.id = t.id OR m.route_name = t.route_name)
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, m.id, 1
FROM sys_menu m
WHERE m.tenant_id = 1 AND m.deleted = 0 AND m.id BETWEEN 600 AND 651;

INSERT IGNORE INTO sys_menu_permission
    (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT
    m.id * 10 + a.seq,
    1,
    m.id,
    a.action_code,
    CASE WHEN a.action_code = 'read' THEN m.permission_code ELSE CONCAT(m.permission_code, ':', a.action_code) END,
    a.permission_name
FROM sys_menu m
JOIN tmp_test_management_menu t ON t.id = m.id AND t.is_leaf = 1
CROSS JOIN (
    SELECT 1 AS seq, 'read' AS action_code, '查看' AS permission_name
    UNION ALL SELECT 2, 'create', '新增'
    UNION ALL SELECT 3, 'update', '修改'
    UNION ALL SELECT 4, 'delete', '删除'
) a
WHERE m.tenant_id = 1 AND m.deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, p.id, 1
FROM sys_menu_permission p
JOIN tmp_test_management_menu t ON t.id = p.menu_id AND t.is_leaf = 1
WHERE p.tenant_id = 1;

DROP TEMPORARY TABLE tmp_test_management_menu;
