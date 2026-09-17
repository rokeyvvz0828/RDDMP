package com.ccb.boot.persistence;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class BootWorkflowRepository {
    private final BootWorkflowMapper mapper;

    public BootWorkflowRepository(BootWorkflowMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> pendingTask(Map<String, Object> params) { return mapper.pendingTask(params); }
    public List<Map<String, Object>> pendingTasks(Map<String, Object> params) { return mapper.pendingTasks(params); }
    public List<Map<String, Object>> missingPendingNotifications() { return mapper.missingPendingNotifications(); }
    public List<Long> instanceStarters(Map<String, Object> params) { return mapper.instanceStarters(params); }
    public Map<String, Object> activeOperator(Map<String, Object> params) { return mapper.activeOperator(params); }
    public List<Map<String, Object>> seededDefinitions(Map<String, Object> params) { return mapper.seededDefinitions(params); }
}
