package com.ccb.project.model;

import java.util.Set;

public record ProjectSummary(
        long id,
        String projectCode,
        String projectName,
        ProjectStatus status,
        long ownerUserId,
        String ownerDisplayName,
        ProjectRole currentRole,
        Set<ProjectAction> allowedActions,
        long version) {
    public ProjectSummary {
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
    }
}
