package com.ccb.workflow.integration;

public record WorkflowTaskAssignedEvent(
        long tenantId,
        long instanceId,
        long taskId,
        long assigneeId,
        long operatorId) {
}
