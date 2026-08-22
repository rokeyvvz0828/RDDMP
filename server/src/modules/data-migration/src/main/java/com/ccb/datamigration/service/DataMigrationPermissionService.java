package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataMigrationPermissionService {
    private final JdbcTemplate jdbc;

    public DataMigrationPermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isAdmin(AuthUser user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code IN ('ADMIN','SUPER_ADMIN','DATA_MIGRATION_ADMIN') AND r.status = 1 AND r.deleted = 0", Integer.class, user.id(), user.tenantId());
        return count != null && count > 0;
    }

    public void requireCategoryPermission(AuthUser user, String category, String action) {
        if (isAdmin(user)) return;
        String normalizedCategory;
        if ("INTERMEDIATE".equalsIgnoreCase(category)) normalizedCategory = "intermediate";
        else if ("TARGET".equalsIgnoreCase(category)) normalizedCategory = "target";
        else throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的表结构类别");
        String base = "data-migration:base:table-fields-" + normalizedCategory;
        String permission = "read".equals(action) ? base : base + ":" + action;
        Integer allowed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu_permission p "
                        + "JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id "
                        + "JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id "
                        + "JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id "
                        + "WHERE ur.user_id = ? AND p.tenant_id = ? AND p.permission_code = ? AND p.action_code = ? "
                        + "AND p.status = 1 AND r.status = 1 AND r.deleted = 0",
                Integer.class, user.id(), user.tenantId(), permission, action);
        if (allowed == null || allowed == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + normalizedCategory + "表结构的" + action + "权限");
        }
    }

    public void requireWrite(AuthUser user, long ownerId) {
        if (!isAdmin(user) && user.id() != ownerId) throw new BusinessException(ErrorCode.FORBIDDEN, "Only administrators or the owner can modify this record");
    }

    public void requireAdmin(AuthUser user) {
        if (!isAdmin(user)) throw new BusinessException(ErrorCode.FORBIDDEN, "Administrator permission required");
    }
}
