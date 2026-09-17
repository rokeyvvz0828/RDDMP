package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class WorkflowBusinessIntegrationRepository {
    private final WorkflowBusinessIntegrationMapper mapper;

    public WorkflowBusinessIntegrationRepository(WorkflowBusinessIntegrationMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> publishedByCode(long tenantId, String code, Long projectId) {
        return mapper.publishedByCode(tenantId, code, projectId);
    }

    public List<Map<String, Object>> publishedDefinitions(long tenantId) {
        return mapper.publishedDefinitions(tenantId);
    }

    public List<Map<String, Object>> publishedById(long tenantId, long definitionId) {
        return mapper.publishedById(tenantId, definitionId);
    }

    public long countMatchingInstance(long tenantId, long instanceId, String businessType, String businessKey, int businessRound) {
        return mapper.countMatchingInstance(tenantId, instanceId, businessType, businessKey, businessRound);
    }

    public List<Map<String, Object>> instanceProgress(long tenantId, long instanceId) {
        return mapper.instanceProgress(tenantId, instanceId);
    }
}
