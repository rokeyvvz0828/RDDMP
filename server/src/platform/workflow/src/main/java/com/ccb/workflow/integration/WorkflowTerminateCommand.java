package com.ccb.workflow.integration;

public record WorkflowTerminateCommand(
        long instanceId,
        String businessType,
        String businessKey,
        int businessRound,
        String reason
) {
}
