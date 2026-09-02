package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.workflow.model.WorkflowActionPolicy;
import com.ccb.workflow.model.WorkflowDefinitionModel;
import com.ccb.workflow.model.WorkflowEdgeModel;
import com.ccb.workflow.model.WorkflowFormBindingModel;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.ccb.workflow.model.WorkflowVariableModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorkflowModelValidator {
    private static final Set<String> NODE_TYPES = Set.of(
            "START", "APPROVAL", "CC", "CONDITION", "PARALLEL_SPLIT", "PARALLEL_JOIN", "END"
    );
    private static final Set<String> ASSIGNEE_TYPES = Set.of(
            "USER", "ROLE", "PROJECT_MEMBER", "PROJECT_ROLE", "ORG_OWNER", "STARTER", "FORM_FIELD", "EXPRESSION"
    );
    private static final Set<String> APPROVAL_MODES = Set.of("ANY", "ALL", "PERCENT");
    private static final Set<String> VARIABLE_TYPES = Set.of(
            "STRING", "INTEGER", "LONG", "DECIMAL", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "USER", "ORG", "JSON"
    );
    private final WorkflowModelAdapter adapter;

    public WorkflowModelValidator(ObjectMapper objectMapper) {
        this.adapter = new WorkflowModelAdapter(objectMapper);
    }

    public ValidationResult validate(String definitionJson) {
        WorkflowDefinitionModel model;
        try {
            model = adapter.adapt(definitionJson);
        } catch (IllegalArgumentException exception) {
            return new ValidationResult(null, List.of(error(null, null, "definitionJson", "JSON_INVALID", exception.getMessage())));
        }
        List<ValidationError> errors = new ArrayList<>();
        validateModel(model, errors);
        return new ValidationResult(model, List.copyOf(errors));
    }

    public WorkflowDefinitionModel requireValid(String definitionJson) {
        ValidationResult result = validate(definitionJson);
        if (!result.valid()) {
            ValidationError first = result.errors().get(0);
            throw new BusinessException(ErrorCode.BAD_REQUEST, first.message());
        }
        return result.model();
    }

    public void requireValid(WorkflowDefinitionModel model) {
        List<ValidationError> errors = new ArrayList<>();
        validateModel(model, errors);
        if (!errors.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, errors.get(0).message());
    }

    private void validateModel(WorkflowDefinitionModel model, List<ValidationError> errors) {
        if (model.schemaVersion() != 1 && model.schemaVersion() != 2) {
            errors.add(error(null, null, "schemaVersion", "SCHEMA_VERSION_UNSUPPORTED", "不支持的流程定义版本: " + model.schemaVersion()));
        }
        if (model.nodes().isEmpty()) {
            errors.add(error(null, null, "nodes", "NODES_EMPTY", "流程节点不能为空"));
            return;
        }

        Map<String, WorkflowNodeModel> nodes = new LinkedHashMap<>();
        for (WorkflowNodeModel node : model.nodes()) {
            if (node.id() == null || node.id().isBlank()) {
                errors.add(error(null, null, "id", "NODE_ID_EMPTY", "流程节点编码不能为空"));
                continue;
            }
            if (nodes.putIfAbsent(node.id(), node) != null) {
                errors.add(error(node.id(), null, "id", "NODE_ID_DUPLICATE", "流程节点编码重复: " + node.id()));
            }
            if (!NODE_TYPES.contains(node.type())) {
                errors.add(error(node.id(), null, "type", "NODE_TYPE_UNSUPPORTED", "不支持的流程节点类型: " + node.type()));
            }
            if (node.label() == null || node.label().isBlank()) {
                errors.add(error(node.id(), null, "label", "NODE_LABEL_EMPTY", "流程节点名称不能为空"));
            }
        }

        List<WorkflowNodeModel> starts = model.nodes().stream().filter(node -> "START".equals(node.type())).toList();
        List<WorkflowNodeModel> ends = model.nodes().stream().filter(node -> "END".equals(node.type())).toList();
        if (starts.size() != 1) errors.add(error(null, null, "nodes", "START_COUNT_INVALID", "流程必须且只能有一个开始节点"));
        if (ends.size() != 1) errors.add(error(null, null, "nodes", "END_COUNT_INVALID", "流程必须且只能有一个结束节点"));

        Map<String, List<WorkflowEdgeModel>> outgoing = new HashMap<>();
        Map<String, List<WorkflowEdgeModel>> incoming = new HashMap<>();
        Set<String> edgeIds = new HashSet<>();
        for (WorkflowEdgeModel edge : model.edges()) {
            if (!edgeIds.add(edge.id())) errors.add(error(null, edge.id(), "id", "EDGE_ID_DUPLICATE", "流程连线编码重复: " + edge.id()));
            if (!nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target())) {
                errors.add(error(null, edge.id(), "source/target", "EDGE_NODE_MISSING", "流程连线引用了不存在的节点"));
                continue;
            }
            if (edge.source().equals(edge.target())) {
                errors.add(error(null, edge.id(), "source/target", "EDGE_SELF_REFERENCE", "流程连线不能连接自身"));
            }
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
        }

        for (WorkflowNodeModel node : model.nodes()) {
            int in = incoming.getOrDefault(node.id(), List.of()).size();
            int out = outgoing.getOrDefault(node.id(), List.of()).size();
            switch (node.type()) {
                case "START" -> {
                    if (in != 0 || out != 1) errors.add(error(node.id(), null, "edges", "START_DEGREE_INVALID", "开始节点必须无入边且只有一条出边"));
                }
                case "END" -> {
                    if (in < 1 || out != 0) errors.add(error(node.id(), null, "edges", "END_DEGREE_INVALID", "结束节点必须至少有一条入边且无出边"));
                }
                case "CONDITION" -> validateConditionGateway(node, outgoing.getOrDefault(node.id(), List.of()), errors);
                case "PARALLEL_SPLIT" -> {
                    if (in != 1 || out < 2) errors.add(error(node.id(), null, "edges", "PARALLEL_SPLIT_DEGREE_INVALID", "并行分支网关必须一入多出"));
                }
                case "PARALLEL_JOIN" -> {
                    if (in < 2 || out != 1) errors.add(error(node.id(), null, "edges", "PARALLEL_JOIN_DEGREE_INVALID", "并行汇聚网关必须多入一出"));
                }
                case "APPROVAL", "CC" -> {
                    if (in != 1 || out != 1) errors.add(error(node.id(), null, "edges", "TASK_DEGREE_INVALID", "审批或抄送节点【" + node.label() + "】必须一入一出"));
                    validateTask(node, model, errors);
                }
                default -> {
                    // The unsupported type error above is the actionable finding.
                }
            }
        }

        if (starts.size() == 1) {
            Set<String> reachable = reachable(starts.get(0).id(), outgoing);
            if (reachable.size() != nodes.size()) {
                errors.add(error(null, null, "nodes", "NODE_UNREACHABLE", "流程存在从开始节点不可达的节点"));
            }
            if (hasCycle(nodes.keySet(), outgoing)) {
                errors.add(error(null, null, "edges", "GRAPH_CYCLE", "流程连线不能形成环路"));
            }
        }

        validateVariables(model.variables(), errors);
        Set<String> variableNames = model.variables().stream().map(WorkflowVariableModel::name).collect(java.util.stream.Collectors.toSet());
        validateFormBindings(model.formBindings(), nodes, variableNames, errors);
        validateConditions(model.edges(), outgoing, nodes, errors);
        validatePolicies(model.actionPolicies(), nodes, errors);
    }

    private void validateConditionGateway(WorkflowNodeModel node, List<WorkflowEdgeModel> edges, List<ValidationError> errors) {
        if (edges.size() < 2) errors.add(error(node.id(), null, "edges", "CONDITION_BRANCHES_TOO_FEW", "条件网关至少需要两条分支"));
        long defaults = edges.stream().filter(WorkflowEdgeModel::defaultFlow).count();
        String configuredDefault = node.config().path("defaultEdgeId").asText("");
        boolean configuredEdgeExists = edges.stream().anyMatch(edge -> edge.id().equals(configuredDefault));
        if (defaults == 0 && !configuredEdgeExists) errors.add(error(node.id(), null, "defaultEdgeId", "DEFAULT_BRANCH_MISSING", "条件网关必须配置默认分支"));
        if (defaults > 1) errors.add(error(node.id(), null, "defaultEdgeId", "DEFAULT_BRANCH_DUPLICATE", "条件网关只能有一条默认分支"));
        if (!configuredDefault.isBlank() && !configuredEdgeExists) errors.add(error(node.id(), null, "defaultEdgeId", "DEFAULT_BRANCH_MISSING", "条件网关默认分支不存在"));
        if (configuredEdgeExists && defaults == 1 && edges.stream().noneMatch(edge -> edge.defaultFlow() && edge.id().equals(configuredDefault))) {
            errors.add(error(node.id(), null, "defaultEdgeId", "DEFAULT_BRANCH_DUPLICATE", "条件网关只能有一条默认分支"));
        }
    }

    private void validateTask(WorkflowNodeModel node, WorkflowDefinitionModel model, List<ValidationError> errors) {
        JsonNode config = node.config();
        if ("CC".equals(node.type())) {
            if (!positiveIds(config.path("userIds"))) errors.add(error(node.id(), null, "userIds", "CC_RECIPIENT_MISSING", "抄送节点必须配置抄送人员"));
            return;
        }
        String assigneeType = config.path("assigneeType").asText("").toUpperCase(Locale.ROOT);
        if (!ASSIGNEE_TYPES.contains(assigneeType)) {
            errors.add(error(node.id(), null, "assigneeType", "ASSIGNEE_TYPE_INVALID", "审批人类型无效: " + assigneeType));
        } else if (Set.of("USER", "ROLE", "PROJECT_MEMBER", "PROJECT_ROLE").contains(assigneeType)
                && !positiveIds(config.path("assigneeIds"))) {
            errors.add(error(node.id(), null, "assigneeIds", "ASSIGNEE_MISSING", "审批节点必须配置审批人或角色"));
        } else if ("FORM_FIELD".equals(assigneeType) && config.path("fieldName").asText("").isBlank()) {
            errors.add(error(node.id(), null, "fieldName", "ASSIGNEE_FIELD_MISSING", "表单字段审批人必须配置字段名"));
        } else if ("EXPRESSION".equals(assigneeType) && config.path("expression").asText("").isBlank()) {
            errors.add(error(node.id(), null, "expression", "ASSIGNEE_EXPRESSION_MISSING", "表达式审批人必须配置表达式"));
        }
        String mode = config.path("mode").asText("ANY").toUpperCase(Locale.ROOT);
        if (!APPROVAL_MODES.contains(mode)) errors.add(error(node.id(), null, "mode", "APPROVAL_MODE_INVALID", "审批模式无效: " + mode));
        if ("PERCENT".equals(mode) && (config.path("percentage").asInt(0) < 1 || config.path("percentage").asInt(0) > 100)) {
            errors.add(error(node.id(), null, "percentage", "APPROVAL_PERCENT_INVALID", "比例会签必须配置 1 到 100 的通过比例"));
        }
        String emptyAction = config.path("emptyAssigneeAction").asText("ERROR").toUpperCase(Locale.ROOT);
        if (!Set.of("ERROR", "WAIT").contains(emptyAction)) errors.add(error(node.id(), null, "emptyAssigneeAction", "EMPTY_ASSIGNEE_ACTION_INVALID", "空审批人的处理方式只能是 ERROR 或 WAIT"));
        JsonNode multiInstance = config.path("multiInstance");
        if (multiInstance.isBoolean() && multiInstance.asBoolean()) {
            String collectionVariable = config.path("collectionVariable").asText("");
            boolean declared = model.variables().stream().anyMatch(variable -> variable.name().equals(collectionVariable));
            if (collectionVariable.isBlank() || !declared) errors.add(error(node.id(), null, "collectionVariable", "MULTI_INSTANCE_COLLECTION_MISSING", "会签集合变量必须已声明"));
        }
    }

    private void validateVariables(List<WorkflowVariableModel> variables, List<ValidationError> errors) {
        Set<String> names = new HashSet<>();
        for (WorkflowVariableModel variable : variables) {
            if (!names.add(variable.name())) errors.add(error(null, null, "name", "VARIABLE_DUPLICATE", "流程变量名称重复: " + variable.name()));
            if (!VARIABLE_TYPES.contains(variable.type())) errors.add(error(null, null, "type", "VARIABLE_TYPE_INVALID", "流程变量类型无效: " + variable.type()));
            if (!Set.of("PROCESS", "TASK").contains(variable.scope())) errors.add(error(null, null, "scope", "VARIABLE_SCOPE_INVALID", "流程变量作用域无效: " + variable.scope()));
        }
    }

    private void validateFormBindings(List<WorkflowFormBindingModel> bindings, Map<String, WorkflowNodeModel> nodes, Set<String> variables, List<ValidationError> errors) {
        for (WorkflowFormBindingModel binding : bindings) {
            if (!nodes.containsKey(binding.nodeId())) errors.add(error(binding.nodeId(), null, "nodeId", "FORM_NODE_MISSING", "表单绑定引用了不存在的节点"));
            if (!variables.contains(binding.variableName())) errors.add(error(binding.nodeId(), null, "variableName", "FORM_VARIABLE_MISSING", "表单绑定引用了未声明的变量: " + binding.variableName()));
        }
    }

    private void validateConditions(List<WorkflowEdgeModel> edges, Map<String, List<WorkflowEdgeModel>> outgoing,
                                    Map<String, WorkflowNodeModel> nodes, List<ValidationError> errors) {
        for (WorkflowEdgeModel edge : edges) {
            WorkflowNodeModel source = nodes.get(edge.source());
            boolean conditionGateway = source != null && "CONDITION".equals(source.type());
            if (!conditionGateway && (edge.defaultFlow() || (edge.condition() != null && !edge.condition().isBlank()))) {
                errors.add(error(null, edge.id(), "condition", "EDGE_CONDITION_NOT_ALLOWED", "普通连线不能配置条件表达式或默认分支"));
                continue;
            }
            boolean configuredDefault = conditionGateway && edge.id().equals(source.config().path("defaultEdgeId").asText(""));
            boolean effectiveDefault = edge.defaultFlow() || configuredDefault;
            if (conditionGateway && effectiveDefault && edge.condition() != null && !edge.condition().isBlank()) {
                errors.add(error(null, edge.id(), "condition", "DEFAULT_BRANCH_CONDITION_FORBIDDEN", "默认分支不能配置条件表达式"));
                continue;
            }
            if (conditionGateway && !effectiveDefault && (edge.condition() == null || edge.condition().isBlank())) {
                errors.add(error(null, edge.id(), "condition", "CONDITION_EXPRESSION_MISSING", "条件网关的非默认分支必须配置条件表达式"));
                continue;
            }
            if (edge.condition() == null || edge.condition().isBlank()) continue;
            if (!edge.condition().startsWith("${") || !edge.condition().endsWith("}")) {
                errors.add(error(null, edge.id(), "condition", "CONDITION_EXPRESSION_INVALID", "条件表达式必须使用 ${...} 格式"));
            }
            // Condition values are supplied at process start, so custom runtime variables
            // do not need to be declared in the design-time variable metadata.
        }
        for (Map.Entry<String, List<WorkflowEdgeModel>> entry : outgoing.entrySet()) {
            long defaults = entry.getValue().stream().filter(WorkflowEdgeModel::defaultFlow).count();
            if (defaults > 1) errors.add(error(entry.getKey(), null, "default", "DEFAULT_BRANCH_DUPLICATE", "同一网关只能配置一条默认分支"));
        }
    }

    private void validatePolicies(Map<String, WorkflowActionPolicy> policies, Map<String, WorkflowNodeModel> nodes, List<ValidationError> errors) {
        for (Map.Entry<String, WorkflowActionPolicy> entry : policies.entrySet()) {
            if (!nodes.containsKey(entry.getKey())) errors.add(error(entry.getKey(), null, "actionPolicy", "POLICY_NODE_MISSING", "动作策略引用了不存在的节点"));
            for (String action : entry.getValue().allowedActions()) {
                if (!WorkflowActionPolicy.KNOWN_ACTIONS.contains(action)) errors.add(error(entry.getKey(), null, "allowedActions", "ACTION_INVALID", "不支持的审批动作: " + action));
            }
        }
    }

    private Set<String> reachable(String start, Map<String, List<WorkflowEdgeModel>> outgoing) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            for (WorkflowEdgeModel edge : outgoing.getOrDefault(current, List.of())) queue.add(edge.target());
        }
        return visited;
    }

    private boolean hasCycle(Set<String> nodeIds, Map<String, List<WorkflowEdgeModel>> outgoing) {
        Map<String, Integer> indegree = new HashMap<>();
        for (String nodeId : nodeIds) indegree.put(nodeId, 0);
        for (List<WorkflowEdgeModel> edges : outgoing.values()) for (WorkflowEdgeModel edge : edges) indegree.computeIfPresent(edge.target(), (ignored, count) -> count + 1);
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((node, count) -> { if (count == 0) queue.add(node); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (WorkflowEdgeModel edge : outgoing.getOrDefault(current, List.of())) {
                int remaining = indegree.computeIfPresent(edge.target(), (ignored, count) -> count - 1);
                if (remaining == 0) queue.add(edge.target());
            }
        }
        return visited != nodeIds.size();
    }

    private boolean positiveIds(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return false;
        for (JsonNode id : node) if (!id.canConvertToLong() || id.asLong() <= 0) return false;
        return true;
    }

    private ValidationError error(String nodeId, String edgeId, String field, String code, String message) {
        return new ValidationError(nodeId, edgeId, field, code, message);
    }

    public record ValidationResult(WorkflowDefinitionModel model, List<ValidationError> errors) {
        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }

    public record ValidationError(String nodeId, String edgeId, String field, String code, String message) {
    }
}
