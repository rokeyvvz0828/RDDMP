package com.ccb.requirement.service;

import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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

        assertTrue(jdbc.updates.stream().anyMatch(update ->
                update.sql().contains("UPDATE req_difference SET review_status")
                        && update.args().contains("已评审")
                        && update.args().contains(9L)));
        assertTrue(jdbc.updates.stream().anyMatch(update ->
                update.sql().contains("INSERT INTO req_review_record")
                        && update.args().contains("通过")));
        assertTrue(jdbc.updates.stream().anyMatch(update ->
                update.sql().contains("INSERT INTO req_change_log")
                        && update.args().contains(9L)));
    }

    @Test
    void rejectedAndReturnedEventsMarkDifferenceReturned() {
        for (WorkflowLifecycleEventType type : List.of(
                WorkflowLifecycleEventType.REJECTED, WorkflowLifecycleEventType.RETURNED)) {
            RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
            RequirementWorkflowListener listener = listener(jdbc);

            listener.consume(event(type));

            assertTrue(jdbc.updates.stream().anyMatch(update ->
                    update.sql().contains("UPDATE req_difference SET review_status")
                            && update.args().contains("已退回")));
            assertTrue(jdbc.updates.stream().anyMatch(update ->
                    update.sql().contains("INSERT INTO req_review_record")
                            && update.args().contains("退回")));
        }
    }

    @Test
    void databaseFailurePropagatesForLifecycleRetry() {
        JdbcTemplate failingJdbc = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                throw new IllegalStateException("database unavailable");
            }
        };
        RequirementWorkflowListener listener = new RequirementWorkflowListener(
                failingJdbc, new RequirementChangeLogService(failingJdbc));

        assertThrows(IllegalStateException.class,
                () -> listener.consume(event(WorkflowLifecycleEventType.APPROVED)));
    }

    private RequirementWorkflowListener listener(RecordingJdbcTemplate jdbc) {
        return new RequirementWorkflowListener(jdbc, new RequirementChangeLogService(jdbc));
    }

    private WorkflowLifecycleEvent event(WorkflowLifecycleEventType type) {
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "requirement", "需求管理", "requirement_diff_review", "req-diff:7",
                "差异评审", 1, "P001", "测试项目", "/requirements/new-project", "0".repeat(64));
        return new WorkflowLifecycleEvent("event-1", 1L, 88L, type, context, 9L,
                LocalDateTime.of(2026, 9, 8, 12, 0));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<UpdateCall> updates = new ArrayList<>();
        private int queryCount;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCount++;
            if (sql.contains("FROM req_difference")) {
                return List.of(Map.of("review_status", "评审中", "review_report_name", "评审报告.docx"));
            }
            if (sql.contains("FROM wf_task_action")) {
                return List.of(Map.of(
                        "operator_id", 9L,
                        "operator_name", "审核人",
                        "comment", "同意",
                        "created_at", LocalDateTime.of(2026, 9, 8, 11, 59)));
            }
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(new UpdateCall(sql, Arrays.asList(args)));
            return 1;
        }
    }

    private record UpdateCall(String sql, List<Object> args) {
    }
}
