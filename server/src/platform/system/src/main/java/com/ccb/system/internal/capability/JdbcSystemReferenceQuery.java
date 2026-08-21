package com.ccb.system.internal.capability;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class JdbcSystemReferenceQuery implements SystemReferenceQuery {
    private static final RowMapper<SystemUserReference> USER_MAPPER = (rs, rowNum) -> new SystemUserReference(
            rs.getLong("id"),
            rs.getString("display_name"),
            rs.getString("username"),
            rs.getString("mobile_phone"),
            rs.getInt("status") == 1);

    private static final RowMapper<SystemParameterReference> PARAMETER_MAPPER = (rs, rowNum) ->
            new SystemParameterReference(rs.getString("config_key"), rs.getString("config_value"));

    private final JdbcTemplate jdbc;

    public JdbcSystemReferenceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<SystemUserReference> searchActiveUsers(AuthUser actor, PageQuery page, String keyword) {
        Objects.requireNonNull(actor, "actor 不能为空");
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        List<Object> args = new ArrayList<>();
        args.add(actor.tenantId());
        String filter = "";
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword != null) {
            filter = " AND (username LIKE ? ESCAPE '\\\\' OR display_name LIKE ? ESCAPE '\\\\' OR mobile_phone LIKE ? ESCAPE '\\\\')";
            String pattern = "%" + escapeLike(normalizedKeyword) + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE tenant_id = ? AND deleted = 0 AND status = 1" + filter,
                Long.class,
                args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<SystemUserReference> records = jdbc.query(
                "SELECT id, display_name, username, mobile_phone, status FROM sys_user "
                        + "WHERE tenant_id = ? AND deleted = 0 AND status = 1" + filter
                        + " ORDER BY display_name ASC, id ASC LIMIT ? OFFSET ?",
                USER_MAPPER,
                listArgs.toArray());
        return new PageResult<>(records, count == null ? 0L : count, normalizedPage.page(), normalizedPage.size());
    }

    @Override
    public Optional<SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly) {
        Objects.requireNonNull(actor, "actor 不能为空");
        String activeFilter = activeOnly ? " AND status = 1" : "";
        List<SystemUserReference> records = jdbc.query(
                "SELECT id, display_name, username, mobile_phone, status FROM sys_user "
                        + "WHERE id = ? AND tenant_id = ? AND deleted = 0" + activeFilter,
                USER_MAPPER,
                userId,
                actor.tenantId());
        return records.stream().findFirst();
    }

    @Override
    public List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode) {
        Objects.requireNonNull(actor, "actor 不能为空");
        if (categoryCode == null || categoryCode.isBlank()) {
            return List.of();
        }
        String normalizedCategory = categoryCode.trim().toUpperCase(Locale.ROOT);
        return jdbc.query("""
                SELECT c.config_key, c.config_value
                FROM sys_dict_type t
                JOIN sys_config c ON c.category_id = t.id AND c.tenant_id = t.tenant_id
                WHERE t.tenant_id = ? AND t.dict_code = ?
                  AND t.status = 1 AND t.deleted = 0
                  AND c.status = 1 AND c.deleted = 0
                ORDER BY c.id ASC
                """, PARAMETER_MAPPER, actor.tenantId(), normalizedCategory);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
