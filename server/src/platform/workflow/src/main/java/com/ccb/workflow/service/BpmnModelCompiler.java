package com.ccb.workflow.service;

import com.ccb.workflow.model.WorkflowDefinitionModel;
import com.ccb.workflow.model.WorkflowEdgeModel;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BpmnModelCompiler {
    private final WorkflowModelValidator validator;

    public BpmnModelCompiler(ObjectMapper objectMapper) {
        this.validator = new WorkflowModelValidator(objectMapper);
    }

    public CompiledBpmn compile(String processKey, String definitionJson) {
        return compile(processKey, validator.requireValid(definitionJson));
    }

    public CompiledBpmn compile(String processKey, WorkflowDefinitionModel definition) {
        validator.requireValid(definition);
        String safeProcessKey = safeId(processKey == null || processKey.isBlank() ? "workflow-process" : processKey);
        BpmnModel model = new BpmnModel();
        model.setTargetNamespace("http://ccb.com/workflow");
        model.addNamespace("ccb", "http://ccb.com/workflow");
        Process process = new Process();
        process.setId(safeProcessKey);
        process.setName(processKey);
        process.setExecutable(true);
        model.addProcess(process);

        Map<String, String> nodeMapping = stableNodeMapping(definition.nodes());
        Map<String, FlowNode> flowNodes = new LinkedHashMap<>();
        for (WorkflowNodeModel node : definition.nodes()) {
            FlowNode flowNode = createFlowNode(node, nodeMapping.get(node.id()));
            process.addFlowElement(flowNode);
            flowNodes.put(node.id(), flowNode);
        }

        Map<String, WorkflowEdgeModel> edgesById = definition.edges().stream().collect(Collectors.toMap(WorkflowEdgeModel::id, edge -> edge, (left, right) -> left, LinkedHashMap::new));
        for (WorkflowEdgeModel edge : definition.edges()) {
            SequenceFlow sequenceFlow = new SequenceFlow(nodeMapping.get(edge.source()), nodeMapping.get(edge.target()));
            sequenceFlow.setId(safeId(edge.id()));
            sequenceFlow.setName(edge.label());
            if (edge.condition() != null && !edge.condition().isBlank()) sequenceFlow.setConditionExpression(edge.condition());
            process.addFlowElement(sequenceFlow);
        }

        for (WorkflowNodeModel node : definition.nodes()) {
            if (!"CONDITION".equals(node.type())) continue;
            FlowNode flowNode = flowNodes.get(node.id());
            if (flowNode instanceof ExclusiveGateway gateway) {
                String defaultEdgeId = node.config().path("defaultEdgeId").asText("");
                WorkflowEdgeModel defaultEdge = definition.edges().stream()
                        .filter(edge -> edge.source().equals(node.id()) && (edge.defaultFlow() || edge.id().equals(defaultEdgeId)))
                        .findFirst().orElse(null);
                if (defaultEdge != null) gateway.setDefaultFlow(safeId(defaultEdge.id()));
            }
        }

        String xml = new String(new BpmnXMLConverter().convertToXML(model), StandardCharsets.UTF_8);
        return new CompiledBpmn(safeProcessKey, model, xml, Map.copyOf(nodeMapping));
    }

    private FlowNode createFlowNode(WorkflowNodeModel node, String mappedId) {
        FlowNode flowNode;
        switch (node.type()) {
            case "START" -> flowNode = new StartEvent();
            case "END" -> flowNode = new EndEvent();
            case "CONDITION" -> flowNode = new ExclusiveGateway();
            case "PARALLEL_SPLIT", "PARALLEL_JOIN" -> flowNode = new ParallelGateway();
            case "APPROVAL", "CC" -> flowNode = createUserTask(node);
            default -> throw new IllegalArgumentException("不支持编译的流程节点类型: " + node.type());
        }
        flowNode.setId(mappedId);
        flowNode.setName(node.label());
        return flowNode;
    }

    private UserTask createUserTask(WorkflowNodeModel node) {
        UserTask task = new UserTask();
        task.setCategory("CC".equals(node.type()) ? "CCB_CC" : "CCB_APPROVAL");
        JsonNode config = node.config();
        String assigneeType = config.path("assigneeType").asText("").toUpperCase(Locale.ROOT);
        List<String> ids = ids(config.path("assigneeIds"));
        if ("USER".equals(assigneeType)) task.setCandidateUsers(ids);
        else if ("ROLE".equals(assigneeType)) task.setCandidateGroups(ids);
        else if ("STARTER".equals(assigneeType)) task.setAssignee("${starterId}");
        else if ("ORG_OWNER".equals(assigneeType)) task.setAssignee("${orgOwnerUserId_" + node.id() + "}");
        else if ("FORM_FIELD".equals(assigneeType)) task.setAssignee("${" + config.path("fieldName").asText() + "}");
        else if ("EXPRESSION".equals(assigneeType)) task.setAssignee(config.path("expression").asText());
        if ("CC".equals(node.type()) && !ids(config.path("userIds")).isEmpty()) task.setCandidateUsers(ids(config.path("userIds")));
        applyMultiInstance(task, config);
        return task;
    }

    private void applyMultiInstance(UserTask task, JsonNode config) {
        if (!config.path("multiInstance").asBoolean(false)) return;
        String collectionVariable = config.path("collectionVariable").asText("");
        MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${" + collectionVariable + "}");
        loop.setElementVariable(config.path("elementVariable").asText("assignee"));
        loop.setSequential(config.path("sequential").asBoolean(false));
        String mode = config.path("mode").asText("ALL").toUpperCase(Locale.ROOT);
        if ("ANY".equals(mode)) loop.setCompletionCondition("${nrOfCompletedInstances >= 1}");
        if ("PERCENT".equals(mode)) {
            int percentage = config.path("percentage").asInt(100);
            loop.setCompletionCondition("${nrOfCompletedInstances * 100 >= nrOfInstances * " + percentage + "}");
        }
        task.setLoopCharacteristics(loop);
    }

    private Map<String, String> stableNodeMapping(List<WorkflowNodeModel> nodes) {
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> used = new java.util.HashSet<>();
        for (WorkflowNodeModel node : nodes) {
            String base = safeId(node.id());
            String candidate = base;
            int suffix = 2;
            while (!used.add(candidate)) candidate = base + "_" + suffix++;
            mapping.put(node.id(), candidate);
        }
        return mapping;
    }

    private List<String> ids(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) result.add(value.asText());
        return result;
    }

    private String safeId(String value) {
        String normalized = value == null ? "workflow" : value.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (normalized.isBlank()) normalized = "workflow";
        if (!Character.isLetter(normalized.charAt(0))) normalized = "n_" + normalized;
        return normalized;
    }

    public record CompiledBpmn(String processKey, BpmnModel model, String xml, Map<String, String> nodeMapping) {
    }
}
