package com.ccb.attachment.service;

import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentCategory;
import com.ccb.attachment.model.AttachmentLink;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.filepreview.model.FilePreviewUrlProvider;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final AttachmentPersistenceRepository repository;
    private final MinioStorageService storage;
    private final FilePreviewUrlProvider previewUrlProvider;

    public ProjectAttachmentPortAdapter(AttachmentPersistenceRepository repository, MinioStorageService storage,
                                        FilePreviewUrlProvider previewUrlProvider) {
        this.repository = repository;
        this.storage = storage;
        this.previewUrlProvider = previewUrlProvider;
    }

    @Override
    @Transactional
    public AttachmentItem uploadAndBind(String businessType, long businessId, MultipartFile file,
                                        Long categoryId, long tenantId, long uploaderId) {
        validateScope(businessType, businessId, tenantId, uploaderId);
        Long normalizedCategoryId = validateCategory(categoryId, businessType, businessId, tenantId);
        FileData fileData = validateFile(file);
        String objectKey = "attachments/" + tenantId + "/" + businessType + "/" + businessId + "/"
                + UUID.randomUUID() + "." + fileData.extension();
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), fileData.contentType());
            long id = nextId();
            repository.insertProjectAttachment(params("id", id, "tenantId", tenantId, "businessType", businessType,
                    "businessId", businessId, "categoryId", normalizedCategoryId, "fileName", fileData.fileName(),
                    "contentType", fileData.contentType(), "fileSize", file.getSize(), "objectKey", objectKey, "uploaderId", uploaderId));
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
    public List<AttachmentCategory> listCategories(String businessType, long businessId, long tenantId) {
        validateBusinessScope(businessType, businessId, tenantId);
        return repository.attachmentCategories(params("businessType", businessType, "businessId", businessId, "tenantId", tenantId))
                .stream().map(row -> new AttachmentCategory(((Number) row.get("id")).longValue(),
                        String.valueOf(row.get("category_name")), ((Number) row.get("sort_no")).intValue())).toList();
    }

    @Override
    @Transactional
    public AttachmentCategory createCategory(String businessType, long businessId, String name,
                                             long tenantId, long creatorId) {
        validateScope(businessType, businessId, tenantId, creatorId);
        String normalizedName = normalizeCategoryName(name);
        Integer existing = repository.countAttachmentCategoryByName(params("businessType", businessType,
                "businessId", businessId, "tenantId", tenantId, "categoryName", normalizedName));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该附件分类已存在");
        }
        long id = nextId();
        Integer maxSortNo = repository.maxAttachmentCategorySort(params("businessType", businessType, "businessId", businessId, "tenantId", tenantId));
        repository.insertAttachmentCategory(params("id", id, "tenantId", tenantId, "businessType", businessType,
                "businessId", businessId, "categoryName", normalizedName, "sortNo", (maxSortNo == null ? 0 : maxSortNo) + 1, "creatorId", creatorId));
        return new AttachmentCategory(id, normalizedName, (maxSortNo == null ? 0 : maxSortNo) + 1);
    }

    @Override
    @Transactional
    public AttachmentItem updateCategory(long attachmentId, String businessType, long businessId,
                                         Long categoryId, long tenantId) {
        validateBusinessScope(businessType, businessId, tenantId);
        Long normalizedCategoryId = validateCategory(categoryId, businessType, businessId, tenantId);
        findRow(attachmentId, businessType, businessId, tenantId);
        int changed = repository.updateProjectAttachmentCategory(params("categoryId", normalizedCategoryId, "id", attachmentId,
                "businessType", businessType, "businessId", businessId, "tenantId", tenantId));
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        return findRequired(attachmentId, businessType, businessId, tenantId);
    }

    @Override
    public PageResult<AttachmentItem> list(String businessType, long businessId, long tenantId,
                                           PageQuery pageQuery, String keyword, Long categoryId) {
        validateBusinessScope(businessType, businessId, tenantId);
        PageQuery query = pageQuery == null ? new PageQuery(1, 20) : pageQuery;
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedCategoryId = categoryId;
        if (categoryId != null) {
            if (categoryId != 0) {
                validateCategory(categoryId, businessType, businessId, tenantId);
            }
        }
        long offset = Math.max(0L, (query.page() - 1L) * query.size());
        Map<String, Object> params = params("businessType", businessType, "businessId", businessId, "tenantId", tenantId,
                "keyword", normalizedKeyword, "categoryId", normalizedCategoryId, "size", query.size(), "offset", offset);
        Long total = repository.countProjectAttachments(params);
        List<AttachmentItem> records = repository.projectAttachments(params).stream().map(this::item).toList();
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
        repository.deleteProjectAttachment(params("id", attachmentId, "businessType", businessType, "businessId", businessId, "tenantId", tenantId));
    }

    @Override
    @Transactional
    public void enqueueBusinessDeletion(String businessType, long businessId, long tenantId) {
        validateBusinessScope(businessType, businessId, tenantId);
        Map<String, Object> params = params("businessType", businessType, "businessId", businessId, "tenantId", tenantId);
        repository.enqueueProjectAttachmentDeletion(params);
        repository.deleteProjectAttachments(params);
    }

    private AttachmentItem findRequired(long id, String businessType, long businessId, long tenantId) {
        Map<String, Object> row = repository.projectAttachment(params("id", id, "businessType", businessType,
                "businessId", businessId, "tenantId", tenantId));
        AttachmentItem item = row == null ? null : item(row);
        if (item == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件元数据保存失败");
        return item;
    }

    private AttachmentRow findRow(long id, String businessType, long businessId, long tenantId) {
        validateBusinessScope(businessType, businessId, tenantId);
        Map<String, Object> row = repository.projectAttachment(params("id", id, "businessType", businessType,
                "businessId", businessId, "tenantId", tenantId));
        if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        return new AttachmentRow(((Number) row.get("id")).longValue(), String.valueOf(row.get("file_name")), String.valueOf(row.get("object_key")));
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

    private AttachmentItem item(Map<String, Object> row) {
        Object category = row.get("category_id");
        Object created = row.get("created_at");
        return new AttachmentItem(((Number) row.get("id")).longValue(), String.valueOf(row.get("file_name")),
                String.valueOf(row.get("content_type")), ((Number) row.get("file_size")).longValue(),
                ((Number) row.get("uploader_id")).longValue(), null,
                created instanceof Timestamp timestamp ? formatTimestamp(timestamp) : null,
                category instanceof Number number ? number.longValue() : null, String.valueOf(row.get("category_name")));
    }

    private Long validateCategory(Long categoryId, String businessType, long businessId, long tenantId) {
        if (categoryId == null) return null;
        if (categoryId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件分类编号无效");
        Integer count = repository.countAttachmentCategory(params("categoryId", categoryId, "businessType", businessType,
                "businessId", businessId, "tenantId", tenantId));
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件分类不存在");
        return categoryId;
    }

    private String normalizeCategoryName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank() || value.length() > 128 || value.contains("\r") || value.contains("\n")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件分类名称无效");
        }
        if ("未分类".equals(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未分类为系统保留分类");
        }
        return value;
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

    private Map<String, Object> params(Object... values) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) params.put(String.valueOf(values[index]), values[index + 1]);
        return params;
    }

    private record FileData(String fileName, String extension, String contentType) {
    }

    private record AttachmentRow(long id, String fileName, String objectKey) {
    }
}
