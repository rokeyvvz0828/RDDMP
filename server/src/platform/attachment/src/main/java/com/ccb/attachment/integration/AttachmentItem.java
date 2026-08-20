package com.ccb.attachment.integration;

import java.time.LocalDateTime;

public record AttachmentItem(
        long id,
        String fileName,
        String contentType,
        long fileSize,
        String fileExtension,
        String status,
        String businessType,
        String businessKey,
        String projectRef,
        long uploaderId,
        LocalDateTime createdAt
) {
}
