package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;

@Repository
public class WorkflowDefinitionSummaryRepository {
    private final WorkflowDefinitionSummaryMapper mapper;
    public WorkflowDefinitionSummaryRepository(WorkflowDefinitionSummaryMapper mapper) { this.mapper = mapper; }
    public int refresh(long definitionId, long tenantId, int versionNo, boolean requiresConfiguration) {
        return mapper.refresh(definitionId, tenantId, versionNo, requiresConfiguration);
    }
}
