package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class WorkflowNodeLabelRepository {
    private final WorkflowNodeLabelMapper mapper;
    public WorkflowNodeLabelRepository(WorkflowNodeLabelMapper mapper) { this.mapper = mapper; }
    public List<String> definitionJson(long instanceId, long tenantId) { return mapper.definitionJson(instanceId, tenantId); }
}
