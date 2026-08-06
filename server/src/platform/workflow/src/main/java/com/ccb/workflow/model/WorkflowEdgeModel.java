package com.ccb.workflow.model;

public record WorkflowEdgeModel(
        String id,
        String source,
        String target,
        String label,
        String condition,
        boolean defaultFlow
) {
}
