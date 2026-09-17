package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface WorkflowNodeLabelMapper {
    List<String> definitionJson(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowNodeLabelMapper.class)
class WorkflowNodeLabelMapperConfiguration {}
