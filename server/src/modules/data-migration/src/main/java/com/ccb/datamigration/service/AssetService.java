package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 通用文件型资产服务；REPORT 也通过本服务处理，不再维护独立上传链路。 */
@Service
public class AssetService {
    public static final String BUSINESS_TYPE = "DATA_MIGRATION_ASSET";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("REPORT", "MEETING", "PLAN", "MAPPING_DOC", "VALIDATION_DOC", "PARAMETER", "DEPENDENCY", "SCRIPT", "TOPIC", "RELEASE_DRILL", "TRANSFORM_DOC", "CONFIG", "OTHER", "RULE", "TABLE_STRUCTURE", "INTERMEDIATE_TABLE");
    private static final String MD5_PATTERN = "[0-9a-fA-F]{32}";

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final MinioStorageService storage;
    private final DataMigrationPermissionService permissions;

    public AssetService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway, MinioStorageService storage, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc; this.attachmentGateway = attachmentGateway; this.storage = storage; this.permissions = permissions;
    }

    public List<Map<String, Object>> list(String type, String keyword, AuthUser user, boolean recycle) {
        if (type != null && !TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported asset type");
        StringBuilder sql = new StringBuilder("SELECT id, project_id, component_id, asset_type, asset_code, asset_name, content_type, file_size, attachment_id, checksum_md5, owner_id, created_at, updated_at FROM dm_asset WHERE tenant_id = ? AND deleted = ?");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), recycle ? 1 : 0));
        if (type != null) { sql.append(" AND asset_type = ?"); args.add(type); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (asset_code LIKE ? OR asset_name LIKE ?)"); String value = "%" + keyword.trim() + "%"; args.add(value); args.add(value); }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> upload(String type, long projectId, Long componentId, String assetCode, Long attachmentId, String checksumMd5, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported asset type");
        if (assetCode == null || assetCode.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "assetCode is required");
        ensureProject(projectId, user); if (componentId != null) ensureComponent(componentId, projectId, user);
        AttachmentItem attachment = resolveAttachment(attachmentId, user);
        if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "File is empty or exceeds 50 MB");
        String md5 = normalizeMd5(checksumMd5);
        Map<String, Object> old = findOptional("SELECT id, object_key, attachment_id, owner_id FROM dm_asset WHERE tenant_id = ? AND project_id = ? AND asset_type = ? AND asset_code = ? AND deleted = 0", user.tenantId(), projectId, type, assetCode.trim());
        if (old != null) permissions.requireWrite(user, ((Number) old.get("owner_id")).longValue());
        long id = old == null ? nextId() : ((Number) old.get("id")).longValue();
        assertMd5Available(md5, user.tenantId(), id);
        if (old == null) jdbc.update("INSERT INTO dm_asset (id, tenant_id, project_id, component_id, asset_type, asset_code, asset_name, content_type, file_size, object_key, attachment_id, checksum_md5, owner_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)", id, user.tenantId(), projectId, componentId, type, assetCode.trim(), attachment.fileName(), attachment.contentType(), attachment.fileSize(), attachment.id(), md5, user.id());
        else jdbc.update("UPDATE dm_asset SET component_id = ?, asset_name = ?, content_type = ?, file_size = ?, object_key = NULL, attachment_id = ?, checksum_md5 = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", componentId, attachment.fileName(), attachment.contentType(), attachment.fileSize(), attachment.id(), md5, id, user.tenantId());
        attachmentGateway.bind(new AttachmentBindingCommand(attachment.id(), BUSINESS_TYPE, String.valueOf(id), String.valueOf(projectId)), user);
        if (old != null && old.get("attachment_id") != null && ((Number) old.get("attachment_id")).longValue() != attachment.id()) attachmentGateway.deleteBound(((Number) old.get("attachment_id")).longValue(), BUSINESS_TYPE, String.valueOf(id), user);
        if (old != null && old.get("object_key") != null) storage.delete((String) old.get("object_key"));
        audit(user, old == null ? "ASSET_UPLOAD" : "ASSET_REPLACE", id);
        return jdbc.queryForMap("SELECT id, project_id, component_id, asset_type, asset_code, asset_name, content_type, file_size, attachment_id, checksum_md5, owner_id, created_at, updated_at FROM dm_asset WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    public boolean isMd5Available(String checksumMd5, AuthUser user) {
        String md5 = normalizeMd5(checksumMd5);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND checksum_md5 = ? AND deleted = 0", Integer.class, user.tenantId(), md5);
        return count == null || count == 0;
    }

    @Transactional public void delete(Collection<Long> ids, AuthUser user) { for (Long id : ids) { Map<String,Object> row = find(id, user, false); permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue()); jdbc.update("UPDATE dm_asset SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId()); audit(user, "ASSET_DELETE", id); } }
    @Transactional public void restore(Collection<Long> ids, AuthUser user) { permissions.requireAdmin(user); for (Long id : ids) { jdbc.update("UPDATE dm_asset SET deleted = 0 WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId()); audit(user, "ASSET_RESTORE", id); } }
    @Transactional public void purge(Collection<Long> ids, AuthUser user) { permissions.requireAdmin(user); for (Long id : ids) { Map<String,Object> row = find(id, user, true); if (row.get("attachment_id") != null) attachmentGateway.deleteBound(((Number) row.get("attachment_id")).longValue(), BUSINESS_TYPE, String.valueOf(id), user); jdbc.update("DELETE FROM dm_asset WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId()); if (row.get("attachment_id") == null && row.get("object_key") != null) storage.delete((String) row.get("object_key")); audit(user, "ASSET_PURGE", id); } }
    public String download(long id, AuthUser user) { Map<String,Object> row = find(id, user, false); return row.get("attachment_id") != null ? "/api/attachments/" + ((Number) row.get("attachment_id")).longValue() + "/download" : storage.presignedUrl((String) row.get("object_key")); }

    private AttachmentItem resolveAttachment(Long attachmentId, AuthUser user) { if (attachmentId == null || attachmentId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请先通过公共附件接口上传文件"); AttachmentItem item = attachmentGateway.get(attachmentId, user); if (item.uploaderId() != user.id() || !"TEMP".equals(item.status())) throw new BusinessException(ErrorCode.FORBIDDEN, "附件必须是当前用户上传的临时附件"); return item; }
    private void assertMd5Available(String md5, long tenantId, long currentId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND checksum_md5 = ? AND deleted = 0 AND id <> ?", Integer.class, tenantId, md5, currentId); if (count != null && count > 0) throw new BusinessException(ErrorCode.CONFLICT, "文件 MD5 已存在，不允许重复提交"); }
    private String normalizeMd5(String md5) { if (md5 == null || md5.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 不能为空"); String value = md5.trim().toLowerCase(Locale.ROOT); if (!value.matches(MD5_PATTERN)) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 格式无效"); return value; }
    private Map<String,Object> find(long id, AuthUser user, boolean deleted) { Map<String,Object> row = findOptional("SELECT id, owner_id, object_key, attachment_id FROM dm_asset WHERE id = ? AND tenant_id = ? AND deleted = ?", id, user.tenantId(), deleted ? 1 : 0); if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Asset not found"); return row; }
    private Map<String,Object> findOptional(String sql, Object... args) { List<Map<String,Object>> rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? null : rows.get(0); }
    private void ensureProject(long id, AuthUser user) { if (jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Project not found"); }
    private void ensureComponent(long id, long projectId, AuthUser user) { if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, projectId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found"); }
    private void audit(AuthUser user, String operation, long id) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ASSET', ?)", user.tenantId(), user.id(), operation, id); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
