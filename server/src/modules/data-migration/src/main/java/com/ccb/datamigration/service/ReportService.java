package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final String MD5_PATTERN = "[0-9a-fA-F]{32}";
    private static final Set<String> REPORT_PERIODS = Set.of("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "IRREGULAR");

    private static final String MAIN_FILE_JOIN =
        " LEFT JOIN dm_content_attachment m ON m.tenant_id = a.tenant_id AND m.business_type = 'REPORT' AND m.business_id = a.id AND m.sort_order = 0 AND m.deleted = 0 " +
        " LEFT JOIN att_file f ON f.id = m.attachment_id AND f.tenant_id = m.tenant_id ";

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;

    public ReportService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway, ContentAttachmentService attachments,
                         ContentFileAssetService fileAssets, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
        this.attachments = attachments;
        this.fileAssets = fileAssets;
        this.permissions = permissions;
    }

    /**
     * 1. 分页查询汇报材料列表
     */
    public PageResult<Map<String, Object>> list(Long projectId, String reportPeriod, String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.project_id, p.project_name, 'REPORT' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "f.content_type, f.file_size, m.attachment_id, a.checksum_md5, a.report_period, a.report_date, a.keywords, " +
            "a.owner_id, a.created_at, a.updated_at, a.created_by, a.updated_by " +
            "FROM dm_report a " + MAIN_FILE_JOIN +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.deleted = 0"
        );
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));

        if (projectId != null) {
            sql.append(" AND a.project_id = ?");
            args.add(projectId);
        }
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
                                     String checksumMd5, AuthUser user) {
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

        String md5 = normalizeMd5(checksumMd5);
        fileAssets.assertMd5Available(md5, user.tenantId(), null);

        long id = nextId();
        jdbc.update(
            "INSERT INTO dm_report (id, tenant_id, project_id, doc_code, doc_name, " +
            "checksum_md5, report_period, report_date, keywords, " +
            "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, user.tenantId(), projectId, "REPORT-" + id, reportName,
            md5, reportPeriod, reportDate, keywords,
            user.id(), user.id(), user.id()
        );

        fileAssets.replaceMainFile(CONTENT_TYPE, id, projectId, attachment, user);
        audit(user, "REPORT_UPLOAD", id);

        return findById(id, user.tenantId());
    }

    /**
     * 3. 批量上传汇报材料
     */
    @Transactional
    public List<Map<String, Object>> batchUpload(long projectId, String reportPeriod,
                                                 List<AttachmentItem> attachments, List<String> checksumMd5s,
                                                 AuthUser user) {
        validateReportPeriod(reportPeriod);
        ensureProject(projectId, user);
        if (attachments == null || checksumMd5s == null || attachments.isEmpty()
                || attachments.size() != checksumMd5s.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件与 MD5 列表不能为空且数量必须一致");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int index = 0; index < attachments.size(); index++) {
            AttachmentItem attachment = attachments.get(index);
            requireTemporaryAttachment(attachment, user);
            if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + attachment.fileName() + " 为空或超过 50 MB");
            }

            String md5 = normalizeMd5(checksumMd5s.get(index));
            fileAssets.assertMd5Available(md5, user.tenantId(), null);

            long id = nextId();
            String reportName = attachment.fileName();
            // 移除文件扩展名作为资料名称
            if (reportName.contains(".")) {
                reportName = reportName.substring(0, reportName.lastIndexOf("."));
            }

            jdbc.update(
                "INSERT INTO dm_report (id, tenant_id, project_id, doc_code, doc_name, " +
                "checksum_md5, report_period, " +
                "owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), projectId, "REPORT-" + id, reportName,
                md5, reportPeriod, user.id(), user.id(), user.id()
            );

            fileAssets.replaceMainFile(CONTENT_TYPE, id, projectId, attachment, user);
            audit(user, "REPORT_BATCH_UPLOAD", id);

            results.add(findById(id, user.tenantId()));
        }
        return results;
    }

    /**
     * 4. 编辑汇报材料
     */
    @Transactional
    public Map<String, Object> update(long id, Long projectId, String reportPeriod, String reportName,
                                     String reportDate, String keywords, Long attachmentId,
                                     String checksumMd5, AuthUser user) {
        Map<String, Object> existing = findById(id, user.tenantId());
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());

        if (reportPeriod != null) validateReportPeriod(reportPeriod);
        if (reportName != null && reportName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资料名称不能为空");
        }
        if (keywords != null && keywords.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关键字索引不能为空");
        }
        if (projectId != null) {
            ensureProject(projectId, user);
        }

        // 处理文件更新：替换 sort_order=0 主文件行，旧主文件解绑
        if (attachmentId != null) {
            AttachmentItem attachment = resolveAttachment(attachmentId, user);
            if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空或超过 50 MB");
            }

            String md5 = normalizeMd5(checksumMd5);
            fileAssets.assertMd5Available(md5, user.tenantId(), id);
            jdbc.update("UPDATE dm_report SET checksum_md5 = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ?",
                    md5, user.id(), id, user.tenantId());
            fileAssets.replaceMainFile(CONTENT_TYPE, id, ((Number) existing.get("project_id")).longValue(), attachment, user);
        }

        // 更新元数据
        StringBuilder updateSql = new StringBuilder("UPDATE dm_report SET updated_by = ?, updated_at = CURRENT_TIMESTAMP");
        List<Object> args = new ArrayList<>(List.of(user.id()));

        if (projectId != null) {
            updateSql.append(", project_id = ?");
            args.add(projectId);
        }
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
        audit(user, "REPORT_UPDATE", id);

        return findById(id, user.tenantId());
    }

    /**
     * 5. 逻辑删除汇报材料
     */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : ids) {
            Map<String, Object> existing = findById(id, user.tenantId());
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());

            jdbc.update(
                "UPDATE dm_report SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND tenant_id = ? AND deleted = 0",
                user.id(), id, user.tenantId()
            );
            audit(user, "REPORT_DELETE", id);
        }
    }

    /**
     * 6. 下载汇报材料
     */
    public String download(long id, AuthUser user) {
        Map<String, Object> existing = findById(id, user.tenantId());
        if (existing.get("attachment_id") != null) {
            return "/api/attachments/" + ((Number) existing.get("attachment_id")).longValue() + "/download";
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报材料未绑定公共附件");
    }

    /**
     * 7. 统一回收站原生分页：汇报软删总数（SQL COUNT，不拉明细）。
     */
    public long countRecycleBin(Long projectId, String reportPeriod, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        StringBuilder sql = new StringBuilder(recycleBinSelect());
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendRecycleBinFilters(sql, args, projectId, reportPeriod, keyword);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    /**
     * 7b. 统一回收站原生分页：按 {@code doc_code ASC, id ASC} 取软删汇报前 {@code limit} 行（原生 LIMIT，不走页大小白名单）。
     */
    public List<Map<String, Object>> fetchRecycleBinPage(Long projectId, String reportPeriod, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
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

    /** 查询汇报材料软删除详情，不执行附件下载或状态变更。 */
    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(recycleBinSelect() + " AND a.id = ?", user.tenantId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报材料不存在于回收站");
        return rows.get(0);
    }

    private static String recycleBinSelect() {
        return "SELECT a.id, a.project_id, p.project_name, 'REPORT' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "f.content_type, f.file_size, m.attachment_id, a.checksum_md5, a.report_period, a.report_date, a.keywords, " +
            "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at " +
            "FROM dm_report a " + MAIN_FILE_JOIN +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.deleted = 1";
    }

    private void appendRecycleBinFilters(StringBuilder sql, List<Object> args, Long projectId, String reportPeriod, String keyword) {
        if (projectId != null) {
            sql.append(" AND a.project_id = ?");
            args.add(projectId);
        }
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
     * 8. 恢复汇报材料
     */
    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            findById(id, user.tenantId(), true);
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
            audit(user, "REPORT_RESTORE", id);
        }
    }

    /**
     * 9. 确认清理汇报材料
     */
    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            Map<String, Object> existing = findById(id, user.tenantId(), true);

            // 解绑并删除附件关系行
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);

            // 物理删除记录
            jdbc.update("DELETE FROM dm_report WHERE id = ? AND tenant_id = ? AND deleted = 1",
                       id, user.tenantId());
            audit(user, "REPORT_PURGE", id);
        }
    }

    /**
     * 检查MD5是否可用
     */
    public boolean isMd5Available(String checksumMd5, AuthUser user) {
        return fileAssets.isMd5Available(checksumMd5, user);
    }

    /**
     * 获取项目选项列表
     */
    public List<Map<String, Object>> getProjectOptions(AuthUser user) {
        return jdbc.queryForList(
            "SELECT id, project_name FROM pm_project WHERE tenant_id = ? AND deleted = 0 ORDER BY project_name",
            user.tenantId()
        );
    }

    // ========== 私有方法 ==========

    private void validateReportPeriod(String reportPeriod) {
        if (reportPeriod == null || !REPORT_PERIODS.contains(reportPeriod)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "汇报周期无效，支持：DAILY/WEEKLY/BIWEEKLY/MONTHLY/IRREGULAR");
        }
    }

    private void ensureProject(long id, AuthUser user) {
        if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0",
            Integer.class, id, user.tenantId()) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
        }
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

    private String normalizeMd5(String md5) {
        if (md5 == null || md5.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 不能为空");
        }
        String value = md5.trim().toLowerCase(Locale.ROOT);
        if (!value.matches(MD5_PATTERN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 MD5 格式无效");
        }
        return value;
    }

    private Map<String, Object> findById(long id, long tenantId) {
        return findById(id, tenantId, false);
    }

    private Map<String, Object> findById(long id, long tenantId, boolean deleted) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.id, a.project_id, 'REPORT' AS asset_type, a.doc_code AS asset_code, a.doc_name AS asset_name, " +
            "f.content_type, f.file_size, m.attachment_id, a.checksum_md5, a.report_period, a.report_date, a.keywords, a.owner_id, " +
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

    private void audit(AuthUser user, String operation, long id) {
        jdbc.update(
            "INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) " +
            "VALUES (?, ?, ?, 'REPORT', ?)",
            user.tenantId(), user.id(), operation, id
        );
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
