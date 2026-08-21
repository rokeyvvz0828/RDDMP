package com.ccb.attachment.service;

import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentLink;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.filepreview.model.FilePreviewUrlProvider;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Adapts project attachments to the project module's persistent attachment contract. */
@Service
public class ProjectAttachmentPortAdapter implements AttachmentPort {
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv",
            "png", "jpg", "jpeg", "gif", "zip", "rar"
    );

    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;
    private final FilePreviewUrlProvider previewUrlProvider;

    public ProjectAttachmentPortAdapter(JdbcTemplate jdbc, MinioStorageService storage,
                                        FilePreviewUrlProvider previewUrlProvider) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.previewUrlProvider = previewUrlProvider;
    }

    @Override
    @Transactional
    public AttachmentItem uploadAndBind(String businessType, long businessId, MultipartFile file,
                                        long tenantId, long uploaderId) {
        validateScope(businessType, businessId, tenantId, uploaderId);
        FileData fileData = validateFile(file);
        String objectKey = "attachments/" + tenantId + "/" + businessType + "/" + businessId + "/"
                + UUID.randomUUID() + "." + fileData.extension();
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), fileData.contentType());
            long id = nextId();
            jdbc.update("INSERT INTO sys_attachment (id, tenant_id, business_type, business_id, file_name, content_type, file_size, object_key, uploader_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, tenantId, businessType, businessId, fileData.fileName(), fileData.contentType(),
                    file.getSize(), objectKey, uploaderId);
            return findRequired(id, businessType, businessId, tenantId);
        } catch (IOException exception) {
            deleteQuietly(objectKey);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取上传文件");
        } catch (RuntimeException exception) {
            deleteQuietly(objectKey);
            throw exception;
        }
    }

    @Override
    public PageResult<AttachmentItem> list(String businessType, long businessId, long tenantId,
                                           PageQuery pageQuery, String keyword) {
        validateBusinessScope(businessType, businessId, tenantId);
        PageQuery query = pageQuery == null ? new PageQuery(1, 20) : pageQuery;
        String normalizedKeyword = normalizeKeyword(keyword);
        StringBuilder where = new StringBuilder(
                " WHERE business_type = ? AND business_id = ? AND tenant_id = ? AND deleted = 0");
        List<Object> arguments = new ArrayList<>(List.of(businessType, businessId, tenantId));
        if (!normalizedKeyword.isBlank()) {
            where.append(" AND file_name LIKE ?");
            arguments.add("%" + normalizedKeyword + "%");
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment" + where,
                Long.class, arguments.toArray());
        long offset = Math.max(0L, (query.page() - 1L) * query.size());
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size());
        pageArguments.add(offset);
        List<AttachmentItem> records = jdbc.query(
                "SELECT id, file_name, content_type, file_size, uploader_id, created_at FROM sys_attachment"
                        + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapItem, pageArguments.toArray());
        return new PageResult<>(records, total == null ? 0L : total, query.page(), query.size());
    }

    @Override
    public AttachmentLink preview(long attachmentId, String businessType, long businessId, long tenantId) {
        AttachmentRow row = findRow(attachmentId, businessType, businessId, tenantId);
        return new AttachmentLink(row.id(), row.fileName(),
                previewUrlProvider.build(storage.presignedUrl(row.objectKey())));
    }

    @Override
    public AttachmentLink download(long attachmentId, String businessType, long businessId, long tenantId) {
        AttachmentRow row = findRow(attachmentId, businessType, businessId, tenantId);
        return new AttachmentLink(row.id(), row.fileName(), storage.presignedUrl(row.objectKey()));
    }

    @Override
    @Transactional
    public void delete(long attachmentId, String businessType, long businessId, long tenantId) {
        AttachmentRow row = findRow(attachmentId, businessType, businessId, tenantId);
        storage.delete(row.objectKey());
        jdbc.update("UPDATE sys_attachment SET deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND business_type = ? AND business_id = ? AND tenant_id = ? AND deleted = 0",
                attachmentId, businessType, businessId, tenantId);
    }

    private AttachmentItem findRequired(long id, String businessType, long businessId, long tenantId) {
        AttachmentItem item = jdbc.query(
                "SELECT id, file_name, content_type, file_size, uploader_id, created_at FROM sys_attachment WHERE id = ? AND business_type = ? AND business_id = ? AND tenant_id = ? AND deleted = 0",
                rs -> rs.next() ? new AttachmentItem(rs.getLong("id"), rs.getString("file_name"),
                        rs.getString("content_type"), rs.getLong("file_size"), rs.getLong("uploader_id"),
                        null, formatTimestamp(rs.getTimestamp("created_at"))) : null,
                id, businessType, businessId, tenantId);
        if (item == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件元数据保存失败");
        return item;
    }

    private AttachmentRow findRow(long id, String businessType, long businessId, long tenantId) {
        validateBusinessScope(businessType, businessId, tenantId);
        AttachmentRow row = jdbc.query(
                "SELECT id, file_name, object_key FROM sys_attachment WHERE id = ? AND business_type = ? AND business_id = ? AND tenant_id = ? AND deleted = 0",
                rs -> rs.next() ? new AttachmentRow(rs.getLong("id"), rs.getString("file_name"),
                        rs.getString("object_key")) : null,
                id, businessType, businessId, tenantId);
        if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        return row;
    }

    private FileData validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择非空文件");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能超过100MB");
        String fileName = normalizeFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持该文件类型");
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || contentType.length() > 255
                || contentType.contains("\r") || contentType.contains("\n")) {
            contentType = "application/octet-stream";
        }
        return new FileData(fileName, extension, contentType);
    }

    private String normalizeFileName(String original) {
        String value = original == null ? "" : original.trim().replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        if (value.isBlank() || value.length() > 255 || value.contains("\r") || value.contains("\n")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名无效");
        }
        return value;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        String value = keyword.trim();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private AttachmentItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new AttachmentItem(rs.getLong("id"), rs.getString("file_name"), rs.getString("content_type"),
                rs.getLong("file_size"), rs.getLong("uploader_id"), null,
                formatTimestamp(rs.getTimestamp("created_at")));
    }

    private void validateScope(String businessType, long businessId, long tenantId, long userId) {
        validateBusinessScope(businessType, businessId, tenantId);
        if (userId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "上传人无效");
    }

    private void validateBusinessScope(String businessType, long businessId, long tenantId) {
        if (businessType == null || !businessType.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件业务类型无效");
        }
        if (businessId <= 0 || tenantId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件业务范围无效");
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DATE_TIME);
    }

    private void deleteQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // Preserve the original upload failure if cleanup also fails.
        }
    }

    private record FileData(String fileName, String extension, String contentType) {
    }

    private record AttachmentRow(long id, String fileName, String objectKey) {
    }
}
