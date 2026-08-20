package com.ccb.workflow.integration;

import java.util.Map;

public record WorkflowStartCommand(
        String definitionCode,
        WorkflowBusinessContext context,
        Map<String, Object> variables
) {
    public WorkflowStartCommand {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
