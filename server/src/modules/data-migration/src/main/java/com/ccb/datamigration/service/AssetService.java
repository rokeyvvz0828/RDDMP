package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("REPORT","MEETING","PLAN","MAPPING_DOC","VALIDATION_DOC","PARAMETER","DEPENDENCY","SCRIPT","TOPIC","RELEASE_DRILL","ISSUE","TRANSFORM_DOC","CONFIG","OTHER","RULE","TABLE_STRUCTURE","INTERMEDIATE_TABLE");
    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;
    private final DataMigrationPermissionService permissions;

    public AssetService(JdbcTemplate jdbc, MinioStorageService storage, DataMigrationPermissionService permissions) { this.jdbc = jdbc; this.storage = storage; this.permissions = permissions; }

    public List<Map<String, Object>> list(String type, String keyword, AuthUser user, boolean recycle) {
        if (type != null && !TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported asset type");
        StringBuilder sql = new StringBuilder("SELECT id, project_id, component_id, asset_type, asset_code, asset_name, content_type, file_size, checksum_md5, owner_id, created_at, updated_at FROM dm_asset WHERE tenant_id = ? AND deleted = ?");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), recycle ? 1 : 0));
        if (type != null) { sql.append(" AND asset_type = ?"); args.add(type); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (asset_code LIKE ? OR asset_name LIKE ?)"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> upload(String type, long projectId, Long componentId, String assetCode, MultipartFile file, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported asset type");
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "File is empty or exceeds 50 MB");
        if (assetCode == null || assetCode.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "assetCode is required");
        ensureProject(projectId, user); if (componentId != null) ensureComponent(componentId, projectId, user);
        Map<String, Object> old = findOptional("SELECT id, object_key, owner_id FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = ? AND asset_code = ? AND deleted = 0", user.tenantId(), projectId, type, assetCode);
        if (old != null) permissions.requireWrite(user, ((Number) old.get("owner_id")).longValue());
        long id = old == null ? nextId() : ((Number) old.get("id")).longValue();
        String key = "data-migration/" + user.tenantId() + "/" + projectId + "/" + UUID.randomUUID();
        try {
            storage.put(key, file.getInputStream(), file.getSize(), Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"));
            if (old == null) jdbc.update("INSERT INTO dm_asset (id, tenant_id, project_id, component_id, asset_type, asset_code, asset_name, content_type, file_size, object_key, owner_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, user.tenantId(), projectId, componentId, type, assetCode, file.getOriginalFilename(), file.getContentType(), file.getSize(), key, user.id());
            else jdbc.update("UPDATE dm_asset SET component_id = ?, asset_name = ?, content_type = ?, file_size = ?, object_key = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", componentId, file.getOriginalFilename(), file.getContentType(), file.getSize(), key, id, user.tenantId());
            if (old != null) storage.delete((String) old.get("object_key"));
            audit(user, old == null ? "ASSET_UPLOAD" : "ASSET_REPLACE", id);
            return jdbc.queryForMap("SELECT id, project_id, component_id, asset_type, asset_code, asset_name, content_type, file_size, checksum_md5, owner_id, created_at, updated_at FROM dm_asset WHERE id = ? AND tenant_id = ?", id, user.tenantId());
        } catch (IOException ex) { storage.delete(key); throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read uploaded file"); }
    }

    @Transactional public void delete(Collection<Long> ids, AuthUser user) { for (Long id : ids) { Map<String,Object> row = find(id, user, false); permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue()); jdbc.update("UPDATE dm_asset SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId()); audit(user, "ASSET_DELETE", id); } }
    @Transactional public void restore(Collection<Long> ids, AuthUser user) { permissions.requireAdmin(user); for (Long id : ids) { jdbc.update("UPDATE dm_asset SET deleted = 0 WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId()); audit(user, "ASSET_RESTORE", id); } }
    @Transactional public void purge(Collection<Long> ids, AuthUser user) { permissions.requireAdmin(user); for (Long id : ids) { Map<String,Object> row = find(id, user, true); jdbc.update("DELETE FROM dm_asset WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId()); storage.delete((String) row.get("object_key")); audit(user, "ASSET_PURGE", id); } }
    public String download(long id, AuthUser user) { Map<String,Object> row = find(id, user, false); return storage.presignedUrl((String) row.get("object_key")); }

    private Map<String,Object> find(long id, AuthUser user, boolean deleted) { Map<String,Object> row = findOptional("SELECT id, owner_id, object_key FROM dm_asset WHERE id = ? AND tenant_id = ? AND deleted = ?", id, user.tenantId(), deleted ? 1 : 0); if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Asset not found"); return row; }
    private Map<String,Object> findOptional(String sql, Object... args) { List<Map<String,Object>> rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? null : rows.get(0); }
    private void ensureProject(long id, AuthUser u) { if (jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, u.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project not found"); }
    private void ensureComponent(long id, long projectId, AuthUser u) { if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, projectId, u.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found"); }
    private void audit(AuthUser u, String op, long id) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ASSET', ?)", u.tenantId(), u.id(), op, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
