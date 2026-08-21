-- 架构子系统初始目录。仅初始化租户 1，不承载运行期租户选择逻辑。

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 800, 1, 0, 'directory', '架构管理', 'ArchitectureRoot', '/architecture', 'LAYOUT', NULL, 'collection', 800
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 800 OR route_name = 'ArchitectureRoot' OR route_path = '/architecture')
);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 801, 1, 800, 'menu', '逻辑子系统', 'ArchitectureLogicalSubsystems', '/architecture/logical-subsystems',
       'architecture/logical-subsystems/index', 'architecture:logical:list', 'document', 10
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 801 OR route_name = 'ArchitectureLogicalSubsystems' OR route_path = '/architecture/logical-subsystems')
);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 802, 1, 800, 'menu', '物理子系统', 'ArchitecturePhysicalSubsystems', '/architecture/physical-subsystems',
       'architecture/physical-subsystems/index', 'architecture:physical:list', 'grid', 20
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND (id = 802 OR route_name = 'ArchitecturePhysicalSubsystems' OR route_path = '/architecture/physical-subsystems')
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu WHERE tenant_id = 1 AND id IN (800, 801, 802) AND deleted = 0;

INSERT IGNORE INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
VALUES
    (8011, 1, 801, 'read', 'architecture:logical:list', '查看'),
    (8012, 1, 801, 'create', 'architecture:logical:create', '新增'),
    (8013, 1, 801, 'update', 'architecture:logical:update', '修改'),
    (8014, 1, 801, 'delete', 'architecture:logical:delete', '删除'),
    (8021, 1, 802, 'read', 'architecture:physical:list', '查看'),
    (8022, 1, 802, 'create', 'architecture:physical:create', '新增'),
    (8023, 1, 802, 'update', 'architecture:physical:update', '修改'),
    (8024, 1, 802, 'delete', 'architecture:physical:delete', '删除');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, tenant_id)
SELECT 1, id, 1 FROM sys_menu_permission
WHERE tenant_id = 1 AND id IN (8011, 8012, 8013, 8014, 8021, 8022, 8023, 8024);

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name)
VALUES
    (360001, 1, 'ARCH_DEPLOYMENT_PLATFORM', '部署平台'),
    (360002, 1, 'ARCH_SYSTEM_TYPE', '系统类型'),
    (360003, 1, 'ARCH_SYSTEM_OWNERSHIP', '系统归属'),
    (360004, 1, 'ARCH_RUNTIME', '系统运行时间'),
    (360005, 1, 'ARCH_SYSTEM_LEVEL', '系统级别'),
    (360006, 1, 'ARCH_DEVELOPMENT_FRAMEWORK', '开发平台框架');

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark)
VALUES
    (360101, 1, 360001, 'architecture.deployment-platform.employee-channel-p2', '员工渠道平台（P2）', 'string', '架构子系统部署平台选项'),
    (360102, 1, 360002, 'architecture.system-type.application-platform', '应用平台类', 'string', '架构子系统系统类型选项'),
    (360103, 1, 360003, 'architecture.system-ownership.channel-integration', '渠道整合层', 'string', '架构子系统系统归属选项'),
    (360104, 1, 360004, 'architecture.runtime.7x24', '7*24', 'string', '架构子系统运行时间选项'),
    (360105, 1, 360005, 'architecture.system-level.a-plus', 'A+', 'string', '架构子系统系统级别选项'),
    (360106, 1, 360006, 'architecture.development-framework.employee-channel-p2', '员工渠道平台（P2）', 'string', '架构子系统开发平台框架选项');
