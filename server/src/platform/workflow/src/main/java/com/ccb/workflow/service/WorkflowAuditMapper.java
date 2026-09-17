package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowAuditMapper {
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("definitionId") Long definitionId, @Param("versionNo") Integer versionNo,
               @Param("instanceId") Long instanceId, @Param("taskId") Long taskId, @Param("eventType") String eventType,
               @Param("operatorId") Long operatorId, @Param("reason") String reason, @Param("payloadJson") String payloadJson);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowAuditMapper.class)
class WorkflowAuditMapperConfiguration {}
