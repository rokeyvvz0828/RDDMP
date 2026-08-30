package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkflowSignatureServiceTest {
    private static final AuthUser ASSIGNEE = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);

    @Test
    void disablesRequirementAndNewEvidenceForLegacySignedNodes() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        WorkflowSignatureService service = new WorkflowSignatureService(jdbc, new ObjectMapper());

        assertFalse(service.required(11L, 1L));
        service.confirmIfRequired(11L, "APPROVE", "同意", false, ASSIGNEE);
        service.confirmIfRequired(11L, "APPROVE", "同意", true, ASSIGNEE);

        assertEquals(0, jdbc.inserts);
        assertEquals(0, jdbc.taskContextQueries);
    }

    @Test
    void keepsHistoricalSignatureEvidenceReadable() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate();
        WorkflowSignatureService service = new WorkflowSignatureService(jdbc, new ObjectMapper());

        List<Map<String, Object>> signatures = service.signatures(21L, 1L);

        assertEquals(1, signatures.size());
        assertEquals(91L, signatures.get(0).get("id"));
        assertEquals(1, jdbc.signatureQueries);
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private int inserts;
        private int taskContextQueries;
        private int signatureQueries;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM wf_signature")) {
                signatureQueries++;
                return List.of(Map.of("id", 91L, "instance_id", 21L, "task_id", 11L));
            }
            taskContextQueries++;
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            inserts++;
            return 1;
        }
    }
}
