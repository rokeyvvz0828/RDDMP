package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.model.UserDirectoryPort;
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
 * 专题材料专属服务（对标迁移方案/会议纪要）。
 *
 * <p>存储于 {@code dm_topic}（V180：新增 granularity/topic_type_code/topic_summary），
 * 一个专题可关联多个当前项目系统（{@code dm_topic_system}）与多个源文件
 * （{@code dm_content_attachment} business_type=TOPIC，sort_order 排序，首文件为主文件）。
 * 专题类型统一来自系统管理/参数管理（{@link SystemReferenceQuery#activeParameters}），
 * 不新建业务字典表、不硬编码类型。支持组合筛选分页、详情、新增、编辑、下载、逻辑删除与统一回收站。
 */
@Service
public class TopicService {
    /** att_file 平台侧绑定域，与其它文件型内容资产一致，令 {@code DataMigrationAssetAttachmentAccessPolicy} 生效。 */
    public static final String BUSINESS_TYPE = ContentFileAssetService.BUSINESS_TYPE;
    /** dm_content_attachment.business_type 值。 */
    private static final String CONTENT_TYPE = "TOPIC";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final Map<String, String> TYPE_CATEGORIES = Map.of(
            "PROJECT", "DM_TOPIC_PROJECT_TYPE",
            "SYSTEM", "DM_TOPIC_SYSTEM_TYPE");

    private static final String MAIN_FILE_JOIN =
            " LEFT JOIN dm_content_attachment m ON m.tenant_id = a.tenant_id AND m.business_type = 'TOPIC' AND m.business_id = a.id AND m.sort_order = 0 AND m.deleted = 0 ";
    private static final String SYSTEM_NAMES_SQL =
            " (SELECT GROUP_CONCAT(s.name ORDER BY s.name SEPARATOR ', ') FROM dm_topic_system ts " +
            " JOIN dm_component c ON c.tenant_id = ts.tenant_id AND c.project_id = ts.project_id AND c.system_code = ts.system_code " +
            " JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.system_code AND s.deleted = 0 " +
            " WHERE ts.tenant_id = a.tenant_id AND ts.topic_id = a.id) ";
    private static final String SYSTEM_CODES_SQL =
            " (SELECT GROUP_CONCAT(ts.system_code ORDER BY ts.system_code SEPARATOR ', ') " +
            " FROM dm_topic_system ts WHERE ts.tenant_id = a.tenant_id AND ts.topic_id = a.id) ";
    private static final String ATTACHMENT_COUNT_SQL =
            " (SELECT COUNT(*) FROM dm_content_attachment ca WHERE ca.tenant_id = a.tenant_id AND ca.business_type = 'TOPIC' AND ca.business_id = a.id AND ca.deleted = 0) ";
    private static final String SELECT_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, a.granularity, a.topic_type_code, " +
            "a.doc_code AS asset_code, a.doc_name AS asset_name, a.topic_summary, " +
            "m.attachment_id, " + SYSTEM_NAMES_SQL + " AS system_names, " + SYSTEM_CODES_SQL + " AS system_codes, " +
            ATTACHMENT_COUNT_SQL + " AS attachment_count, " +
            "a.owner_id, a.created_by, a.created_at, a.updated_by, a.updated_at ";
    private static final String RECYCLE_COLUMNS =
            "SELECT a.id, a.project_id, p.project_name, 'TOPIC' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "a.granularity, a.topic_type_code, a.topic_summary, m.attachment_id, " +
            SYSTEM_NAMES_SQL + " AS system_names, " + SYSTEM_CODES_SQL + " AS system_codes, " +
            ATTACHMENT_COUNT_SQL + " AS attachment_count, a.owner_id, " +
            "a.created_at, a.updated_at, a.deleted_by, a.deleted_at ";

    private final JdbcTemplate jdbc;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final ContentDocCodeGenerator docCodes;
    private final SystemReferenceQuery systemReferences;
    private final DataMigrationCodeValueService codeValues;

    public TopicService(JdbcTemplate jdbc, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                        DataMigrationPermissionService permissions, UserDirectoryPort userDirectory,
                        ContentDocCodeGenerator docCodes, SystemReferenceQuery systemReferences,
                        DataMigrationCodeValueService codeValues) {
        this.jdbc = jdbc;
        this.attachments = attachments;
        this.fileAssets = fileAssets;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
        this.docCodes = docCodes;
        this.systemReferences = systemReferences;
        this.codeValues = codeValues;
    }

    /**
     * 分页查询专题列表：租户 + 项目恒定过滤，支持颗粒度、参数类型、涉及系统（多选）与名称/简述关键字组合筛选。
     */
    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String topicTypeCode,
                                                String systemCodes, String keyword, int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        // 筛选允许任意非空值，保证历史数据只读展示；写入才强制校验参数管理启用项。
        if (topicTypeCode != null && !topicTypeCode.isBlank()) ensureTypeActive(granularity, topicTypeCode, user);

        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append("FROM dm_topic a ").append(MAIN_FILE_JOIN)
                .append("LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 ")
                .append("WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, scope, granularity, topicTypeCode, systemCodes, keyword);

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

    /** 单条详情（含多附件与涉及系统）。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT_COLUMNS + "FROM dm_topic a " + MAIN_FILE_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 0", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "专题不存在");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(rows, user.tenantId());
        row.put("attachments", attachments.list(CONTENT_TYPE, id, user.tenantId()));
        return row;
    }

    // ============ 写入 ============

    /**
     * 新增专题：必填元数据 + 至少一个源文件；系统级必须选择涉及系统，项目级不得提交系统。
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        permissions.requireAccessible(projectId, user);
        String granularity = requireText(body.get("granularity"), "专题颗粒度不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_TOPIC_GRANULARITY, "专题颗粒度", granularity, user);
        String topicTypeCode = requireText(body.get("topicTypeCode"), "专题类型不能为空");
        ensureTypeActive(granularity, topicTypeCode, user);
        String topicName = requireText(firstNonNull(body.get("topicName"), body.get("docName"), body.get("doc_name")), "专题名称不能为空");
        String summary = requireText(firstNonNull(body.get("topicSummary"), body.get("summary")), "专题简述不能为空");
        List<String> systemCodes = extractSystemCodes(granularity, body.get("systemCodes"), projectId, user);

        List<Map<String, Object>> files = extractFiles(body);
        if (files.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "至少上传一个源文件");
        List<Map<String, Object>> entries = resolveFiles(files, Set.of(), user);

        long id = nextId();
        try {
            jdbc.update("INSERT INTO dm_topic (id, tenant_id, project_id, doc_code, doc_name, " +
                    "granularity, topic_type_code, topic_summary, owner_id, created_by, updated_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, tenantId, projectId, docCodes.generate(CONTENT_TYPE), topicName.trim(),
                    granularity, topicTypeCode, summary, user.id(), user.id(), user.id());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "专题编号冲突，请刷新后重试");
        }
        attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, entries, user);
        saveSystemRelations(id, projectId, systemCodes, user);
        audit(user, "TOPIC_CREATE", projectId, id);
        return detail(id, user);
    }

    /**
     * 编辑专题：元数据全量可改；提供 files 时全量重设附件集合，否则维持原附件。
     * 归属恒取库中记录，入参 projectId 一律忽略。
     */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        Map<String, Object> existing = findRaw(id, tenantId);
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

        boolean granularityProvided = body.containsKey("granularity");
        String granularity = granularityProvided ? requireText(body.get("granularity"), "专题颗粒度不能为空") : (String) existing.get("granularity");
        codeValues.requireActive(DataMigrationCodeValueService.DM_TOPIC_GRANULARITY, "专题颗粒度", granularity, user);
        boolean typeProvided = body.containsKey("topicTypeCode");
        String topicTypeCode = typeProvided ? requireText(body.get("topicTypeCode"), "专题类型不能为空") : (String) existing.get("topic_type_code");
        ensureTypeActive(granularity, topicTypeCode, user);
        String topicName = body.containsKey("topicName") ? requireText(body.get("topicName"), "专题名称不能为空") : (String) existing.get("doc_name");
        String summary = body.containsKey("topicSummary") || body.containsKey("summary")
                ? requireText(firstNonNull(body.get("topicSummary"), body.get("summary")), "专题简述不能为空") : (String) existing.get("topic_summary");
        boolean systemsProvided = body.containsKey("systemCodes");
        List<String> systemCodes = systemsProvided
                ? extractSystemCodes(granularity, body.get("systemCodes"), projectId, user)
                : ("PROJECT".equals(granularity) ? List.of() : loadSystemCodes(id, tenantId));

        List<Map<String, Object>> files = extractFiles(body);
        if (!files.isEmpty()) {
            Set<Long> bound = boundAttachmentIds(id, tenantId);
            List<Map<String, Object>> entries = resolveFiles(files, bound, user);
            attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, entries, user);
        }

        int changed = jdbc.update(
                "UPDATE dm_topic SET doc_name = ?, granularity = ?, topic_type_code = ?, topic_summary = ?, " +
                "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                topicName.trim(), granularity, topicTypeCode, summary, user.id(), id, tenantId);
        if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "专题状态已变化，请刷新后重试");

        saveSystemRelations(id, projectId, systemCodes, user);
        audit(user, "TOPIC_UPDATE", projectId, id);
        return detail(id, user);
    }

    /** 批量逻辑删除（进统一回收站），记录删除人/时间。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> existing = findRaw(id, user.tenantId());
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            int changed = jdbc.update("UPDATE dm_topic SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "专题状态已变化，请刷新后重试");
            audit(user, "TOPIC_DELETE", projectId, id);
        }
    }

    /** 下载（T32）：返回平台附件下载路径（首/主文件 sort_order=0），先校验记录项目归属。 */
    public String download(long id, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        List<Long> mainIds = jdbc.queryForList(
                "SELECT m.attachment_id FROM dm_content_attachment m JOIN dm_topic a ON a.id = m.business_id AND a.tenant_id = m.tenant_id " +
                "WHERE m.tenant_id = ? AND m.business_type = 'TOPIC' AND m.business_id = ? AND m.sort_order = 0 AND m.deleted = 0 AND a.deleted = 0",
                Long.class, user.tenantId(), id);
        if (mainIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "专题未绑定源文件");
        return "/api/attachments/" + mainIds.get(0) + "/download";
    }

    /** 专题附件列表（T32：先校验记录项目归属）。 */
    public List<Map<String, Object>> listAttachments(long id, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        return attachments.list(CONTENT_TYPE, id, user.tenantId());
    }

    /** 专题类型选项：只返回指定颗粒度对应参数类别中的启用项。 */
    public List<Map<String, Object>> getTypeOptions(String granularity, AuthUser user) {
        codeValues.requireActive(DataMigrationCodeValueService.DM_TOPIC_GRANULARITY, "专题颗粒度", granularity, user);
        String category = TYPE_CATEGORIES.get(granularity);
        List<Map<String, Object>> options = new ArrayList<>();
        if (category == null) return options;
        for (SystemParameterReference parameter : systemReferences.activeParameters(user, category)) {
            options.add(Map.of("value", parameter.code(), "label", parameter.label()));
        }
        return options;
    }

    /**
     * 涉及系统下拉：{@code projectId} 必填，一次性返回该项目 {@code dm_component} 活动清单的全部系统
     * （单项目数迁系统数量小，约 150 条以内），由前端在浏览器内本地随输随筛；
     * 备选展示统一为「系统编号 - 系统名称」。
     */

    // ============ 统一回收站 SPI ============

    public long countRecycleBin(long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dm_topic a WHERE a.tenant_id = ? AND a.deleted = 1");
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
                .append("FROM dm_topic a ").append(MAIN_FILE_JOIN)
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
                RECYCLE_COLUMNS + "FROM dm_topic a " + MAIN_FILE_JOIN +
                "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
                "WHERE a.tenant_id = ? AND a.id = ? AND a.deleted = 1", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "专题不存在于回收站");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(rows, user.tenantId());
        // 添加附件列表
        List<Map<String, Object>> attachmentList = attachments.list("TOPIC", id, user.tenantId());
        row.put("attachments", attachmentList);
        return row;
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            int changed = jdbc.update("UPDATE dm_topic SET deleted = 0, deleted_by = NULL, deleted_at = NULL, " +
                    "updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND tenant_id = ? AND deleted = 1", user.id(), id, user.tenantId());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "专题状态已变化，请刷新后重试");
            audit(user, "TOPIC_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);
            jdbc.update("DELETE FROM dm_topic_system WHERE tenant_id = ? AND topic_id = ?", user.tenantId(), id);
            jdbc.update("DELETE FROM dm_topic WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
            audit(user, "TOPIC_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private void appendFilters(StringBuilder sql, List<Object> args, long projectId, String granularity,
                               String topicTypeCode, String systemCodes, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (granularity != null && !granularity.isBlank()) {
            sql.append(" AND a.granularity = ?");
            args.add(granularity);
        }
        if (topicTypeCode != null && !topicTypeCode.isBlank()) {
            sql.append(" AND a.topic_type_code = ?");
            args.add(topicTypeCode.trim());
        }
        if (systemCodes != null && !systemCodes.isBlank()) {
            List<String> codes = splitCodes(systemCodes);
            sql.append(" AND EXISTS (SELECT 1 FROM dm_topic_system ts WHERE ts.tenant_id = a.tenant_id AND ts.topic_id = a.id AND ts.system_code IN (");
            sql.append(String.join(",", java.util.Collections.nCopies(codes.size(), "?")));
            sql.append("))");
            args.addAll(codes);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.doc_name LIKE ? OR a.topic_summary LIKE ?)");
            args.add(value);
            args.add(value);
        }
    }

    private void appendRecycleFilters(StringBuilder sql, List<Object> args, long projectId, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.doc_name LIKE ? OR a.topic_summary LIKE ?)");
            args.add(value);
            args.add(value);
        }
    }

    /** 颗粒度 + 类型联动校验：编码必须属于对应参数类别且启用；未给颗粒度时在两个类别内查找。 */
    private void ensureTypeActive(String granularity, String topicTypeCode, AuthUser user) {
        if (topicTypeCode == null || topicTypeCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "专题类型不能为空");
        }
        List<String> categories = new ArrayList<>();
        if (granularity != null && !granularity.isBlank()) {
            String category = TYPE_CATEGORIES.get(granularity);
            if (category == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "专题颗粒度无效，支持：PROJECT/SYSTEM");
            categories.add(category);
        } else {
            categories.addAll(TYPE_CATEGORIES.values());
        }
        for (String category : categories) {
            for (SystemParameterReference parameter : systemReferences.activeParameters(user, category)) {
                if (topicTypeCode.equals(parameter.code())) return;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "专题类型无效或已停用，请从系统管理/参数管理启用后重试");
    }

    private List<String> extractSystemCodes(String granularity, Object raw, long projectId, AuthUser user) {
        List<String> codes = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item == null) continue;
                String code = String.valueOf(item).trim();
                if (!code.isEmpty() && !codes.contains(code)) codes.add(code);
            }
        } else if (raw != null) {
            for (String code : splitCodes(String.valueOf(raw))) {
                if (!codes.contains(code)) codes.add(code);
            }
        }
        if ("PROJECT".equals(granularity)) {
            if (!codes.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目级专题不能关联系统");
            return List.of();
        }
        if ("SYSTEM".equals(granularity)) {
            if (codes.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "系统级专题必须至少选择一个涉及系统");
            for (String code : codes) ensureSystemBelongsToProject(code, projectId, user);
            return codes;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "专题颗粒度无效，支持：PROJECT/SYSTEM");
    }

    private void saveSystemRelations(long topicId, long projectId, List<String> systemCodes, AuthUser user) {
        jdbc.update("DELETE FROM dm_topic_system WHERE tenant_id = ? AND topic_id = ?", user.tenantId(), topicId);
        for (String systemCode : systemCodes) {
            jdbc.update("INSERT INTO dm_topic_system (id, tenant_id, topic_id, project_id, system_code, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    nextId(), user.tenantId(), topicId, projectId, systemCode, user.id());
        }
    }

    private List<String> loadSystemCodes(long topicId, long tenantId) {
        return jdbc.queryForList("SELECT system_code FROM dm_topic_system WHERE tenant_id = ? AND topic_id = ? ORDER BY id",
                String.class, tenantId, topicId);
    }

    private void ensureSystemBelongsToProject(String systemCode, long projectId, AuthUser user) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component c " +
                "WHERE c.tenant_id = ? AND c.project_id = ? AND c.system_code = ? AND c.enabled = 1",
                Integer.class, user.tenantId(), projectId, systemCode);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "涉及系统不存在或不属于当前项目");
        }
    }

    /** 附件解析：已绑定附件直接复用；新附件必须是当前用户上传的临时附件且不超过 50MB。 */
    private List<Map<String, Object>> resolveFiles(List<Map<String, Object>> files, Set<Long> bound, AuthUser user) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> file : files) {
            long attachmentId = requireLong(file.get("attachmentId"), "附件不能为空");
            Map<String, Object> entry = new LinkedHashMap<>();
            if (bound.contains(attachmentId)) {
                entry.put("attachmentId", attachmentId);
                entry.put("fileName", file.get("fileName"));
                entries.add(entry);
                continue;
            }
            AttachmentItem item = fileAssets.resolveAttachment(attachmentId, user);
            if (item.fileSize() <= 0 || item.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + item.fileName() + " 为空或超过 50 MB");
            }
            entry.put("attachmentId", item.id());
            entry.put("fileName", item.fileName());
            entries.add(entry);
        }
        return entries;
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
            single.put("fileName", body.get("fileName"));
            files.add(single);
        }
        return files;
    }

    private Set<Long> boundAttachmentIds(long topicId, long tenantId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = 'TOPIC' AND business_id = ? AND deleted = 0",
                Long.class, tenantId, topicId));
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tenant_id, project_id, granularity, topic_type_code, doc_code, doc_name, topic_summary, owner_id " +
                "FROM dm_topic WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "专题不存在");
        return rows.get(0);
    }

    /** 回收站恢复/彻底删除前的行定位 + 项目归属校验，返回归属项目。 */
    private long requireRecycleBinScope(long id, AuthUser user) {
        List<Long> projects = jdbc.queryForList("SELECT project_id FROM dm_topic WHERE id = ? AND tenant_id = ? AND deleted = 1",
                Long.class, id, user.tenantId());
        if (projects.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "专题不存在于回收站");
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
                "VALUES (?, ?, ?, ?, 'TOPIC', ?)",
                user.tenantId(), user.id(), projectId, operation, id);
    }

    private List<String> splitCodes(String raw) {
        List<String> codes = new ArrayList<>();
        if (raw == null) return codes;
        for (String part : raw.split(",")) {
            String code = part.trim();
            if (!code.isEmpty() && !codes.contains(code)) codes.add(code);
        }
        return codes;
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

    private static Object firstNonNull(Object... values) {
        for (Object v : values) if (v != null) return v;
        return null;
    }

    private static long requireLong(Object raw, String message) {
        if (raw == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        try { return Long.parseLong(String.valueOf(raw).trim()); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
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
}
