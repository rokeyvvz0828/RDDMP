package com.ccb.workflow.model;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowVariableModel(
        String name,
        String type,
        boolean required,
        JsonNode defaultValue,
        String scope
) {
}
