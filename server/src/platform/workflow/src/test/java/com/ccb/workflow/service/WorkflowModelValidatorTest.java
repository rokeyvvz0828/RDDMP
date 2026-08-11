package com.ccb.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowModelValidatorTest {
    private final WorkflowModelValidator validator = new WorkflowModelValidator(new ObjectMapper());

    @Test
    void acceptsEnterpriseGatewayParallelAndMultiInstanceModel() {
        var result = validator.validate("""
                {
                  "schemaVersion": 2,
                  "variables": [
                    {"name":"amount","type":"DECIMAL","required":true},
                    {"name":"approvers","type":"JSON","required":true}
                  ],
                  "formBindings": [
                    {"nodeId":"approve","fieldName":"amount","variableName":"amount","required":true}
                  ],
                  "nodes": [
                    {"id":"start","type":"START","label":"发起","config":{}},
                    {"id":"approve","type":"APPROVAL","label":"部门审批","config":{"assigneeType":"USER","assigneeIds":[2],"mode":"ANY","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN"]}}},
                    {"id":"condition","type":"CONDITION","label":"金额判断","config":{"defaultEdgeId":"e-default"}},
                    {"id":"split","type":"PARALLEL_SPLIT","label":"并行审批","config":{}},
                    {"id":"role-approve","type":"APPROVAL","label":"角色会签","config":{"assigneeType":"ROLE","assigneeIds":[8],"mode":"ALL","multiInstance":true,"collectionVariable":"approvers"}},
                    {"id":"org-approve","type":"APPROVAL","label":"组织负责人审批","config":{"assigneeType":"ORG_OWNER","mode":"PERCENT","percentage":60}},
                    {"id":"join","type":"PARALLEL_JOIN","label":"并行汇聚","config":{}},
                    {"id":"end","type":"END","label":"结束","config":{}}
                  ],
                  "edges": [
                    {"id":"e-start","source":"start","target":"approve"},
                    {"id":"e-approve","source":"approve","target":"condition"},
                    {"id":"e-condition","source":"condition","target":"split","condition":"${amount > 100}"},
                    {"id":"e-default","source":"condition","target":"end","default":true},
                    {"id":"e-split-role","source":"split","target":"role-approve"},
                    {"id":"e-split-org","source":"split","target":"org-approve"},
                    {"id":"e-role-join","source":"role-approve","target":"join"},
                    {"id":"e-org-join","source":"org-approve","target":"join"},
                    {"id":"e-join","source":"join","target":"end"}
                  ]
                }
                """);

        assertTrue(result.valid(), () -> result.errors().toString());
        assertEquals(8, result.model().nodes().size());
        assertEquals("PARALLEL_JOIN", result.model().nodes().stream().filter(node -> node.id().equals("join")).findFirst().orElseThrow().type());
    }

    @Test
    void acceptsConditionUsingRuntimeCustomVariable() {
        var result = validator.validate("""
                {"schemaVersion":2,"variables":[],"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"condition","type":"CONDITION","label":"条件","config":{"defaultEdgeId":"e-default"}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"condition"},
                  {"id":"e2","source":"condition","target":"end","condition":"${releaseVersion == 'prod'}"},
                  {"id":"e-default","source":"condition","target":"end","default":true}
                ]}
                """);

        assertTrue(result.valid(), () -> result.errors().toString());
    }

    @Test
    void acceptsFrontendNumberVariableType() {
        var result = validator.validate("""
                {"schemaVersion":2,"variables":[{"name":"amount","type":"NUMBER"}],"nodes":[
                  {"id":"start","type":"START","label":"start","config":{}},
                  {"id":"end","type":"END","label":"end","config":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"end"}
                ]}
                """);

        assertTrue(result.valid(), () -> result.errors().toString());
    }

    @Test
    void reportsReadableMessageWhenApprovalHasMultipleOutgoingEdges() {
        var result = validator.validate("""
                {"schemaVersion":2,"variables":[],"nodes":[
                  {"id":"start","type":"START","label":"开始","config":{}},
                  {"id":"approval","type":"APPROVAL","label":"财务审批","config":{"assigneeType":"STARTER","mode":"ANY"}},
                  {"id":"cc","type":"CC","label":"通知抄送","config":{"userIds":[2]}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"approval"},
                  {"id":"e2","source":"approval","target":"cc"},
                  {"id":"e3","source":"approval","target":"end"},
                  {"id":"e4","source":"cc","target":"end"}
                ]}
                """);

        var error = result.errors().stream().filter(item -> item.code().equals("TASK_DEGREE_INVALID")).findFirst().orElseThrow();
        assertEquals("审批或抄送节点【财务审批】必须一入一出", error.message());
    }

    @Test
    void rejectsConditionWithoutDefault() {
        var result = validator.validate("""
                {"schemaVersion":2,"variables":[],"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"condition","type":"CONDITION","label":"条件","config":{}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"condition"},
                  {"id":"e2","source":"condition","target":"end","condition":"${customFlag == true}"}
                ]}
                """);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.code().equals("DEFAULT_BRANCH_MISSING")));
    }

    @Test
    void preservesLegacyStepsThroughNewAdapter() {
        var result = validator.validate("{\"steps\":[{\"key\":\"MANAGER\",\"name\":\"部门负责人\",\"assigneeId\":2}]}");

        assertTrue(result.valid(), () -> result.errors().toString());
        assertEquals(1, result.model().schemaVersion());
        assertEquals("legacy-MANAGER", result.model().nodes().get(1).id());
    }
    @Test
    void rejectsConditionExpressionOnOrdinaryEdge() {
        var result = validator.validate("""
                {"schemaVersion":2,"variables":[{"name":"amount","type":"DECIMAL"}],"nodes":[
                  {"id":"start","type":"START","label":"开始","config":{}},
                  {"id":"approval","type":"APPROVAL","label":"审批","config":{"assigneeType":"STARTER","mode":"ANY"}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"approval"},
                  {"id":"e2","source":"approval","target":"end","condition":"${amount > 100}"}
                ]}
                """);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.code().equals("EDGE_CONDITION_NOT_ALLOWED")));
    }
    @Test
    void reportsChineseMessageWhenApprovalAssigneeIsMissing() {
        var result = validator.validate("""
                {"schemaVersion":2,"variables":[],"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"approval","type":"APPROVAL","label":"部门审批","config":{"assigneeType":"USER","assigneeIds":[],"mode":"ANY"}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[
                  {"id":"e1","source":"start","target":"approval"},
                  {"id":"e2","source":"approval","target":"end"}
                ]}
                """);

        var error = result.errors().stream().filter(item -> item.code().equals("ASSIGNEE_MISSING")).findFirst().orElseThrow();
        assertEquals("审批节点必须配置审批人或角色", error.message());
    }
}
