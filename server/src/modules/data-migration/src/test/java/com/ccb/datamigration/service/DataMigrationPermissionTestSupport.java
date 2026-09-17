package com.ccb.datamigration.service;

import org.springframework.jdbc.core.JdbcTemplate;

final class DataMigrationPermissionTestSupport {
    private DataMigrationPermissionTestSupport() { }
    static DataMigrationPermissionRepository repository(JdbcTemplate jdbc) {
        return new DataMigrationPermissionRepository(new DataMigrationPermissionMapper() {
            @Override public Integer adminPermissionCount(long userId, long tenantId) {
                return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id JOIN sys_role_permission rp ON rp.role_id=r.id AND rp.tenant_id=r.tenant_id JOIN sys_menu_permission p ON p.id=rp.permission_id AND p.tenant_id=rp.tenant_id WHERE ur.user_id=? AND ur.tenant_id=? AND p.permission_code IN ('system:admin','data-migration:bypass','data-migration:manage') AND p.status=1 AND r.status=1 AND r.deleted=0", Integer.class, userId, tenantId);
            }
            @Override public Integer actionPermissionCount(long userId, long tenantId, String permissionCode, String action) {
                return jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id AND rp.tenant_id=p.tenant_id JOIN sys_user_role ur ON ur.role_id=rp.role_id AND ur.tenant_id=rp.tenant_id JOIN sys_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id WHERE ur.user_id=? AND p.tenant_id=? AND p.permission_code=? AND p.action_code=? AND p.status=1 AND r.status=1 AND r.deleted=0", Integer.class, userId, tenantId, permissionCode, action);
            }
        });
    }
}
