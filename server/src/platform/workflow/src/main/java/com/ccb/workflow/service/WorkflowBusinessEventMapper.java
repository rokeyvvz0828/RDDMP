package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowBusinessEventMapper {
    List<Map<String,Object>> selectDeliveries(@Param("tenantId") long tenantId, @Param("status") String status, @Param("offset") long offset, @Param("size") long size);
    long countDeliveries(@Param("tenantId") long tenantId, @Param("status") String status);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowBusinessEventMapper.class)
class WorkflowBusinessEventMapperConfiguration {}
