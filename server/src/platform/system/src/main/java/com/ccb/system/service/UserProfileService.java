/*
 * 文件：server/src/platform/system/src/main/java/com/ccb/system/service/UserProfileService.java
 * 说明：系统人员档案的租户范围只读批量查询服务。
 * 用途：为已登录用户提供人员展示所需的姓名、账号、手机号、组织与角色白名单字段。
 * 作者：Codex
 */
package com.ccb.system.service;

import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserProfileService {
    /** 角色展示项，只暴露标识、代码与名称。 */
    public record RoleItem(long id, String code, String name) {
    }

    /** 人员档案行；不包含口令、租户、删除标记与登录信息。 */
    public record Row(long id, String username, String displayName, String mobilePhone,
                      String orgName, String avatarObjectKey, int status, List<RoleItem> roles) {
    }

    private static final String USER_SQL_TEMPLATE = """
            SELECT u.id, u.username, u.display_name, COALESCE(u.mobile_phone, '') AS mobile_phone,
                   COALESCE(o.org_name, '') AS org_name, u.avatar_object_key, u.status
            FROM sys_user u
            LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0
            WHERE u.tenant_id = ? AND u.deleted = 0 AND u.id IN (%s)
            ORDER BY u.id
            """;

    private static final String ROLE_SQL_TEMPLATE = """
            SELECT ur.user_id, r.id, r.role_code, r.role_name
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
            WHERE ur.tenant_id = ? AND ur.user_id IN (%s) AND r.status = 1 AND r.deleted = 0
            ORDER BY r.id
            """;

    private final JdbcTemplate jdbc;

    public UserProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 关键逻辑：强制 tenantId 与 deleted = 0；先批量查用户再一次性查角色并在内存聚合，避免按人查询；
    // 停用用户仍返回，由 status 标记；空标识不执行任何 SQL。
    public List<Row> query(AuthUser actor, List<Long> userIds) {
        Objects.requireNonNull(actor, "actor 不能为空");
        List<Long> ids = distinctIds(userIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(ids.size());
        Map<Long, List<RoleItem>> roles = rolesByUser(actor.tenantId(), ids, placeholders);
        List<Object> args = new ArrayList<>();
        args.add(actor.tenantId());
        args.addAll(ids);
        return jdbc.query(USER_SQL_TEMPLATE.formatted(placeholders),
                (rs, rowNum) -> new Row(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("mobile_phone"),
                        rs.getString("org_name"),
                        rs.getString("avatar_object_key"),
                        rs.getInt("status"),
                        roles.getOrDefault(rs.getLong("id"), List.of())),
                args.toArray());
    }

    private Map<Long, List<RoleItem>> rolesByUser(long tenantId, List<Long> ids, String placeholders) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        Map<Long, List<RoleItem>> result = new LinkedHashMap<>();
        jdbc.query(ROLE_SQL_TEMPLATE.formatted(placeholders),
                (RowCallbackHandler) rs -> result
                        .computeIfAbsent(rs.getLong("user_id"), key -> new ArrayList<>())
                        .add(new RoleItem(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name"))),
                args.toArray());
        return result;
    }

    private List<Long> distinctIds(List<Long> userIds) {
        if (userIds == null) {
            return List.of();
        }
        return userIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
