package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionValidatorTest {
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator(new ObjectMapper());

    @Test
    void acceptsLinearGraphWithApprovalAndCc() {
        var graph = validator.parse("""
                {"schemaVersion":1,"nodes":[
                  {"id":"start","type":"START","label":"发起","position":{"x":0,"y":0},"config":{}},
                  {"id":"approve","type":"APPROVAL","label":"部门审批","position":{"x":0,"y":1},"config":{"assigneeType":"USER","assigneeIds":[2],"mode":"ANY"}},
                  {"id":"cc","type":"CC","label":"抄送财务","position":{"x":0,"y":2},"config":{"userIds":[3]}},
                  {"id":"end","type":"END","label":"结束","position":{"x":0,"y":3},"config":{}}
                ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"cc"},{"id":"e3","source":"cc","target":"end"}]}
                """);
        assertEquals(4, graph.nodes().size());
        assertEquals("APPROVAL", graph.node("approve").type());
    }

    @Test
    void rejectsDisconnectedAndCyclicGraph() {
        assertThrows(BusinessException.class, () -> validator.parse("""
                {"schemaVersion":1,"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"approve","type":"APPROVAL","label":"审批","config":{"assigneeType":"USER","assigneeIds":[2]}},
                  {"id":"end","type":"END","label":"结束","config":{}},
                  {"id":"orphan","type":"CC","label":"孤立","config":{"userIds":[3]}}
                ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"end"}]}
                """));
        assertThrows(BusinessException.class, () -> validator.parse("""
                {"schemaVersion":1,"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"approve","type":"APPROVAL","label":"审批","config":{"assigneeType":"USER","assigneeIds":[2]}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"start"}]}
                """));
    }

    @Test
    void acceptsLegacyStepsAndConvertsThemToGraph() {
        var graph = validator.parse("{\"steps\":[{\"key\":\"MANAGER\",\"name\":\"部门负责人\",\"assigneeId\":2}]}");
        assertEquals("START", graph.nodes().get(0).type());
        assertEquals("END", graph.nodes().get(graph.nodes().size() - 1).type());
        assertEquals("部门负责人", graph.node("legacy-MANAGER").label());
    }

    @Test
    void rejectsApprovalWithoutAssignee() {
        assertThrows(BusinessException.class, () -> validator.parse("""
                {"schemaVersion":1,"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"approve","type":"APPROVAL","label":"审批","config":{"assigneeType":"USER","assigneeIds":[]}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"end"}]}
                """));
    }
}