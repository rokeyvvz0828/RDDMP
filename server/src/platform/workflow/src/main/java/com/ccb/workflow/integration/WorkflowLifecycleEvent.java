package com.ccb.workflow.integration;

import java.time.LocalDateTime;

public record WorkflowLifecycleEvent(
        String eventId,
        long tenantId,
        long instanceId,
        WorkflowLifecycleEventType eventType,
        WorkflowBusinessContext context,
        long operatorId,
        LocalDateTime occurredAt
) {
}
