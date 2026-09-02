package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import com.ccb.common.api.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 文件型内容资产服务（REQ-20260831-050）：表名按资产类型参数化，
 * 承接原 AssetService 的 upsert、主文件替换（dm_content_attachment sort_order=0）、
 * 下载、软删/恢复/彻底删除与跨 7 张文件表的 MD5 查重。REPORT 由 ReportService 复用本服务的
 * 附件与查重能力，其余结构化类型由 StructuredAssetService 承接。
 */
@Service
public class ContentFileAssetService {
    public static final String BUSINESS_TYPE = "DATA_MIGRATION_ASSET";
    /** 本服务直接承接的五种文件型类型（PLAN 走 PlanService，REPORT 走 ReportService，结构化类型走 StructuredAssetService）。 */
    // PLAN 已剥离至 PlanService（REQ-20260820-031 增量）；dm_plan 仍留在 FILE_TABLES（MD5 查重域/看板计数）。
    public static final Set<String> MANAGED_TYPES = Set.of("MAPPING_DOC", "DEPENDENCY", "SCRIPT", "TOPIC", "RELEASE_DRILL");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final String MD5_PATTERN = "[0-9a-fA-F]{32}";

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final ContentAttachmentService attachments;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;

    @Autowired
    public ContentFileAssetService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway,
                                   ContentAttachmentService attachments, DataMigrationPermissionService permissions,
                                   UserDirectoryPort userDirectory) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
        this.attachments = attachments;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
    }

    public ContentFileAssetService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway,
                                   ContentAttachmentService attachments, DataMigrationPermissionService permissions) {
        this(jdbc, attachmentGateway, attachments, permissions, null);
    }

    public PageResult<Map<String, Object>> list(String type, Long projectId, Long componentId, String keyword,
                                                int page, int size, AuthUser user) {
        String table = requireManagedTable(type);
        StringBuilder sql = new StringBuilder("SELECT a.id, a.project_id, a.component_id, ? AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, ")
                .append("m.attachment_id, a.checksum_md5, a.owner_id, a.created_at, a.updated_at ")
                .append("FROM ").append(table).append(" a ")
                .append("LEFT JOIN dm_content_attachment m ON m.tenant_id = a.tenant_id AND m.business_type = ? AND m.business_id = a.id AND m.sort_order = 0 AND m.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(type, type, user.tenantId()));
        if (projectId != null) { sql.append(" AND a.project_id = ?"); args.add(projectId); }
        if (componentId != null) { sql.append(" AND a.component_id = ?"); args.add(componentId); }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (a.doc_code LIKE ? OR a.doc_name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?");
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize); pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), pageArgs.toArray());
        for (Map<String, Object> row : rows) {
            Object rawAttachmentId = row.get("attachment_id");
            if (rawAttachmentId instanceof Number number) {
                try {
                    AttachmentItem item = attachmentGateway.get(number.longValue(), user);
                    row.put("content_type", item.contentType());
                    row.put("file_size", item.fileSize());
                    row.put("file_name", item.fileName());
                } catch (BusinessException ignored) {
                    // 关系行可能已与平台附件状态发生变化，列表仍返回业务记录。
                }
            }
        }
        return new PageResult<>(rows, total == null ? 0L : total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> list(String type, String keyword, AuthUser user) {
        return list(type, null, null, keyword, 1, 20, user);
    }

    /** 上传（upsert 按 项目+编号）：替换主文件为 sort_order=0 行。 */
    @Transactional
    public Map<String, Object> upload(String type, long projectId, Long componentId, String assetCode,
                                      Long attachmentId, String checksumMd5, AuthUser user) {
        String table = requireManagedTable(type);
        if (assetCode == null || assetCode.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "assetCode is required");
        ensureProject(projectId, user);
        if (componentId != null) ensureComponent(componentId, projectId, user);
        AttachmentItem attachment = resolveAttachment(attachmentId, user);
        if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "File is empty or exceeds 50 MB");
        String md5 = normalizeMd5(checksumMd5);
        Map<String, Object> old = findOptional("SELECT id, owner_id FROM " + table + " WHERE tenant_id = ? AND project_id = ? AND doc_code = ? AND deleted = 0",
                user.tenantId(), projectId, assetCode.trim());
        if (old != null) permissions.requireWrite(user, ((Number) old.get("owner_id")).longValue());
        long id;
        if (old == null) {
            id = nextId();
            assertMd5Available(md5, user.tenantId(), null);
            try {
                jdbc.update("INSERT INTO " + table + " (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, user.tenantId(), projectId, componentId, assetCode.trim(), attachment.fileName(), md5, user.id(), user.id(), user.id());
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "文档编号在该项目下已存在，请刷新后重试");
            }
        } else {
            id = ((Number) old.get("id")).longValue();
            assertMd5Available(md5, user.tenantId(), id);
            jdbc.update("UPDATE " + table + " SET component_id = ?, doc_name = ?, checksum_md5 = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    componentId, attachment.fileName(), md5, user.id(), id, user.tenantId());
        }
        replaceMainFile(type, id, projectId, attachment, user);
        audit(user, old == null ? "ASSET_UPLOAD" : "ASSET_REPLACE", id);
        return jdbc.queryForMap("SELECT id, project_id, component_id, doc_code AS asset_code, doc_name AS asset_name, checksum_md5, owner_id, created_at, updated_at FROM " + table + " WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    /** 跨 7 张文件表 MD5 查重命中列表（含来源类型）。 */
    public List<Map<String, Object>> md5Hits(String checksumMd5, AuthUser user) {
        String md5 = normalizeMd5(checksumMd5);
        return jdbc.queryForList(ContentAssetTables.md5UnionSql(), ContentAssetTables.md5UnionArgs(user.tenantId(), md5));
    }

    public boolean isMd5Available(String checksumMd5, AuthUser user) {
        return md5Hits(checksumMd5, user).isEmpty();
    }

    /** 上传前置校验：同租户活动记录中不得存在相同 MD5（可排除自身）。 */
    public void assertMd5Available(String md5, long tenantId, Long currentId) {
        String value = normalizeMd5(md5);
        List<Map<String, Object>> hits = jdbc.queryForList(ContentAssetTables.md5UnionSql(), ContentAssetTables.md5UnionArgs(tenantId, value));
        for (Map<String, Object> hit : hits) {
            if (currentId == null || ((Number) hit.get("id")).longValue() != currentId) {
                throw new BusinessException(ErrorCode.CONFLICT, "文件 MD5 已存在（命中类型：" + hit.get("asset_type") + "），不允许重复提交");
            }
        }
    }

    @Transactional
    public void delete(String type, Collection<Long> ids, AuthUser user) {
        String table = requireManagedTable(type);
        for (Long id : ids) {
            Map<String, Object> row = findOptional("SELECT owner_id FROM " + table + " WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
            if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Asset not found");
            permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
            int changed = jdbc.update("UPDATE " + table + " SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    user.id(), user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "Asset state changed, please retry");
            audit(user, "ASSET_DELETE", id);
        }
    }

    @Transactional
    public void restore(String type, Collection<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        String table = requireManagedTable(type);
        for (Long id : ids) {
            try {
                int changed = jdbc.update("UPDATE " + table + " SET deleted = 0, deleted_by = NULL, deleted_at = NULL, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 1",
                        user.id(), id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "Asset state changed, please retry");
            } catch (DataIntegrityViolationException ex) {
                // 恢复后活动行 doc_code 与既有活动行冲突（uk_dm_*_active_code）：与统一方案同构，翻译为 CONFLICT(40900)。
                throw new BusinessException(ErrorCode.CONFLICT, "内容编号在该项目下已存在，无法恢复");
            }
            audit(user, "ASSET_RESTORE", id);
        }
    }

    @Transactional
    public void purge(String type, Collection<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        String table = requireManagedTable(type);
        for (Long id : ids) {
            if (findOptional("SELECT id FROM " + table + " WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId()) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Asset not found");
            }
            attachments.unbindAndRemoveAll(type, BUSINESS_TYPE, id, user);
            jdbc.update("DELETE FROM " + table + " WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
            audit(user, "ASSET_PURGE", id);
        }
    }

    public long downloadAttachmentId(String type, long id, AuthUser user) {
        String table = requireManagedTable(type);
        List<Long> attachmentIds = jdbc.queryForList(
                "SELECT m.attachment_id FROM dm_content_attachment m JOIN " + table + " a ON a.id = m.business_id AND a.tenant_id = m.tenant_id " +
                "WHERE m.tenant_id = ? AND m.business_type = ? AND m.business_id = ? AND m.sort_order = 0 AND m.deleted = 0 AND a.deleted = 0",
                Long.class, user.tenantId(), type, id);
        if (attachmentIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "资产未绑定公共附件");
        return attachmentIds.get(0);
    }

    /** 统一回收站：某类型软删记录总数（SQL COUNT，不拉明细）。 */
    public long countDeleted(String type, String keyword, AuthUser user) {
        String table = requireManagedTable(type);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" a ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleBinKeyword(sql, args, keyword);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    /** 统一回收站原生分页：某类型软删记录按业务编号（doc_code）升序、空值末尾，取前 {@code limit} 行。 */
    public List<Map<String, Object>> listDeletedPage(String type, String keyword, int limit, AuthUser user) {
        String table = requireManagedTable(type);
        if (limit <= 0) return List.of();
        StringBuilder sql = new StringBuilder("SELECT a.id, a.project_id, a.component_id, ? AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, ")
                .append("a.checksum_md5, a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at ")
                .append("FROM ").append(table).append(" a ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(type, user.tenantId()));
        appendRecycleBinKeyword(sql, args, keyword);
        sql.append(" ORDER BY (a.doc_code IS NULL OR a.doc_code = ''), a.doc_code ASC, a.deleted_at DESC, a.id ASC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        if (userDirectory != null) {
            for (Map<String, Object> row : rows) {
                Object raw = row.get("deleted_by");
                if (raw instanceof Number number) userDirectory.findActive(user.tenantId(), number.longValue())
                        .ifPresent(item -> row.put("deleted_by_name", item.displayName()));
            }
        }
        return rows;
    }

    /** 查询文件型内容的软删除详情，不触发附件下载。 */
    public Map<String, Object> findDeletedDetail(String type, long id, AuthUser user) {
        String table = requireManagedTable(type);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id, a.project_id, a.component_id, ? AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, "
                        + "m.attachment_id, f.file_name, f.content_type, f.file_size, a.checksum_md5, a.owner_id, a.created_at, a.updated_at, "
                        + "a.deleted_by, a.deleted_at FROM " + table + " a "
                        + "LEFT JOIN dm_content_attachment m ON m.tenant_id = a.tenant_id AND m.business_type = ? AND m.business_id = a.id AND m.sort_order = 0 AND m.deleted = 0 "
                        + "LEFT JOIN att_file f ON f.id = m.attachment_id AND f.tenant_id = m.tenant_id "
                        + "WHERE a.id = ? AND a.tenant_id = ? AND a.deleted = 1",
                type, type, id, user.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Asset not found in recycle bin");
        Map<String, Object> row = rows.get(0);
        if (userDirectory != null && row.get("deleted_by") instanceof Number number) {
            userDirectory.findActive(user.tenantId(), number.longValue()).ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
        return row;
    }

    private static void appendRecycleBinKeyword(StringBuilder sql, List<Object> args, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (a.doc_code LIKE ? OR a.doc_name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value);
        }
    }

    /** 回收站行定位（跨内容表统一入口）：返回 id/doc_name/deleted 行，未找到返回空。 */
    public Optional<Map<String, Object>> findRecycleBinRow(String table, long id, long tenantId) {
        return Optional.ofNullable(findOptional("SELECT id, doc_name, deleted FROM " + requireKnownTable(table) + " WHERE id = ? AND tenant_id = ?", id, tenantId));
    }

    /** 附件投影写入口：文件型资产主文件替换（sort_order=0），旧主文件解绑。 */
    @Transactional
    public void replaceMainFile(String businessType, long businessId, long projectId, AttachmentItem attachment, AuthUser user) {
        List<Long> oldIds = jdbc.queryForList(
                "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND sort_order = 0 AND deleted = 0",
                Long.class, user.tenantId(), businessType, businessId);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("attachmentId", attachment.id());
        entry.put("fileName", attachment.fileName());
        attachments.replaceAll(businessType, BUSINESS_TYPE, businessId, projectId, List.of(entry), user);
        for (Long oldId : oldIds) {
            if (oldId != attachment.id()) attachmentGateway.deleteBound(oldId, BUSINESS_TYPE, String.valueOf(businessId), user);
        }
    }

    /** 内容表 owner/软删状态定位，供附件访问策略跨表校验。 */
    public Optional<Map<String, Object>> locateOwner(String table, long id, long tenantId) {
        String checked = requireKnownTable(table);
        return Optional.ofNullable(findOptional("SELECT owner_id, deleted FROM " + checked + " WHERE id = ? AND tenant_id = ?", id, tenantId));
    }

    public AttachmentItem resolveAttachment(Long attachmentId, AuthUser user) {
        if (attachmentId == null || attachmentId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请先通过公共附件接口上传文件");
        AttachmentItem item = attachmentGateway.get(attachmentId, user);
        if (item.uploaderId() != user.id() || !"TEMP".equals(item.status())) throw new BusinessException(ErrorCode.FORBIDDEN, "附件必须是当前用户上传的临时附件");
        return item;
    }

    public String normalizeMd5(String md5) {
        if (md5 == null || md5.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 不能为空");
        String value = md5.trim().toLowerCase(Locale.ROOT);
        if (!value.matches(MD5_PATTERN)) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 格式无效");
        return value;
    }

    public void audit(AuthUser user, String operation, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'ASSET', ?)", user.tenantId(), user.id(), operation, id);
    }

    private String requireManagedTable(String type) {
        if (type == null || !MANAGED_TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported asset type");
        return ContentAssetTables.tableFor(type);
    }

    private int normalizePageSize(int size) {
        return Set.of(20, 50, 100).contains(size) ? size : 20;
    }

    private String requireKnownTable(String table) {
        if (!ContentAssetTables.ALL_TABLES.contains(table)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported content table");
        return table;
    }

    protected Map<String, Object> findOptional(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void ensureProject(long id, AuthUser user) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, user.tenantId()) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Project not found");
        }
    }

    private void ensureComponent(long id, long projectId, AuthUser user) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, projectId, user.tenantId()) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found");
        }
    }

    protected long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
