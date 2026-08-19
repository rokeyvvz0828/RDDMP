package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.model.UserDirectory;
import com.ccb.system.model.UserDirectoryUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JdbcUserDirectory implements UserDirectory {
    private static final String SELECT = """
            SELECT u.id, u.username, u.display_name, u.org_id, o.org_name
            FROM sys_user u
            LEFT JOIN sys_org o
              ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0
            """;
    private static final String ACTIVE_SCOPE = "u.tenant_id = ? AND u.deleted = 0 AND u.status = 1";

    private final JdbcTemplate jdbc;

    public JdbcUserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<UserDirectoryUser> searchActive(long tenantId, String keyword, PageQuery pageQuery) {
        String normalized = keyword == null ? "" : keyword.trim();
        String keywordFilter = normalized.isEmpty() ? "" : " AND (u.username LIKE ? OR u.display_name LIKE ?)";
        List<Object> filterArgs = new ArrayList<>();
        filterArgs.add(tenantId);
        if (!normalized.isEmpty()) {
            String like = "%" + normalized + "%";
            filterArgs.add(like);
            filterArgs.add(like);
        }

        List<Object> queryArgs = new ArrayList<>(filterArgs);
        queryArgs.add((pageQuery.page() - 1) * pageQuery.size());
        queryArgs.add(pageQuery.size());
        List<UserDirectoryUser> records = jdbc.query(
                SELECT + " WHERE " + ACTIVE_SCOPE + keywordFilter
                        + " ORDER BY u.display_name, u.id LIMIT ?, ?",
                this::mapUser,
                queryArgs.toArray());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u WHERE " + ACTIVE_SCOPE + keywordFilter,
                Long.class,
                filterArgs.toArray());
        return new PageResult<>(records, total == null ? 0L : total, pageQuery.page(), pageQuery.size());
    }

    @Override
    public Map<Long, UserDirectoryUser> requireActive(long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> sortedIds = userIds.stream().sorted().toList();
        if (sortedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw unavailableUser();
        }
        String placeholders = String.join(", ", sortedIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(sortedIds);
        List<UserDirectoryUser> records = jdbc.query(
                SELECT + " WHERE " + ACTIVE_SCOPE + " AND u.id IN (" + placeholders + ") ORDER BY u.id",
                this::mapUser,
                args.toArray());
        Map<Long, UserDirectoryUser> result = new LinkedHashMap<>();
        records.forEach(user -> result.put(user.id(), user));
        if (result.size() != userIds.size() || !result.keySet().containsAll(userIds)) {
            throw unavailableUser();
        }
        return Map.copyOf(result);
    }

    private UserDirectoryUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserDirectoryUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getLong("org_id"),
                rs.getString("org_name"));
    }

    private BusinessException unavailableUser() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在、已停用或不属于当前租户");
    }
}
