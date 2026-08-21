package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectComponentService {
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;

    public ProjectComponentService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    public Map<String, Object> options(AuthUser user) {
        return Map.of("projects", projects(user), "components", components(user, null));
    }

    public List<Map<String, Object>> projects(AuthUser user) {
        return jdbc.queryForList("SELECT id, project_code, project_name, description, status, owner_id, created_at, updated_at FROM dm_project WHERE tenant_id = ? AND deleted = 0 ORDER BY updated_at DESC, id DESC", user.tenantId());
    }

    public List<Map<String, Object>> components(AuthUser user, Long projectId) {
        if (projectId == null) return jdbc.queryForList("SELECT id, project_id, component_code, component_name, description, owner_id, created_at, updated_at FROM dm_component WHERE tenant_id = ? AND deleted = 0 ORDER BY updated_at DESC, id DESC", user.tenantId());
        return jdbc.queryForList("SELECT id, project_id, component_code, component_name, description, owner_id, created_at, updated_at FROM dm_component WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY updated_at DESC, id DESC", user.tenantId(), projectId);
    }

    @Transactional
    public Map<String, Object> createProject(Map<String, Object> body, AuthUser user) {
        requireText(body, "projectCode"); requireText(body, "projectName");
        String code = normalizeProjectCode(body.get("projectCode"));
        String name = requireProjectName(body.get("projectName"));
        String description = requireDescription(body.get("description"));
        if (exists("SELECT COUNT(*) FROM dm_project WHERE tenant_id = ? AND project_code = ? AND deleted = 0", user.tenantId(), code)) throw new BusinessException(ErrorCode.CONFLICT, "Project code already exists");
        if (exists("SELECT COUNT(*) FROM dm_project WHERE tenant_id = ? AND project_name = ? AND deleted = 0", user.tenantId(), name)) throw new BusinessException(ErrorCode.CONFLICT, "Project name already exists");
        long id = nextId();
        jdbc.update("INSERT INTO dm_project (id, tenant_id, project_code, project_name, description, owner_id) VALUES (?, ?, ?, ?, ?, ?)", id, user.tenantId(), code, name, description, user.id());
        audit(user, "PROJECT_CREATE", "PROJECT", id);
        return jdbc.queryForMap("SELECT id, project_code, project_name, description, status, owner_id, created_at, updated_at FROM dm_project WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createComponent(Map<String, Object> body, AuthUser user) {
        long projectId = number(body, "projectId"); requireText(body, "componentCode"); requireText(body, "componentName");
        ensureProject(projectId, user);
        String code = String.valueOf(body.get("componentCode")).trim();
        if (exists("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND component_code = ? AND deleted = 0", user.tenantId(), projectId, code)) throw new BusinessException(ErrorCode.CONFLICT, "Component code already exists in project");
        long id = nextId();
        jdbc.update("INSERT INTO dm_component (id, tenant_id, project_id, component_code, component_name, description, owner_id) VALUES (?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, code, body.get("componentName"), body.getOrDefault("description", ""), user.id());
        audit(user, "COMPONENT_CREATE", "COMPONENT", id);
        return jdbc.queryForMap("SELECT id, project_id, component_code, component_name, description, owner_id, created_at, updated_at FROM dm_component WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateProject(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> row = find("SELECT id, owner_id FROM dm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        String name = requireProjectName(body.get("projectName"));
        String description = requireDescription(body.get("description"));
        if (exists("SELECT COUNT(*) FROM dm_project WHERE tenant_id = ? AND project_name = ? AND id <> ? AND deleted = 0", user.tenantId(), name, id)) throw new BusinessException(ErrorCode.CONFLICT, "Project name already exists");
        // project_code is immutable after creation; it is never updated here.
        jdbc.update("UPDATE dm_project SET project_name = ?, description = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", name, description, body.getOrDefault("status", "ACTIVE"), id, user.tenantId());
        audit(user, "PROJECT_UPDATE", "PROJECT", id);
        return jdbc.queryForMap("SELECT id, project_code, project_name, description, status, owner_id, created_at, updated_at FROM dm_project WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateComponent(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> row = find("SELECT id, project_id, owner_id FROM dm_component WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        requireText(body, "componentName");
        jdbc.update("UPDATE dm_component SET component_name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", body.get("componentName"), body.getOrDefault("description", ""), id, user.tenantId());
        audit(user, "COMPONENT_UPDATE", "COMPONENT", id);
        return jdbc.queryForMap("SELECT id, project_id, component_code, component_name, description, owner_id, created_at, updated_at FROM dm_component WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public void deleteProject(long id, AuthUser user) {
        Map<String, Object> row = find("SELECT id, owner_id FROM dm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        if (exists("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND deleted = 0", user.tenantId(), id) || exists("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND deleted = 0", user.tenantId(), id)) throw new BusinessException(ErrorCode.CONFLICT, "Project has related records");
        jdbc.update("UPDATE dm_project SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId()); audit(user, "PROJECT_DELETE", "PROJECT", id);
    }

    @Transactional
    public void deleteComponent(long id, AuthUser user) {
        Map<String, Object> row = find("SELECT id, owner_id FROM dm_component WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        if (exists("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND component_id = ? AND deleted = 0", user.tenantId(), id)) throw new BusinessException(ErrorCode.CONFLICT, "Component has related assets");
        jdbc.update("UPDATE dm_component SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId()); audit(user, "COMPONENT_DELETE", "COMPONENT", id);
    }

    private Map<String, Object> find(String sql, Object... args) { try { return jdbc.queryForMap(sql, args); } catch (Exception ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "Record not found"); } }
    private void ensureProject(long id, AuthUser user) { if (!exists("SELECT COUNT(*) FROM dm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project not found"); }
    private boolean exists(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count != null && count > 0; }
    private void audit(AuthUser user, String op, String type, long id) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, ?, ?)", user.tenantId(), user.id(), op, type, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
    private static void requireText(Map<String, Object> body, String key) { if (body.get(key) == null || String.valueOf(body.get(key)).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required"); }
    private static long number(Map<String, Object> body, String key) { requireText(body, key); try { return Long.parseLong(String.valueOf(body.get(key))); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); } }
    private static String normalizeProjectCode(Object raw) {
        String code = String.valueOf(raw).trim().toUpperCase();
        if (!code.matches("[A-Z0-9]+")) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project code must contain only letters and digits");
        if (code.length() > 64) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project code must be at most 64 characters");
        return code;
    }
    private static String requireProjectName(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "projectName is required");
        String name = String.valueOf(raw).trim();
        if (name.length() > 100) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project name must be at most 100 characters");
        return name;
    }
    private static String requireDescription(Object raw) {
        if (raw == null) return "";
        String description = String.valueOf(raw).trim();
        if (description.length() > 500) throw new BusinessException(ErrorCode.BAD_REQUEST, "Description must be at most 500 characters");
        return description;
    }
}
