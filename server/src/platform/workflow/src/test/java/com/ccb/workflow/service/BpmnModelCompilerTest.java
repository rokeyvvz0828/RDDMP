package com.ccb.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnModelCompilerTest {
    private final BpmnModelCompiler compiler = new BpmnModelCompiler(new ObjectMapper());

    @Test
    void compilesConfiguredDefaultBranchIntoExclusiveGateway() {
        var compiled = compiler.compile("expense_approval", """
                {
                  "schemaVersion": 2,
                  "variables": [{"name":"amount","type":"DECIMAL"}],
                  "formBindings": [],
                  "nodes": [
                    {"id":"start","type":"START","label":"发起","position":{"x":0,"y":0},"config":{}},
                    {"id":"gateway","type":"CONDITION","label":"金额判断","position":{"x":200,"y":0},"config":{"defaultEdgeId":"e-default"}},
                    {"id":"approval","type":"APPROVAL","label":"大额审批","position":{"x":400,"y":-100},"config":{"assigneeType":"STARTER","mode":"ANY"}},
                    {"id":"end","type":"END","label":"结束","position":{"x":600,"y":0},"config":{}}
                  ],
                  "edges": [
                    {"id":"e-start","source":"start","target":"gateway"},
                    {"id":"e-condition","source":"gateway","target":"approval","condition":"${amount > 1000}"},
                    {"id":"e-default","source":"gateway","target":"end","default":true},
                    {"id":"e-approval","source":"approval","target":"end"}
                  ]
                }
                """);

        var gateway = (ExclusiveGateway) compiled.model().getFlowElement("gateway");
        assertEquals("e-default", gateway.getDefaultFlow());
        assertTrue(compiled.xml().contains("default=\"e-default\""));
        assertTrue(((SequenceFlow) compiled.model().getFlowElement("e-default")).getConditionExpression() == null);
        assertEquals("${amount > 1000}", ((SequenceFlow) compiled.model().getFlowElement("e-condition")).getConditionExpression());
    }
}
