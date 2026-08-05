package com.ccb.workflow.model;

import java.util.List;
import java.util.Map;

public record WorkflowDefinitionModel(
        int schemaVersion,
        List<WorkflowNodeModel> nodes,
        List<WorkflowEdgeModel> edges,
        List<WorkflowVariableModel> variables,
        List<WorkflowFormBindingModel> formBindings,
        Map<String, WorkflowActionPolicy> actionPolicies
) {
    public WorkflowDefinitionModel {
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        variables = List.copyOf(variables == null ? List.of() : variables);
        formBindings = List.copyOf(formBindings == null ? List.of() : formBindings);
        actionPolicies = Map.copyOf(actionPolicies == null ? Map.of() : actionPolicies);
    }
}
