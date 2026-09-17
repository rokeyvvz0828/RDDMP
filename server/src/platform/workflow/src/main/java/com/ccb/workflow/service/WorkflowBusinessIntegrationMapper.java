package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowBusinessIntegrationMapper {
    List<Map<String, Object>> publishedByCode(@Param("tenantId") long tenantId,
                                               @Param("code") String code,
                                               @Param("projectId") Long projectId);

    List<Map<String, Object>> publishedDefinitions(@Param("tenantId") long tenantId);

    List<Map<String, Object>> publishedById(@Param("tenantId") long tenantId,
                                            @Param("definitionId") long definitionId);

    long countMatchingInstance(@Param("tenantId") long tenantId,
                               @Param("instanceId") long instanceId,
                               @Param("businessType") String businessType,
                               @Param("businessKey") String businessKey,
                               @Param("businessRound") int businessRound);

    List<Map<String, Object>> instanceProgress(@Param("tenantId") long tenantId,
                                                @Param("instanceId") long instanceId);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowBusinessIntegrationMapper.class)
class WorkflowBusinessIntegrationMapperConfiguration {}
