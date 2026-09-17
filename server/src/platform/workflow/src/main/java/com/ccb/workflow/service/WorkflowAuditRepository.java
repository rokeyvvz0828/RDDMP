package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;

@Repository
public class WorkflowAuditRepository {
    private final WorkflowAuditMapper mapper;
    public WorkflowAuditRepository(WorkflowAuditMapper mapper) { this.mapper = mapper; }
    public void insert(long id, long tenantId, Long definitionId, Integer versionNo, Long instanceId, Long taskId,
                       String eventType, Long operatorId, String reason, String payloadJson) {
        mapper.insert(id, tenantId, definitionId, versionNo, instanceId, taskId, eventType, operatorId, reason, payloadJson);
    }
}
