package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.Map;

@Repository
public class WorkflowTaskQueryRepository {
    private final WorkflowTaskQueryMapper mapper;
    public WorkflowTaskQueryRepository(WorkflowTaskQueryMapper mapper) { this.mapper = mapper; }
    public long pendingTasks(long tenantId, long projectId, long userId) { return mapper.pendingTasks(tenantId, projectId, userId); }
    public Map<String, Object> instanceProject(long instanceId, long tenantId) { return mapper.instanceProject(instanceId, tenantId); }
    public Map<String, Object> variables(long instanceId, long tenantId) { return mapper.variables(instanceId, tenantId); }
    public Long latestDraftVersion(long definitionId, long tenantId) { return mapper.latestDraftVersion(definitionId, tenantId); }
    public Map<String, Object> taskContext(long taskId, long tenantId) { return mapper.taskContext(taskId, tenantId); }
    public Map<String, Object> currentTask(long tenantId, long userId, String businessType, String businessKey) { return mapper.currentTask(tenantId, userId, businessType, businessKey); }
    public int flowableTaskCount(long taskId, long tenantId) { return mapper.flowableTaskCount(taskId, tenantId); }
    public Map<String, Object> versionJson(Object definitionId, Object versionNo, Object tenantId) { return mapper.versionJson(definitionId, versionNo, tenantId); }
    public String instanceStatus(long instanceId, long tenantId) { return mapper.instanceStatus(instanceId, tenantId); }
}
