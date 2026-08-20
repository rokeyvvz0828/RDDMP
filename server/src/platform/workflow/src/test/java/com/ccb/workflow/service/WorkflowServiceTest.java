package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowServiceTest {
    @Test
    void routesSchemaV2VersionToFlowableWhenDefinitionMetadataDefaultsToV1() {
        var jdbc = new StubJdbcTemplate(Map.of(
                "definition_schema_version", 1,
                "version_schema_version", 2,
                "definition_json", "{\"schemaVersion\":2,\"nodes\":[],\"edges\":[]}"
        ));
        var flowable = new RecordingFlowableWorkflowService();
        var service = new WorkflowService(jdbc, new ObjectMapper(), flowable, null, null, event -> { });

        service.updateDefinition(9001L, "demo", "演示流程", "{\"schemaVersion\":2}",
                new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true));

        assertTrue(flowable.updateCalled);
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> row;

        private StubJdbcTemplate(Map<String, Object> row) {
            this.row = row;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(row);
        }
    }

    private static final class RecordingFlowableWorkflowService extends FlowableWorkflowService {
        private boolean updateCalled;

        private RecordingFlowableWorkflowService() {
            super(null, new ObjectMapper(), null, null, null, null, null);
        }

        @Override
        public void updateDefinition(long definitionId, String code, String name, String definitionJson, AuthUser user) {
            updateCalled = true;
        }
    }
}
