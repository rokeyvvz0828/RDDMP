package com.ccb.security.repository;

import com.ccb.security.model.AuthUser;
import com.ccb.security.model.RouteNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
        return findPermissions(userId, tenantId, null);
    }

    public List<String> findPermissions(long userId, long tenantId, Long projectId) {
        Set<String> permissions = new TreeSet<>(jdbcTemplate.queryForList("""
                SELECT DISTINCT p.permission_code
                FROM sys_menu_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id
                JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id
                JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                WHERE ur.user_id = ? AND p.tenant_id = ? AND p.status = 1 AND r.status = 1 AND r.deleted = 0
                  AND p.permission_code LIKE 'system:%'
                """, String.class, userId, tenantId));
        if (projectId == null) return List.copyOf(permissions);
        permissions.addAll(jdbcTemplate.queryForList("""
                SELECT DISTINCT permission.permission_code
                FROM sys_menu_permission permission
                WHERE permission.tenant_id = ? AND permission.status = 1
                  AND permission.permission_code NOT LIKE 'system:%'
                  AND (
                    EXISTS (
                      SELECT 1 FROM pm_project project
                      WHERE project.id = ? AND project.tenant_id = ? AND project.deleted = 0
                        AND project.owner_id = ?
                    )
                    OR EXISTS (
                      SELECT 1 FROM pm_project_member member
                      JOIN pm_project_member_role mr ON mr.member_id = member.id AND mr.tenant_id = member.tenant_id
                      JOIN pm_project_role role ON role.id = mr.role_id AND role.project_id = member.project_id
                        AND role.tenant_id = member.tenant_id AND role.deleted = 0
                      LEFT JOIN pm_project_role_permission rp ON rp.project_id = role.project_id
                        AND rp.role_id = role.id AND rp.tenant_id = role.tenant_id
                      WHERE member.project_id = ? AND member.tenant_id = ? AND member.user_id = ?
                        AND member.status = 1 AND member.deleted = 0
                        AND (role.role_code = 'PM' OR rp.permission_id = permission.id)
                    )
                    OR EXISTS (
                      SELECT 1 FROM sys_user_role ur JOIN sys_role system_role
                        ON system_role.id = ur.role_id AND system_role.tenant_id = ur.tenant_id
                      WHERE ur.user_id = ? AND ur.tenant_id = ? AND system_role.role_code = 'SUPER_ADMIN'
                        AND system_role.status = 1 AND system_role.deleted = 0
                    )
                  )
                """, String.class, tenantId, projectId, tenantId, userId, projectId, tenantId, userId, userId, tenantId));
        return List.copyOf(permissions);
    }

    public boolean hasAnyProjectManagementPermission(long userId, long tenantId) {
        Integer granted = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pm_project project
                WHERE project.tenant_id = ? AND project.deleted = 0
                  AND (
                    project.owner_id = ?
                    OR EXISTS (
                      SELECT 1 FROM pm_project_member member
                      JOIN pm_project_member_role mr ON mr.member_id = member.id AND mr.tenant_id = member.tenant_id
                      JOIN pm_project_role role ON role.id = mr.role_id AND role.project_id = member.project_id
                        AND role.tenant_id = member.tenant_id AND role.deleted = 0
                      LEFT JOIN pm_project_role_permission rp ON rp.project_id = role.project_id
                        AND rp.role_id = role.id AND rp.tenant_id = role.tenant_id
                      LEFT JOIN sys_menu_permission permission ON permission.id = rp.permission_id
                        AND permission.tenant_id = rp.tenant_id AND permission.status = 1
                      WHERE member.project_id = project.id AND member.tenant_id = project.tenant_id
                        AND member.user_id = ? AND member.status = 1 AND member.deleted = 0
                        AND (role.role_code = 'PM' OR permission.permission_code = 'project:project:list')
                    )
                    OR EXISTS (
                      SELECT 1 FROM sys_user_role ur JOIN sys_role system_role
                        ON system_role.id = ur.role_id AND system_role.tenant_id = ur.tenant_id
                      WHERE ur.user_id = ? AND ur.tenant_id = ? AND system_role.role_code = 'SUPER_ADMIN'
                        AND system_role.status = 1 AND system_role.deleted = 0
                    )
                  )
                """, Integer.class, tenantId, userId, userId, userId, tenantId);
        return granted != null && granted > 0;
    }

    public boolean hasProjectAccess(long userId, long tenantId, long projectId) {
        Integer allowed = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pm_project project
                WHERE project.id = ? AND project.tenant_id = ? AND project.deleted = 0
                  AND (
                    project.owner_id = ?
                    OR EXISTS (
                      SELECT 1 FROM pm_project_member member
                      WHERE member.project_id = project.id AND member.tenant_id = project.tenant_id
                        AND member.user_id = ? AND member.status = 1 AND member.deleted = 0
                    )
                    OR EXISTS (
                      SELECT 1 FROM sys_user_role ur JOIN sys_role role
                        ON role.id = ur.role_id AND role.tenant_id = ur.tenant_id
                      WHERE ur.user_id = ? AND ur.tenant_id = ? AND role.role_code = 'SUPER_ADMIN'
                        AND role.status = 1 AND role.deleted = 0
                    )
                  )
                """, Integer.class, projectId, tenantId, userId, userId, userId, tenantId);
        return allowed != null && allowed > 0;
    }

    private List<String> legacyFindPermissions(long userId, long tenantId) {
        return jdbcTemplate.queryForList("""
                SELECT permission_code FROM (
                SELECT DISTINCT p.permission_code AS permission_code FROM sys_menu_permission p
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
                UNION
                SELECT 'system:access' AS permission_code WHERE EXISTS (
                    SELECT 1 FROM pm_project p WHERE p.tenant_id = ? AND p.deleted = 0
                      AND (p.owner_id = ? OR EXISTS (
                        SELECT 1 FROM pm_project_member member
                        JOIN pm_project_member_role mr ON mr.member_id = member.id AND mr.tenant_id = member.tenant_id
                        JOIN pm_project_role pr ON pr.id = mr.role_id AND pr.tenant_id = mr.tenant_id
                        WHERE member.project_id = p.id AND member.user_id = ? AND member.status = 1 AND member.deleted = 0
                          AND pr.project_id = p.id AND pr.role_code = 'PM' AND pr.deleted = 0
                      ))
                )
                UNION
                SELECT 'system:audit:list' AS permission_code WHERE EXISTS (
                    SELECT 1 FROM pm_project p WHERE p.tenant_id = ? AND p.deleted = 0
                      AND (p.owner_id = ? OR EXISTS (
                        SELECT 1 FROM pm_project_member member
                        JOIN pm_project_member_role mr ON mr.member_id = member.id AND mr.tenant_id = member.tenant_id
                        JOIN pm_project_role pr ON pr.id = mr.role_id AND pr.tenant_id = mr.tenant_id
                        WHERE member.project_id = p.id AND member.user_id = ? AND member.status = 1 AND member.deleted = 0
                          AND pr.project_id = p.id AND pr.role_code = 'PM' AND pr.deleted = 0
                      ))
                )
                ) permissions
                ORDER BY permission_code
                """, String.class, userId, tenantId, userId, tenantId,
                tenantId, userId, userId, tenantId, userId, userId);
    }

    public List<RouteNode> findRoutes(long userId, long tenantId) {
        return findRoutes(userId, tenantId, null);
    }

    public List<RouteNode> findRoutes(long userId, long tenantId, Long projectId) {
        return jdbcTemplate.query("""
                SELECT id, parent_id, menu_type, menu_name, route_name, route_path, component_path,
                       permission_code, icon, sort_no
                FROM sys_menu
                WHERE tenant_id = ? AND status = 1 AND visible = 1 AND deleted = 0
                ORDER BY parent_id, sort_no, id
                """, (rs, rowNum) -> new RouteNode(rs.getLong("id"), rs.getLong("parent_id"),
                        rs.getString("menu_type"), rs.getString("menu_name"), rs.getString("route_name"),
                        rs.getString("route_path"), rs.getString("component_path"),
                        rs.getString("permission_code"), rs.getString("icon"), rs.getInt("sort_no"), List.of()),
                tenantId);
    }

    private List<RouteNode> legacyFindRoutes(long userId, long tenantId) {
        return jdbcTemplate.query("""
                SELECT id, parent_id, menu_type, menu_name, route_name, route_path, component_path,
                       permission_code, icon, sort_no
                FROM (
                SELECT DISTINCT m.id, m.parent_id, m.menu_type, m.menu_name, m.route_name,
                       m.route_path, m.component_path, m.permission_code, m.icon, m.sort_no
                FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.tenant_id = m.tenant_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.tenant_id = rm.tenant_id
                JOIN sys_role r ON r.id = rm.role_id AND r.tenant_id = rm.tenant_id
                WHERE ur.user_id = ? AND m.tenant_id = ? AND m.status = 1 AND m.visible = 1 AND m.deleted = 0
                  AND r.status = 1
                UNION
                SELECT m.id, m.parent_id, m.menu_type, m.menu_name, m.route_name,
                       m.route_path, m.component_path, m.permission_code, m.icon, m.sort_no
                FROM sys_menu m
                WHERE m.tenant_id = ? AND m.status = 1 AND m.visible = 1 AND m.deleted = 0
                  AND (m.route_path = '/system' OR m.route_path = '/system/audit')
                  AND EXISTS (
                    SELECT 1 FROM pm_project p WHERE p.tenant_id = ? AND p.deleted = 0
                      AND (p.owner_id = ? OR EXISTS (
                        SELECT 1 FROM pm_project_member member
                        JOIN pm_project_member_role mr ON mr.member_id = member.id AND mr.tenant_id = member.tenant_id
                        JOIN pm_project_role pr ON pr.id = mr.role_id AND pr.tenant_id = mr.tenant_id
                        WHERE member.project_id = p.id AND member.user_id = ? AND member.status = 1 AND member.deleted = 0
                          AND pr.project_id = p.id AND pr.role_code = 'PM' AND pr.deleted = 0
                      ))
                  )
                ) routes
                ORDER BY parent_id, sort_no, id
                """, (rs, rowNum) -> new RouteNode(rs.getLong("id"), rs.getLong("parent_id"),
                        rs.getString("menu_type"), rs.getString("menu_name"), rs.getString("route_name"),
                        rs.getString("route_path"), rs.getString("component_path"),
                        rs.getString("permission_code"), rs.getString("icon"), rs.getInt("sort_no"), List.of()),
                userId, tenantId, tenantId, tenantId, userId, userId);
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

    public String findAvatarObjectKey(long userId, long tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT avatar_object_key FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0",
                String.class,
                userId,
                tenantId);
    }

    public int updateAvatarObjectKey(long userId, long tenantId, String objectKey) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET avatar_object_key = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND deleted = 0
                """, objectKey, Timestamp.from(Instant.now()), userId, tenantId);
    }

    private AuthUser mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuthUser(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("username"),
                rs.getString("password_hash"), rs.getString("display_name"), rs.getLong("org_id"),
                rs.getBoolean("status"), rs.getString("org_name"), rs.getString("avatar_object_key"));
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
