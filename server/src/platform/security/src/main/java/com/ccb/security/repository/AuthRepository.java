package com.ccb.security.repository;

import com.ccb.security.model.AuthUser;
import com.ccb.security.model.RouteNode;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class AuthRepository {
    private final AuthMapper mapper;

    public AuthRepository(AuthMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<AuthUser> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username)).map(this::toAuthUser);
    }

    public Optional<AuthUser> findById(long id, long tenantId) {
        return Optional.ofNullable(mapper.findById(id, tenantId)).map(this::toAuthUser);
    }

    public List<String> findRoles(long userId, long tenantId) {
        return mapper.findRoles(userId, tenantId);
    }

    public List<String> findPermissions(long userId, long tenantId) {
        return findPermissions(userId, tenantId, null);
    }

    public List<String> findPermissions(long userId, long tenantId, Long projectId) {
        Set<String> permissions = new TreeSet<>(mapper.findSystemPermissions(userId, tenantId));
        if (projectId != null) permissions.addAll(mapper.findProjectPermissions(userId, tenantId, projectId));
        return List.copyOf(permissions);
    }

    public boolean hasAnyProjectManagementPermission(long userId, long tenantId) {
        return mapper.countProjectManagementPermissions(userId, tenantId) > 0;
    }

    public boolean hasProjectAccess(long userId, long tenantId, long projectId) {
        return mapper.countProjectAccess(userId, tenantId, projectId) > 0;
    }

    public List<RouteNode> findRoutes(long userId, long tenantId) {
        return findRoutes(userId, tenantId, null);
    }

    public List<RouteNode> findRoutes(long userId, long tenantId, Long projectId) {
        return mapper.findRoutes(tenantId).stream().map(this::toRoute).toList();
    }

    public void recordLogin(String username, boolean success, String reason, String clientIp, String userAgent) {
        mapper.recordLogin(nextId(), username, success, reason, clientIp, userAgent);
    }

    public void updateLastLogin(long userId) {
        mapper.updateLastLogin(userId, Timestamp.from(Instant.now()));
    }

    public int updatePassword(long userId, long tenantId, String passwordHash) {
        return mapper.updatePassword(userId, tenantId, passwordHash, Timestamp.from(Instant.now()));
    }

    public String findAvatarObjectKey(long userId, long tenantId) {
        return mapper.findAvatarObjectKey(userId, tenantId);
    }

    public int updateAvatarObjectKey(long userId, long tenantId, String objectKey) {
        return mapper.updateAvatarObjectKey(userId, tenantId, objectKey, Timestamp.from(Instant.now()));
    }

    private AuthUser toAuthUser(Map<String, Object> row) {
        return new AuthUser(number(row, "id"), number(row, "tenant_id"), string(row, "username"),
                string(row, "password_hash"), string(row, "display_name"), number(row, "org_id"),
                booleanValue(row.get("status")), string(row, "org_name"), string(row, "avatar_object_key"));
    }

    private RouteNode toRoute(Map<String, Object> row) {
        return new RouteNode(number(row, "id"), number(row, "parent_id"), string(row, "menu_type"),
                string(row, "menu_name"), string(row, "route_name"), string(row, "route_path"),
                string(row, "component_path"), string(row, "permission_code"), string(row, "icon"),
                (int) number(row, "sort_no"), List.of());
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
