package com.ccb.workflow.service;

import com.ccb.workflow.model.WorkflowActionPolicy;
import com.ccb.workflow.model.WorkflowDefinitionModel;
import com.ccb.workflow.model.WorkflowEdgeModel;
import com.ccb.workflow.model.WorkflowFormBindingModel;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.ccb.workflow.model.WorkflowPosition;
import com.ccb.workflow.model.WorkflowVariableModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorkflowModelAdapter {
    private final ObjectMapper objectMapper;

    public WorkflowModelAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorkflowDefinitionModel adapt(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("流程定义不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("流程定义必须是 JSON 对象");
            if (root.has("steps") && !root.has("nodes")) return adaptLegacy(root.path("steps"));
            int schemaVersion = root.path("schemaVersion").asInt(0);
            if (schemaVersion != 1 && schemaVersion != 2) {
                throw new IllegalArgumentException("不支持的流程定义版本: " + schemaVersion);
            }
            return new WorkflowDefinitionModel(
                    schemaVersion,
                    parseNodes(root.path("nodes")),
                    parseEdges(root.path("edges")),
                    parseVariables(root.path("variables")),
                    parseFormBindings(root.path("formBindings")),
                    parsePolicies(root.path("nodes"))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("流程定义必须是合法 JSON", exception);
        }
    }

    private WorkflowDefinitionModel adaptLegacy(JsonNode stepsNode) {
        if (!stepsNode.isArray() || stepsNode.isEmpty()) {
            throw new IllegalArgumentException("流程审批节点不能为空");
        }
        List<WorkflowNodeModel> nodes = new ArrayList<>();
        List<WorkflowEdgeModel> edges = new ArrayList<>();
        nodes.add(new WorkflowNodeModel("start", "START", "发起", new WorkflowPosition(120, 80), JsonNodeFactory.instance.objectNode()));
        String previous = "start";
        int index = 0;
        for (JsonNode step : stepsNode) {
            String key = requiredText(step, "key");
            String nodeId = "legacy-" + key;
            ObjectNode config = JsonNodeFactory.instance.objectNode();
            if (step.path("assigneeId").asLong(0) > 0) {
                config.put("assigneeType", "USER");
                config.putArray("assigneeIds").add(step.path("assigneeId").asLong());
            } else {
                config.put("assigneeType", "STARTER");
            }
            config.put("mode", "ANY");
            nodes.add(new WorkflowNodeModel(nodeId, "APPROVAL", step.path("name").asText(key), new WorkflowPosition(120, 220 + index * 140), config));
            edges.add(new WorkflowEdgeModel("legacy-edge-" + index, previous, nodeId, null, null, false));
            previous = nodeId;
            index++;
        }
        nodes.add(new WorkflowNodeModel("end", "END", "结束", new WorkflowPosition(120, 220 + index * 140), JsonNodeFactory.instance.objectNode()));
        edges.add(new WorkflowEdgeModel("legacy-edge-end", previous, "end", null, null, false));
        return new WorkflowDefinitionModel(1, nodes, edges, List.of(), List.of(), Map.of());
    }

    private List<WorkflowNodeModel> parseNodes(JsonNode nodesNode) {
        List<WorkflowNodeModel> nodes = new ArrayList<>();
        if (!nodesNode.isArray()) return nodes;
        for (JsonNode node : nodesNode) {
            String id = requiredText(node, "id");
            String type = requiredText(node, "type").toUpperCase(Locale.ROOT);
            String label = node.path("label").asText(id).trim();
            JsonNode position = node.path("position");
            WorkflowPosition workflowPosition = new WorkflowPosition(position.path("x").asDouble(0), position.path("y").asDouble(0));
            JsonNode config = node.path("config");
            nodes.add(new WorkflowNodeModel(id, type, label, workflowPosition, config.isObject() ? config : JsonNodeFactory.instance.objectNode()));
        }
        return nodes;
    }

    private List<WorkflowEdgeModel> parseEdges(JsonNode edgesNode) {
        List<WorkflowEdgeModel> edges = new ArrayList<>();
        if (!edgesNode.isArray()) return edges;
        for (JsonNode edge : edgesNode) {
            String condition = edge.path("condition").asText(null);
            if (condition == null && edge.path("config").isObject()) condition = edge.path("config").path("condition").asText(null);
            boolean defaultFlow = edge.path("default").asBoolean(false) || edge.path("isDefault").asBoolean(false);
            edges.add(new WorkflowEdgeModel(
                    requiredText(edge, "id"),
                    requiredText(edge, "source"),
                    requiredText(edge, "target"),
                    edge.path("label").asText(null),
                    condition == null || condition.isBlank() ? null : condition.trim(),
                    defaultFlow
            ));
        }
        return edges;
    }

    private List<WorkflowVariableModel> parseVariables(JsonNode variablesNode) {
        List<WorkflowVariableModel> variables = new ArrayList<>();
        if (!variablesNode.isArray()) return variables;
        for (JsonNode variable : variablesNode) {
            variables.add(new WorkflowVariableModel(
                    requiredText(variable, "name"),
                    variable.path("type").asText("STRING").toUpperCase(Locale.ROOT),
                    variable.path("required").asBoolean(false),
                    variable.get("defaultValue"),
                    variable.path("scope").asText("PROCESS").toUpperCase(Locale.ROOT)
            ));
        }
        return variables;
    }

    private List<WorkflowFormBindingModel> parseFormBindings(JsonNode bindingsNode) {
        List<WorkflowFormBindingModel> bindings = new ArrayList<>();
        if (!bindingsNode.isArray()) return bindings;
        for (JsonNode binding : bindingsNode) {
            bindings.add(new WorkflowFormBindingModel(
                    requiredText(binding, "nodeId"),
                    requiredText(binding, "fieldName"),
                    requiredText(binding, "variableName"),
                    binding.path("required").asBoolean(false)
            ));
        }
        return bindings;
    }

    private Map<String, WorkflowActionPolicy> parsePolicies(JsonNode nodesNode) {
        Map<String, WorkflowActionPolicy> policies = new LinkedHashMap<>();
        if (!nodesNode.isArray()) return policies;
        for (JsonNode node : nodesNode) {
            JsonNode policy = node.path("config").path("actionPolicy");
            if (!policy.isObject() || !policy.path("allowedActions").isArray()) continue;
            Set<String> actions = new LinkedHashSet<>();
            for (JsonNode action : policy.path("allowedActions")) actions.add(action.asText("").toUpperCase(Locale.ROOT));
            policies.put(node.path("id").asText(), new WorkflowActionPolicy(actions));
        }
        return policies;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException("流程字段不能为空: " + field);
        return value;
    }
}
