package com.ccb.release.window.model;

import java.time.LocalDateTime;

public record CreateReleaseWindowRequest(
        String windowName, String projectId, String projectCode, String projectName,
        LocalDateTime declarationStart, LocalDateTime declarationEnd,
        LocalDateTime productionStart, LocalDateTime productionEnd,
        Boolean regularEnabled, String description
) {
}
