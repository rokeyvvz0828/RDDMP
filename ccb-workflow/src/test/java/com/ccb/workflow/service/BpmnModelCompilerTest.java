package com.ccb.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnModelCompilerTest {
    private final BpmnModelCompiler compiler = new BpmnModelCompiler(new ObjectMapper());

    @Test
    void compilesGatewayAndMultiInstanceToFlowableBpmn() throws Exception {
        String json = """
                {"schemaVersion":2,
                 "variables":[{"name":"amount","type":"DECIMAL"},{"name":"approvers","type":"JSON"}],
                 "nodes":[
                   {"id":"start","type":"START","label":"发起","config":{}},
                   {"id":"approve","type":"APPROVAL","label":"审批","config":{"assigneeType":"USER","assigneeIds":[2]}},
                   {"id":"gateway","type":"CONDITION","label":"条件","config":{"defaultEdgeId":"e-default"}},
                   {"id":"end","type":"END","label":"结束","config":{}},
                   {"id":"split","type":"PARALLEL_SPLIT","label":"并行","config":{}},
                   {"id":"sign","type":"APPROVAL","label":"会签","config":{"assigneeType":"ROLE","assigneeIds":[8],"mode":"ALL","multiInstance":true,"collectionVariable":"approvers"}},
                   {"id":"join","type":"PARALLEL_JOIN","label":"汇聚","config":{}},
                   {"id":"starter-approve","type":"APPROVAL","label":"发起人审批","config":{"assigneeType":"STARTER"}}
                 ],
                 "edges":[
                   {"id":"e1","source":"start","target":"approve"},
                   {"id":"e2","source":"approve","target":"gateway"},
                   {"id":"e3","source":"gateway","target":"split","condition":"${amount > 100}"},
                   {"id":"e-default","source":"gateway","target":"end","default":true},
                   {"id":"e4","source":"split","target":"sign"},
                   {"id":"e5","source":"split","target":"starter-approve"},
                   {"id":"e6","source":"sign","target":"join"},
                   {"id":"e7","source":"starter-approve","target":"join"},
                   {"id":"e8","source":"join","target":"end"}
                 ]}
                """;

        var compiled = compiler.compile("expense-approval", json);
        assertTrue(compiled.xml().contains("exclusiveGateway"));
        assertTrue(compiled.xml().contains("parallelGateway"));
        assertTrue(compiled.xml().contains("multiInstanceLoopCharacteristics"));
        assertFalse(compiled.xml().contains("serviceTask"));
        assertFalse(compiled.xml().contains("messageEvent"));
        assertEquals(8, compiled.nodeMapping().size());

        var parsed = new BpmnXMLConverter().convertToBpmnModel(
                XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(compiled.xml()))
        );
        assertDoesNotThrow(() -> parsed.getMainProcess().getFlowElement(compiled.nodeMapping().get("sign")));
        assertEquals("expense-approval", parsed.getMainProcess().getId());
    }
}
