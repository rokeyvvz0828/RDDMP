package com.ccb.workflow.model;

import java.util.LinkedHashSet;
import java.util.Set;

public record WorkflowActionPolicy(Set<String> allowedActions) {
    public static final Set<String> KNOWN_ACTIONS = Set.of(
            "APPROVE", "REJECT", "RETURN", "TRANSFER", "DELEGATE", "ADD_SIGN", "REMOVE_SIGN", "CC"
    );

    public WorkflowActionPolicy {
        allowedActions = Set.copyOf(allowedActions == null ? Set.of() : new LinkedHashSet<>(allowedActions));
    }

    public static WorkflowActionPolicy defaults() {
        return new WorkflowActionPolicy(KNOWN_ACTIONS);
    }
}
