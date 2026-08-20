package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowSignatureServiceTest {
    private static final AuthUser ASSIGNEE = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);

    @Test
    void requiresExplicitConfirmationAndRecordsCurrentIdentity() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(7L, "PENDING", true);
        WorkflowSignatureService service = new WorkflowSignatureService(jdbc, new ObjectMapper());

        assertThrows(BusinessException.class, () -> service.confirmIfRequired(11L, "APPROVE", "同意", false, ASSIGNEE));
        service.confirmIfRequired(11L, "APPROVE", "同意", true, ASSIGNEE);

        assertEquals(1, jdbc.inserts);
        assertEquals(7L, jdbc.insertArgs[8]);
        assertEquals("reviewer", jdbc.insertArgs[9]);
        assertEquals("审批人", jdbc.insertArgs[10]);
    }

    @Test
    void rejectsConfirmationFromUserWhoDoesNotOwnPendingTask() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(8L, "PENDING", true);
        WorkflowSignatureService service = new WorkflowSignatureService(jdbc, new ObjectMapper());

        assertThrows(BusinessException.class, () -> service.confirmIfRequired(11L, "APPROVE", null, true, ASSIGNEE));
        assertEquals(0, jdbc.inserts);
    }

    @Test
    void leavesExistingUnsignedNodesCompatible() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(7L, "PENDING", false);
        WorkflowSignatureService service = new WorkflowSignatureService(jdbc, new ObjectMapper());

        service.confirmIfRequired(11L, "APPROVE", null, false, ASSIGNEE);

        assertEquals(0, jdbc.inserts);
    }

    @Test
    void signsOnlyApproveRejectAndReturnActions() {
        for (String action : List.of("APPROVE", "REJECT", "RETURN")) {
            StubJdbcTemplate jdbc = new StubJdbcTemplate(7L, "PENDING", true);
            WorkflowSignatureService service = new WorkflowSignatureService(jdbc, new ObjectMapper());
            assertThrows(BusinessException.class, () -> service.confirmIfRequired(11L, action, null, false, ASSIGNEE));
            service.confirmIfRequired(11L, action, null, true, ASSIGNEE);
            assertEquals(1, jdbc.inserts);
            assertEquals(action, jdbc.insertArgs[5]);
        }

        for (String action : List.of("ADD_SIGN", "TRANSFER", "DELEGATE", "CC")) {
            StubJdbcTemplate jdbc = new StubJdbcTemplate(7L, "PENDING", true);
            new WorkflowSignatureService(jdbc, new ObjectMapper())
                    .confirmIfRequired(11L, action, null, false, ASSIGNEE);
            assertEquals(0, jdbc.inserts);
        }
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final long assigneeId;
        private final String status;
        private final boolean signatureRequired;
        private int inserts;
        private Object[] insertArgs;

        private StubJdbcTemplate(long assigneeId, String status, boolean signatureRequired) {
            this.assigneeId = assigneeId;
            this.status = status;
            this.signatureRequired = signatureRequired;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            String definition = "{\"nodes\":[{\"id\":\"review\",\"type\":\"APPROVAL\",\"config\":{\"signatureRequired\":" + signatureRequired + "}}]}";
            return List.of(Map.of("id", 11L, "instance_id", 21L, "node_id", "review", "assignee_id", assigneeId,
                    "task_status", status, "business_round", 2, "data_digest", "b".repeat(64), "definition_json", definition));
        }

        @Override
        public int update(String sql, Object... args) {
            inserts++;
            insertArgs = args;
            return 1;
        }
    }
}
