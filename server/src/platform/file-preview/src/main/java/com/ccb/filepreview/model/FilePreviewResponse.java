package com.ccb.filepreview.model;

public record FilePreviewResponse(
        String previewId,
        String fileName,
        String contentType,
        long size,
        String previewUrl
) {
}
