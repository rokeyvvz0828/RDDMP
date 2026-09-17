package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowLifecycleEventMapper {
    List<Map<String, Object>> instanceContext(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId);
    int insertEvent(Map<String, Object> values);
    int insertDelivery(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("eventId") String eventId, @Param("subscriberKey") String subscriberKey);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowLifecycleEventMapper.class)
class WorkflowLifecycleEventMapperConfiguration {}
