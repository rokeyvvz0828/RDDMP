package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowMonitorMapper {
    List<Map<String,Object>> instances(Map<String,Object> params);
    long countInstances(Map<String,Object> params);
    List<Map<String,Object>> instancesSeek(Map<String,Object> params);
    Map<String,Object> detail(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    Map<String,Object> definitionJson(@Param("definitionId") Object definitionId, @Param("versionNo") Object versionNo, @Param("tenantId") long tenantId);
    List<Map<String,Object>> instanceStatus(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    int softDelete(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    Map<String,Object> runningInstance(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    int cancelTasks(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    int terminateInstance(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    List<Map<String,Object>> projectScope(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    List<Map<String,Object>> nodeStates(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    List<Map<String,Object>> timeline(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowMonitorMapper.class)
class WorkflowMonitorMapperConfiguration {}
