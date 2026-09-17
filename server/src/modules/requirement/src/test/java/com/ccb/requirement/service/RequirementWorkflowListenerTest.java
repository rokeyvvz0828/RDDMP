package com.ccb.requirement.service;

import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementWorkflowListenerTest {

    @Test
    void ignoresStartedEvent() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        RequirementWorkflowListener listener = listener(jdbc);

        listener.consume(event(WorkflowLifecycleEventType.STARTED));

        assertTrue(jdbc.updates.isEmpty());
        assertEquals(0, jdbc.queryCount);
    }

    @Test
    void approvedEventMarksDifferenceReviewedWithApproverAsOperator() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        RequirementWorkflowListener listener = listener(jdbc);

        listener.consume(event(WorkflowLifecycleEventType.APPROVED));

        assertTrue(jdbc.updates.contains("status:已评审:9"));
        assertTrue(jdbc.updates.contains("record:通过:9"));
        assertTrue(jdbc.jdbcUpdates.stream().anyMatch(update ->
                update.contains("INSERT INTO req_change_log") && update.contains("9")));
    }

    @Test
    void rejectedAndReturnedEventsMarkDifferenceReturned() {
        for (WorkflowLifecycleEventType type : List.of(
                WorkflowLifecycleEventType.REJECTED, WorkflowLifecycleEventType.RETURNED)) {
            RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
            RequirementWorkflowListener listener = listener(jdbc);

            listener.consume(event(type));

            assertTrue(jdbc.updates.contains("status:已退回:9"));
            assertTrue(jdbc.updates.contains("record:退回:9"));
        }
    }

    @Test
    void databaseFailurePropagatesForLifecycleRetry() {
        RequirementWorkflowMapper failingMapper = new RequirementWorkflowMapper() {
            @Override
            public Map<String, Object> findActiveDifference(long tenantId, long differenceId) {
                throw new IllegalStateException("database unavailable");
            }
            @Override public int updateReviewStatus(long tenantId, long differenceId, String reviewStatus, long operatorId) { return 0; }
            @Override public Map<String, Object> findLatestWorkflowAction(long tenantId, long instanceId) { return null; }
            @Override public int insertReviewRecord(long id, long tenantId, long differenceId, long reviewerId, String reviewerName, LocalDateTime reviewTime, String conclusion, String comment, String reportDocName, long createdBy) { return 0; }
        };
        RequirementWorkflowListener listener = new RequirementWorkflowListener(
                new RequirementWorkflowRepository(failingMapper), RequirementChangeLogTestSupport.service(new com.ccb.requirement.support.StubJdbcTemplate()));

        assertThrows(IllegalStateException.class,
                () -> listener.consume(event(WorkflowLifecycleEventType.APPROVED)));
    }

    private RequirementWorkflowListener listener(RecordingJdbcTemplate jdbc) {
        return new RequirementWorkflowListener(new RequirementWorkflowRepository(jdbc), RequirementChangeLogTestSupport.service(jdbc));
    }

    private WorkflowLifecycleEvent event(WorkflowLifecycleEventType type) {
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "requirement", "需求管理", "requirement_diff_review", "req-diff:7",
                "差异评审", 1, "P001", "测试项目", "/requirements/new-project", "0".repeat(64));
        return new WorkflowLifecycleEvent("event-1", 1L, 88L, type, context, 9L,
                LocalDateTime.of(2026, 9, 8, 12, 0));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate implements RequirementWorkflowMapper {
        private final List<String> updates = new ArrayList<>();
        private final List<String> jdbcUpdates = new ArrayList<>();
        private int queryCount;

        @Override
        public Map<String, Object> findActiveDifference(long tenantId, long differenceId) {
            queryCount++;
            return Map.of("review_status", "评审中", "review_report_name", "评审报告.docx");
        }

        @Override
        public int updateReviewStatus(long tenantId, long differenceId, String reviewStatus, long operatorId) {
            updates.add("status:" + reviewStatus + ":" + operatorId);
            return 1;
        }

        @Override
        public Map<String, Object> findLatestWorkflowAction(long tenantId, long instanceId) {
            return Map.of("operator_id", 9L, "operator_name", "审核人", "comment", "同意",
                    "created_at", LocalDateTime.of(2026, 9, 8, 11, 59));
        }

        @Override
        public int insertReviewRecord(long id, long tenantId, long differenceId, long reviewerId, String reviewerName,
                                      LocalDateTime reviewTime, String conclusion, String comment, String reportDocName,
                                      long createdBy) {
            updates.add("record:" + conclusion + ":" + reviewerId);
            return 1;
        }

        @Override
        public int update(String sql, Object... args) {
            jdbcUpdates.add(sql + " " + java.util.Arrays.toString(args));
            return 1;
        }
    }
}
