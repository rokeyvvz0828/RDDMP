package com.ccb.attachment.model;

/** 持久附件元数据，不包含 MinIO 对象键。 */
public record AttachmentItem(
        long id,
        String fileName,
        String contentType,
        long size,
        long uploaderId,
        String uploaderName,
        String createdAt
) {
}
