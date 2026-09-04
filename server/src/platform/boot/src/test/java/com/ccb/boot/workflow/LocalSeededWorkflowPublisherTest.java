package com.ccb.boot.workflow;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalSeededWorkflowPublisherTest {
    private static final Map<String, Object> OPERATOR = Map.of(
            "id", 1L, "username", "admin", "display_name", "管理员", "org_id", 1L);

    @Test
    void publishesDraftDefinitionsAndSkipsPublishedDefinitions() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowService workflows = mock(WorkflowService.class);
        stubOperator(jdbc);
        when(jdbc.queryForList(contains("FROM wf_definition"), any(Object[].class))).thenReturn(List.of(
                definition(31L, "architecture.subsystem.change", "DRAFT"),
                definition(51L, "architecture.resource-request", "PUBLISHED")));

        publisher(jdbc, workflows).run(new DefaultApplicationArguments(new String[0]));

        verify(workflows).publish(org.mockito.ArgumentMatchers.eq(31L), any(AuthUser.class));
        verify(workflows, never()).publish(org.mockito.ArgumentMatchers.eq(51L), any(AuthUser.class));
    }

    @Test
    void publishesBothDraftDefinitions() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowService workflows = mock(WorkflowService.class);
        stubOperator(jdbc);
        when(jdbc.queryForList(contains("FROM wf_definition"), any(Object[].class))).thenReturn(List.of(
                definition(31L, "architecture.subsystem.change", "DRAFT"),
                definition(51L, "architecture.resource-request", "DRAFT")));

        publisher(jdbc, workflows).run(new DefaultApplicationArguments(new String[0]));

        verify(workflows, times(2)).publish(anyLong(), any(AuthUser.class));
    }

    @Test
    void failsWhenARequiredDefinitionIsMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowService workflows = mock(WorkflowService.class);
        stubOperator(jdbc);
        when(jdbc.queryForList(contains("FROM wf_definition"), any(Object[].class))).thenReturn(List.of(
                definition(31L, "architecture.subsystem.change", "DRAFT")));

        assertThrows(IllegalStateException.class,
                () -> publisher(jdbc, workflows).run(new DefaultApplicationArguments(new String[0])));
    }

    private void stubOperator(JdbcTemplate jdbc) {
        when(jdbc.queryForList(contains("FROM sys_user"), any(Object[].class))).thenReturn(List.of(OPERATOR));
    }

    private LocalSeededWorkflowPublisher publisher(JdbcTemplate jdbc, WorkflowService workflows) {
        return new LocalSeededWorkflowPublisher(jdbc, workflows, 1L, 1L,
                "architecture.subsystem.change,architecture.resource-request");
    }

    private Map<String, Object> definition(long id, String code, String status) {
        return Map.of("id", id, "code", code, "status", status);
    }
}
