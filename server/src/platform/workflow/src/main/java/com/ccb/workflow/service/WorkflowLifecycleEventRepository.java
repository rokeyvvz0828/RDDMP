package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class WorkflowLifecycleEventRepository {
    private final WorkflowLifecycleEventMapper mapper;

    public WorkflowLifecycleEventRepository(WorkflowLifecycleEventMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> instanceContext(long instanceId, long tenantId) {
        return mapper.instanceContext(instanceId, tenantId);
    }

    public int insertEvent(Map<String, Object> values) {
        return mapper.insertEvent(values);
    }

    public int insertDelivery(long id, long tenantId, String eventId, String subscriberKey) {
        return mapper.insertDelivery(id, tenantId, eventId, subscriberKey);
    }
}
