package com.ccb.security.repository;

import com.ccb.security.model.AuthUser;
import com.ccb.security.model.RouteNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<AuthUser> findByUsername(String username) {
        return jdbcTemplate.query("""
                SELECT u.id, u.tenant_id, u.username, u.password_hash, u.display_name, u.org_id,
                       u.status, u.avatar_object_key, o.org_name
                FROM sys_user u LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0
                WHERE u.tenant_id = 1 AND u.username = ? AND u.deleted = 0
                """, this::mapUser, username).stream().findFirst();
    }

    public Optional<AuthUser> findById(long id, long tenantId) {
        return jdbcTemplate.query("""
                SELECT u.id, u.tenant_id, u.username, u.password_hash, u.display_name, u.org_id,
                       u.status, u.avatar_object_key, o.org_name
                FROM sys_user u LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0
                WHERE u.id = ? AND u.tenant_id = ? AND u.deleted = 0
                """, this::mapUser, id, tenantId).stream().findFirst();
    }

    public List<String> findRoles(long userId, long tenantId) {
        return jdbcTemplate.queryForList("""
                SELECT r.role_code FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id AND ur.tenant_id = r.tenant_id
                WHERE ur.user_id = ? AND r.tenant_id = ? AND r.status = 1 ORDER BY r.id
                """, String.class, userId, tenantId);
    }

    public List<String> findPermissions(long userId, long tenantId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT p.permission_code FROM sys_menu_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id
                JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id
                JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                WHERE ur.user_id = ? AND p.tenant_id = ? AND p.status = 1 AND r.status = 1
                UNION
                SELECT DISTINCT m.permission_code FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.tenant_id = m.tenant_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.tenant_id = rm.tenant_id
                JOIN sys_role r ON r.id = rm.role_id AND r.tenant_id = rm.tenant_id
                WHERE ur.user_id = ? AND m.tenant_id = ? AND m.status = 1 AND m.visible = 1 AND m.deleted = 0
                  AND r.status = 1 AND m.permission_code IS NOT NULL AND m.permission_code <> ''
                ORDER BY permission_code
                """, String.class, userId, tenantId, userId, tenantId);
    }

    public List<RouteNode> findRoutes(long userId, long tenantId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT m.id, m.parent_id, m.menu_type, m.menu_name, m.route_name,
                       m.route_path, m.component_path, m.permission_code, m.icon, m.sort_no
                FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.tenant_id = m.tenant_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.tenant_id = rm.tenant_id
                JOIN sys_role r ON r.id = rm.role_id AND r.tenant_id = rm.tenant_id
                WHERE ur.user_id = ? AND m.tenant_id = ? AND m.status = 1 AND m.visible = 1 AND m.deleted = 0
                  AND r.status = 1 ORDER BY m.parent_id, m.sort_no, m.id
                """, (rs, rowNum) -> new RouteNode(rs.getLong("id"), rs.getLong("parent_id"),
                        rs.getString("menu_type"), rs.getString("menu_name"), rs.getString("route_name"),
                        rs.getString("route_path"), rs.getString("component_path"),
                        rs.getString("permission_code"), rs.getString("icon"), rs.getInt("sort_no"), List.of()),
                userId, tenantId);
    }

    public void recordLogin(String username, boolean success, String reason, String clientIp, String userAgent) {
        jdbcTemplate.update("""
                INSERT INTO sys_login_log (id, tenant_id, username, success, failure_reason, client_ip, user_agent)
                VALUES (?, 1, ?, ?, ?, ?, ?)
                """, nextId(), username, success, reason, clientIp, userAgent);
    }

    public void updateLastLogin(long userId) {
        jdbcTemplate.update("UPDATE sys_user SET last_login_at = ? WHERE id = ? AND tenant_id = 1",
                Timestamp.from(Instant.now()), userId);
    }

    public int updatePassword(long userId, long tenantId, String passwordHash) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET password_hash = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND deleted = 0
                """, passwordHash, Timestamp.from(Instant.now()), userId, tenantId);
    }

    private AuthUser mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuthUser(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("username"),
                rs.getString("password_hash"), rs.getString("display_name"), rs.getLong("org_id"),
                rs.getBoolean("status"), rs.getString("org_name"), rs.getString("avatar_object_key"));
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
