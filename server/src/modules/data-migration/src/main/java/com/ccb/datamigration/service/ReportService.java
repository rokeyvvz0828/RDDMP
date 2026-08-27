package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 汇报材料专属服务。
 * 复用 dm_asset 表，asset_type='REPORT'，支持多维度筛选、批量上传、编辑、逻辑删除和回收站。
 */
@Service
public class ReportService {
    public static final String BUSINESS_TYPE = "DATA_MIGRATION_ASSET";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final String MD5_PATTERN = "[0-9a-fA-F]{32}";
    private static final Set<String> REPORT_PERIODS = Set.of("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "IRREGULAR");

    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;
    private final MinioStorageService storage;
    private final DataMigrationPermissionService permissions;

    public ReportService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway, MinioStorageService storage, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
        this.storage = storage;
        this.permissions = permissions;
    }

    /**
     * 1. 分页查询汇报材料列表
     */
    public PageResult<Map<String, Object>> list(Long projectId, String reportPeriod, String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.project_id, p.project_name, a.asset_type, a.asset_code, a.asset_name, " +
            "a.content_type, a.file_size, a.attachment_id, a.checksum_md5, a.report_period, a.report_date, a.keywords, " +
            "a.owner_id, a.created_at, a.updated_at, a.created_by, a.updated_by " +
            "FROM dm_asset a " +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.asset_type = 'REPORT' AND a.deleted = 0"
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
            sql.append(" AND (a.asset_name LIKE ? OR a.keywords LIKE ?)");
            args.add(value);
            args.add(value);
        }

        // 计算总数
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        if (total == null) total = 0L;

        // 分页查询
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC");
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
        assertMd5Available(md5, user.tenantId(), 0L);

        long id = nextId();
        jdbc.update(
            "INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, " +
            "content_type, file_size, attachment_id, checksum_md5, report_period, report_date, keywords, " +
            "owner_id, created_by, updated_by) VALUES (?, ?, ?, 'REPORT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, user.tenantId(), projectId, "REPORT-" + id, reportName, attachment.contentType(),
            attachment.fileSize(), attachment.id(), md5, reportPeriod, reportDate, keywords,
            user.id(), user.id(), user.id()
        );

        attachmentGateway.bind(new AttachmentBindingCommand(attachment.id(), BUSINESS_TYPE, String.valueOf(id), String.valueOf(projectId)), user);
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
            assertMd5Available(md5, user.tenantId(), 0L);

            long id = nextId();
            String reportName = attachment.fileName();
            // 移除文件扩展名作为资料名称
            if (reportName.contains(".")) {
                reportName = reportName.substring(0, reportName.lastIndexOf("."));
            }

            jdbc.update(
                "INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, " +
                "content_type, file_size, attachment_id, checksum_md5, report_period, " +
                "owner_id, created_by, updated_by) VALUES (?, ?, ?, 'REPORT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), projectId, "REPORT-" + id, reportName, attachment.contentType(),
                attachment.fileSize(), attachment.id(), md5, reportPeriod, user.id(), user.id(), user.id()
            );

            attachmentGateway.bind(new AttachmentBindingCommand(attachment.id(), BUSINESS_TYPE, String.valueOf(id), String.valueOf(projectId)), user);
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

        // 处理文件更新
        if (attachmentId != null) {
            AttachmentItem attachment = resolveAttachment(attachmentId, user);
            if (attachment.fileSize() <= 0 || attachment.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空或超过 50 MB");
            }

            String md5 = normalizeMd5(checksumMd5);
            assertMd5Available(md5, user.tenantId(), id);
            long bindingProjectId = projectId != null
                ? projectId
                : ((Number) existing.get("project_id")).longValue();
            Long oldAttachmentId = existing.get("attachment_id") == null
                ? null
                : ((Number) existing.get("attachment_id")).longValue();

            attachmentGateway.bind(new AttachmentBindingCommand(
                attachment.id(), BUSINESS_TYPE, String.valueOf(id), String.valueOf(bindingProjectId)), user);

            jdbc.update(
                "UPDATE dm_asset SET attachment_id = ?, checksum_md5 = ?, content_type = ?, file_size = ?, " +
                "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ?",
                attachment.id(), md5, attachment.contentType(), attachment.fileSize(), user.id(), id, user.tenantId()
            );

            if (oldAttachmentId != null) {
                attachmentGateway.deleteBound(oldAttachmentId, BUSINESS_TYPE, String.valueOf(id), user);
            }
        }

        // 更新元数据
        StringBuilder updateSql = new StringBuilder("UPDATE dm_asset SET updated_by = ?, updated_at = CURRENT_TIMESTAMP");
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
            updateSql.append(", asset_name = ?");
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
                "UPDATE dm_asset SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
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
        return storage.presignedUrl((String) existing.get("object_key"));
    }

    /**
     * 7. 回收站列表
     */
    public PageResult<Map<String, Object>> recycleBinList(Long projectId, String reportPeriod, 
                                                         String keyword, int page, int size, AuthUser user) {
        permissions.requireAdmin(user);

        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.project_id, p.project_name, a.asset_type, a.asset_code, a.asset_name, " +
            "a.content_type, a.file_size, a.attachment_id, a.checksum_md5, a.report_period, a.report_date, a.keywords, " +
            "a.owner_id, a.created_at, a.updated_at, a.deleted_by, a.deleted_at " +
            "FROM dm_asset a " +
            "LEFT JOIN pm_project p ON a.project_id = p.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 " +
            "WHERE a.tenant_id = ? AND a.asset_type = 'REPORT' AND a.deleted = 1"
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
            sql.append(" AND (a.asset_name LIKE ? OR a.keywords LIKE ?)");
            args.add(value);
            args.add(value);
        }

        // 计算总数
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        if (total == null) total = 0L;

        // 分页查询
        sql.append(" ORDER BY a.deleted_at DESC, a.id DESC");
        sql.append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        return new PageResult<>(records, total, page, size);
    }

    /**
     * 8. 恢复汇报材料
     */
    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : ids) {
            findById(id, user.tenantId(), true);
            int changed = jdbc.update(
                "UPDATE dm_asset SET deleted = 0, deleted_by = NULL, deleted_at = NULL " +
                "WHERE id = ? AND tenant_id = ? AND deleted = 1",
                id, user.tenantId()
            );
            if (changed != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "汇报材料状态已变化，请刷新后重试");
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
            
            // 删除附件
            if (existing.get("attachment_id") != null) {
                attachmentGateway.deleteBound(((Number) existing.get("attachment_id")).longValue(),
                    BUSINESS_TYPE, String.valueOf(id), user);
            }
            
            // 物理删除记录
            jdbc.update("DELETE FROM dm_asset WHERE id = ? AND tenant_id = ? AND deleted = 1", 
                       id, user.tenantId());
            audit(user, "REPORT_PURGE", id);
        }
    }

    /**
     * 检查MD5是否可用
     */
    public boolean isMd5Available(String checksumMd5, AuthUser user) {
        String md5 = normalizeMd5(checksumMd5);
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND checksum_md5 = ? AND deleted = 0",
            Integer.class, user.tenantId(), md5
        );
        return count == null || count == 0;
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

    private void assertMd5Available(String md5, long tenantId, long currentId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND checksum_md5 = ? AND deleted = 0 AND id <> ?",
            Integer.class, tenantId, md5, currentId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "文件 MD5 已存在，不允许重复提交");
        }
    }

    private Map<String, Object> findById(long id, long tenantId) {
        return findById(id, tenantId, false);
    }

    private Map<String, Object> findById(long id, long tenantId, boolean deleted) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, project_id, asset_type, asset_code, asset_name, content_type, file_size, " +
            "attachment_id, checksum_md5, report_period, report_date, keywords, owner_id, " +
            "created_at, updated_at, created_by, updated_by, deleted_by, deleted_at " +
            "FROM dm_asset WHERE id = ? AND tenant_id = ? AND deleted = ?",
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
