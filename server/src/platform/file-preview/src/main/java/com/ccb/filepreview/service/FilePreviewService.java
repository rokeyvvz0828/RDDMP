package com.ccb.filepreview.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.filepreview.config.FilePreviewProperties;
import com.ccb.filepreview.model.FilePreviewCapabilities;
import com.ccb.filepreview.model.FilePreviewResponse;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FilePreviewService {
    private static final String OBJECT_PREFIX = "file-preview/";
    private static final Pattern PREVIEW_ID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{1,12}$");

    private final FilePreviewProperties properties;
    private final MinioStorageService storageService;
    private final KkFileViewUrlBuilder urlBuilder;

    public FilePreviewService(FilePreviewProperties properties, MinioStorageService storageService,
                              KkFileViewUrlBuilder urlBuilder) {
        this.properties = properties;
        this.storageService = storageService;
        this.urlBuilder = urlBuilder;
    }

    public FilePreviewResponse upload(MultipartFile file) {
        ensureEnabled();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Please select a non-empty file");
        }
        if (properties.getMaxFileSizeBytes() <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invalid file preview size configuration");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File exceeds the preview size limit");
        }

        String fileName = normalizedFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!allowedExtensions().contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "This file type is not allowed for preview");
        }

        String previewId = UUID.randomUUID() + "." + extension;
        String objectKey = OBJECT_PREFIX + previewId;
        String contentType = normalizedContentType(file.getContentType());
        try {
            storageService.put(objectKey, file.getInputStream(), file.getSize(), contentType);
            String previewUrl = urlBuilder.build(storageService.presignedUrl(objectKey));
            return new FilePreviewResponse(previewId, fileName, contentType, file.getSize(), previewUrl);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read the selected file");
        } catch (RuntimeException exception) {
            try {
                storageService.delete(objectKey);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public FilePreviewCapabilities capabilities() {
        return new FilePreviewCapabilities(
                properties.isEnabled(),
                properties.getMaxFileSizeBytes(),
                allowedExtensions()
        );
    }

    public void delete(String previewId) {
        ensureEnabled();
        if (previewId == null || !PREVIEW_ID_PATTERN.matcher(previewId).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid preview file identifier");
        }
        storageService.delete(OBJECT_PREFIX + previewId);
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File preview is not enabled");
        }
    }

    private Set<String> allowedExtensions() {
        return properties.getAllowedExtensions().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizedFileName(String originalFilename) {
        String fileName = originalFilename == null ? "" : originalFilename.trim();
        fileName = fileName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        if (fileName.isBlank() || fileName.length() > 255 || fileName.indexOf('\r') >= 0 || fileName.indexOf('\n') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid file name");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator <= 0 || separator == fileName.length() - 1) return "";
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || contentType.length() > 255
                || contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            return "application/octet-stream";
        }
        return contentType;
    }
}
