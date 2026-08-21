package com.ccb.release.window.model;

import java.time.LocalDateTime;

public record ReleaseWindow(
        long id, long tenantId, String windowCode, String windowName,
        String projectId, String projectCode, String projectName,
        LocalDateTime declarationStart, LocalDateTime declarationEnd,
        LocalDateTime productionStart, LocalDateTime productionEnd,
        boolean regularEnabled, String description, long rowVersion,
        long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
