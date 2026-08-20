package com.ccb.workflow.integration;

public record WorkflowDefinitionReference(
        String source,
        String businessType,
        String businessKey,
        String displayName
) {
}
