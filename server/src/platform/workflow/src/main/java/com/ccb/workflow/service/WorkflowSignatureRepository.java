package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowSignatureRepository {
    private final WorkflowSignatureMapper mapper;
    public WorkflowSignatureRepository(WorkflowSignatureMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> signatures(long instanceId, long tenantId) { return mapper.signatures(instanceId, tenantId); }
}
