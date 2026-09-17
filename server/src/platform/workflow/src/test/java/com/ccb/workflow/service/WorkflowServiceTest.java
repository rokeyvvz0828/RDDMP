package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowServiceTest {
    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);

    @Test
    void readsDefinitionSummariesThroughMapperAndRecordsBoundedSuccessMetric() {
        WorkflowDefinitionMapper mapper = mock(WorkflowDefinitionMapper.class);
        when(mapper.selectDefinitionSummaries(1L, null, null, 0L, 20L)).thenReturn(List.of(
                Map.of("id", 9001L, "code", "demo", "scope_type", "TEMPLATE", "requires_configuration", 1)));
        when(mapper.countDefinitionSummaries(1L, null, null)).thenReturn(1L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkflowService service = workflowService();
        service.setDefinitionMapper(mapper);
        service.setMeterRegistry(registry);

        var result = service.definitions(new com.ccb.common.api.PageQuery(1, 20), admin);

        assertEquals(1L, result.total());
        assertEquals(Boolean.TRUE, result.records().get(0).get("requires_configuration"));
        verify(mapper).selectDefinitionSummaries(1L, null, null, 0L, 20L);
        verify(mapper).countDefinitionSummaries(1L, null, null);
        assertMetricTags(registry, "success", "TEMPLATE");
    }

    @Test
    void recordsInvalidScopeWithFixedErrorMetricTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkflowService service = workflowService();
        service.setMeterRegistry(registry);

        assertThrows(com.ccb.common.exception.BusinessException.class,
                () -> service.definitions(new com.ccb.common.api.PageQuery(1, 20), null, "untrusted-scope-value", admin));

        assertMetricTags(registry, "error", "INVALID");
    }

    @Test
    void definitionSummaryMapperNeverSelectsDefinitionJson() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/mapper/workflow/WorkflowDefinitionMapper.xml")) {
            assertTrue(stream != null);
            String mapperXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
            assertTrue(!mapperXml.contains("definition_json"));
        }
    }

    @Test
    void standardDefinitionCreationRefreshesSummaryInItsWritePath() {
        FlowableWorkflowService flowable = mock(FlowableWorkflowService.class);
        WorkflowDefinitionSummaryProjector projector = mock(WorkflowDefinitionSummaryProjector.class);
        WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
        String definitionJson = "{\"schemaVersion\":1,\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"end\",\"type\":\"END\"}],\"edges\":[{\"id\":\"start-end\",\"source\":\"start\",\"target\":\"end\"}]}";
        when(flowable.isEnterpriseDefinition(definitionJson)).thenReturn(false);
        when(definitions.definitionSummary(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(Map.of("id", 9001L, "code", "demo", "name", "Demo", "scope_type", "TEMPLATE", "status", "DRAFT", "current_version", 0));
        WorkflowService service = new WorkflowService(new ObjectMapper(), flowable, null, null, event -> { });
        service.setDefinitionRepository(definitions);
        service.setRuntimeRepository(mock(WorkflowRuntimeRepository.class));
        service.setDefinitionSummaryProjector(projector);

        service.createDefinition("demo", "Demo", definitionJson, admin);

        verify(projector).refresh(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("TEMPLATE"), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(definitionJson));
    }

    @Test
    void routesSchemaV2VersionToFlowableWhenDefinitionMetadataDefaultsToV1() {
        var jdbc = new StubJdbcTemplate(Map.of(
                "definition_schema_version", 1,
                "version_schema_version", 2,
                "definition_json", "{\"schemaVersion\":2,\"nodes\":[],\"edges\":[]}"
        ));
        var flowable = new RecordingFlowableWorkflowService();
        var service = new WorkflowService(new ObjectMapper(), flowable, null, null, event -> { });
        WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
        when(definitions.detail(9001L, 1L)).thenReturn(Map.of("id", 9001L, "scope_type", "TEMPLATE", "status", "DRAFT", "current_version", 0));
        when(definitions.enterprise(9001L, 1L)).thenReturn(jdbc.row);
        service.setDefinitionRepository(definitions);

        service.updateDefinition(9001L, "demo", "演示流程", "{\"schemaVersion\":2}",
                new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true));

        assertTrue(flowable.updateCalled);
    }

    private WorkflowService workflowService() {
        return new WorkflowService(new ObjectMapper(), new RecordingFlowableWorkflowService(), null, null, event -> { });
    }

    private void assertMetricTags(SimpleMeterRegistry registry, String outcome, String scopeType) {
        Meter meter = registry.find("ccb.workflow.definitions.query")
                .tags("operation", "definitions", "scope_type", scopeType, "outcome", outcome).meter();
        assertTrue(meter != null);
        assertEquals(List.of("operation", "outcome", "scope_type"), meter.getId().getTags().stream().map(tag -> tag.getKey()).sorted().toList());
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
            super(new ObjectMapper(), null, null, null, null, null, null);
        }

        @Override
        public void updateDefinition(long definitionId, String code, String name, String definitionJson, AuthUser user) {
            updateCalled = true;
        }
    }
}
