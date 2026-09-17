package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class FlowableWorkflowRepository {
    private final FlowableWorkflowMapper mapper;

    public FlowableWorkflowRepository(FlowableWorkflowMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> inbox(long tenantId, long userId) { return mapper.inbox(tenantId, userId); }
    public int insertDefinition(Map<String, Object> params) { return mapper.insertDefinition(params); }
    public int insertVersion(Map<String, Object> params) { return mapper.insertVersion(params); }
    public int updateDefinition(Map<String, Object> params) { return mapper.updateDefinition(params); }
    public int updateVersion(Map<String, Object> params) { return mapper.updateVersion(params); }
    public int publishVersion(Map<String, Object> params) { return mapper.publishVersion(params); }
    public int publishDefinition(Map<String, Object> params) { return mapper.publishDefinition(params); }
    public int insertDraftVersion(Map<String, Object> params) { return mapper.insertDraftVersion(params); }
    public int unpublishDefinition(Map<String, Object> params) { return mapper.unpublishDefinition(params); }
    public int insertInstance(Map<String, Object> params) { return mapper.insertInstance(params); }
    public int updateTaskAssignee(Map<String, Object> params) { return mapper.updateTaskAssignee(params); }
    public int updateTaskDecision(Map<String, Object> params) { return mapper.updateTaskDecision(params); }
    public int cancelPendingTasks(Map<String, Object> params) { return mapper.cancelPendingTasks(params); }
    public int updateInstanceStatus(Map<String, Object> params) { return mapper.updateInstanceStatus(params); }
    public int insertApprovalTask(Map<String, Object> params) { return mapper.insertApprovalTask(params); }
    public int insertCcTask(Map<String, Object> params) { return mapper.insertCcTask(params); }
    public int insertTaskAction(Map<String, Object> params) { return mapper.insertTaskAction(params); }
    public int insertAddSignTask(Map<String, Object> params) { return mapper.insertAddSignTask(params); }
    public List<Map<String, Object>> instances(long tenantId) { return mapper.instances(tenantId); }
    public List<Map<String, Object>> timeline(long instanceId, long tenantId) { return mapper.timeline(instanceId, tenantId); }
    public Map<String, Object> pendingTask(long taskId, long tenantId, long userId) { return mapper.pendingTask(taskId, tenantId, userId); }
    public Map<String, Object> definition(long id, long tenantId) { return mapper.definition(id, tenantId); }
    public Map<String, Object> publishedDefinition(long id, long tenantId) { return mapper.publishedDefinition(id, tenantId); }
    public Map<String, Object> latestVersion(long definitionId, long tenantId) { return mapper.latestVersion(definitionId, tenantId); }
    public Map<String, Object> version(long definitionId, int versionNo, long tenantId) { return mapper.version(definitionId, versionNo, tenantId); }
    public Map<String, Object> instance(long instanceId, long tenantId) { return mapper.instance(instanceId, tenantId); }
    public Map<String, Object> instanceByProcess(String processInstanceId, long tenantId) { return mapper.instanceByProcess(processInstanceId, tenantId); }
    public Map<String, Object> activeUser(long id, long tenantId) { return mapper.activeUser(id, tenantId); }
    public long countInstance(long id, long tenantId) { return mapper.countInstance(id, tenantId); }
    public int countTask(long tenantId, String flowableTaskId, Long assigneeId) { return mapper.countTask(tenantId, flowableTaskId, assigneeId); }
    public String instanceStatus(long id, long tenantId) { return mapper.instanceStatus(id, tenantId); }
}
