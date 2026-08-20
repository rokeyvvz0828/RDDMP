package com.ccb.attachment.integration;

public record AttachmentBindingCommand(
        long attachmentId,
        String businessType,
        String businessKey,
        String projectRef
) {
}
