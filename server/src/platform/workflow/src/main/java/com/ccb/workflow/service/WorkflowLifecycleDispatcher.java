package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@Service
public class WorkflowLifecycleDispatcher {
    private static final int MAX_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final Map<String, WorkflowLifecycleConsumer> consumers;
    @Value("${ccb.workflow.lifecycle-dispatch-stale-seconds:60}")
    private long staleClaimSeconds = 60;

    public WorkflowLifecycleDispatcher(JdbcTemplate jdbc, List<WorkflowLifecycleConsumer> consumers) {
        this.jdbc = jdbc;
        List<WorkflowLifecycleConsumer> registered = consumers == null ? List.of() : consumers;
        Map<String, WorkflowLifecycleConsumer> indexed = new LinkedHashMap<>();
        for (WorkflowLifecycleConsumer consumer : registered) {
            if (indexed.putIfAbsent(consumer.subscriberKey(), consumer) != null) {
                throw new IllegalStateException("Duplicate workflow lifecycle subscriberKey: " + consumer.subscriberKey());
            }
        }
        this.consumers = Collections.unmodifiableMap(indexed);
    }

    @Scheduled(fixedDelayString = "${ccb.workflow.lifecycle-dispatch-delay-ms:5000}")
    public void scheduledDispatch() {
        dispatchBatch(50);
    }

    public int dispatchBatch(int limit) {
        recoverStaleClaims();
        int bounded = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.id, d.tenant_id, d.event_id, d.subscriber_key, d.attempt_count FROM wf_lifecycle_delivery d WHERE d.status IN ('PENDING','RETRY') AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= CURRENT_TIMESTAMP) ORDER BY d.id LIMIT ?", bounded);
        return dispatchRows(rows, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) return 0;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.id, d.tenant_id, d.event_id, d.subscriber_key, d.attempt_count FROM wf_lifecycle_delivery d WHERE d.event_id = ? AND d.status IN ('PENDING','RETRY') AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= CURRENT_TIMESTAMP) ORDER BY d.id", eventId.trim());
        return dispatchRows(rows, true);
    }

    @Transactional
    public void retry(String eventId, String subscriberKey, long tenantId) {
        int changed = jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'RETRY', attempt_count = 0, last_error = NULL, next_attempt_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ? AND status = 'DEAD'",
                tenantId, eventId, subscriberKey);
        if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "未找到可重试的失败事件投递");
    }

    private int dispatchRows(List<Map<String, Object>> rows, boolean immediate) {
        int processed = 0;
        for (Map<String, Object> row : rows) {
            if (dispatchOne(row, immediate)) processed++;
        }
        return processed;
    }

    private boolean dispatchOne(Map<String, Object> delivery, boolean immediate) {
        long id = ((Number) delivery.get("id")).longValue();
        if (!claim(id)) return false;
        String subscriberKey = String.valueOf(delivery.get("subscriber_key"));
        WorkflowLifecycleConsumer consumer = consumers.get(subscriberKey);
        int attempts = ((Number) delivery.get("attempt_count")).intValue() + 1;
        if (consumer == null) {
            fail(id, attempts, "Lifecycle consumer is not registered: " + subscriberKey, immediate);
            return true;
        }
        try {
            consumer.consume(loadEvent(String.valueOf(delivery.get("event_id")), ((Number) delivery.get("tenant_id")).longValue()));
            jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'DELIVERED', attempt_count = ?, delivered_at = CURRENT_TIMESTAMP, next_attempt_at = NULL, last_error = NULL WHERE id = ? AND status = 'DISPATCHING'", attempts, id);
        } catch (RuntimeException exception) {
            fail(id, attempts, safeMessage(exception), immediate);
        }
        return true;
    }

    private boolean claim(long id) {
        return jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'DISPATCHING' WHERE id = ? AND status IN ('PENDING','RETRY') AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)", id) == 1;
    }

    private void recoverStaleClaims() {
        long boundedSeconds = Math.max(10, Math.min(staleClaimSeconds, 3600));
        jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'RETRY', next_attempt_at = CURRENT_TIMESTAMP, last_error = COALESCE(last_error, 'Recovered stale dispatch claim') WHERE status = 'DISPATCHING' AND updated_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(boundedSeconds)));
    }

    private WorkflowLifecycleEvent loadEvent(String eventId, long tenantId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT event_id, tenant_id, instance_id, event_type, business_module_code, business_module_name, business_type, business_key, business_title, business_round, project_ref, project_name, action_path, data_digest, operator_id, occurred_at FROM wf_lifecycle_event WHERE event_id = ? AND tenant_id = ?", eventId, tenantId);
        WorkflowBusinessContext context = new WorkflowBusinessContext(value(row.get("business_module_code")), value(row.get("business_module_name")), String.valueOf(row.get("business_type")), String.valueOf(row.get("business_key")),
                String.valueOf(row.get("business_title")), ((Number) row.get("business_round")).intValue(), value(row.get("project_ref")), value(row.get("project_name")),
                String.valueOf(row.get("action_path")), String.valueOf(row.get("data_digest")));
        Object occurred = row.get("occurred_at");
        LocalDateTime occurredAt = occurred instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : LocalDateTime.now();
        return new WorkflowLifecycleEvent(eventId, tenantId, ((Number) row.get("instance_id")).longValue(),
                WorkflowLifecycleEventType.valueOf(String.valueOf(row.get("event_type"))), context,
                ((Number) row.get("operator_id")).longValue(), occurredAt);
    }

    private void fail(long id, int attempts, String message, boolean immediate) {
        if (attempts >= MAX_ATTEMPTS) {
            jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'DEAD', attempt_count = ?, last_error = ?, next_attempt_at = NULL WHERE id = ? AND status = 'DISPATCHING'",
                    attempts, message, id);
            return;
        }
        if (immediate) {
            jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'PENDING', attempt_count = ?, last_error = ?, next_attempt_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'DISPATCHING'",
                    attempts, message, id);
            return;
        }
        int backoffMinutes = Math.min(60, 1 << Math.min(attempts - 1, 5));
        jdbc.update("UPDATE wf_lifecycle_delivery SET status = 'RETRY', attempt_count = ?, last_error = ?, next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? MINUTE) WHERE id = ? AND status = 'DISPATCHING'",
                attempts, message, backoffMinutes, id);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
