package com.ccb.workflow.integration;

public record WorkflowStartResult(
        long instanceId,
        long definitionId,
        int definitionVersion,
        String status,
        WorkflowBusinessContext context
) {
}
