package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RequirementBaselineRepository {
    private final RequirementBaselineMapper mapper;
    public RequirementBaselineRepository(RequirementBaselineMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> list(long tenantId, long projectId) { return mapper.list(tenantId, projectId); }
    public Map<String, Object> find(long tenantId, long baselineId) { return mapper.findBaseline(tenantId, baselineId); }
    public List<Map<String, Object>> items(long tenantId, long baselineId) { return mapper.items(tenantId, baselineId); }
    public Map<String, Object> project(long tenantId, long projectId) { return mapper.project(tenantId, projectId); }
    public long pendingDifferenceCount(long tenantId, long projectId) { return mapper.pendingDifferenceCount(tenantId, projectId); }
    public List<Map<String, Object>> reviewedDifferences(long tenantId, long projectId) { return mapper.reviewedDifferences(tenantId, projectId); }
    public long baselineCount(long tenantId, long projectId) { return mapper.baselineCount(tenantId, projectId); }
    public void insertBaseline(Map<String, Object> values) { mapper.insertBaseline(values); }
    public void insertItem(Map<String, Object> values) { mapper.insertItem(values); }
    public void assignDifference(long baselineId, long operatorId, long tenantId, long differenceId) { mapper.assignDifference(baselineId, operatorId, tenantId, differenceId); }
    public void markProjectBaselined(long operatorId, long tenantId, long projectId) { mapper.markProjectBaselined(operatorId, tenantId, projectId); }
}
