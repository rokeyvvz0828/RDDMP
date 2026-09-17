package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowRuntimeMapper {
    int insertInstance(Map<String, Object> params);
    Map<String, Object> instanceSummary(Map<String, Object> params);
    int insertAddSignTask(Map<String, Object> params);
    int completeTask(Map<String, Object> params);
    int updateRunningInstanceStatus(Map<String, Object> params);
    Map<String, Object> pendingTask(Map<String, Object> params);
    Map<String, Object> versionJson(Map<String, Object> params);
    int insertApprovalTask(Map<String, Object> params);
    List<Map<String, Object>> instanceCompletionContext(Map<String, Object> params);
    List<Map<String, Object>> activeUsersByIds(Map<String, Object> params);
    List<Map<String, Object>> activeUsersByRoles(Map<String, Object> params);
    Map<String, Object> activeUser(Map<String, Object> params);
    int insertCcTask(Map<String, Object> params);
    int insertTaskAction(Map<String, Object> params);
    int cancelPendingTasks(Map<String, Object> params);
    int cancelSiblingTasks(Map<String, Object> params);
    int countPendingGroup(Map<String, Object> params);
    String instanceStatus(Map<String, Object> params);
    int insertOperationAudit(Map<String, Object> params);
}
