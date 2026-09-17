package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowLifecycleDispatcherMapper {
    List<Map<String, Object>> pending(@Param("limit") int limit);
    List<Map<String, Object>> pendingEvent(@Param("eventId") String eventId);
    int retryDead(@Param("tenantId") long tenantId, @Param("eventId") String eventId, @Param("subscriberKey") String subscriberKey);
    int markDelivered(@Param("id") long id, @Param("attempts") int attempts);
    int claim(@Param("id") long id);
    int recoverStale(@Param("before") Timestamp before);
    Map<String, Object> loadEvent(@Param("eventId") String eventId, @Param("tenantId") long tenantId);
    int markDead(@Param("id") long id, @Param("attempts") int attempts, @Param("message") String message);
    int markPending(@Param("id") long id, @Param("attempts") int attempts, @Param("message") String message);
    int markRetry(@Param("id") long id, @Param("attempts") int attempts, @Param("message") String message, @Param("backoffMinutes") int backoffMinutes);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowLifecycleDispatcherMapper.class)
class WorkflowLifecycleDispatcherMapperConfiguration {}
