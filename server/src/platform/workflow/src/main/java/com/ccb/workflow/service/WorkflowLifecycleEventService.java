package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkflowLifecycleEventService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowLifecycleEventService.class);
    private final WorkflowLifecycleEventRepository repository;
    private final List<WorkflowLifecycleConsumer> consumers;
    private WorkflowLifecycleDispatcher dispatcher;

    public WorkflowLifecycleEventService(WorkflowLifecycleEventRepository repository, List<WorkflowLifecycleConsumer> consumers) {
        this.repository = repository;
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
    }

    @Autowired
    void setDispatcher(WorkflowLifecycleDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public String emit(long instanceId, WorkflowLifecycleEventType eventType, AuthUser operator) {
        List<Map<String, Object>> rows = repository.instanceContext(instanceId, operator.tenantId());
        if (rows.isEmpty() || rows.get(0).get("business_type") == null) return null;
        Map<String, Object> context = rows.get(0);
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventValues = new HashMap<>();
        eventValues.put("id", nextId());
        eventValues.put("eventId", eventId);
        eventValues.put("tenantId", operator.tenantId());
        eventValues.put("instanceId", instanceId);
        eventValues.put("eventType", eventType.name());
        eventValues.put("businessModuleCode", context.get("business_module_code"));
        eventValues.put("businessModuleName", context.get("business_module_name"));
        eventValues.put("businessType", context.get("business_type"));
        eventValues.put("businessKey", context.get("business_key"));
        eventValues.put("businessRound", context.get("business_round"));
        eventValues.put("businessTitle", context.get("business_title"));
        eventValues.put("projectRef", context.get("project_ref"));
        eventValues.put("projectName", context.get("project_name"));
        eventValues.put("actionPath", context.get("action_path"));
        eventValues.put("dataDigest", context.get("data_digest"));
        eventValues.put("operatorId", operator.id());
        repository.insertEvent(eventValues);
        boolean hasDeliveries = false;
        for (WorkflowLifecycleConsumer consumer : consumers) {
            if (consumer.supports(String.valueOf(context.get("business_type")))) {
                repository.insertDelivery(nextId(), operator.tenantId(), eventId, requireSubscriberKey(consumer.subscriberKey()));
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
