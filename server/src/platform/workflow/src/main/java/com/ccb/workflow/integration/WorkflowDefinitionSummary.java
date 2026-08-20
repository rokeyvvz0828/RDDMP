package com.ccb.workflow.integration;

public record WorkflowDefinitionSummary(
        long definitionId,
        String code,
        String name,
        int versionNo
) {
}
