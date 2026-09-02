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
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTemplateScopeTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "admin", "", "管理员", 1L, true);
    private static final String TEMPLATE_GRAPH = """
            {"schemaVersion":1,"nodes":[
              {"id":"start","type":"START","label":"发起","config":{}},
              {"id":"approve","type":"APPROVAL","label":"项目审批","config":{"assigneeType":"TEMPLATE_PLACEHOLDER","assigneeIds":[],"mode":"ANY"}},
              {"id":"end","type":"END","label":"结束","config":{}}
            ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"end"}]}
            """;

    @Test
    void validatorsAcceptStructuralTemplatePlaceholder() {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("TEMPLATE_PLACEHOLDER", new WorkflowDefinitionValidator(mapper).parse(TEMPLATE_GRAPH)
                .node("approve").config().path("assigneeType").asText());
        assertTrue(new WorkflowModelValidator(mapper).validate(TEMPLATE_GRAPH).valid());
    }

    @Test
    void compilerRefusesToTurnTemplatePlaceholderIntoExecutableTask() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new BpmnModelCompiler(new ObjectMapper()).compile("template", TEMPLATE_GRAPH));
        assertTrue(error.getMessage().contains("不能编译"));
    }

    @Test
    void templateCannotBePublished() {
        WorkflowService service = new WorkflowService(new TemplateJdbcTemplate(), new ObjectMapper(), null, null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.publish(100L, USER));

        assertEquals("全局模板不能发布，请先创建项目流程", error.getMessage());
    }

    @Test
    void templateRejectsConcreteUserEvenWhenApiIsCalledDirectly() {
        WorkflowService service = new WorkflowService(new TemplateJdbcTemplate(), new ObjectMapper(), null, null, null);
        String graphWithUser = TEMPLATE_GRAPH.replace("TEMPLATE_PLACEHOLDER", "USER").replace("\"assigneeIds\":[]", "\"assigneeIds\":[7]");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateDefinition(100L, "template", "审批模板", graphWithUser, USER));

        assertEquals("全局模板只能保留审批人占位或发起人，不能选择具体用户或角色", error.getMessage());
    }

    private static final class TemplateJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of(
                    "id", 100L,
                    "code", "template",
                    "name", "审批模板",
                    "scope_type", "TEMPLATE",
                    "status", "DRAFT",
                    "current_version", 0,
                    "model_schema_version", 1));
        }
    }
}
