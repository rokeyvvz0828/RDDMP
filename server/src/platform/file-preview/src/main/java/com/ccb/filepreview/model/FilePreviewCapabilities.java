package com.ccb.filepreview.model;

import java.util.Set;

public record FilePreviewCapabilities(
        boolean enabled,
        long maxFileSizeBytes,
        Set<String> allowedExtensions
) {
}
