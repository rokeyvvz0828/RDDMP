package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowDefinitionValidator {
    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> NODE_TYPES = Set.of("START", "APPROVAL", "CC", "END");
    private static final Set<String> ASSIGNEE_TYPES = Set.of(
            "USER", "ROLE", "PROJECT_MEMBER", "PROJECT_ROLE", "STARTER", "VARIABLE"
    );
    private static final Set<String> MODES = Set.of("ANY", "ALL");

    private final ObjectMapper objectMapper;

    public WorkflowDefinitionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorkflowGraph parse(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw invalid("流程定义不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            if (root == null || !root.isObject()) throw invalid("流程定义必须是 JSON 对象");
            if (root.has("steps") && !root.has("nodes")) return parseLegacy(root.path("steps"));
            return parseGraph(root);
        } catch (JsonProcessingException exception) {
            throw invalid("流程定义必须是合法 JSON");
        }
    }

    private WorkflowGraph parseGraph(JsonNode root) {
        int schemaVersion = root.path("schemaVersion").asInt(0);
        if (schemaVersion != SCHEMA_VERSION) throw invalid("不支持的流程定义版本");
        JsonNode nodesNode = root.path("nodes");
        JsonNode edgesNode = root.path("edges");
        if (!nodesNode.isArray() || nodesNode.isEmpty()) throw invalid("流程节点不能为空");
        if (!edgesNode.isArray()) throw invalid("流程连线不能为空");

        List<WorkflowNode> nodes = new ArrayList<>();
        for (JsonNode node : nodesNode) {
            String id = text(node, "id");
            String type = text(node, "type").toUpperCase();
            if (!NODE_TYPES.contains(type)) throw invalid("不支持的流程节点类型：" + type);
            String label = node.path("label").asText(id);
            if (label.isBlank()) throw invalid("流程节点名称不能为空");
            JsonNode positionNode = node.path("position");
            Position position = new Position(positionNode.path("x").asDouble(80), positionNode.path("y").asDouble(80));
            JsonNode config = node.path("config");
            nodes.add(new WorkflowNode(id, type, label, position, config.isObject() ? config : objectMapper.createObjectNode()));
        }

        List<WorkflowEdge> edges = new ArrayList<>();
        for (JsonNode edge : edgesNode) {
            String id = text(edge, "id");
            edges.add(new WorkflowEdge(id, text(edge, "source"), text(edge, "target"), edge.path("condition").asText(null), edge.path("default").asBoolean(false)));
        }
        WorkflowGraph graph = new WorkflowGraph(schemaVersion, nodes, edges);
        validate(graph);
        return graph;
    }

    private WorkflowGraph parseLegacy(JsonNode stepsNode) {
        if (!stepsNode.isArray() || stepsNode.isEmpty()) throw invalid("流程审批节点不能为空");
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();
        nodes.add(new WorkflowNode("start", "START", "发起", new Position(120, 80), objectMapper.createObjectNode()));
        String previous = "start";
        int index = 0;
        for (JsonNode step : stepsNode) {
            String key = text(step, "key");
            String nodeId = "legacy-" + key;
            String label = step.path("name").asText(key);
            var config = objectMapper.createObjectNode();
            if (step.has("assigneeId") && step.path("assigneeId").asLong(0) > 0) {
                config.put("assigneeType", "USER");
                config.putArray("assigneeIds").add(step.path("assigneeId").asLong());
            } else {
                config.put("assigneeType", "STARTER");
            }
            config.put("mode", "ANY");
            nodes.add(new WorkflowNode(nodeId, "APPROVAL", label, new Position(120, 220 + index * 140), config));
            edges.add(new WorkflowEdge("legacy-edge-" + index, previous, nodeId));
            previous = nodeId;
            index++;
        }
        nodes.add(new WorkflowNode("end", "END", "结束", new Position(120, 220 + index * 140), objectMapper.createObjectNode()));
        edges.add(new WorkflowEdge("legacy-edge-end", previous, "end"));
        WorkflowGraph graph = new WorkflowGraph(SCHEMA_VERSION, nodes, edges);
        validate(graph);
        return graph;
    }

    private void validate(WorkflowGraph graph) {
        Map<String, WorkflowNode> nodesById = new HashMap<>();
        for (WorkflowNode node : graph.nodes()) {
            if (!nodesById.isEmpty() && nodesById.containsKey(node.id())) throw invalid("流程节点编码重复：" + node.id());
            nodesById.put(node.id(), node);
        }
        List<WorkflowNode> starts = graph.nodes().stream().filter(node -> "START".equals(node.type())).toList();
        List<WorkflowNode> ends = graph.nodes().stream().filter(node -> "END".equals(node.type())).toList();
        if (starts.size() != 1) throw invalid("流程必须且只能有一个开始节点");
        if (ends.size() != 1) throw invalid("流程必须且只能有一个结束节点");

        Map<String, List<WorkflowEdge>> outgoing = new HashMap<>();
        Map<String, List<WorkflowEdge>> incoming = new HashMap<>();
        Set<String> edgeIds = new HashSet<>();
        for (WorkflowEdge edge : graph.edges()) {
            if (!edgeIds.add(edge.id())) throw invalid("流程连线编码重复：" + edge.id());
            if (!nodesById.containsKey(edge.source()) || !nodesById.containsKey(edge.target())) throw invalid("流程连线引用了不存在的节点");
            if (edge.source().equals(edge.target())) throw invalid("流程不能连接到自身");
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
        }
        for (WorkflowNode node : graph.nodes()) {
            int outgoingCount = outgoing.getOrDefault(node.id(), List.of()).size();
            int incomingCount = incoming.getOrDefault(node.id(), List.of()).size();
            if ("START".equals(node.type()) && (incomingCount != 0 || outgoingCount != 1)) throw invalid("开始节点必须无入边且只有一条出边");
            if ("END".equals(node.type()) && (incomingCount != 1 || outgoingCount != 0)) throw invalid("结束节点必须只有一条入边且无出边");
            if (!"START".equals(node.type()) && !"END".equals(node.type()) && (incomingCount != 1 || outgoingCount != 1)) throw invalid("串行流程节点必须有且只有一条入边和一条出边：" + node.label());
            validateNodeConfig(node);
        }

        Set<String> visited = new HashSet<>();
        String current = starts.get(0).id();
        while (current != null) {
            if (!visited.add(current)) throw invalid("流程连线不能形成环路");
            WorkflowNode node = nodesById.get(current);
            List<WorkflowEdge> next = outgoing.getOrDefault(current, List.of());
            current = next.isEmpty() ? null : next.get(0).target();
            if ("END".equals(node.type())) break;
        }
        if (visited.size() != graph.nodes().size()) throw invalid("流程存在从开始节点不可达的节点");
    }

    private void validateNodeConfig(WorkflowNode node) {
        if ("APPROVAL".equals(node.type())) {
            String assigneeType = node.config().path("assigneeType").asText("").toUpperCase();
            String mode = node.config().path("mode").asText("ANY").toUpperCase();
            if (!ASSIGNEE_TYPES.contains(assigneeType)) throw invalid("审批节点审批人类型无效：" + node.label());
            if (!MODES.contains(mode)) throw invalid("审批节点审批模式无效：" + node.label());
            if (Set.of("USER", "ROLE", "PROJECT_MEMBER", "PROJECT_ROLE").contains(assigneeType)
                    && !hasPositiveId(node.config().path("assigneeIds"))) {
                throw invalid("审批节点必须配置审批人：" + node.label());
            }
        }
        if ("CC".equals(node.type()) && !hasPositiveId(node.config().path("userIds"))) {
            throw invalid("抄送节点必须配置抄送人：" + node.label());
        }
    }

    private boolean hasPositiveId(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return false;
        for (JsonNode id : node) if (id.asLong(0) <= 0) return false;
        return true;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw invalid("流程字段不能为空：" + field);
        return value;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public record WorkflowGraph(int schemaVersion, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        public WorkflowNode node(String id) { return nodes.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null); }
        public List<WorkflowEdge> outgoingEdges(String id) { return edges.stream().filter(item -> item.source().equals(id)).toList(); }
        public WorkflowEdge outgoing(String id) {
            List<WorkflowEdge> candidates = outgoingEdges(id);
            String configuredDefault = node(id) == null ? "" : node(id).config().path("defaultEdgeId").asText("");
            return candidates.stream().filter(item -> item.defaultFlow() || item.id().equals(configuredDefault)).findFirst()
                    .orElseGet(() -> candidates.stream().findFirst().orElse(null));
        }
    }

    public record WorkflowNode(String id, String type, String label, Position position, JsonNode config) {}
    public record WorkflowEdge(String id, String source, String target, String condition, boolean defaultFlow) {
        public WorkflowEdge(String id, String source, String target) { this(id, source, target, null, false); }
    }
    public record Position(double x, double y) {}
}
