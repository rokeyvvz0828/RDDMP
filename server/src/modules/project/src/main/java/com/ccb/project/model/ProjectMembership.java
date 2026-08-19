package com.ccb.project.model;

import java.util.Set;

public record ProjectMembership(
        long projectId,
        long userId,
        String username,
        String displayName,
        ProjectRole role,
        Set<ProjectAction> allowedActions) {
    public ProjectMembership {
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
    }
}
