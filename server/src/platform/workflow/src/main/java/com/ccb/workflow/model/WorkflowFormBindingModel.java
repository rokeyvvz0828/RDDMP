package com.ccb.workflow.model;

public record WorkflowFormBindingModel(
        String nodeId,
        String fieldName,
        String variableName,
        boolean required
) {
}
