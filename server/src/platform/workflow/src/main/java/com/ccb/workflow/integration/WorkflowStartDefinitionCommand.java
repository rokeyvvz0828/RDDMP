package com.ccb.workflow.integration;

import java.util.Map;

public record WorkflowStartDefinitionCommand(
        long definitionId,
        WorkflowBusinessContext context,
        Map<String, Object> variables
) {
}
