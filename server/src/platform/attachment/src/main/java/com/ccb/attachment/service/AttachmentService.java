package com.ccb.attachment.service;

import com.ccb.attachment.config.AttachmentProperties;
import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.filepreview.model.FilePreviewUrlProvider;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AttachmentService implements AttachmentGateway {
    private final AttachmentPersistenceRepository repository;
    private final MinioStorageService storage;
    private final FilePreviewUrlProvider previewUrlProvider;
    private final AttachmentAccessPolicyRegistry policies;
    private final AttachmentProperties properties;

    public AttachmentService(AttachmentPersistenceRepository repository, MinioStorageService storage, FilePreviewUrlProvider previewUrlProvider,
                             AttachmentAccessPolicyRegistry policies, AttachmentProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.previewUrlProvider = previewUrlProvider;
        this.policies = policies;
        this.properties = properties;
    }

    @Transactional
    public AttachmentItem upload(MultipartFile file, AuthUser user) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择附件");
        if (file.getSize() > properties.getMaxFileSizeBytes()) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件大小超过限制");
        String fileName = safeFileName(file.getOriginalFilename());
        String extension = extension(fileName);
        String objectExtension = extension == null ? "bin" : extension;
        String objectKey = "attachments/" + user.tenantId() + "/" + UUID.randomUUID() + "." + objectExtension;
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件读取失败");
        }
        long id = nextId();
        repository.insertTemporaryFile(params("id", id, "tenantId", user.tenantId(), "fileName", fileName,
                "contentType", file.getContentType(), "fileSize", file.getSize(), "objectKey", objectKey,
                "extension", extension, "uploaderId", user.id(), "retentionSeconds", properties.getTempRetention().toSeconds()));
        audit(id, user, "UPLOAD", null, null, fileName);
        return get(id, user);
    }

    @Override
    @Transactional
    public void bind(AttachmentBindingCommand command, AuthUser operator) {
        if (command == null || command.attachmentId() <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不能为空");
        String businessType = requireText(command.businessType(), "业务类型", 64);
        String businessKey = requireText(command.businessKey(), "业务主键", 128);
        Map<String, Object> row = row(command.attachmentId(), operator.tenantId());
        if ("BOUND".equals(row.get("status"))) {
            if (businessType.equals(row.get("business_type")) && businessKey.equals(row.get("business_key"))) return;
            throw new BusinessException(ErrorCode.CONFLICT, "附件已绑定到其他业务");
        }
        if (!"TEMP".equals(row.get("status")) || ((Number) row.get("uploader_id")).longValue() != operator.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能绑定当前用户上传的临时附件");
        }
        int changed = repository.bindTemporaryFile(params("businessType", businessType, "businessKey", businessKey,
                "projectRef", optional(command.projectRef(), 64), "id", command.attachmentId(),
                "tenantId", operator.tenantId(), "uploaderId", operator.id()));
        if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "附件状态已变化，请刷新后重试");
        audit(command.attachmentId(), operator, "BIND", businessType, businessKey, null);
    }

    @Override
    public AttachmentItem get(long attachmentId, AuthUser operator) {
        Map<String, Object> row = authorizedRow(attachmentId, AttachmentOperation.READ, operator);
        return item(row);
    }

    public String preview(long attachmentId, AuthUser operator) {
        Map<String, Object> row = authorizedRow(attachmentId, AttachmentOperation.PREVIEW, operator);
        return previewUrlProvider.previewUrl(storage.presignedUrl(String.valueOf(row.get("object_key"))));
    }

    public String download(long attachmentId, AuthUser operator) {
        Map<String, Object> row = authorizedRow(attachmentId, AttachmentOperation.DOWNLOAD, operator);
        return storage.presignedUrl(String.valueOf(row.get("object_key")));
    }

    @Transactional
    public void deleteTemp(long attachmentId, AuthUser operator) {
        Map<String, Object> row = row(attachmentId, operator.tenantId());
        if (!"TEMP".equals(row.get("status")) || ((Number) row.get("uploader_id")).longValue() != operator.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除当前用户上传的临时附件");
        }
        markDeleted(row, operator, "DELETE_TEMP");
    }

    @Override
    @Transactional
    public void deleteBound(long attachmentId, String businessType, String businessKey, AuthUser operator) {
        Map<String, Object> row = row(attachmentId, operator.tenantId());
        if (!"BOUND".equals(row.get("status")) || !businessType.equals(row.get("business_type")) || !businessKey.equals(row.get("business_key"))) {
            throw new BusinessException(ErrorCode.CONFLICT, "附件业务绑定不匹配");
        }
        policies.requireAccess(businessType, businessKey, AttachmentOperation.DELETE, operator);
        markDeleted(row, operator, "DELETE_BOUND");
    }

    private void markDeleted(Map<String, Object> row, AuthUser operator, String operation) {
        long id = ((Number) row.get("id")).longValue();
        repository.markAttachmentFileDeleted(params("id", id, "tenantId", operator.tenantId()));
        audit(id, operator, operation, value(row.get("business_type")), value(row.get("business_key")), null);
    }

    private Map<String, Object> authorizedRow(long id, AttachmentOperation operation, AuthUser user) {
        Map<String, Object> row = row(id, user.tenantId());
        String status = String.valueOf(row.get("status"));
        if ("TEMP".equals(status)) {
            if (((Number) row.get("uploader_id")).longValue() != user.id()) throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该临时附件");
        } else if ("BOUND".equals(status)) {
            policies.requireAccess(String.valueOf(row.get("business_type")), String.valueOf(row.get("business_key")), operation, user);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或已删除");
        }
        return row;
    }

    Map<String, Object> row(long id, long tenantId) {
        Map<String, Object> row = repository.attachmentFile(params("id", id, "tenantId", tenantId));
        if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        return row;
    }

    private AttachmentItem item(Map<String, Object> row) {
        Object created = row.get("created_at");
        LocalDateTime createdAt = created instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : null;
        return new AttachmentItem(((Number) row.get("id")).longValue(), String.valueOf(row.get("file_name")), value(row.get("content_type")),
                ((Number) row.get("file_size")).longValue(), value(row.get("file_extension")), String.valueOf(row.get("status")),
                value(row.get("business_type")), value(row.get("business_key")), value(row.get("project_ref")),
                ((Number) row.get("uploader_id")).longValue(), createdAt);
    }

    private void audit(long attachmentId, AuthUser user, String operation, String businessType, String businessKey, String detail) {
        repository.insertAttachmentOperation(params("id", nextId(), "tenantId", user.tenantId(), "attachmentId", attachmentId,
                "operation", operation, "operatorId", user.id(), "businessType", businessType, "businessKey", businessKey, "detail", detail));
    }

    private String safeFileName(String name) {
        String value = name == null ? "attachment" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        return value.isEmpty() ? "attachment" : value.substring(0, Math.min(value.length(), 255));
    }
    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) return null;
        String extension = name.substring(index + 1).toLowerCase(Locale.ROOT);
        return extension.substring(0, Math.min(extension.length(), 32));
    }
    private String requireText(String value, String label, int max) { String normalized = value == null ? "" : value.trim(); if (normalized.isEmpty() || normalized.length() > max) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "无效"); return normalized; }
    private String optional(String value, int max) { return value == null || value.isBlank() ? null : value.trim().substring(0, Math.min(value.trim().length(), max)); }
    private String value(Object value) { return value == null ? null : String.valueOf(value); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }

    private Map<String, Object> params(Object... values) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) params.put(String.valueOf(values[index]), values[index + 1]);
        return params;
    }
}
