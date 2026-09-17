package com.ccb.boot.persistence;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface BootWorkflowMapper {
    List<Map<String, Object>> pendingTask(Map<String, Object> params);
    List<Map<String, Object>> pendingTasks(Map<String, Object> params);
    List<Map<String, Object>> missingPendingNotifications();
    List<Long> instanceStarters(Map<String, Object> params);
    Map<String, Object> activeOperator(Map<String, Object> params);
    List<Map<String, Object>> seededDefinitions(Map<String, Object> params);
}
