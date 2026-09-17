package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FlowableWorkflowMapper {
    int insertDefinition(Map<String, Object> params);
    int insertVersion(Map<String, Object> params);
    int updateDefinition(Map<String, Object> params);
    int updateVersion(Map<String, Object> params);
    int publishVersion(Map<String, Object> params);
    int publishDefinition(Map<String, Object> params);
    int insertDraftVersion(Map<String, Object> params);
    int unpublishDefinition(Map<String, Object> params);
    int insertInstance(Map<String, Object> params);
    int updateTaskAssignee(Map<String, Object> params);
    int updateTaskDecision(Map<String, Object> params);
    int cancelPendingTasks(Map<String, Object> params);
    int updateInstanceStatus(Map<String, Object> params);
    int insertApprovalTask(Map<String, Object> params);
    int insertCcTask(Map<String, Object> params);
    int insertTaskAction(Map<String, Object> params);
    int insertAddSignTask(Map<String, Object> params);
    List<Map<String, Object>> inbox(@Param("tenantId") long tenantId, @Param("userId") long userId);
    List<Map<String, Object>> instances(@Param("tenantId") long tenantId);
    List<Map<String, Object>> timeline(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    Map<String, Object> pendingTask(@Param("taskId") long taskId, @Param("tenantId") long tenantId, @Param("userId") long userId);
    Map<String, Object> definition(@Param("id") long id, @Param("tenantId") long tenantId);
    Map<String, Object> publishedDefinition(@Param("id") long id, @Param("tenantId") long tenantId);
    Map<String, Object> latestVersion(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    Map<String, Object> version(@Param("definitionId") long definitionId, @Param("versionNo") int versionNo, @Param("tenantId") long tenantId);
    Map<String, Object> instance(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    Map<String, Object> instanceByProcess(@Param("processInstanceId") String processInstanceId, @Param("tenantId") long tenantId);
    Map<String, Object> activeUser(@Param("id") long id, @Param("tenantId") long tenantId);
    long countInstance(@Param("id") long id, @Param("tenantId") long tenantId);
    int countTask(@Param("tenantId") long tenantId, @Param("flowableTaskId") String flowableTaskId, @Param("assigneeId") Long assigneeId);
    String instanceStatus(@Param("id") long id, @Param("tenantId") long tenantId);
}
