package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementDifferenceMapper {
    long count(@Param("p") Map<String, Object> p);
    List<Map<String, Object>> page(@Param("p") Map<String, Object> p);
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("id") long id);
    int insert(@Param("p") Map<String, Object> p);
    int update(@Param("p") Map<String, Object> p);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("operatorId") long operatorId);
    Map<String, Object> project(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int submitReview(@Param("tenantId") long tenantId, @Param("id") long id, @Param("report") String report, @Param("instanceId") long instanceId, @Param("operatorId") long operatorId);
    int cancelReview(@Param("tenantId") long tenantId, @Param("id") long id, @Param("reason") String reason, @Param("operatorId") long operatorId);
    Map<String, Object> activeUser(@Param("tenantId") long tenantId, @Param("userId") long userId);
    int transfer(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId, @Param("userName") String userName, @Param("operatorId") long operatorId);
    int insertFlow(@Param("p") Map<String, Object> p);
    List<Long> runningInstanceIds(@Param("tenantId") long tenantId, @Param("businessKey") String businessKey);
    int completePendingTasks(@Param("tenantId") long tenantId, @Param("instanceId") long instanceId);
    int terminateInstance(@Param("tenantId") long tenantId, @Param("instanceId") long instanceId);
    int clearWorkflowInstance(@Param("tenantId") long tenantId, @Param("id") long id);
    List<Map<String, Object>> reviewers(@Param("tenantId") long tenantId);
    List<Map<String, Object>> userOptions(@Param("tenantId") long tenantId, @Param("keyword") String keyword);
    Long publishedDefinitionId(@Param("tenantId") long tenantId, @Param("code") String code);
    List<Map<String, Object>> approvalLogs(@Param("tenantId") long tenantId, @Param("instanceId") long instanceId);
    int systemCount(@Param("tenantId") long tenantId, @Param("systemId") long systemId);
    Long maxSequence(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
}
