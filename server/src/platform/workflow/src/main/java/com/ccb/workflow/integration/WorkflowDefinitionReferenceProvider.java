package com.ccb.workflow.integration;

import java.util.List;

public interface WorkflowDefinitionReferenceProvider {
    List<WorkflowDefinitionReference> activeReferences(long tenantId, long definitionId);
}
