package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowLifecycleEventServiceTest {
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void dispatchesOnlyAfterCommitWhenTransactionSynchronizationIsActive() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        WorkflowLifecycleEventService service = service(dispatcher);
        TransactionSynchronizationManager.initSynchronization();

        String eventId = service.emit(21L, WorkflowLifecycleEventType.APPROVED, user());

        assertNotNull(eventId);
        assertEquals(0, dispatcher.calls.get());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertEquals(1, dispatcher.calls.get());
        assertEquals(eventId, dispatcher.eventId);
    }

    @Test
    void doesNotDispatchWhenTransactionRollsBack() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        WorkflowLifecycleEventService service = service(dispatcher);
        TransactionSynchronizationManager.initSynchronization();

        service.emit(21L, WorkflowLifecycleEventType.APPROVED, user());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertEquals(0, dispatcher.calls.get());
    }

    @Test
    void dispatchesImmediatelyWithoutTransactionSynchronization() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        WorkflowLifecycleEventService service = service(dispatcher);

        String eventId = service.emit(21L, WorkflowLifecycleEventType.APPROVED, user());

        assertEquals(1, dispatcher.calls.get());
        assertEquals(eventId, dispatcher.eventId);
    }

    @Test
    void isolatesImmediateDispatchFailureFromEventCreation() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        dispatcher.failure = new IllegalStateException("notification unavailable");
        WorkflowLifecycleEventService service = service(dispatcher);

        assertDoesNotThrow(() -> service.emit(21L, WorkflowLifecycleEventType.APPROVED, user()));
        assertEquals(1, dispatcher.calls.get());
    }

    private WorkflowLifecycleEventService service(RecordingDispatcher dispatcher) {
        WorkflowLifecycleConsumer consumer = new WorkflowLifecycleConsumer() {
            public String subscriberKey() { return "release"; }
            public boolean supports(String businessType) { return "release".equals(businessType); }
            public void consume(com.ccb.workflow.integration.WorkflowLifecycleEvent event) { }
        };
        WorkflowLifecycleEventService service = new WorkflowLifecycleEventService(new EventJdbcTemplate(), List.of(consumer));
        service.setDispatcher(dispatcher);
        return service;
    }

    private AuthUser user() {
        return new AuthUser(7L, 1L, "approver", "hash", "审批人", 1L, true);
    }

    private static final class RecordingDispatcher extends WorkflowLifecycleDispatcher {
        private final AtomicInteger calls = new AtomicInteger();
        private String eventId;
        private RuntimeException failure;

        private RecordingDispatcher() {
            super(new JdbcTemplate(), List.of());
        }

        @Override
        public int dispatchEvent(String eventId) {
            calls.incrementAndGet();
            this.eventId = eventId;
            if (failure != null) throw failure;
            return 1;
        }
    }

    private static final class EventJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.ofEntries(
                    Map.entry("tenant_id", 1L),
                    Map.entry("business_module_code", "release"),
                    Map.entry("business_module_name", "配置管理"),
                    Map.entry("business_type", "release"),
                    Map.entry("business_key", "SQ-001"),
                    Map.entry("business_title", "版本申请 SQ-001"),
                    Map.entry("business_round", 1),
                    Map.entry("action_path", "/release/applications/SQ-001"),
                    Map.entry("data_digest", "a".repeat(64))));
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }
    }
}
