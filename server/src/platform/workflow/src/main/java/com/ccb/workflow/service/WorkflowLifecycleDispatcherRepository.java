package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowLifecycleDispatcherRepository {
    private final WorkflowLifecycleDispatcherMapper mapper;
    public WorkflowLifecycleDispatcherRepository(WorkflowLifecycleDispatcherMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> pending(int limit) { return mapper.pending(limit); }
    public List<Map<String,Object>> pendingEvent(String eventId) { return mapper.pendingEvent(eventId); }
    public int retryDead(long tenantId, String eventId, String subscriberKey) { return mapper.retryDead(tenantId, eventId, subscriberKey); }
    public int markDelivered(long id, int attempts) { return mapper.markDelivered(id, attempts); }
    public int claim(long id) { return mapper.claim(id); }
    public int recoverStale(Timestamp before) { return mapper.recoverStale(before); }
    public Map<String,Object> loadEvent(String eventId, long tenantId) { return mapper.loadEvent(eventId, tenantId); }
    public int markDead(long id, int attempts, String message) { return mapper.markDead(id, attempts, message); }
    public int markPending(long id, int attempts, String message) { return mapper.markPending(id, attempts, message); }
    public int markRetry(long id, int attempts, String message, int backoffMinutes) { return mapper.markRetry(id, attempts, message, backoffMinutes); }
}
