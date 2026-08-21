/*
 * 文件：server/src/platform/system/src/main/java/com/ccb/system/service/SystemUserDirectory.java
 * 说明：系统用户只读目录的 JDBC 适配实现。
 * 用途：按租户、启用状态和删除标记查询必要用户展示字段。
 * 作者：hengguan
 */
package com.ccb.system.service;

import com.ccb.system.model.UserDirectoryItem;
import com.ccb.system.model.UserDirectoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SystemUserDirectory implements UserDirectoryPort {
    private static final String SELECT = "SELECT u.id, u.username, u.display_name, u.org_id, "
            + "COALESCE(o.org_name, '') AS org_name, COALESCE(u.mobile_phone, '') AS mobile_phone "
            + "FROM sys_user u LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0 ";

    private final JdbcTemplate jdbc;

    public SystemUserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 关键逻辑：查询强制绑定 tenantId、status=1、deleted=0，并把返回数量限制在 100 以内。
    @Override
    public List<UserDirectoryItem> listActive(long tenantId, String keyword, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        String filter = "";
        if (keyword != null && !keyword.isBlank()) {
            filter = "AND (u.username LIKE ? OR u.display_name LIKE ? OR COALESCE(u.mobile_phone, '') LIKE ?) ";
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        args.add(boundedLimit);
        return jdbc.query(SELECT + "WHERE u.tenant_id = ? AND u.deleted = 0 AND u.status = 1 " + filter
                        + "ORDER BY u.display_name, u.id LIMIT ?",
                (rs, rowNum) -> item(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                        rs.getLong("org_id"), rs.getString("org_name"), rs.getString("mobile_phone")),
                args.toArray());
    }

    @Override
    public Optional<UserDirectoryItem> findActive(long tenantId, long userId) {
        return jdbc.query(SELECT + "WHERE u.tenant_id = ? AND u.id = ? AND u.deleted = 0 AND u.status = 1",
                        (rs, rowNum) -> item(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                                rs.getLong("org_id"), rs.getString("org_name"), rs.getString("mobile_phone")),
                        tenantId, userId)
                .stream().findFirst();
    }

    private UserDirectoryItem item(long id, String username, String displayName, long orgId,
                                   String orgName, String mobilePhone) {
        return new UserDirectoryItem(id, username, displayName, orgId, orgName, mobilePhone);
    }
}
