package com.ccb.workflow.integration;

import java.time.LocalDateTime;

public record WorkflowProgress(
        long instanceId,
        long definitionId,
        int definitionVersion,
        String status,
        WorkflowBusinessContext context,
        LocalDateTime createdAt
) {
}
