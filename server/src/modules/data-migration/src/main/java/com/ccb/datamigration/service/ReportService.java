package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 汇报材料专属服务。
 * 存储于 dm_report（REQ-20260831-050 一菜单一表），主文件经 dm_content_attachment（sort_order=0）登记，
 * 支持多维度筛选、批量上传、编辑、逻辑删除和回收站。
 */
@Service
public class ReportService {
    public static final String BUSINESS_TYPE = ContentFileAssetService.BUSINESS_TYPE;
    private static final String CONTENT_TYPE = "REPORT";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> REPORT_PERIODS = Set.of("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "IRREGULAR");

    private static final String MAIN_FILE_JOIN =
        " LEFT JOIN dm_content_attachment m ON m.tenant_id = a.tenant_id AND m.business_type = 'REPORT' AND m.business_id = a.id AND m.sort_order = 0 AND m.deleted = 0 " +
        " LEFT JOIN att_file f ON f.id = m.attachment_id AND f.tenant_id = m.tenant_id ";

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;
    private final ContentDocCodeGenerator docCodes;

    @Autowired
    public ReportService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway, ContentAttachmentService attachments,
                         ContentFileAssetService fileAssets, DataMigrationPermissionService permissions,
                         ContentDocCodeGenerator docCodes) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
        this.attachments = attachments;
        this.fileAssets = fileAssets;
        this.permissions = permissions;
        this.docCodes = docCodes;
    }

    public ReportService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway, ContentAttachmentService attachments,
                         ContentFileAssetService fileAssets, DataMigrationPermissionService permissions) {
        this(jdbc, attachmentGateway, attachments, fileAssets, permissions, new ContentDocCodeGenerator());
    }

    /**
     * 1. 分页查询汇报材料列表（T32：{@code projectId} 必填，SQL 恒定项目过滤）
     */
    public PageResult<Map<String, Object>> list(Long projectId, String reportPeriod, String keyword, int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.project_id, p.project_name, 'REPORT' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "f.content_type, f.file_size, m.attachment_id, a.report_period, a.report_date, a.keywords, " +
            "a.owner_id, a.created_at, a.updated_at, a.created_by, a.updated_by " +
            "FROM dm_report a " + MAIN_FILE_JOIN +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.deleted = 0"
        );
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));

        sql.append(" AND a.project_id = ?");
        args.add(scope);
        if (reportPeriod != null && !reportPeriod.isBlank() && REPORT_PERIODS.contains(reportPeriod)) {
            sql.append(" AND a.report_period = ?");
            args.add(reportPeriod);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.doc_name LIKE ? OR a.keywords LIKE ?)");
            args.add(value);
            args.add(value);
        }

        // 计算总数
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        if (total == null) total = 0L;

        // 分页查询
        sql.append(" ORDER BY a.doc_code ASC, a.id ASC");
        sql.append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        return new PageResult<>(records, total, page, size);
    }

    /**
     * 2. 单条上传汇报材料
     */
    @Transactional
    public Map<String, Object> upload(long projectId, String reportPeriod, String reportName, 
                                     String reportDate, String keywords, Long attachmentId,
                                     AuthUser user) {
        validateReportPeriod(reportPeriod);
        if (reportName == null || reportName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资料名称不能为空");
        }
        if (keywords == null || keywords.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关键字索引不能为空");
        }

        ensureProject(projectId, user);
        AttachmentItem attachment = resolveAttachment(attachmentId, user);
        if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空或超过 50 MB");
        }

        long id = nextId();
        jdbc.update(
            "INSERT INTO dm_report (id, tenant_id, project_id, doc_code, doc_name, " +
            "report_period, report_date, keywords, " +
            "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, user.tenantId(), projectId, docCodes.generate(CONTENT_TYPE), reportName,
            reportPeriod, reportDate, keywords,
            user.id(), user.id(), user.id()
        );

        fileAssets.replaceMainFile(CONTENT_TYPE, id, projectId, attachment, user);
        audit(user, "REPORT_UPLOAD", projectId, id);

        return findById(id, user.tenantId());
    }

    /**
     * 3. 批量上传汇报材料
     */
    @Transactional
    public List<Map<String, Object>> batchUpload(long projectId, String reportPeriod,
                                                 List<AttachmentItem> attachments,
                                                 AuthUser user) {
        validateReportPeriod(reportPeriod);
        ensureProject(projectId, user);
        if (attachments == null || attachments.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件列表不能为空");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int index = 0; index < attachments.size(); index++) {
            AttachmentItem attachment = attachments.get(index);
            requireTemporaryAttachment(attachment, user);
            if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + attachment.fileName() + " 为空或超过 50 MB");
            }

            long id = nextId();
            String reportName = attachment.fileName();
            // 移除文件扩展名作为资料名称
            if (reportName.contains(".")) {
                reportName = reportName.substring(0, reportName.lastIndexOf("."));
            }

            jdbc.update(
                "INSERT INTO dm_report (id, tenant_id, project_id, doc_code, doc_name, " +
                "report_period, " +
                "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), projectId, docCodes.generate(CONTENT_TYPE), reportName,
                reportPeriod, user.id(), user.id(), user.id()
            );

            fileAssets.replaceMainFile(CONTENT_TYPE, id, projectId, attachment, user);
            audit(user, "REPORT_BATCH_UPLOAD", projectId, id);

            results.add(findById(id, user.tenantId()));
        }
        return results;
    }

    /**
     * 4. 编辑汇报材料
     *
     * <p>T32 决策 D2：维护操作的归属恒取库中记录，不再接受 {@code projectId} 入参，
     * {@code UPDATE} 也不包含 {@code project_id}，归属不可变更。
     */
    @Transactional
    public Map<String, Object> update(long id, String reportPeriod, String reportName,
                                     String reportDate, String keywords, Long attachmentId,
                                     AuthUser user) {
        Map<String, Object> existing = findById(id, user.tenantId());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());

        if (reportPeriod != null) validateReportPeriod(reportPeriod);
        if (reportName != null && reportName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资料名称不能为空");
        }
        if (keywords != null && keywords.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关键字索引不能为空");
        }

        // 处理文件更新：替换 sort_order=0 主文件行，旧主文件解绑
        if (attachmentId != null) {
            AttachmentItem attachment = resolveAttachment(attachmentId, user);
            if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空或超过 50 MB");
            }

            fileAssets.replaceMainFile(CONTENT_TYPE, id, projectId, attachment, user);
        }

        // 更新元数据
        StringBuilder updateSql = new StringBuilder("UPDATE dm_report SET updated_by = ?, updated_at = CURRENT_TIMESTAMP");
        List<Object> args = new ArrayList<>(List.of(user.id()));

        if (reportPeriod != null) {
            updateSql.append(", report_period = ?");
            args.add(reportPeriod);
        }
        if (reportName != null) {
            updateSql.append(", doc_name = ?");
            args.add(reportName);
        }
        if (reportDate != null) {
            updateSql.append(", report_date = ?");
            args.add(reportDate);
        }
        if (keywords != null) {
            updateSql.append(", keywords = ?");
            args.add(keywords);
        }

        updateSql.append(" WHERE id = ? AND tenant_id = ?");
        args.add(id);
        args.add(user.tenantId());

        jdbc.update(updateSql.toString(), args.toArray());
        audit(user, "REPORT_UPDATE", projectId, id);

        return findById(id, user.tenantId());
    }

    /**
     * 5. 逻辑删除汇报材料（T32：先按记录库中归属做项目隔离）
     */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : ids) {
            Map<String, Object> existing = findById(id, user.tenantId());
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());

            jdbc.update(
                "UPDATE dm_report SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND tenant_id = ? AND deleted = 0",
                user.id(), id, user.tenantId()
            );
            audit(user, "REPORT_DELETE", projectId, id);
        }
    }

    /**
     * 6. 下载汇报材料（T32：先校验记录项目归属）
     */
    public String download(long id, AuthUser user) {
        Map<String, Object> existing = findById(id, user.tenantId());
        permissions.requireStoredProject(existing.get("project_id"), user);
        if (existing.get("attachment_id") != null) {
            return "/api/attachments/" + ((Number) existing.get("attachment_id")).longValue() + "/download";
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报材料未绑定公共附件");
    }

    /**
     * 7. 统一回收站原生分页：汇报软删总数（T32：{@code projectId} 必填，SQL COUNT，不拉明细）。
     */
    public long countRecycleBin(long projectId, String reportPeriod, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        StringBuilder sql = new StringBuilder(recycleBinSelect());
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleBinFilters(sql, args, projectId, reportPeriod, keyword);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    /**
     * 7b. 统一回收站原生分页：按 {@code doc_code ASC, id ASC} 取指定项目软删汇报前 {@code limit} 行（原生 LIMIT，不走页大小白名单）。
     */
    public List<Map<String, Object>> fetchRecycleBinPage(long projectId, String reportPeriod, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        if (limit <= 0) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(recycleBinSelect());
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleBinFilters(sql, args, projectId, reportPeriod, keyword);
        sql.append(" ORDER BY a.doc_code ASC, a.id ASC LIMIT ?");
        args.add(limit);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 查询汇报材料软删除详情（T32：含项目归属校验），不执行附件下载或状态变更。 */
    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(recycleBinSelect() + " AND a.id = ?", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报材料不存在于回收站");
        Map<String, Object> row = rows.get(0);
        permissions.requireStoredProject(row.get("project_id"), user);
        return row;
    }

    private static String recycleBinSelect() {
        return "SELECT a.id, a.project_id, p.project_name, 'REPORT' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "f.content_type, f.file_size, m.attachment_id, a.report_period, a.report_date, a.keywords, " +
            "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at " +
            "FROM dm_report a " + MAIN_FILE_JOIN +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.deleted = 1";
    }

    private void appendRecycleBinFilters(StringBuilder sql, List<Object> args, long projectId, String reportPeriod, String keyword) {
        sql.append(" AND a.project_id = ?");
        args.add(projectId);
        if (reportPeriod != null && !reportPeriod.isBlank() && REPORT_PERIODS.contains(reportPeriod)) {
            sql.append(" AND a.report_period = ?");
            args.add(reportPeriod);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.doc_name LIKE ? OR a.keywords LIKE ?)");
            args.add(value);
            args.add(value);
        }
    }

    /**
     * 8. 恢复汇报材料（T32：仅能恢复调用者可访问项目内的记录）
     */
    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            Map<String, Object> stored = findById(id, user.tenantId(), true);
            long projectId = permissions.requireStoredProject(stored.get("project_id"), user);
            try {
                int changed = jdbc.update(
                    "UPDATE dm_report SET deleted = 0, deleted_by = NULL, deleted_at = NULL " +
                    "WHERE id = ? AND tenant_id = ? AND deleted = 1",
                    id, user.tenantId()
                );
                if (changed != 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, "汇报材料状态已变化，请刷新后重试");
                }
            } catch (DataIntegrityViolationException ex) {
                // 恢复后活动行 doc_code 与既有活动行冲突（uk_dm_report_active_code）：与统一方案同构，翻译为 CONFLICT(40900)。
                throw new BusinessException(ErrorCode.CONFLICT, "汇报材料编号在该项目下已存在，无法恢复");
            }
            audit(user, "REPORT_RESTORE", projectId, id);
        }
    }

    /**
     * 9. 确认清理汇报材料（T32：仅能清理调用者可访问项目内的记录）
     */
    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            Map<String, Object> existing = findById(id, user.tenantId(), true);
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

            // 解绑并删除附件关系行
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);

            // 物理删除记录
            jdbc.update("DELETE FROM dm_report WHERE id = ? AND tenant_id = ? AND deleted = 1",
                       id, user.tenantId());
            audit(user, "REPORT_PURGE", projectId, id);
        }
    }

    /**
     * 获取项目选项列表（T32-r1：只返回调用者可访问的项目）。
     *
     * <p>可访问项目集直接取 platform/system 的项目成员口径，本模块不再复制 {@code pm_project_member} SQL；
     * 项目表仅用于读取展示所需的名称。该端点当前已无前端调用方（页面使用 {@code /api/project/workbench}），
     * 保留仅为契约兼容，不得借此列举他人项目。
     */
    public List<Map<String, Object>> getProjectOptions(AuthUser user) {
        Set<Long> accessible = new HashSet<>(permissions.accessibleProjectIds(user));
        if (accessible.isEmpty()) return List.of();
        return jdbc.queryForList(
            "SELECT id, project_name FROM pm_project WHERE tenant_id = ? AND deleted = 0 ORDER BY project_name",
            user.tenantId()
        ).stream().filter(row -> accessible.contains(((Number) row.get("id")).longValue())).toList();
    }

    // ========== 私有方法 ==========

    private void validateReportPeriod(String reportPeriod) {
        if (reportPeriod == null || !REPORT_PERIODS.contains(reportPeriod)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报周期无效，支持：DAILY/WEEKLY/BIWEEKLY/MONTHLY/IRREGULAR");
        }
    }

    private void ensureProject(long id, AuthUser user) {
        // T32：统一委托模块内项目隔离守卫（租户内存在未删 + 管理员或活动成员）
        permissions.requireAccessible(id, user);
    }

    private AttachmentItem resolveAttachment(Long attachmentId, AuthUser user) {
        if (attachmentId == null || attachmentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先通过公共附件接口上传文件");
        }
        AttachmentItem item = attachmentGateway.get(attachmentId, user);
        requireTemporaryAttachment(item, user);
        return item;
    }

    private void requireTemporaryAttachment(AttachmentItem item, AuthUser user) {
        if (item.uploaderId() != user.id() || !"TEMP".equals(item.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件必须是当前用户上传的临时附件");
        }
    }

    private Map<String, Object> findById(long id, long tenantId) {
        return findById(id, tenantId, false);
    }

    private Map<String, Object> findById(long id, long tenantId, boolean deleted) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.id, a.project_id, 'REPORT' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "f.content_type, f.file_size, m.attachment_id, a.report_period, a.report_date, a.keywords, a.owner_id, " +
            "a.created_at, a.updated_at, a.created_by, a.updated_by, a.deleted_by, a.deleted_at " +
            "FROM dm_report a " + MAIN_FILE_JOIN +
            "WHERE a.id = ? AND a.tenant_id = ? AND a.deleted = ?",
            id, tenantId, deleted ? 1 : 0
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报材料不存在");
        }
        return rows.get(0);
    }

    private void audit(AuthUser user, String operation, long projectId, long id) {
        jdbc.update(
            "INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, entity_id) " +
            "VALUES (?, ?, ?, ?, 'REPORT', ?)",
            user.tenantId(), user.id(), projectId, operation, id
        );
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
