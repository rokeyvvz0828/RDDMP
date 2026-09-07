package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迁移映射专属服务（对标迁移程序菜单）。
 *
 * <p>存储于 {@code dm_mapping_doc}（V186：新增 mapping_type），一条记录绑定多个源文件
 * （{@code dm_content_attachment} business_type=MAPPING_DOC，sort_order 递增）。
 * 映射类型统一来自系统管理/参数管理，系统编号必须是当前项目 enabled=1 的 dm_component。
 * 支持组合筛选分页、详情、新增、编辑、单文件/打包下载、逻辑删除与统一回收站。
 */
@Service
public class MappingService {
    /** att_file 平台侧绑定域，与其它文件型内容资产一致。 */
    public static final String BUSINESS_TYPE = ContentFileAssetService.BUSINESS_TYPE;
    /** dm_content_attachment.business_type 值。 */
    private static final String CONTENT_TYPE = "MAPPING_DOC";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 200;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private static final String SYSTEM_JOIN =
            " LEFT JOIN dm_component c ON c.tenant_id = a.tenant_id AND c.project_id = a.project_id AND c.system_code = a.system_code " +
            " LEFT JOIN arch_physical_subsystem sys ON sys.tenant_id = c.tenant_id AND sys.code = c.system_code AND sys.deleted = 0 ";
    private static final String SELECT_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, a.mapping_type, a.system_code, " +
            "sys.short_name AS system_short_name, sys.name AS system_name, " +
            "a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";
    private static final String RECYCLE_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, 'MAPPING_DOC' AS asset_type, " +
            "a.doc_code AS asset_code, a.doc_name AS asset_name, a.mapping_type, a.system_code, " +
            "sys.short_name AS system_short_name, sys.name AS system_name, " +
            "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at ";

    private final JdbcTemplate jdbc;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final ContentDocCodeGenerator docCodes;
    private final DataMigrationCodeValueService codeValues;

    @Autowired
    public MappingService(JdbcTemplate jdbc, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                          DataMigrationPermissionService permissions, UserDirectoryPort userDirectory,
                          ContentDocCodeGenerator docCodes, DataMigrationCodeValueService codeValues) {
        this.jdbc = jdbc;
        this.attachments = attachments;
        this.fileAssets = fileAssets;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
        this.docCodes = docCodes;
        this.codeValues = codeValues;
    }

    /** 分页查询：租户 + 项目恒定过滤，支持映射类型、系统编号、文件名称关键字组合筛选。 */
    public PageResult<Map<String, Object>> list(Long projectId, String mappingType, String systemCode,
                                                String keyword, int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        if (mappingType != null && !mappingType.isBlank()) {
            codeValues.requireActive(DataMigrationCodeValueService.DM_MAPPING_TYPE, "映射类型", mappingType, user);
        }
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append("FROM dm_mapping_doc a ").append(SYSTEM_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, scope, mappingType, systemCode, keyword);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        if (total == null) total = 0L;
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 详情（含多附件列表）。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT_COLUMNS + "FROM dm_mapping_doc a " + SYSTEM_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 0", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移映射不存在");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(rows, user.tenantId());
        row.put("attachments", attachments.list(CONTENT_TYPE, id, user.tenantId()));
        return row;
    }

    /** 新增：必填元数据 + 至少一个源文件；系统编号必须是当前项目启用组件。 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        permissions.requireAccessible(projectId, user);
        String mappingType = requireText(body.get("mappingType"), "映射类型不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_MAPPING_TYPE, "映射类型", mappingType, user);
        String systemCode = requireText(body.get("systemCode"), "系统编号不能为空");
        ensureSystemBelongsToProject(systemCode, projectId, user);
        String fileName = requireFileName(firstNonNull(body.get("fileName"), body.get("file_name"), body.get("docName"), body.get("doc_name")));
        List<Map<String, Object>> files = resolveFiles(body, user);
        if (files.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传源文件");

        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_mapping_doc (id, tenant_id, project_id, system_code, doc_code, doc_name, " +
                    "mapping_type, owner_id, created_by, updated_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, tenantId, projectId, systemCode.trim(), docCodes.generate(CONTENT_TYPE), fileName.trim(),
                    mappingType, user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "文件编号冲突，请刷新后重试");
        }
        attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, files, user);
        audit(user, "MAPPING_CREATE", projectId, id);
        return detail(id, user);
    }

    /** 编辑：全量元数据可改；未传 files 时保留原附件，传 files 时按提交列表重设多附件。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> existing = findRaw(id, user.tenantId());
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

        String mappingType = body.containsKey("mappingType")
                ? requireText(body.get("mappingType"), "映射类型不能为空") : (String) existing.get("mapping_type");
        codeValues.requireActive(DataMigrationCodeValueService.DM_MAPPING_TYPE, "映射类型", mappingType, user);
        String systemCode = body.containsKey("systemCode")
                ? requireText(body.get("systemCode"), "系统编号不能为空") : (String) existing.get("system_code");
        ensureSystemBelongsToProject(systemCode, projectId, user);
        String fileName = body.containsKey("fileName") || body.containsKey("file_name") || body.containsKey("docName") || body.containsKey("doc_name")
                ? requireFileName(firstNonNull(body.get("fileName"), body.get("file_name"), body.get("docName"), body.get("doc_name")))
                : (String) existing.get("doc_name");

        if (body.containsKey("files")) {
            List<Map<String, Object>> files = resolveFiles(body, boundAttachmentIds(id, user.tenantId()), user);
            if (files.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少保留一个源文件");
            attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, files, user);
        }

        int changed = jdbc.update(
                "UPDATE dm_mapping_doc SET doc_name = ?, mapping_type = ?, system_code = ?, " +
                "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                fileName.trim(), mappingType, systemCode, user.id(), id, user.tenantId());
        if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移映射状态已变化，请刷新后重试");
        audit(user, "MAPPING_UPDATE", projectId, id);
        return detail(id, user);
    }

    /** 批量逻辑删除（进统一回收站），记录删除人/时间。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> existing = findRaw(id, user.tenantId());
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            int changed = jdbc.update("UPDATE dm_mapping_doc SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移映射状态已变化，请刷新后重试");
            audit(user, "MAPPING_DELETE", projectId, id);
        }
    }

    /** 单文件下载：不传 attachmentId 时取第一个活动附件。 */
    public String download(long id, Long attachmentId, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        List<Long> attachmentIds = jdbc.queryForList(
                "SELECT m.attachment_id FROM dm_content_attachment m " +
                "JOIN dm_mapping_doc a ON a.id = m.business_id AND a.tenant_id = m.tenant_id " +
                "WHERE m.tenant_id = ? AND m.business_type = 'MAPPING_DOC' AND m.business_id = ? " +
                "AND m.deleted = 0 AND a.deleted = 0 ORDER BY m.sort_order ASC, m.created_at ASC",
                Long.class, user.tenantId(), id);
        if (attachmentIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移映射未绑定源文件");
        long target = attachmentId == null ? attachmentIds.get(0) : attachmentId;
        if (!attachmentIds.contains(target)) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或不属于该迁移映射");
        return "/api/attachments/" + target + "/download";
    }

    /** 附件列表（先校验记录项目归属）。 */
    public List<Map<String, Object>> listAttachments(long id, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        return attachments.list(CONTENT_TYPE, id, user.tenantId());
    }

    /** 映射类型选项：参数管理中启用的 DM_MAPPING_TYPE。 */
    public List<Map<String, Object>> getTypeOptions(AuthUser user) {
        return codeValues.options(DataMigrationCodeValueService.DM_MAPPING_TYPE, user);
    }

    /** 系统编号下拉：按项目返回 dm_component 启用清单，前端本地随输随筛。 */

    // ============ 统一回收站 SPI ============

    public long countRecycleBin(long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_mapping_doc a WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> fetchRecycleBinPage(long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        if (limit <= 0) return List.of();
        StringBuilder sql = new StringBuilder(RECYCLE_COLUMNS)
                .append("FROM dm_mapping_doc a ").append(SYSTEM_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        sql.append(" ORDER BY a.doc_code ASC, a.id ASC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        decorateUsers(rows, user.tenantId());
        return rows;
    }

    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                RECYCLE_COLUMNS + "FROM dm_mapping_doc a " + SYSTEM_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移映射不存在于回收站");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(rows, user.tenantId());
        // 添加附件列表
        List<Map<String, Object>> attachmentList = attachments.list("MAPPING_DOC", id, user.tenantId());
        row.put("attachments", attachmentList);
        return row;
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            try {
                int changed = jdbc.update("UPDATE dm_mapping_doc SET deleted = 0, deleted_by = NULL, deleted_at = NULL " +
                        "WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移映射状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "文件编号已存在活动记录，无法恢复");
            }
            audit(user, "MAPPING_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            int changed = jdbc.update("DELETE FROM dm_mapping_doc WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移映射状态已变化，请刷新后重试");
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);
            audit(user, "MAPPING_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private void appendFilters(StringBuilder sql, List<Object> args, long projectId, String mappingType,
                               String systemCode, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (mappingType != null && !mappingType.isBlank()) {
            sql.append(" AND a.mapping_type = ?");
            args.add(mappingType.trim());
        }
        if (systemCode != null && !systemCode.isBlank()) {
            sql.append(" AND a.system_code = ?");
            args.add(systemCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND a.doc_name LIKE ?");
            args.add(value);
        }
    }

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, long projectId, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND a.doc_name LIKE ?");
            args.add(value);
        }
    }

    private void ensureSystemBelongsToProject(String systemCode, long projectId, AuthUser user) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component c " +
                "WHERE c.tenant_id = ? AND c.project_id = ? AND c.system_code = ? AND c.enabled = 1",
                Integer.class, user.tenantId(), projectId, systemCode);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统编号不存在或不属于当前项目");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveFiles(Map<String, Object> body, AuthUser user) {
        return resolveFiles(body, Set.of(), user);
    }

    /** 附件解析：已绑定附件直接复用；新附件必须是当前用户上传的临时附件且不超过 50MB。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveFiles(Map<String, Object> body, Set<Long> bound, AuthUser user) {
        List<Map<String, Object>> files = new ArrayList<>();
        Object rawFiles = body.get("files");
        if (rawFiles instanceof Collection<?> c) {
            for (Object item : c) if (item instanceof Map) files.add((Map<String, Object>) item);
        }
        if (files.isEmpty() && body.get("attachmentId") != null) {
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("attachmentId", body.get("attachmentId"));
            single.put("fileName", body.get("fileName"));
            files.add(single);
        }
        List<Map<String, Object>> resolved = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Map<String, Object> file : files) {
            long attachmentId = requireLong(file.get("attachmentId"), "附件不能为空");
            if (!seen.add(attachmentId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不能重复选择");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            if (bound.contains(attachmentId)) {
                entry.put("attachmentId", attachmentId);
                entry.put("fileName", file.get("fileName"));
                resolved.add(entry);
                continue;
            }
            AttachmentItem item = fileAssets.resolveAttachment(attachmentId, user);
            if (item.fileSize() <= 0 || item.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + item.fileName() + " 为空或超过 50 MB");
            }
            entry.put("attachmentId", item.id());
            entry.put("fileName", item.fileName());
            resolved.add(entry);
        }
        return resolved;
    }

    private Set<Long> boundAttachmentIds(long mappingId, long tenantId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 0",
                Long.class, tenantId, CONTENT_TYPE, mappingId));
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id, mapping_type, system_code, doc_code, doc_name, owner_id " +
                "FROM dm_mapping_doc WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移映射不存在");
        return rows.get(0);
    }

    private long requireRecycleBinScope(long id, AuthUser user) {
        List<Long> projects = jdbc.queryForList("SELECT project_id FROM dm_mapping_doc WHERE id = ? AND tenant_id = ? AND deleted = 1",
                Long.class, id, user.tenantId());
        if (projects.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移映射不存在于回收站");
        long projectId = projects.get(0);
        permissions.requireStoredProject(projectId, user);
        return projectId;
    }

    private List<Map<String, Object>> decorateUsers(List<Map<String, Object>> rows, long tenantId) {
        if (userDirectory == null) return rows;
        for (Map<String, Object> row : rows) {
            Object created = row.get("created_by");
            if (created instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("created_by_name", item.displayName()));
            Object updated = row.get("updated_by");
            if (updated instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("updated_by_name", item.displayName()));
            Object deleted = row.get("deleted_by");
            if (deleted instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
        return rows;
    }

    private void audit(AuthUser user, String operation, long projectId, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) " +
                "VALUES (?, ?, ?, ?, 'MAPPING', ?)",
                user.tenantId(), user.id(), projectId, operation, id);
    }

    private int normalizePageSize(int size) {
        return PAGE_SIZES.contains(size) ? size : 20;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String requireText(Object value, String message) {
        String text = optionalText(value, null);
        if (text == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return text;
    }

    private String requireFileName(Object value) {
        String fileName = requireText(value, "文件名称不能为空");
        if (fileName.codePointCount(0, fileName.length()) > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名称不能超过 200 个字符");
        }
        return fileName;
    }

    private String optionalText(Object value, Object otherValue) {
        Object candidate = value != null ? value : otherValue;
        if (candidate == null) return null;
        String text = String.valueOf(candidate).trim();
        return text.isEmpty() ? null : text;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private long requireLong(Object value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
