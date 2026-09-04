package com.ccb.datamigration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StructuredAssetService {
    private static final Set<String> TYPES = Set.of("RULE","PARAMETER");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DataMigrationPermissionService permissions;
    private final ContentDocCodeGenerator docCodes;
    @Autowired
    public StructuredAssetService(JdbcTemplate jdbc, ObjectMapper objectMapper, DataMigrationPermissionService permissions,
                                  ContentDocCodeGenerator docCodes) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.permissions = permissions;
        this.docCodes = docCodes;
    }
    public StructuredAssetService(JdbcTemplate jdbc, ObjectMapper objectMapper, DataMigrationPermissionService permissions) {
        this(jdbc, objectMapper, permissions, new ContentDocCodeGenerator());
    }
    /** T32：列表必须落在单个可访问项目内，SQL 恒定 {@code project_id} 过滤。 */
    public List<Map<String,Object>> list(String type, Long projectId, String keyword, AuthUser user) {
        String table = table(type);
        long scope = permissions.requireProject(projectId, user);
        String q = "SELECT id, project_id, component_id, '" + type + "' AS asset_type, doc_code AS asset_code, doc_name AS asset_name, structured_data, owner_id, created_at, updated_at FROM " + table + " WHERE tenant_id = ? AND project_id = ? AND deleted = 0 AND (doc_code LIKE ? OR doc_name LIKE ?) ORDER BY updated_at DESC, id DESC";
        String k = "%" + Optional.ofNullable(keyword).orElse("") + "%";
        return jdbc.queryForList(q, user.tenantId(), scope, k, k);
    }
    public Map<String,Object> save(String type, Map<String,Object> body, AuthUser user) {
        String table = table(type);
        if (body.get("projectId") == null || body.get("assetName") == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset fields are required");
        long projectId;
        try { projectId = Long.parseLong(String.valueOf(body.get("projectId"))); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId must be numeric"); }
        // T32：新增归属取前端 projectId，但必须是本租户存在且调用者可访问的项目
        permissions.requireAccessible(projectId, user);
        if (body.get("componentId") != null && jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, body.get("componentId"), projectId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found");
        String structuredData;
        try { structuredData = objectMapper.writeValueAsString(body.getOrDefault("structuredData", Map.of())); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "structuredData must be valid JSON"); }
        long id = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
        jdbc.update("INSERT INTO " + table + " (id, tenant_id, project_id, component_id, doc_code, doc_name, structured_data, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, body.get("componentId"), docCodes.generate(type), body.get("assetName"), structuredData, user.id(), user.id(), user.id());
        audit(user, "STRUCTURED_CREATE", projectId, id);
        return jdbc.queryForMap("SELECT id, project_id, component_id, '" + type + "' AS asset_type, doc_code AS asset_code, doc_name AS asset_name, structured_data, owner_id, created_at, updated_at FROM " + table + " WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String,Object> update(String type, long id, Map<String,Object> body, AuthUser user) {
        String table = table(type);
        Map<String,Object> current = find(id, table, user, false);
        permissions.requireWrite(user, ((Number) current.get("owner_id")).longValue());
        if (body.get("assetName") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset fields are required");
        }
        // T32 决策 D2：维护操作的归属恒取库中记录，入参 projectId 一律忽略，UPDATE 不再包含 project_id
        long projectId = permissions.requireStoredProject(current.get("project_id"), user);
        Long componentId = body.get("componentId") == null || String.valueOf(body.get("componentId")).isBlank()
                ? null : number(body.get("componentId"), "componentId");
        if (componentId != null) ensureComponent(componentId, projectId, user);
        String name = text(body.get("assetName"), "assetName");
        String structuredData = json(body.getOrDefault("structuredData", Map.of()));
        jdbc.update("UPDATE " + table + " SET component_id = ?, doc_name = ?, structured_data = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", componentId, name, structuredData, id, user.tenantId());
        audit(user, "STRUCTURED_UPDATE", projectId, id);
        return find(id, table, user, false);
    }

    @Transactional
    public void delete(Collection<Long> ids, String type, AuthUser user) {
        String table = table(type);
        for (Long id : ids == null ? List.<Long>of() : ids) {
            Map<String,Object> row = find(id, table, user, false);
            long projectId = permissions.requireStoredProject(row.get("project_id"), user);
            permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
            if (hasRelation(id, user)) throw new BusinessException(ErrorCode.CONFLICT, "Structured asset has related records");
            jdbc.update("UPDATE " + table + " SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId());
            audit(user, "STRUCTURED_DELETE", projectId, id);
        }
    }

    /** 统一回收站：结构化表软删总数（T32 按项目统计，SQL COUNT，不拉明细）。 */
    public long countDeleted(String type, long projectId, String keyword, AuthUser user) {
        String table = table(type);
        StringBuilder q = new StringBuilder("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND project_id = ? AND deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), projectId));
        appendRecycleBinKeyword(q, args, keyword);
        Long total = jdbc.queryForObject(q.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    /** 统一回收站原生分页（T32 按项目）：结构化表软删按业务编号（doc_code）升序、空值末尾，取前 {@code limit} 行。 */
    public List<Map<String,Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user) {
        String table = table(type);
        if (limit <= 0) return List.of();
        StringBuilder q = new StringBuilder("SELECT id, project_id, component_id, '" + type + "' AS asset_type, doc_code AS asset_code, doc_name AS asset_name, structured_data, owner_id, created_at, updated_at, deleted_by, deleted_at FROM " + table + " WHERE tenant_id = ? AND project_id = ? AND deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), projectId));
        appendRecycleBinKeyword(q, args, keyword);
        q.append(" ORDER BY (doc_code IS NULL OR doc_code = ''), doc_code ASC, deleted_at DESC, id ASC LIMIT ?");
        args.add(limit);
        return jdbc.queryForList(q.toString(), args.toArray());
    }

    /** 查询结构化内容的软删除详情。 */
    public Map<String, Object> findDeletedDetail(String type, long id, AuthUser user) {
        String table = table(type);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id, component_id, '" + type + "' AS asset_type, doc_code AS asset_code, doc_name AS asset_name, "
                        + "structured_data, owner_id, created_at, updated_at, deleted_by, deleted_at FROM " + table
                        + " WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found in recycle bin");
        permissions.requireStoredProject(rows.get(0).get("project_id"), user);
        return rows.get(0);
    }

    private static void appendRecycleBinKeyword(StringBuilder q, List<Object> args, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            q.append(" AND (doc_code LIKE ? OR doc_name LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k);
        }
    }

    /** 统一回收站：结构化表恢复（管理员权限由回收站入口统一校验）。 */
    @Transactional
    public void restore(String type, Collection<Long> ids, AuthUser user) {
        String table = table(type);
        for (Long id : ids) {
            // T32：恢复前按库中归属做项目隔离校验，跨项目 id 直接拒绝
            long projectId = permissions.requireStoredProject(find(id, table, user, true).get("project_id"), user);
            try {
                int changed = jdbc.update("UPDATE " + table + " SET deleted = 0, deleted_by = NULL, deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "Structured asset state changed, please retry");
            } catch (DataIntegrityViolationException ex) {
                // 恢复后活动行 doc_code 与既有活动行冲突（uk_dm_*_active_code）：与统一方案同构，翻译为 CONFLICT(40900)。
                throw new BusinessException(ErrorCode.CONFLICT, "内容编号在该项目下已存在，无法恢复");
            }
            audit(user, "STRUCTURED_RESTORE", projectId, id);
        }
    }

    /** 统一回收站：结构化表彻底删除。 */
    @Transactional
    public void purge(String type, Collection<Long> ids, AuthUser user) {
        String table = table(type);
        for (Long id : ids) {
            long projectId = permissions.requireStoredProject(find(id, table, user, true).get("project_id"), user);
            int changed = jdbc.update("DELETE FROM " + table + " WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found in recycle bin");
            audit(user, "STRUCTURED_PURGE", projectId, id);
        }
    }

    private static String table(String type) {
        if (type == null || !TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
        return ContentAssetTables.tableFor(type);
    }

    private Map<String,Object> find(long id, String table, AuthUser user, boolean deleted) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, project_id, component_id, '" + ContentAssetTables.typeFor(table) + "' AS asset_type, doc_code AS asset_code, doc_name AS asset_name, structured_data, owner_id, created_at, updated_at FROM " + table + " WHERE id = ? AND tenant_id = ? AND deleted = ?", id, user.tenantId(), deleted ? 1 : 0);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Structured asset not found");
        return rows.get(0);
    }

    private boolean hasRelation(long id, AuthUser user) {
        String condition = "tenant_id = ? AND deleted = 0 AND id <> ? AND (JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.tableStructureId')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.intermediateTableId')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.targetTableId')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.sourceTableId')) = ?)";
        String sql = ContentAssetTables.activeCountUnionSql(condition, ContentAssetTables.STRUCTURED_TABLES);
        Object[] args = new Object[]{user.tenantId(), id, String.valueOf(id), String.valueOf(id), String.valueOf(id), String.valueOf(id),
                                     user.tenantId(), id, String.valueOf(id), String.valueOf(id), String.valueOf(id), String.valueOf(id)};
        List<Long> counts = jdbc.queryForList(sql, Long.class, args);
        return counts.stream().mapToLong(Long::longValue).sum() > 0;
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

    private void audit(AuthUser user, String operation, long projectId, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, ?, 'ASSET', ?)", user.tenantId(), user.id(), projectId, operation, id);
    }
}
