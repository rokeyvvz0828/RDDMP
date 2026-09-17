package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowSignatureMapper {
    List<Map<String, Object>> signatures(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowSignatureMapper.class)
class WorkflowSignatureMapperConfiguration {}
