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
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code IN ('ADMIN','SUPER_ADMIN') AND r.status = 1 AND r.deleted = 0", Integer.class, user.id(), user.tenantId());
        return count != null && count > 0;
    }

    public void requireWrite(AuthUser user, long ownerId) {
        if (!isAdmin(user) && user.id() != ownerId) throw new BusinessException(ErrorCode.FORBIDDEN, "Only administrators or the owner can modify this record");
    }

    public void requireAdmin(AuthUser user) {
        if (!isAdmin(user)) throw new BusinessException(ErrorCode.FORBIDDEN, "Administrator permission required");
    }
}
