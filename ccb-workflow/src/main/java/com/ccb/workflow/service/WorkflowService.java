package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class WorkflowService {
    private static final String APPROVE = "APPROVE";
    private static final String REJECT = "REJECT";
    private static final String ADD_SIGN = "ADD_SIGN";
    private static final String CC = "CC";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionValidator validator;
    private final FlowableWorkflowService flowableWorkflowService;
    private final WorkflowMonitorService workflowMonitorService;

    public WorkflowService(JdbcTemplate jdbc, ObjectMapper objectMapper, FlowableWorkflowService flowableWorkflowService, WorkflowMonitorService workflowMonitorService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.validator = new WorkflowDefinitionValidator(objectMapper);
        this.flowableWorkflowService = flowableWorkflowService;
        this.workflowMonitorService = workflowMonitorService;
    }

    public List<Map<String, Object>> definitions(AuthUser user) {
        return jdbc.queryForList("SELECT id, code, name, status, current_version, created_at FROM wf_definition WHERE tenant_id = ? AND deleted = 0 ORDER BY id DESC", user.tenantId());
    }

    @Transactional
    public Map<String, Object> createDefinition(String code, String name, String definitionJson, AuthUser user) {
        if (flowableWorkflowService.isEnterpriseDefinition(definitionJson)) return flowableWorkflowService.createDefinition(code, name, definitionJson, user);
        validator.parse(definitionJson);
        long id = nextId();
        jdbc.update("INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, deleted) VALUES (?, ?, ?, ?, 'DRAFT', 0, 0)", id, user.tenantId(), requireText(code, "流程编码"), requireText(name, "流程名称"));
        jdbc.update("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, status) VALUES (?, ?, ?, 1, ?, 'DRAFT')", nextId(), user.tenantId(), id, definitionJson);
        audit(user, "workflow.definition.create");
        return jdbc.queryForMap("SELECT id, code, name, status, current_version FROM wf_definition WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public void publish(long definitionId, AuthUser user) {
        if (enterpriseDefinition(definitionId, user.tenantId())) { flowableWorkflowService.publish(definitionId, user); return; }
        Map<String, Object> version = jdbc.queryForMap("SELECT version_no, definition_json FROM wf_version WHERE definition_id = ? AND tenant_id = ? ORDER BY version_no DESC LIMIT 1", definitionId, user.tenantId());
        validator.parse(String.valueOf(version.get("definition_json")));
        int versionNo = ((Number) version.get("version_no")).intValue();
        jdbc.update("UPDATE wf_version SET status = 'PUBLISHED' WHERE definition_id = ? AND tenant_id = ? AND version_no = ?", definitionId, user.tenantId(), versionNo);
        jdbc.update("UPDATE wf_definition SET status = 'PUBLISHED', current_version = ? WHERE id = ? AND tenant_id = ?", versionNo, definitionId, user.tenantId());
        audit(user, "workflow.definition.publish");
    }

    @Transactional
    public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> variables, AuthUser user) {
        if (enterpriseDefinition(definitionId, user.tenantId())) return flowableWorkflowService.start(definitionId, businessKey, variables, user);
        Map<String, Object> definition = jdbc.queryForMap("SELECT d.current_version, v.definition_json FROM wf_definition d JOIN wf_version v ON v.definition_id = d.id AND v.tenant_id = d.tenant_id AND v.version_no = d.current_version WHERE d.id = ? AND d.tenant_id = ? AND d.status = 'PUBLISHED' AND v.status = 'PUBLISHED'", definitionId, user.tenantId());
        WorkflowDefinitionValidator.WorkflowGraph graph = validator.parse(String.valueOf(definition.get("definition_json")));
        long instanceId = nextId();
        jdbc.update("INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, starter_id) VALUES (?, ?, ?, ?, ?, 'RUNNING', ?)", instanceId, user.tenantId(), definitionId, definition.get("current_version"), requireText(businessKey, "业务单号"), user.id());
        advanceFromNode(instanceId, user.tenantId(), user.id(), graph, "start");
        audit(user, "workflow.instance.start");
        return jdbc.queryForMap("SELECT id, definition_id, version_no, business_key, status FROM wf_instance WHERE id = ? AND tenant_id = ?", instanceId, user.tenantId());
    }

    public void terminate(long instanceId, String reason, AuthUser user) {
        workflowMonitorService.terminate(instanceId, reason, user);
    }

    public List<Map<String, Object>> instances(AuthUser user) {
        return workflowMonitorService.instances(user);
    }

    public List<Map<String, Object>> timeline(long instanceId, AuthUser user) {
        return workflowMonitorService.timeline(instanceId, user);
    }

    public List<Map<String, Object>> inbox(AuthUser user) {
        List<Map<String, Object>> result = new ArrayList<>(flowableWorkflowService.inbox(user));
        result.addAll(jdbc.queryForList("SELECT t.id, t.instance_id, t.task_key, t.node_id, t.task_type, t.task_group_key, t.status, COALESCE(t.assignee_name, u.display_name) AS assignee_name, t.created_at, i.business_key, i.status AS instance_status FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id LEFT JOIN sys_user u ON u.id = t.assignee_id AND u.tenant_id = t.tenant_id WHERE t.tenant_id = ? AND t.assignee_id = ? AND t.flowable_task_id IS NULL AND t.status IN ('PENDING', 'SENT') ORDER BY t.id DESC", user.tenantId(), user.id()));
        return result;
    }

    @Transactional
    public void decide(long taskId, String action, String comment, Long targetUserId, List<Long> ccUserIds, AuthUser user) {
        Integer flowableTask = jdbc.queryForObject("SELECT COUNT(*) FROM wf_task WHERE id = ? AND tenant_id = ? AND flowable_task_id IS NOT NULL", Integer.class, taskId, user.tenantId());
        if (flowableTask != null && flowableTask > 0) { flowableWorkflowService.decide(taskId, action, comment, targetUserId, ccUserIds, user); return; }
        String normalizedAction = requireText(action, "审批动作").toUpperCase();
        if (!Set.of(APPROVE, REJECT, ADD_SIGN, CC).contains(normalizedAction)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的审批动作：" + normalizedAction);
        Map<String, Object> task = findPendingTask(taskId, user);
        String taskType = String.valueOf(task.get("task_type"));
        if ("CC".equals(taskType)) throw new BusinessException(ErrorCode.BAD_REQUEST, "抄送记录不能执行审批动作");

        if (CC.equals(normalizedAction)) {
            List<Long> recipients = ccUserIds == null ? List.of() : ccUserIds;
            if (recipients.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择抄送人员");
            createCcTasks(((Number) task.get("instance_id")).longValue(), user.tenantId(), String.valueOf(task.get("task_key")), recipients, user.id(), comment);
            recordAction(task, normalizedAction, user, null, comment, Map.of("userIds", recipients));
            audit(user, "workflow.task.cc");
            return;
        }

        if (ADD_SIGN.equals(normalizedAction)) {
            if (targetUserId == null || targetUserId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择加签人员");
            Assignee target = findActiveUser(targetUserId, user.tenantId());
            if (target.id() == user.id()) throw new BusinessException(ErrorCode.BAD_REQUEST, "加签人员不能是当前审批人");
            jdbc.update("INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, parent_task_id, assignee_type, assignee_name, assignee_id, status) VALUES (?, ?, ?, ?, ?, 'ADD_SIGN', ?, ?, 'USER', ?, ?, 'PENDING')", nextId(), user.tenantId(), task.get("instance_id"), task.get("task_key"), task.get("node_id"), UUID.randomUUID().toString(), task.get("id"), target.name(), target.id());
            recordAction(task, normalizedAction, user, target.id(), comment, Map.of("targetUserId", target.id()));
            audit(user, "workflow.task.add-sign");
            return;
        }

        String status = APPROVE.equals(normalizedAction) ? APPROVE : REJECT;
        int changed = jdbc.update("UPDATE wf_task SET status = ?, comment = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND assignee_id = ? AND status = 'PENDING'", status, comment, taskId, user.tenantId(), user.id());
        if (changed == 0) throw new BusinessException(ErrorCode.CONFLICT, "任务已处理或已不属于当前账号");
        recordAction(task, normalizedAction, user, null, comment, Map.of());

        long instanceId = ((Number) task.get("instance_id")).longValue();
        if (REJECT.equals(normalizedAction)) {
            cancelPendingInstanceTasks(instanceId, user.tenantId(), taskId);
            jdbc.update("UPDATE wf_instance SET status = 'REJECTED' WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", instanceId, user.tenantId());
            audit(user, "workflow.task.reject");
            return;
        }

        String taskTypeValue = String.valueOf(task.get("task_type"));
        if ("ADD_SIGN".equals(taskTypeValue)) {
            audit(user, "workflow.task.add-sign-approve");
            return;
        }

        String groupKey = String.valueOf(task.get("task_group_key"));
        WorkflowDefinitionValidator.WorkflowGraph graph = loadGraph(task);
        WorkflowDefinitionValidator.WorkflowNode currentNode = graph.node(String.valueOf(task.get("node_id")));
        String mode = currentNode == null ? "ANY" : currentNode.config().path("mode").asText("ANY").toUpperCase();
        if ("ANY".equals(mode)) cancelSiblingTasks(task, user.tenantId(), taskId);
        if ("ALL".equals(mode) && hasPendingGroup(groupKey, user.tenantId())) {
            jdbc.update("UPDATE wf_instance SET status = 'RUNNING' WHERE id = ? AND tenant_id = ?", instanceId, user.tenantId());
            audit(user, "workflow.task.approve");
            return;
        }
        advanceFromNode(instanceId, user.tenantId(), ((Number) task.get("starter_id")).longValue(), graph, String.valueOf(task.get("node_id")));
        audit(user, "workflow.task.approve");
    }

    private Map<String, Object> findPendingTask(long taskId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id, t.tenant_id, t.instance_id, t.task_key, t.node_id, t.task_type, t.task_group_key, t.parent_task_id, t.assignee_id, i.definition_id, i.version_no, i.starter_id FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id WHERE t.id = ? AND t.tenant_id = ? AND t.assignee_id = ? AND t.status = 'PENDING'", taskId, user.tenantId(), user.id());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.CONFLICT, "任务已处理或已不属于当前账号");
        return rows.get(0);
    }

    private WorkflowDefinitionValidator.WorkflowGraph loadGraph(Map<String, Object> task) {
        Map<String, Object> version = jdbc.queryForMap("SELECT definition_json FROM wf_version WHERE definition_id = ? AND version_no = ? AND tenant_id = ?", task.get("definition_id"), task.get("version_no"), task.get("tenant_id"));
        return validator.parse(String.valueOf(version.get("definition_json")));
    }

    private void advanceFromNode(long instanceId, long tenantId, long starterId, WorkflowDefinitionValidator.WorkflowGraph graph, String currentNodeId) {
        String nodeId = currentNodeId;
        while (true) {
            WorkflowDefinitionValidator.WorkflowEdge edge = graph.outgoing(nodeId);
            if (edge == null) throw new BusinessException(ErrorCode.CONFLICT, "流程节点没有下一步：" + nodeId);
            WorkflowDefinitionValidator.WorkflowNode next = graph.node(edge.target());
            if (next == null) throw new BusinessException(ErrorCode.CONFLICT, "流程下一节点不存在");
            if ("END".equals(next.type())) {
                jdbc.update("UPDATE wf_instance SET status = 'APPROVED' WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", instanceId, tenantId);
                return;
            }
            if ("CC".equals(next.type())) {
                List<Long> userIds = ids(next.config().path("userIds"));
                createCcTasks(instanceId, tenantId, next.id(), userIds, starterId, "流程节点抄送");
                nodeId = next.id();
                continue;
            }
            if ("APPROVAL".equals(next.type())) {
                createApprovalTasks(instanceId, tenantId, starterId, next);
                jdbc.update("UPDATE wf_instance SET status = 'RUNNING' WHERE id = ? AND tenant_id = ?", instanceId, tenantId);
                return;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "流程存在不支持的运行节点：" + next.type());
        }
    }

    private void createApprovalTasks(long instanceId, long tenantId, long starterId, WorkflowDefinitionValidator.WorkflowNode node) {
        List<Assignee> assignees = resolveAssignees(node.config(), tenantId, starterId);
        String groupKey = UUID.randomUUID().toString();
        String assigneeType = node.config().path("assigneeType").asText("USER").toUpperCase();
        for (Assignee assignee : assignees) {
            jdbc.update("INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status) VALUES (?, ?, ?, ?, ?, 'APPROVAL', ?, ?, ?, ?, 'PENDING')", nextId(), tenantId, instanceId, node.id(), node.id(), groupKey, assigneeType, assignee.name(), assignee.id());
        }
    }

    private List<Assignee> resolveAssignees(JsonNode config, long tenantId, long starterId) {
        String type = config.path("assigneeType").asText("").toUpperCase();
        if ("STARTER".equals(type)) return List.of(findActiveUser(starterId, tenantId));
        List<Long> ids = ids(config.path("assigneeIds"));
        if (ids.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "审批节点未配置审批人");
        String placeholders = placeholders(ids.size());
        String sql;
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        if ("ROLE".equals(type)) {
            sql = "SELECT DISTINCT u.id, u.display_name FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE u.tenant_id = ? AND u.deleted = 0 AND u.status = 1 AND r.deleted = 0 AND ur.role_id IN (" + placeholders + ") ORDER BY u.id";
        } else if ("USER".equals(type)) {
            sql = "SELECT u.id, u.display_name FROM sys_user u WHERE u.tenant_id = ? AND u.deleted = 0 AND u.status = 1 AND u.id IN (" + placeholders + ") ORDER BY u.id";
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批人类型不支持：" + type);
        }
        List<Assignee> result = jdbc.query(sql, args.toArray(), (rs, rowNum) -> new Assignee(rs.getLong("id"), rs.getString("display_name")));
        if (result.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "审批人不存在或已停用");
        if ("USER".equals(type) && result.size() != new HashSet<>(ids).size()) throw new BusinessException(ErrorCode.BAD_REQUEST, "部分审批人不存在或已停用");
        return result;
    }

    private void createCcTasks(long instanceId, long tenantId, String taskKey, List<Long> userIds, long operatorId, String comment) {
        if (userIds == null || userIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "抄送节点未配置抄送人");
        List<Assignee> recipients = userIds.stream().distinct().map(id -> findActiveUser(id, tenantId)).toList();
        for (Assignee recipient : recipients) {
            long taskId = nextId();
            jdbc.update("INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, comment, completed_at) VALUES (?, ?, ?, ?, ?, 'CC', ?, 'USER', ?, ?, 'SENT', ?, CURRENT_TIMESTAMP)", taskId, tenantId, instanceId, taskKey, taskKey, UUID.randomUUID().toString(), recipient.name(), recipient.id(), comment);
            jdbc.update("INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, target_user_id, comment) VALUES (?, ?, ?, ?, 'CC', ?, ?, ?)", nextId(), tenantId, instanceId, taskId, operatorId, recipient.id(), comment);
        }
    }

    private Assignee findActiveUser(long userId, long tenantId) {
        List<Assignee> rows = jdbc.query("SELECT id, display_name FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0 AND status = 1", (rs, rowNum) -> new Assignee(rs.getLong("id"), rs.getString("display_name")), userId, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "目标用户不存在或已停用");
        return rows.get(0);
    }

    private void cancelPendingInstanceTasks(long instanceId, long tenantId, long currentTaskId) {
        jdbc.update("UPDATE wf_task SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND instance_id = ? AND id <> ? AND status = 'PENDING'", tenantId, instanceId, currentTaskId);
    }
    private void cancelSiblingTasks(Map<String, Object> task, long tenantId, long currentTaskId) {
        Object group = task.get("task_group_key");
        if (group == null || "null".equals(String.valueOf(group))) return;
        jdbc.update("UPDATE wf_task SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND instance_id = ? AND task_group_key = ? AND id <> ? AND status = 'PENDING'", tenantId, task.get("instance_id"), group, currentTaskId);
    }

    private boolean hasPendingGroup(String groupKey, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wf_task WHERE tenant_id = ? AND task_group_key = ? AND status = 'PENDING'", Integer.class, tenantId, groupKey);
        return count != null && count > 0;
    }

    private void recordAction(Map<String, Object> task, String action, AuthUser user, Long targetUserId, String comment, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = payload == null || payload.isEmpty() ? null : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审批动作记录失败");
        }
        jdbc.update("INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, target_user_id, comment, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), task.get("instance_id"), task.get("id"), action, user.id(), targetUserId, comment, payloadJson);
    }

    private List<Long> ids(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<Long> result = new ArrayList<>();
        for (JsonNode item : node) {
            long id = item.asLong(0);
            if (id > 0 && !result.contains(id)) result.add(id);
        }
        return result;
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        return value.trim();
    }

    private void audit(AuthUser user, String code) {
        jdbc.update("INSERT INTO sys_operation_log (id, tenant_id, operator_id, operation_code, request_method, request_path, success) VALUES (?, ?, ?, ?, 'SERVICE', ?, 1)", nextId(), user.tenantId(), user.id(), code, "/api/workflows");
    }

    private boolean enterpriseDefinition(long definitionId, long tenantId) {
        Integer version = jdbc.queryForObject("SELECT model_schema_version FROM wf_definition WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, definitionId, tenantId);
        return version != null && version == 2;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private record Assignee(long id, String name) {}
}