package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 测试替身：从测试夹具表读取“系统管理/参数管理”类别，供 DataMigrationCodeValueService
 * 的角色字典校验/选项读取使用；其余平台查询能力未使用即拒绝。
 */
final class StubSystemReferenceQuery implements SystemReferenceQuery {
    private final JdbcTemplate jdbc;

    StubSystemReferenceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<SystemUserReference> searchActiveUsers(AuthUser actor, PageQuery page, String keyword) {
        throw new UnsupportedOperationException("not needed");
    }

    @Override
    public Optional<SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly) {
        throw new UnsupportedOperationException("not needed");
    }

    @Override
    public List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode) {
        return jdbc.query(
                "SELECT c.config_key, c.config_value FROM sys_config c "
                        + "JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id "
                        + "WHERE c.tenant_id = ? AND t.dict_code = ? AND t.status = 1 AND t.deleted = 0 "
                        + "AND c.status = 1 AND c.deleted = 0 ORDER BY c.id",
                (rs, rowNum) -> new SystemParameterReference(rs.getString("config_key"), rs.getString("config_value")),
                actor.tenantId(), categoryCode);
    }
}
