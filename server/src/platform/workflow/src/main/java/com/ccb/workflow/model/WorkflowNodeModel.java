package com.ccb.workflow.model;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowNodeModel(
        String id,
        String type,
        String label,
        WorkflowPosition position,
        JsonNode config
) {
    public WorkflowNodeModel {
        position = position == null ? new WorkflowPosition(0, 0) : position;
        config = config == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : config;
    }
}
