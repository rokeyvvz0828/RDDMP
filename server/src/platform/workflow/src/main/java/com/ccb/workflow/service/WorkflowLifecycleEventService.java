package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkflowLifecycleEventService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowLifecycleEventService.class);
    private final JdbcTemplate jdbc;
    private final List<WorkflowLifecycleConsumer> consumers;
    private WorkflowLifecycleDispatcher dispatcher;

    public WorkflowLifecycleEventService(JdbcTemplate jdbc, List<WorkflowLifecycleConsumer> consumers) {
        this.jdbc = jdbc;
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
    }

    @Autowired
    void setDispatcher(WorkflowLifecycleDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public String emit(long instanceId, WorkflowLifecycleEventType eventType, AuthUser operator) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT tenant_id, business_module_code, business_module_name, business_type, business_key, business_title, business_round, project_ref, project_name, action_path, data_digest FROM wf_instance WHERE id = ? AND tenant_id = ? AND deleted = 0",
                instanceId, operator.tenantId());
        if (rows.isEmpty() || rows.get(0).get("business_type") == null) return null;
        Map<String, Object> context = rows.get(0);
        String eventId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO wf_lifecycle_event (id, event_id, tenant_id, instance_id, event_type, business_module_code, business_module_name, business_type, business_key, business_round, business_title, project_ref, project_name, action_path, data_digest, operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), eventId, operator.tenantId(), instanceId, eventType.name(), context.get("business_module_code"), context.get("business_module_name"), context.get("business_type"), context.get("business_key"),
                context.get("business_round"), context.get("business_title"), context.get("project_ref"), context.get("project_name"),
                context.get("action_path"), context.get("data_digest"), operator.id());
        boolean hasDeliveries = false;
        for (WorkflowLifecycleConsumer consumer : consumers) {
            if (consumer.supports(String.valueOf(context.get("business_type")))) {
                jdbc.update("INSERT INTO wf_lifecycle_delivery (id, tenant_id, event_id, subscriber_key, status, next_attempt_at) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)",
                        nextId(), operator.tenantId(), eventId, requireSubscriberKey(consumer.subscriberKey()));
                hasDeliveries = true;
            }
        }
        if (hasDeliveries) dispatchAfterCommit(eventId);
        return eventId;
    }

    private void dispatchAfterCommit(String eventId) {
        if (dispatcher == null) return;
        Runnable dispatch = () -> {
            try {
                dispatcher.dispatchEvent(eventId);
            } catch (RuntimeException exception) {
                log.warn("Immediate workflow lifecycle dispatch failed for event {}", eventId, exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("Workflow lifecycle event {} has an active transaction without synchronization; scheduled dispatch will compensate", eventId);
        } else {
            dispatch.run();
        }
    }

    private String requireSubscriberKey(String value) {
        if (value == null || value.isBlank() || value.length() > 96) throw new IllegalStateException("Workflow lifecycle subscriberKey is invalid");
        return value.trim();
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
