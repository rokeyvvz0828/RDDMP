package com.ccb.workflow.service;

import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowLifecycleDispatcherTest {
    @Test
    void eventDispatchUsesAnIndependentTransactionAfterCommit() throws NoSuchMethodException {
        Transactional transactional = WorkflowLifecycleDispatcher.class
                .getMethod("dispatchEvent", String.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void keepsEventIdentityAcrossRetryAndEventuallyDelivers() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(0);
        AtomicInteger calls = new AtomicInteger();
        WorkflowLifecycleConsumer consumer = consumer(event -> {
            assertEquals("event-1", event.eventId());
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary failure");
        });
        WorkflowLifecycleDispatcher dispatcher = new WorkflowLifecycleDispatcher(jdbc.repository(), List.of(consumer));

        dispatcher.dispatchBatch(50);
        assertEquals("RETRY", jdbc.status);
        assertEquals(1, jdbc.attempts);

        dispatcher.dispatchBatch(50);
        assertEquals("DELIVERED", jdbc.status, "consumer calls=" + calls.get());
        assertEquals(2, jdbc.attempts);
        assertEquals(2, calls.get());
    }

    @Test
    void movesDeliveryToDeadAfterFifthFailure() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(4);
        WorkflowLifecycleDispatcher dispatcher = new WorkflowLifecycleDispatcher(jdbc.repository(),
                List.of(consumer(event -> { throw new IllegalStateException("permanent failure"); })));

        dispatcher.dispatchBatch(1);

        assertEquals("DEAD", jdbc.status);
        assertEquals(5, jdbc.attempts);
    }

    @Test
    void dispatchesOnlyTheRequestedEventAfterClaimingIt() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(0);
        AtomicInteger calls = new AtomicInteger();
        WorkflowLifecycleDispatcher dispatcher = new WorkflowLifecycleDispatcher(jdbc.repository(),
                List.of(consumer(event -> calls.incrementAndGet())));

        assertEquals(1, dispatcher.dispatchEvent("event-1"));

        assertEquals("event-1", jdbc.requestedEventId);
        assertEquals("DELIVERED", jdbc.status);
        assertEquals(1, calls.get());
    }

    @Test
    void skipsConsumptionWhenAnotherDispatcherAlreadyClaimedTheDelivery() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(0);
        jdbc.claimAllowed = false;
        AtomicInteger calls = new AtomicInteger();
        WorkflowLifecycleDispatcher dispatcher = new WorkflowLifecycleDispatcher(jdbc.repository(),
                List.of(consumer(event -> calls.incrementAndGet())));

        assertEquals(0, dispatcher.dispatchEvent("event-1"));

        assertEquals("PENDING", jdbc.status);
        assertEquals(0, calls.get());
    }

    @Test
    void immediateFailureReturnsToPendingForScheduledCompensation() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(0);
        WorkflowLifecycleDispatcher dispatcher = new WorkflowLifecycleDispatcher(jdbc.repository(),
                List.of(consumer(event -> { throw new IllegalStateException("temporary failure"); })));

        assertEquals(1, dispatcher.dispatchEvent("event-1"));

        assertEquals("PENDING", jdbc.status);
        assertEquals(1, jdbc.attempts);
    }

    @Test
    void scheduledDispatchRecoversAStaleClaimBeforeProcessing() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(1);
        jdbc.status = "DISPATCHING";
        AtomicInteger calls = new AtomicInteger();
        WorkflowLifecycleDispatcher dispatcher = new WorkflowLifecycleDispatcher(jdbc.repository(),
                List.of(consumer(event -> calls.incrementAndGet())));

        assertEquals(1, dispatcher.dispatchBatch(1));

        assertEquals(1, jdbc.recoveredClaims);
        assertEquals("DELIVERED", jdbc.status);
        assertEquals(1, calls.get());
    }

    private WorkflowLifecycleConsumer consumer(java.util.function.Consumer<WorkflowLifecycleEvent> action) {
        return new WorkflowLifecycleConsumer() {
            public String subscriberKey() { return "release"; }
            public boolean supports(String businessType) { return "release".equals(businessType); }
            public void consume(WorkflowLifecycleEvent event) { action.accept(event); }
        };
    }

    private static final class StubJdbcTemplate extends WorkflowLifecycleDispatcherRepository {
        private String status = "PENDING";
        private int attempts;
        private String lastSql;
        private String requestedEventId;
        private boolean claimAllowed = true;
        private int recoveredClaims;

        private StubJdbcTemplate(int attempts) {
            super(null);
            this.attempts = attempts;
        }

        @Override
        public List<Map<String, Object>> pending(int limit) {
            if (!status.equals("PENDING") && !status.equals("RETRY")) return List.of();
            return List.of(Map.of("id", 11L, "tenant_id", 1L, "event_id", "event-1",
                    "subscriber_key", "release", "attempt_count", attempts));
        }

        @Override
        public List<Map<String, Object>> pendingEvent(String eventId) {
            requestedEventId = eventId;
            return pending(1);
        }

        @Override
        public Map<String, Object> loadEvent(String eventId, long tenantId) {
            return Map.ofEntries(
                    Map.entry("event_id", "event-1"), Map.entry("tenant_id", 1L), Map.entry("instance_id", 21L),
                    Map.entry("event_type", "APPROVED"), Map.entry("business_type", "release"),
                    Map.entry("business_module_code", "release"), Map.entry("business_module_name", "配置管理"),
                    Map.entry("business_key", "SQ-001"), Map.entry("business_title", "版本申请 SQ-001"),
                    Map.entry("business_round", 1), Map.entry("action_path", "/release/applications/SQ-001"),
                    Map.entry("data_digest", "a".repeat(64)), Map.entry("operator_id", 7L),
                    Map.entry("occurred_at", Timestamp.valueOf(LocalDateTime.of(2026, 8, 14, 10, 0))));
        }

        @Override
        public int recoverStale(Timestamp before) {
                if (!"DISPATCHING".equals(status)) return 0;
                status = "RETRY";
                recoveredClaims++;
            return 1;
        }

        @Override
        public int claim(long id) {
                if (!claimAllowed || (!"PENDING".equals(status) && !"RETRY".equals(status))) return 0;
                status = "DISPATCHING";
            return 1;
        }

        @Override
        public int markDelivered(long id, int attemptCount) {
                status = "DELIVERED";
                attempts = attemptCount;
            return 1;
        }

        @Override
        public int markPending(long id, int attemptCount, String message) {
                status = "PENDING";
                attempts = attemptCount;
            return 1;
        }

        @Override
        public int markRetry(long id, int attemptCount, String message, int backoffMinutes) {
                status = "RETRY";
                attempts = attemptCount;
            return 1;
        }

        @Override
        public int markDead(long id, int attemptCount, String message) {
                status = "DEAD";
                attempts = attemptCount;
            return 1;
        }

        WorkflowLifecycleDispatcherRepository repository() { return this; }
    }
}
