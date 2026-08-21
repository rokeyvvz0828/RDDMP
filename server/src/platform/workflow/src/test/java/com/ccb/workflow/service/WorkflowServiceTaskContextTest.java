package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowServiceTaskContextTest {
    private static final AuthUser ASSIGNEE = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);

    @Test
    void projectsActionableContextForCurrentPendingAssignee() {
        ContextJdbcTemplate jdbc = new ContextJdbcTemplate(taskRow(7L, "PENDING", "RUNNING", "/release/applications/SQ-001"));
        WorkflowService service = service(jdbc, true);

        Map<String, Object> context = service.taskContext(11L, ASSIGNEE);

        assertEquals(11L, context.get("task_id"));
        assertEquals(21L, context.get("instance_id"));
        assertEquals("SQ-001", context.get("business_key"));
        assertEquals("测试审核", context.get("node_name"));
        assertEquals(List.of("APPROVE", "RETURN", "REJECT"), context.get("allowed_actions"));
        assertEquals(true, context.get("signature_required"));
        assertEquals(true, context.get("actionable"));
        assertTrue(jdbc.lastSql.contains("t.assignee_id"));
        assertTrue(jdbc.lastSql.contains("CAST(v.definition_json AS CHAR) AS definition_json"));
    }

    @Test
    void resolvesCurrentUsersPendingTaskByBusinessIdentity() {
        ContextJdbcTemplate jdbc = new ContextJdbcTemplate(taskRow(7L, "PENDING", "RUNNING", "/release/applications/SQ-001"));
        WorkflowService service = service(jdbc, true);

        Map<String, Object> context = service.currentTaskContext("release_application", "SQ-001", ASSIGNEE);

        assertEquals(11L, context.get("task_id"));
        assertEquals(true, context.get("actionable"));
        assertTrue(jdbc.sqls.get(0).contains("i.business_type = ?"));
        assertTrue(jdbc.sqls.get(0).contains("i.business_key = ?"));
        assertTrue(jdbc.sqls.get(0).contains("t.assignee_id = ?"));
        assertTrue(jdbc.sqls.get(0).contains("t.status = 'PENDING'"));
    }

    @Test
    void returnsNoContextWhenCurrentUserHasNoPendingBusinessTask() {
        WorkflowService service = service(new ContextJdbcTemplate(null), false);

        assertEquals(null, service.currentTaskContext("release_application", "SQ-001", ASSIGNEE));
    }

    @Test
    void returnsReadOnlyContextForCurrentUsersCompletedTask() {
        WorkflowService service = service(new ContextJdbcTemplate(taskRow(7L, "APPROVED", "RUNNING", "/release/applications/SQ-001")), true);

        Map<String, Object> context = service.taskContext(11L, ASSIGNEE);

        assertFalse((Boolean) context.get("actionable"));
        assertEquals(List.of(), context.get("allowed_actions"));
    }

    @Test
    void rejectsTaskOwnedByAnotherUser() {
        WorkflowService service = service(new ContextJdbcTemplate(taskRow(8L, "PENDING", "RUNNING", "/release/applications/SQ-001")), true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.taskContext(11L, ASSIGNEE));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
    }

    @Test
    void rejectsMissingTaskAndUnsafeBusinessPath() {
        WorkflowService missing = service(new ContextJdbcTemplate(null), false);
        BusinessException missingError = assertThrows(BusinessException.class, () -> missing.taskContext(11L, ASSIGNEE));
        assertEquals(ErrorCode.CONFLICT, missingError.code());

        WorkflowService unsafe = service(new ContextJdbcTemplate(taskRow(7L, "PENDING", "RUNNING", "https://example.test/review")), false);
        BusinessException unsafeError = assertThrows(BusinessException.class, () -> unsafe.taskContext(11L, ASSIGNEE));
        assertEquals(ErrorCode.CONFLICT, unsafeError.code());
    }

    @Test
    void rejectsDecisionOutsideNodeActionPolicyBeforeMutation() {
        ContextJdbcTemplate jdbc = new ContextJdbcTemplate(taskRow(7L, "PENDING", "RUNNING", "/release/applications/SQ-001"));
        WorkflowService service = service(jdbc, false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.decide(11L, "ADD_SIGN", "加签", 8L, List.of(), false, ASSIGNEE));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertEquals(0, jdbc.updates);
    }

    private WorkflowService service(ContextJdbcTemplate jdbc, boolean signatureRequired) {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowNodeLabelResolver labels = new WorkflowNodeLabelResolver(null, mapper) {
            @Override
            public List<Map<String, Object>> decorateTasks(List<Map<String, Object>> rows, long tenantId) {
                rows.forEach(row -> row.put("node_name", "测试审核"));
                return rows;
            }
        };
        WorkflowService service = new WorkflowService(jdbc, mapper, null, null, labels);
        service.setSignatureService(new WorkflowSignatureService(jdbc, mapper) {
            @Override
            public boolean required(long taskId, long tenantId) {
                return signatureRequired;
            }
        });
        return service;
    }

    private static Map<String, Object> taskRow(long assigneeId, String taskStatus, String instanceStatus, String actionPath) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 11L);
        row.put("instance_id", 21L);
        row.put("definition_id", 31L);
        row.put("version_no", 2);
        row.put("business_key", "SQ-001");
        row.put("business_type", "release_application");
        row.put("business_title", "版本申请 SQ-001");
        row.put("business_round", 2);
        row.put("project_ref", "P1");
        row.put("project_name", "统一研发交付平台升级项目");
        row.put("action_path", actionPath);
        row.put("task_key", "review");
        row.put("node_id", "review");
        row.put("task_type", "APPROVAL");
        row.put("task_status", taskStatus);
        row.put("instance_status", instanceStatus);
        row.put("assignee_id", assigneeId);
        row.put("definition_json", "{\"schemaVersion\":2,\"nodes\":[{\"id\":\"review\",\"type\":\"APPROVAL\",\"label\":\"测试审核\",\"position\":{\"x\":100,\"y\":100},\"config\":{\"actionPolicy\":{\"allowedActions\":[\"APPROVE\",\"RETURN\",\"REJECT\"]},\"signatureRequired\":true}}],\"edges\":[]}");
        return row;
    }

    private static final class ContextJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> row;
        private String lastSql = "";
        private final java.util.ArrayList<String> sqls = new java.util.ArrayList<>();
        private int updates;

        private ContextJdbcTemplate(Map<String, Object> row) {
            this.row = row;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastSql = sql;
            sqls.add(sql);
            return row == null ? List.of() : List.of(new LinkedHashMap<>(row));
        }

        @Override
        public int update(String sql, Object... args) {
            updates++;
            return 1;
        }
    }
}
