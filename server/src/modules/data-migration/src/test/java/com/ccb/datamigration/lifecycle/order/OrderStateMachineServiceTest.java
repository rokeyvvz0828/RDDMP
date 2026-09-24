package com.ccb.datamigration.lifecycle.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/** 任务流转引擎行为测试（T5，D-09/D-11/D-14/D-19）：暂停锁定与留痕、恢复回退、打回持久化、主状态派生。 */
class OrderStateMachineServiceTest {
    private static final AuthUser USER = new AuthUser(9, 1, "ljy", "hash", "李佳一", 11, true, "org", null);

    private StubJdbc jdbc;
    private LifecyclePermissionService permissions;
    private OrderStateMachineService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbc();
        permissions = Mockito.mock(LifecyclePermissionService.class);
        service = new OrderStateMachineService(jdbc, permissions, Mockito.mock(LifecycleAuditService.class),
                new ObjectMapper());
    }

    @Test
    void suspendOnNonFlowStatusIsRejected() {
        jdbc.order.put("order_status", "CLOSED");
        assertThatThrownBy(() -> service.suspend(USER, 42L, "暂停"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.ORDER_NO_ACTIVE_FLOW));
    }

    @Test
    void resumeOnNonSuspendedOrderIsRejected() {
        jdbc.order.put("order_status", "EXECUTING");
        assertThatThrownBy(() -> service.resume(USER, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.ORDER_NO_ACTIVE_FLOW));
    }

    @Test
    void suspendLocksOpenProcessesAndRecordsSuspendLog() {
        jdbc.order.put("order_status", "EXECUTING");
        jdbc.processes.put(1, process(1, "EXECUTING", "WAIT_REVIEW", 0, 1));
        jdbc.processes.put(2, process(2, "CLOSED", "PASSED", 0, 0));
        List<String> events = new ArrayList<>();
        jdbc.events = events;

        Map<String, Object> result = service.suspend(USER, 42L, "测试暂停");

        assertThat(result.get("orderStatus")).isEqualTo("SUSPENDED");
        assertThat(jdbc.order.get("order_status")).isEqualTo("SUSPENDED");
        assertThat(jdbc.order.get("suspend_before_status")).isEqualTo("EXECUTING");
        assertThat(events).contains("lock-process", "suspend-log", "status-log", "notify");
        assertThat(jdbc.lockedProcesses).containsExactly(1);
    }

    @Test
    void resumeRestoresPreSuspendStatusAndExtendsPlanFinishTime() {
        jdbc.order.put("order_status", "SUSPENDED");
        jdbc.order.put("suspend_before_status", "EXECUTING");
        jdbc.order.put("suspend_at", java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(2)));
        jdbc.suspendLogJson = "[{\"processSeq\":1,\"processStatus\":\"EXECUTING\",\"unlockedAt\":\"2026-09-24 09:00:00.0\"}]";
        jdbc.processes.put(1, process(1, "LOCKED", "WAIT_REVIEW", 0, 1));

        Map<String, Object> result = service.resume(USER, 42L);

        assertThat(result.get("orderStatus")).isEqualTo("EXECUTING");
        assertThat(jdbc.order.get("order_status")).isEqualTo("EXECUTING");
        assertThat(jdbc.order.get("plan_finish_time")).isNotNull();
        assertThat(jdbc.restoredProcessSeqs).containsExactly(1);
        assertThat(jdbc.events).contains("resume-status-log");
    }

    @Test
    void auditRejectedPersistsRejectCountAndDerivesReviewRejectedOverExecuting() {
        jdbc.order.put("order_status", "EXECUTING");
        jdbc.processes.put(1, process(1, "REVIEWING", "WAIT_REVIEW", 0, 1));
        jdbc.order.put("total_process_count", 1);

        Map<String, Object> result = service.applyAuditResult(USER, 42L, 1, "REJECTED");

        assertThat(result.get("stage")).isEqualTo("REJECTED");
        assertThat(result.get("rejectCount")).isEqualTo(1);
        assertThat(jdbc.rejectCountUpdated).isEqualTo(1);
        assertThat(jdbc.processes.get(1).get("process_status")).isEqualTo("EXECUTING");
        assertThat(jdbc.order.get("order_status")).isEqualTo("REVIEW_REJECTED");
        assertThat(jdbc.events).contains("audit-status", "reject-status-log", "derive-status-log");
    }

    @Test
    void auditPassedClosesProcessAndUnlocksOrderStatus() {
        jdbc.order.put("order_status", "REVIEWING");
        jdbc.order.put("total_process_count", 1);
        jdbc.processes.put(1, process(1, "REVIEWING", "WAIT_REVIEW", 0, 1));

        Map<String, Object> result = service.applyAuditResult(USER, 42L, 1, "PASSED");

        assertThat(result.get("stage")).isEqualTo("CLOSED");
        assertThat(jdbc.processes.get(1).get("process_status")).isEqualTo("CLOSED");
        assertThat(jdbc.order.get("order_status")).isEqualTo("CLOSED");
    }

    private Map<String, Object> process(int seq, String status, String auditStatus, int rejectCount, long processId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", processId);
        row.put("order_id", 42L);
        row.put("process_seq", seq);
        row.put("process_name", "工序" + seq);
        row.put("process_status", status);
        row.put("pre_depend_status", "NONE");
        row.put("exit_filled", 1);
        row.put("deliverable_submitted", 1);
        row.put("audit_status", auditStatus);
        row.put("must_audit", 1);
        row.put("must_submit_deliverable", 1);
        row.put("reject_count", rejectCount);
        row.put("unlocked_at", "EXECUTING".equals(status) ? java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)) : null);
        row.put("closed_at", "CLOSED".equals(status) ? java.sql.Timestamp.valueOf(LocalDateTime.now()) : null);
        return row;
    }

    private final class StubJdbc extends JdbcTemplate {
        private final Map<String, Object> order = new LinkedHashMap<>();
        private final Map<Integer, Map<String, Object>> processes = new LinkedHashMap<>();
        private final List<Integer> lockedProcesses = new ArrayList<>();
        private final List<Integer> restoredProcessSeqs = new ArrayList<>();
        private List<String> events = new ArrayList<>();
        private String suspendLogJson = null;
        private int rejectCountUpdated = -1;

        private StubJdbc() {
            order.put("id", 42L);
            order.put("tenant_id", 1L);
            order.put("order_code", "WO-000001");
            order.put("task_id", 7L);
            order.put("component_id", 101L);
            order.put("project_id", 5L);
            order.put("current_executor_id", 9L);
            order.put("participant_ids", "[]");
            order.put("sla_exempt", 0);
            order.put("deadline_status", "NORMAL");
            order.put("plan_finish_time", java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(3)));
            order.put("snapshot_json", "{\"schemaVersion\":1}");
            order.put("aggregate_edges", null);
            order.put("block_reason", "NONE");
            order.put("total_process_count", 1);
            order.put("closed_process_count", 0);
            order.put("flow_progress", 0.0);
            order.put("suspend_at", null);
            order.put("suspend_before_status", null);
            order.put("closed_at", null);
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            if (sql.contains("FROM work_order WHERE id")) {
                return order;
            }
            if (sql.contains("FROM order_process_instance WHERE order_id = ? AND process_seq")) {
                int seq = ((Number) args[1]).intValue();
                return processes.get(seq);
            }
            throw new AssertionError("Unexpected queryForMap: " + sql);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM order_process_instance WHERE order_id") && sql.contains("ORDER BY process_seq")) {
                return new ArrayList<>(processes.values());
            }
            if (sql.contains("FROM order_suspend_log")) {
                if (suspendLogJson == null) {
                    return List.of();
                }
                Map<String, Object> log = new LinkedHashMap<>();
                log.put("pre_suspend_processes", suspendLogJson);
                return List.of(log);
            }
            if (sql.contains("FROM work_order WHERE task_id")) {
                return List.of(order);
            }
            if (sql.contains("FROM dm_component")) {
                return List.of();
            }
            throw new AssertionError("Unexpected queryForList: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("process_status = 'CLOSED'")) {
                return requiredType.cast(Integer.valueOf(0));
            }
            if (sql.contains("process_status = 'LOCKED'")) {
                return requiredType.cast(Integer.valueOf(0));
            }
            if (sql.contains("FROM sys_user")) {
                return requiredType.cast(Integer.valueOf(1));
            }
            throw new AssertionError("Unexpected queryForObject: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE order_process_instance SET audit_status")) {
                int processId = ((Number) args[1]).intValue();
                for (Map<String, Object> p : processes.values()) {
                    if (p.get("id").equals(args[1]) || ((Number) p.get("id")).intValue() == processId) {
                        p.put("audit_status", args[0]);
                    }
                }
                events.add("audit-status");
                return 1;
            }
            if (sql.startsWith("UPDATE order_process_instance SET process_status = 'EXECUTING', reject_count")) {
                rejectCountUpdated = ((Number) args[0]).intValue();
                int mu = 0;
                for (Map<String, Object> p : processes.values()) {
                    if (p.get("id").equals(args[1])) {
                        p.put("process_status", "EXECUTING");
                        p.put("reject_count", rejectCountUpdated);
                        mu++;
                    }
                }
                events.add("reject-status-log");
                return mu > 0 ? 1 : 0;
            }
            if (sql.startsWith("UPDATE order_process_instance SET process_status = 'CLOSED'")) {
                for (Map<String, Object> p : processes.values()) {
                    p.put("process_status", "CLOSED");
                    p.put("closed_at", java.sql.Timestamp.valueOf(LocalDateTime.now()));
                }
                events.add("close-process");
                return 1;
            }
            if (sql.startsWith("UPDATE order_process_instance SET process_status = 'LOCKED'")) {
                int id = ((Number) args[0]).intValue();
                for (Map.Entry<Integer, Map<String, Object>> entry : processes.entrySet()) {
                    if (entry.getValue().get("id").equals(args[0])) {
                        entry.getValue().put("process_status", "LOCKED");
                        entry.getValue().put("unlocked_at", null);
                        lockedProcesses.add(entry.getKey());
                    }
                }
                events.add("lock-process");
                return 1;
            }
            if (sql.startsWith("UPDATE order_process_instance SET process_status = ?")) {
                int seq = ((Number) args[3]).intValue();
                restoredProcessSeqs.add(seq);
                processes.get(seq).put("process_status", args[0]);
                events.add("restore-process");
                return 1;
            }
            if (sql.startsWith("UPDATE work_order SET order_status = 'SUSPENDED'")) {
                order.put("order_status", "SUSPENDED");
                order.put("suspend_before_status", args[0]);
                order.put("suspend_at", args[1]);
                return 1;
            }
            if (sql.startsWith("UPDATE work_order SET order_status = ?, resume_at")) {
                order.put("order_status", args[0]);
                order.put("resume_at", args[1]);
                order.put("suspend_before_status", null);
                order.put("suspend_hours", args[2]);
                order.put("plan_finish_time", args[3]);
                events.add("resume-status-log");
                return 1;
            }
            if (sql.startsWith("UPDATE work_order SET order_status = ?, deadline_status = 'NORMAL'")) {
                order.put("order_status", args[0]);
                order.put("deadline_status", "NORMAL");
                events.add("derive-status-log");
                return 1;
            }
            if (sql.startsWith("UPDATE work_order SET order_status = ?, updated_at")) {
                order.put("order_status", args[0]);
                events.add("derive-status-log");
                return 1;
            }
            if (sql.startsWith("UPDATE work_order SET closed_process_count")) {
                return 1;
            }
            if (sql.startsWith("UPDATE work_order SET block_reason")) {
                return 1;
            }
            if (sql.startsWith("UPDATE order_process_instance SET process_status = 'EXECUTING', unlocked_at")) {
                events.add("unlock-process");
                return 1;
            }
            if (sql.startsWith("INSERT INTO order_suspend_log")) {
                events.add("suspend-log");
                return 1;
            }
            if (sql.startsWith("UPDATE order_suspend_log SET resume_at")) {
                events.add("resume-update-log");
                return 1;
            }
            if (sql.startsWith("INSERT INTO order_status_log")) {
                events.add("status-log");
                return 1;
            }
            if (sql.startsWith("INSERT IGNORE INTO notification")) {
                events.add("notify");
                return 1;
            }
            if (sql.startsWith("UPDATE task SET task_status")) {
                events.add("task-aggregate");
                return 1;
            }
            throw new AssertionError("Unexpected update: " + sql);
        }
    }
}
