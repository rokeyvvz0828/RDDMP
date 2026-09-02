package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迁移方案专属服务（REQ-20260820-031 增量，对标会议纪要 / 汇报材料）。
 *
 * <p>存储于 {@code dm_plan}（V159 域化：新增 granularity/plan_type/system_id/plan_summary 与活动维度唯一键），
 * 一条方案可绑定多个源文件（{@code dm_content_attachment} business_type=PLAN，sort_order 排序，首文件为主文件）。
 * 支持项目 / 颗粒度 / 方案类型 / 关联系统组合筛选、单条录入与批量上传（多文件归入同一方案）、编辑（重传/追加文件）、
 * 下载、逻辑删除与统一回收站。唯一约束：{@code (tenant, project, granularity, plan_type, system_id)} 活动域仅一条。
 */
@Service
public class PlanService {
    /** att_file 平台侧绑定域，与其它文件型内容资产一致，令 {@code DataMigrationAssetAttachmentAccessPolicy} 生效。 */
    public static final String BUSINESS_TYPE = ContentFileAssetService.BUSINESS_TYPE;
    /** dm_content_attachment.business_type 值。 */
    private static final String CONTENT_TYPE = "PLAN";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final String MD5_PATTERN = "[0-9a-fA-F]{32}";
    private static final Set<String> GRANULARITIES = Set.of("PROJECT", "SYSTEM");
    private static final Set<String> PLAN_TYPES = Set.of("BUSINESS", "DATA");
    private static final long NO_SYSTEM = 0L;

    private static final String MAIN_FILE_JOIN =
        " LEFT JOIN dm_content_attachment m ON m.tenant_id = a.tenant_id AND m.business_type = 'PLAN' AND m.business_id = a.id AND m.sort_order = 0 AND m.deleted = 0 " +
        " LEFT JOIN att_file f ON f.id = m.attachment_id AND f.tenant_id = m.tenant_id ";
    private static final String SYSTEM_JOIN =
        " LEFT JOIN arch_physical_subsystem sys ON sys.id = a.system_id AND sys.tenant_id = a.tenant_id AND sys.deleted = 0 ";
    private static final String SELECT_COLUMNS =
        "SELECT a.id, a.project_id, p.project_name, a.granularity, a.plan_type, a.system_id, sys.name AS system_name, " +
        "a.doc_code AS asset_code, a.doc_name AS asset_name, a.plan_summary, " +
        "f.content_type, f.file_size, m.attachment_id, a.checksum_md5, " +
        "(SELECT COUNT(*) FROM dm_content_attachment ca WHERE ca.tenant_id = a.tenant_id AND ca.business_type = 'PLAN' AND ca.business_id = a.id AND ca.deleted = 0) AS attachment_count, " +
        "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";
    private static final String RECYCLE_COLUMNS =
        "SELECT a.id, a.project_id, p.project_name, 'PLAN' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
        "a.granularity, a.plan_type, a.system_id, sys.name AS system_name, a.plan_summary, " +
        "f.content_type, f.file_size, m.attachment_id, a.checksum_md5, a.owner_id, " +
        "a.created_at, a.updated_at, a.deleted_by, a.deleted_at ";

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;

    public PlanService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway, ContentAttachmentService attachments,
                       ContentFileAssetService fileAssets, DataMigrationPermissionService permissions,
                       UserDirectoryPort userDirectory) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
        this.attachments = attachments;
        this.fileAssets = fileAssets;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
    }

    /** 为行集回填上传人/更新人/删除人显示名（与会议纪要同款目录解析）。 */
    private List<Map<String, Object>> decorateUsers(List<Map<String, Object>> rows, long tenantId) {
        if (userDirectory == null) return rows;
        for (Map<String, Object> row : rows) {
            if (row.get("created_by") instanceof Number created)
                userDirectory.findActive(tenantId, created.longValue()).ifPresent(item -> row.put("created_by_name", item.displayName()));
            if (row.get("updated_by") instanceof Number updated)
                userDirectory.findActive(tenantId, updated.longValue()).ifPresent(item -> row.put("updated_by_name", item.displayName()));
            if (row.get("deleted_by") instanceof Number deleted)
                userDirectory.findActive(tenantId, deleted.longValue()).ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
        return rows;
    }

    // ============ 查询 ============

    /** 分页列表：项目 / 颗粒度 / 方案类型 / 关联系统组合筛选 + 方案名称关键字模糊；关键字仅命中 doc_name。 */
    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String planType, Long systemId,
                                                String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
            .append("FROM dm_plan a ").append(MAIN_FILE_JOIN).append(SYSTEM_JOIN)
            .append("LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 ")
            .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        if (projectId != null) { sql.append(" AND a.project_id = ?"); args.add(projectId); }
        if (granularity != null && GRANULARITIES.contains(granularity)) { sql.append(" AND a.granularity = ?"); args.add(granularity); }
        if (planType != null && PLAN_TYPES.contains(planType)) { sql.append(" AND a.plan_type = ?"); args.add(planType); }
        if (systemId != null) { sql.append(" AND a.system_id = ?"); args.add(systemId); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND a.doc_name LIKE ?"); args.add("%" + keyword.trim() + "%"); }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        if (total == null) total = 0L;
        sql.append(" ORDER BY a.doc_code ASC, a.id ASC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(Math.max(0, (page - 1) * size));
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total, page, size);
    }

    /** 单条详情，含多附件列表。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            SELECT_COLUMNS + "FROM dm_plan a " + MAIN_FILE_JOIN + SYSTEM_JOIN +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 0", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移方案不存在");
        Map<String, Object> row = rows.get(0);
        decorateUsers(rows, user.tenantId());
        row.put("attachments", attachments.list(CONTENT_TYPE, id, user.tenantId()));
        return row;
    }

    // ============ 写入 ============

    /**
     * 新增迁移方案：全量元数据 + 一个或多个源文件（一条方案挂多文件）。
     * 方案名称留空时取首个文件名（去扩展名）。
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        long projectId = requireLong(body.get("projectId"), "所属项目不能为空");
        String granularity = requireText(body.get("granularity"), "资产颗粒度不能为空");
        String planType = requireText(firstNonNull(body.get("planType"), body.get("plan_type")), "迁移方案类型不能为空");
        Long systemIdArg = optionalLong(body.get("systemId"));
        String summary = optionalText(firstNonNull(body.get("summary"), body.get("planSummary"), body.get("plan_summary")));
        validateEnum(granularity, GRANULARITIES, "资产颗粒度无效，支持：PROJECT/SYSTEM");
        validateEnum(planType, PLAN_TYPES, "迁移方案类型无效，支持：BUSINESS/DATA");
        long systemId = resolveSystemId(granularity, systemIdArg);
        if ("SYSTEM".equals(granularity)) ensureSystemBelongsToProject(systemId, projectId, user);
        ensureProject(projectId, user);

        List<Map<String, Object>> files = extractFiles(body);
        if (files.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "至少上传一个源文件");

        List<Map<String, Object>> entries = new ArrayList<>();
        String firstMd5 = null;
        String firstFileName = null;
        for (Map<String, Object> file : files) {
            long attachmentId = requireLong(file.get("attachmentId"), "附件不能为空");
            AttachmentItem item = fileAssets.resolveAttachment(attachmentId, user);
            if (item.fileSize() <= 0 || item.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + item.fileName() + " 为空或超过 50 MB");
            }
            String md5 = normalizeMd5(file.get("checksumMd5"));
            fileAssets.assertMd5Available(md5, tenantId, null);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("attachmentId", item.id());
            entry.put("fileName", item.fileName());
            entries.add(entry);
            if (firstMd5 == null) { firstMd5 = md5; firstFileName = item.fileName(); }
        }

        String planName = optionalText(body.get("planName"));
        String docName = (planName != null) ? planName : stripExtension(firstFileName);
        if (docName == null || docName.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "方案名称不能为空");

        assertDimensionUnique(tenantId, projectId, granularity, planType, systemId, null);
        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_plan (id, tenant_id, project_id, component_id, doc_code, doc_name, checksum_md5, " +
                    "granularity, plan_type, system_id, plan_summary, owner_id, created_by, updated_by) " +
                    "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, tenantId, projectId, "PLAN-" + id, docName.trim(), firstMd5,
                    granularity, planType, systemId, summary, user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw dimensionConflict();
        }
        attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, entries, user);
        audit(user, "PLAN_UPLOAD", id);
        return detail(id, user);
    }

    /** 编辑迁移方案：元数据全量可改；提供 files 时全量重设附件集合（保留/新增/软删），否则维持原附件。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        Map<String, Object> existing = findRaw(id, tenantId);
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());

        long projectId = body.containsKey("projectId") ? requireLong(body.get("projectId"), "所属项目不能为空")
                : ((Number) existing.get("project_id")).longValue();
        String granularity = body.containsKey("granularity") ? requireText(body.get("granularity"), "资产颗粒度不能为空")
                : (String) existing.get("granularity");
        String planType = body.containsKey("planType") || body.containsKey("plan_type")
                ? requireText(firstNonNull(body.get("planType"), body.get("plan_type")), "迁移方案类型不能为空")
                : (String) existing.get("plan_type");
        Long systemIdArg = body.containsKey("systemId") ? optionalLong(body.get("systemId")) : null;
        validateEnum(granularity, GRANULARITIES, "资产颗粒度无效，支持：PROJECT/SYSTEM");
        validateEnum(planType, PLAN_TYPES, "迁移方案类型无效，支持：BUSINESS/DATA");
        long systemId;
        if (body.containsKey("systemId")) {
            systemId = resolveSystemId(granularity, systemIdArg);
        } else {
            systemId = ((Number) existing.get("system_id")).longValue();
        }
        ensureProject(projectId, user);
        if ("SYSTEM".equals(granularity)) ensureSystemBelongsToProject(systemId, projectId, user);

        StringBuilder set = new StringBuilder("updated_by = ?, updated_at = CURRENT_TIMESTAMP");
        List<Object> args = new ArrayList<>(List.of(user.id()));
        set.append(", project_id = ?"); args.add(projectId);
        set.append(", granularity = ?"); args.add(granularity);
        set.append(", plan_type = ?"); args.add(planType);
        set.append(", system_id = ?"); args.add(systemId);
        if (body.containsKey("planName")) {
            String name = requireText(body.get("planName"), "方案名称不能为空");
            set.append(", doc_name = ?"); args.add(name.trim());
        }
        if (body.containsKey("summary") || body.containsKey("planSummary") || body.containsKey("plan_summary")) {
            set.append(", plan_summary = ?");
            args.add(optionalText(firstNonNull(body.get("summary"), body.get("planSummary"), body.get("plan_summary"))));
        }

        List<Map<String, Object>> files = extractFiles(body);
        if (!files.isEmpty()) {
            Set<Long> bound = boundAttachmentIds(id, tenantId);
            List<Map<String, Object>> entries = new ArrayList<>();
            String firstMd5 = null;
            for (Map<String, Object> file : files) {
                long attachmentId = requireLong(file.get("attachmentId"), "附件不能为空");
                if (!bound.contains(attachmentId)) {
                    // 新绑定文件：必须携带 MD5，校验体积与全域查重
                    String md5 = normalizeMd5(file.get("checksumMd5"));
                    AttachmentItem item = fileAssets.resolveAttachment(attachmentId, user);
                    if (item.fileSize() <= 0 || item.fileSize() > MAX_FILE_SIZE) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + item.fileName() + " 为空或超过 50 MB");
                    }
                    fileAssets.assertMd5Available(md5, tenantId, id);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("attachmentId", item.id());
                    entry.put("fileName", item.fileName());
                    entries.add(entry);
                    if (firstMd5 == null) firstMd5 = md5;
                } else {
                    // 已绑定文件（编辑时回传以保留）：详情接口不暴露逐附件 MD5，故不强制校验
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("attachmentId", attachmentId);
                    entry.put("fileName", textOrNull(file.get("fileName")));
                    entries.add(entry);
                }
            }
            set.append(", checksum_md5 = ?"); args.add(firstMd5 != null ? firstMd5 : existing.get("checksum_md5"));
            assertDimensionUnique(tenantId, projectId, granularity, planType, systemId, id);
            args.add(id); args.add(tenantId);
            try {
                jdbc.update("UPDATE dm_plan SET " + set + " WHERE id = ? AND tenant_id = ?", args.toArray());
            } catch (DataIntegrityViolationException ex) {
                throw dimensionConflict();
            }
            attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, entries, user);
        } else {
            assertDimensionUnique(tenantId, projectId, granularity, planType, systemId, id);
            args.add(id); args.add(tenantId);
            try {
                jdbc.update("UPDATE dm_plan SET " + set + " WHERE id = ? AND tenant_id = ?", args.toArray());
            } catch (DataIntegrityViolationException ex) {
                throw dimensionConflict();
            }
        }
        audit(user, "PLAN_UPDATE", id);
        return detail(id, user);
    }

    /** 批量逻辑删除（进统一回收站），记录删除人/时间。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : ids) {
            Map<String, Object> existing = findRaw(id, user.tenantId());
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            int changed = jdbc.update("UPDATE dm_plan SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移方案状态已变化，请刷新后重试");
            audit(user, "PLAN_DELETE", id);
        }
    }

    /** 下载：返回平台附件下载路径（首/主文件 sort_order=0）。 */
    public String download(long id, AuthUser user) {
        List<Long> mainIds = jdbc.queryForList(
            "SELECT m.attachment_id FROM dm_content_attachment m JOIN dm_plan a ON a.id = m.business_id AND a.tenant_id = m.tenant_id " +
            "WHERE m.tenant_id = ? AND m.business_type = 'PLAN' AND m.business_id = ? AND m.sort_order = 0 AND m.deleted = 0 AND a.deleted = 0",
            Long.class, user.tenantId(), id);
        if (mainIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移方案未绑定源文件");
        return "/api/attachments/" + mainIds.get(0) + "/download";
    }

    /** 方案附件列表。 */
    public List<Map<String, Object>> listAttachments(long id, AuthUser user) {
        findRaw(id, user.tenantId());
        return attachments.list(CONTENT_TYPE, id, user.tenantId());
    }

    // ============ 统一回收站 SPI ============

    public long countRecycleBin(Long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_plan a WHERE a.tenant_id = ? AND a.deleted = 1");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleFilters(sql, args, projectId, keyword);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> fetchRecycleBinPage(Long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        if (limit <= 0) return List.of();
        StringBuilder sql = new StringBuilder(RECYCLE_COLUMNS)
            .append("FROM dm_plan a ").append(MAIN_FILE_JOIN).append(SYSTEM_JOIN)
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
        List<Map<String, Object>> rows = jdbc.queryForList(RECYCLE_COLUMNS + "FROM dm_plan a " + MAIN_FILE_JOIN + SYSTEM_JOIN +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移方案不存在于回收站");
        return rows.get(0);
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            findRecycleBinRow(id, user.tenantId());
            try {
                int changed = jdbc.update("UPDATE dm_plan SET deleted = 0, deleted_by = NULL, deleted_at = NULL " +
                        "WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移方案状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "该(颗粒度+方案类型+关联系统)已存在活动迁移方案，无法恢复");
            }
            audit(user, "PLAN_RESTORE", id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            findRecycleBinRow(id, user.tenantId());
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);
            jdbc.update("DELETE FROM dm_plan WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
            audit(user, "PLAN_PURGE", id);
        }
    }

    // ============ 关联数据 ============

    /** 关联系统下拉：复用会议纪要同款 dm_component ⋈ arch_physical_subsystem（按项目过滤）。 */
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) {
        if (projectId == null) return List.of();
        return jdbc.queryForList(
            "SELECT s.id AS value, CONCAT(s.code, ' - ', COALESCE(s.short_name, s.name, '')) AS label " +
            "FROM dm_component c " +
            "JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 " +
            "WHERE c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0 " +
            "GROUP BY s.id, s.code, s.short_name, s.name ORDER BY s.code",
            user.tenantId(), projectId);
    }

    // ============ 私有辅助 ============

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, Long projectId, String keyword) {
        if (projectId != null) { sql.append(" AND a.project_id = ?"); args.add(projectId); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND a.doc_name LIKE ?"); args.add("%" + keyword.trim() + "%"); }
    }

    private long resolveSystemId(String granularity, Long systemId) {
        if ("SYSTEM".equals(granularity)) {
            if (systemId == null || systemId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "系统级方案必须选择关联系统");
            return systemId;
        }
        return NO_SYSTEM;
    }

    private void assertDimensionUnique(long tenantId, long projectId, String granularity, String planType, long systemId, Long excludeId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_plan WHERE tenant_id = ? AND project_id = ? " +
                "AND granularity = ? AND plan_type = ? AND system_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId, granularity, planType, systemId));
        if (excludeId != null) { sql.append(" AND id <> ?"); args.add(excludeId); }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        if (count != null && count > 0) throw dimensionConflict();
    }

    private BusinessException dimensionConflict() {
        return new BusinessException(ErrorCode.CONFLICT, "同一项目下该(颗粒度+方案类型+关联系统)已存在迁移方案，仅允许一条");
    }

    private Set<Long> boundAttachmentIds(long planId, long tenantId) {
        return new java.util.HashSet<>(jdbc.queryForList(
            "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = 'PLAN' AND business_id = ? AND deleted = 0",
            Long.class, tenantId, planId));
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, tenant_id, project_id, granularity, plan_type, system_id, doc_code, doc_name, plan_summary, checksum_md5, owner_id " +
            "FROM dm_plan WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移方案不存在");
        return rows.get(0);
    }

    private void findRecycleBinRow(long id, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dm_plan WHERE id = ? AND tenant_id = ? AND deleted = 1",
                Integer.class, id, tenantId);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移方案不存在于回收站");
    }

    private void ensureProject(long id, AuthUser user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Integer.class, id, user.tenantId());
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
    }

    private void ensureSystemBelongsToProject(long subsystemId, long projectId, AuthUser user) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dm_component c " +
            "JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 " +
            "WHERE c.tenant_id = ? AND c.project_id = ? AND s.id = ? AND c.deleted = 0",
            Integer.class, user.tenantId(), projectId, subsystemId);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "关联系统不存在或不属于当前项目");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFiles(Map<String, Object> body) {
        List<Map<String, Object>> files = new ArrayList<>();
        Object raw = firstNonNull(body.get("files"), body.get("attachments"));
        if (raw instanceof Collection<?> c) {
            for (Object item : c) if (item instanceof Map) files.add((Map<String, Object>) item);
        }
        if (files.isEmpty() && body.get("attachmentId") != null) {
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("attachmentId", body.get("attachmentId"));
            single.put("checksumMd5", body.get("checksumMd5"));
            single.put("fileName", body.get("fileName"));
            files.add(single);
        }
        return files;
    }

    private void validateEnum(String value, Set<String> allowed, String message) {
        if (value == null || !allowed.contains(value)) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private String normalizeMd5(Object raw) {
        String md5 = raw == null ? null : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (md5 == null || md5.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 不能为空");
        if (!md5.matches(MD5_PATTERN)) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 格式无效");
        return md5;
    }

    private void audit(AuthUser user, String operation, long id) {
        jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) VALUES (?, ?, ?, 'PLAN', ?)",
                user.tenantId(), user.id(), operation, id);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private static String stripExtension(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Object firstNonNull(Object... values) {
        for (Object v : values) if (v != null) return v;
        return null;
    }

    private static long requireLong(Object raw, String message) {
        if (raw == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        try { return Long.parseLong(String.valueOf(raw).trim()); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
    }

    private static Long optionalLong(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try { return Long.parseLong(String.valueOf(raw).trim()); }
        catch (NumberFormatException ex) { return null; }
    }

    private static String requireText(Object raw, String message) {
        String text = raw == null ? null : String.valueOf(raw).trim();
        if (text == null || text.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return text;
    }

    private static String optionalText(Object raw) {
        if (raw == null) return null;
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private static String textOrNull(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
