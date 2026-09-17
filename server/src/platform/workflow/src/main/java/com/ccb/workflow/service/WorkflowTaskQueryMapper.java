package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface WorkflowTaskQueryMapper {
    long pendingTasks(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("userId") long userId);
    Map<String, Object> instanceProject(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    Map<String, Object> variables(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    Long latestDraftVersion(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    Map<String, Object> taskContext(@Param("taskId") long taskId, @Param("tenantId") long tenantId);
    Map<String, Object> currentTask(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("businessType") String businessType, @Param("businessKey") String businessKey);
    int flowableTaskCount(@Param("taskId") long taskId, @Param("tenantId") long tenantId);
    Map<String, Object> versionJson(@Param("definitionId") Object definitionId, @Param("versionNo") Object versionNo, @Param("tenantId") Object tenantId);
    String instanceStatus(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
}
