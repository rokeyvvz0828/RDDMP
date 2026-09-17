package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class WorkflowRuntimeRepository {
    private final WorkflowRuntimeMapper mapper;

    public WorkflowRuntimeRepository(WorkflowRuntimeMapper mapper) { this.mapper = mapper; }
    public int insertInstance(Map<String, Object> params) { return mapper.insertInstance(params); }
    public Map<String, Object> instanceSummary(Map<String, Object> params) { return mapper.instanceSummary(params); }
    public int insertAddSignTask(Map<String, Object> params) { return mapper.insertAddSignTask(params); }
    public int completeTask(Map<String, Object> params) { return mapper.completeTask(params); }
    public int updateRunningInstanceStatus(Map<String, Object> params) { return mapper.updateRunningInstanceStatus(params); }
    public Map<String, Object> pendingTask(Map<String, Object> params) { return mapper.pendingTask(params); }
    public Map<String, Object> versionJson(Map<String, Object> params) { return mapper.versionJson(params); }
    public int insertApprovalTask(Map<String, Object> params) { return mapper.insertApprovalTask(params); }
    public List<Map<String, Object>> instanceCompletionContext(Map<String, Object> params) { return mapper.instanceCompletionContext(params); }
    public List<Map<String, Object>> activeUsersByIds(Map<String, Object> params) { return mapper.activeUsersByIds(params); }
    public List<Map<String, Object>> activeUsersByRoles(Map<String, Object> params) { return mapper.activeUsersByRoles(params); }
    public Map<String, Object> activeUser(Map<String, Object> params) { return mapper.activeUser(params); }
    public int insertCcTask(Map<String, Object> params) { return mapper.insertCcTask(params); }
    public int insertTaskAction(Map<String, Object> params) { return mapper.insertTaskAction(params); }
    public int cancelPendingTasks(Map<String, Object> params) { return mapper.cancelPendingTasks(params); }
    public int cancelSiblingTasks(Map<String, Object> params) { return mapper.cancelSiblingTasks(params); }
    public int countPendingGroup(Map<String, Object> params) { return mapper.countPendingGroup(params); }
    public String instanceStatus(Map<String, Object> params) { return mapper.instanceStatus(params); }
    public int insertOperationAudit(Map<String, Object> params) { return mapper.insertOperationAudit(params); }
}
