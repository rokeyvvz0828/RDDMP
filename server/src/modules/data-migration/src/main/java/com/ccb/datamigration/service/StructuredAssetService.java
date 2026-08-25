package com.ccb.datamigration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StructuredAssetService {
    private static final Set<String> TYPES = Set.of("RULE","PARAMETER","TABLE_STRUCTURE","INTERMEDIATE_TABLE");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DataMigrationPermissionService permissions;
    public StructuredAssetService(JdbcTemplate jdbc, ObjectMapper objectMapper, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.permissions = permissions;
    }
    public List<Map<String,Object>> list(String type, String keyword, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
        String q = "SELECT id, project_id, component_id, asset_type, asset_code, asset_name, structured_data, owner_id, created_at, updated_at FROM dm_asset WHERE tenant_id = ? AND asset_type = ? AND deleted = 0 AND (asset_code LIKE ? OR asset_name LIKE ?) ORDER BY updated_at DESC, id DESC";
        String k = "%" + Optional.ofNullable(keyword).orElse("") + "%";
        return jdbc.queryForList(q, user.tenantId(), type, k, k);
    }
    public Map<String,Object> save(String type, Map<String,Object> body, AuthUser user) {
        if (!TYPES.contains(type) || body.get("projectId") == null || body.get("assetCode") == null || body.get("assetName") == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset fields are required");
        long projectId;
        try { projectId = Long.parseLong(String.valueOf(body.get("projectId"))); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId must be numeric"); }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, projectId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project not found");
        if (body.get("componentId") != null && jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, body.get("componentId"), projectId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found");
        String structuredData;
        try { structuredData = objectMapper.writeValueAsString(body.getOrDefault("structuredData", Map.of())); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "structuredData must be valid JSON"); }
        long id = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
        jdbc.update("INSERT INTO dm_asset (id, tenant_id, project_id, component_id, asset_type, asset_code, asset_name, structured_data, owner_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, body.get("componentId"), type, body.get("assetCode"), body.get("assetName"), structuredData, user.id());
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, 'STRUCTURED_CREATE', 'ASSET', ?)", user.tenantId(), user.id(), id);
        return jdbc.queryForMap("SELECT id, project_id, component_id, asset_type, asset_code, asset_name, structured_data, owner_id, created_at, updated_at FROM dm_asset WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String,Object> update(String type, long id, Map<String,Object> body, AuthUser user) {
        validateType(type);
        Map<String,Object> current = find(id, type, user, false);
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        if (body.get("projectId") == null || body.get("assetCode") == null || body.get("assetName") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset fields are required");
        }
        long projectId = number(body.get("projectId"), "projectId");
        ensureProject(projectId, user);
        Long componentId = body.get("componentId") == null || String.valueOf(body.get("componentId")).isBlank()
                ? null : number(body.get("componentId"), "componentId");
        if (componentId != null) ensureComponent(componentId, projectId, user);
        String code = text(body.get("assetCode"), "assetCode");
        String name = text(body.get("assetName"), "assetName");
        if (exists("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = ? AND asset_code = ? AND deleted = 0 AND id <> ?", user.tenantId(), projectId, type, code, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Structured asset code already exists");
        }
        String structuredData = json(body.getOrDefault("structuredData", Map.of()));
        jdbc.update("UPDATE dm_asset SET project_id = ?, component_id = ?, asset_code = ?, asset_name = ?, structured_data = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = 0", projectId, componentId, code, name, structuredData, id, user.tenantId(), type);
        audit(user, "STRUCTURED_UPDATE", id);
        return find(id, type, user, false);
    }

    @Transactional
    public void delete(Collection<Long> ids, String type, AuthUser user) {
        validateType(type);
        for (Long id : ids == null ? List.<Long>of() : ids) {
            Map<String,Object> row = find(id, type, user, false);
            permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
            if (hasRelation(id, user)) throw new BusinessException(ErrorCode.CONFLICT, "Structured asset has related records");
            jdbc.update("UPDATE dm_asset SET deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = 0", id, user.tenantId(), type);
            audit(user, "STRUCTURED_DELETE", id);
        }
    }

    private void validateType(String type) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
    }

    private Map<String,Object> find(long id, String type, AuthUser user, boolean deleted) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, project_id, component_id, asset_type, asset_code, asset_name, structured_data, owner_id, created_at, updated_at FROM dm_asset WHERE id = ? AND tenant_id = ? AND asset_type = ? AND deleted = ?", id, user.tenantId(), type, deleted ? 1 : 0);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found");
        return rows.get(0);
    }

    private boolean hasRelation(long id, AuthUser user) {
        String sql = "SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND deleted = 0 AND id <> ? AND (JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.tableStructureId')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.intermediateTableId')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.targetTableId')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.sourceTableId')) = ?)";
        return exists(sql, user.tenantId(), id, String.valueOf(id), String.valueOf(id), String.valueOf(id), String.valueOf(id));
    }

    private void ensureProject(long id, AuthUser user) {
        if (!exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project not found");
    }

    private void ensureComponent(long id, long projectId, AuthUser user) {
        if (!exists("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", id, projectId, user.tenantId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "structuredData must be valid JSON"); }
    }

    private static String text(Object value, String field) {
        if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + " is required");
        return String.valueOf(value).trim();
    }

    private static long number(Object value, String field) {
        try { return Long.parseLong(text(value, field)); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, field + " must be numeric"); }
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private void audit(AuthUser user, String operation, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ASSET', ?)", user.tenantId(), user.id(), operation, id);
    }
}
