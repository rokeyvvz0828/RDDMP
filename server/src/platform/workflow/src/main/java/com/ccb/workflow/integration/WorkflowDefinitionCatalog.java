package com.ccb.workflow.integration;

import com.ccb.security.model.AuthUser;

import java.util.List;

public interface WorkflowDefinitionCatalog {
    List<WorkflowDefinitionSummary> publishedDefinitions(AuthUser operator);

    WorkflowDefinitionSummary requirePublished(long definitionId, AuthUser operator);
}
