package com.ccb.release.window.model;

import java.time.LocalDateTime;

public record ReleaseWindowResponse(
        long id, String windowCode, String windowName,
        String projectId, String projectCode, String projectName,
        LocalDateTime declarationStart, LocalDateTime declarationEnd,
        LocalDateTime productionStart, LocalDateTime productionEnd,
        boolean regularEnabled, String description,
        String status, String statusLabel, boolean regularApplicationSelectable, String unavailableReason,
        long rowVersion, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
