package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowDefinitionSummaryMapper {
    int refresh(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId,
                @Param("versionNo") int versionNo, @Param("requiresConfiguration") boolean requiresConfiguration);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowDefinitionSummaryMapper.class)
class WorkflowDefinitionSummaryMapperConfiguration {}
