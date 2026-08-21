package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.model.WorkflowDefinitionModel;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FlowableWorkflowService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final WorkflowModelValidator modelValidator;
    private final WorkflowModelAdapter modelAdapter;
    private final BpmnModelCompiler compiler;
    private final WorkflowAssigneeResolver assigneeResolver;
    private final WorkflowAuditService auditService;
    private WorkflowLifecycleEventService lifecycleEvents;
    private WorkflowSignatureService signatureService;
    private WorkflowTaskAssignmentPublisher taskAssignments;

    public FlowableWorkflowService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                   RepositoryService repositoryService, RuntimeService runtimeService,
                                   TaskService taskService, WorkflowAssigneeResolver assigneeResolver,
                                   WorkflowAuditService auditService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.assigneeResolver = assigneeResolver;
        this.auditService = auditService;
        this.modelValidator = new WorkflowModelValidator(objectMapper);
        this.modelAdapter = new WorkflowModelAdapter(objectMapper);
        this.compiler = new BpmnModelCompiler(objectMapper);
    }

    @Autowired(required = false)
    void setLifecycleEvents(WorkflowLifecycleEventService lifecycleEvents) {
        this.lifecycleEvents = lifecycleEvents;
    }

    @Autowired(required = false)
    void setSignatureService(WorkflowSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Autowired(required = false)
    void setTaskAssignments(WorkflowTaskAssignmentPublisher taskAssignments) {
        this.taskAssignments = taskAssignments;
    }

    public boolean isEnterpriseDefinition(String definitionJson) {
        return modelAdapter.adapt(definitionJson).schemaVersion() == 2;
    }

    @Transactional
    public Map<String, Object> createDefinition(String code, String name, String definitionJson, AuthUser user) {
        WorkflowDefinitionModel model = modelValidator.requireValid(definitionJson);
        long id = nextId();
        jdbc.update("INSERT INTO wf_definition (id, tenant_id, code, name, status, current_version, model_schema_version, deleted) VALUES (?, ?, ?, ?, 'DRAFT', 0, ?, 0)",
                id, user.tenantId(), requireText(code, "流程编码"), requireText(name, "流程名称"), model.schemaVersion());
        jdbc.update("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status) VALUES (?, ?, ?, 1, ?, ?, 'DRAFT')",
                nextId(), user.tenantId(), id, definitionJson, model.schemaVersion());
        auditService.record(user, "DEFINITION_CREATED", id, 1, null, null, null, Map.of("code", code));
        return jdbc.queryForMap("SELECT id, code, name, status, current_version, model_schema_version FROM wf_definition WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public void updateDefinition(long definitionId, String code, String name, String definitionJson, AuthUser user) {
        WorkflowDefinitionModel model = modelValidator.requireValid(definitionJson);
        Map<String, Object> definition = findDefinition(definitionId, user.tenantId());
        if (!"DRAFT".equals(String.valueOf(definition.get("status")))) throw new BusinessException(ErrorCode.CONFLICT, "已发布流程不能编辑，请复制后创建新版本");
        Map<String, Object> version = jdbc.queryForMap("SELECT version_no FROM wf_version WHERE definition_id = ? AND tenant_id = ? AND status = 'DRAFT' ORDER BY version_no DESC LIMIT 1", definitionId, user.tenantId());
        jdbc.update("UPDATE wf_definition SET code = ?, name = ?, model_schema_version = ? WHERE id = ? AND tenant_id = ? AND deleted = 0 AND status = 'DRAFT'", requireText(code, "流程编码"), requireText(name, "流程名称"), model.schemaVersion(), definitionId, user.tenantId());
        jdbc.update("UPDATE wf_version SET definition_json = ?, model_schema_version = ? WHERE definition_id = ? AND tenant_id = ? AND version_no = ? AND status = 'DRAFT'", definitionJson, model.schemaVersion(), definitionId, user.tenantId(), version.get("version_no"));
        auditService.record(user, "DEFINITION_UPDATED", definitionId, ((Number) version.get("version_no")).intValue(), null, null, null, Map.of("code", code));
    }

    @Transactional
    public void publish(long definitionId, AuthUser user) {
        Map<String, Object> definition = findDefinition(definitionId, user.tenantId());
        Map<String, Object> version = jdbc.queryForMap("SELECT version_no, definition_json FROM wf_version WHERE definition_id = ? AND tenant_id = ? ORDER BY version_no DESC LIMIT 1", definitionId, user.tenantId());
        String json = String.valueOf(version.get("definition_json"));
        // Validate before deployment so invalid gateway models return a business error
        // instead of being converted into a generic deployment failure.
        modelValidator.requireValid(json);
        BpmnModelCompiler.CompiledBpmn compiled = compiler.compile(String.valueOf(definition.get("code")), json);
        Deployment deployment = repositoryService.createDeployment()
                .name(String.valueOf(definition.get("name")))
                .key(String.valueOf(definition.get("code")))
                .tenantId(String.valueOf(user.tenantId()))
                .addString(String.valueOf(definition.get("code")) + ".bpmn20.xml", compiled.xml())
                .deploy();
        ProcessDefinition processDefinition = processDefinition(deployment.getId());
        String mappingJson = writeJson(compiled.nodeMapping());
        int versionNo = ((Number) version.get("version_no")).intValue();
        jdbc.update("UPDATE wf_version SET status = 'PUBLISHED', bpmn_xml = ?, deployment_id = ?, node_mapping_json = ? WHERE definition_id = ? AND tenant_id = ? AND version_no = ?",
                compiled.xml(), deployment.getId(), mappingJson, definitionId, user.tenantId(), versionNo);
        jdbc.update("UPDATE wf_definition SET status = 'PUBLISHED', current_version = ?, model_schema_version = ?, bpmn_xml = ?, deployment_id = ?, node_mapping_json = ? WHERE id = ? AND tenant_id = ?",
                versionNo, 2, compiled.xml(), deployment.getId(), mappingJson, definitionId, user.tenantId());
        auditService.record(user, "DEFINITION_PUBLISHED", definitionId, versionNo, null, null, null,
                Map.of("deploymentId", deployment.getId(), "processDefinitionId", processDefinition.getId()));
    }

    @Transactional
    public void unpublish(long definitionId, AuthUser user) {
        Map<String, Object> definition = findDefinition(definitionId, user.tenantId());
        if (!"PUBLISHED".equals(String.valueOf(definition.get("status")))) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已发布流程才能取消发布");
        }
        int currentVersion = ((Number) definition.get("current_version")).intValue();
        Map<String, Object> version = jdbc.queryForMap("SELECT definition_json, model_schema_version FROM wf_version WHERE definition_id = ? AND tenant_id = ? AND version_no = ? AND status = 'PUBLISHED'", definitionId, user.tenantId(), currentVersion);
        Integer nextVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) + 1 FROM wf_version WHERE definition_id = ? AND tenant_id = ?", Integer.class, definitionId, user.tenantId());
        int draftVersion = nextVersion == null ? currentVersion + 1 : nextVersion;
        jdbc.update("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT')",
                nextId(), user.tenantId(), definitionId, draftVersion, version.get("definition_json"), version.get("model_schema_version"));
        jdbc.update("UPDATE wf_definition SET status = 'DRAFT', current_version = ?, bpmn_xml = NULL, deployment_id = NULL, node_mapping_json = NULL WHERE id = ? AND tenant_id = ? AND deleted = 0",
                draftVersion, definitionId, user.tenantId());
        auditService.record(user, "DEFINITION_UNPUBLISHED", definitionId, draftVersion, null, null, null, Map.of("previousVersion", currentVersion));
    }
    @Transactional
    public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> inputVariables, AuthUser user) {
        Map<String, Object> definition = findPublishedDefinition(definitionId, user.tenantId());
        String json = String.valueOf(definition.get("definition_json"));
        WorkflowDefinitionValidator.WorkflowGraph graph = graph(json);
        WorkflowAssigneeResolver.ProcessVariables prepared = assigneeResolver.prepareProcessVariables(graph, user.tenantId(), user.id(), inputVariables);
        ProcessDefinition processDefinition = processDefinition(String.valueOf(definition.get("deployment_id")));
        org.flowable.engine.runtime.ProcessInstance processInstance = runtimeService.startProcessInstanceById(processDefinition.getId(), requireText(businessKey, "业务单号"), prepared.values());
        long instanceId = nextId();
        String variablesJson = writeJson(prepared.values());
        jdbc.update("INSERT INTO wf_instance (id, tenant_id, definition_id, version_no, business_key, status, starter_id, flowable_process_instance_id, flowable_process_definition_id, variables_json) VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?)",
                instanceId, user.tenantId(), definitionId, definition.get("current_version"), businessKey, user.id(), processInstance.getProcessInstanceId(), processInstance.getProcessDefinitionId(), variablesJson);
        syncTasks(instanceId, definitionId, ((Number) definition.get("current_version")).intValue(), processInstance.getProcessInstanceId(), user.tenantId(), user);
        auditService.record(user, "INSTANCE_STARTED", definitionId, ((Number) definition.get("current_version")).intValue(), instanceId, null, null,
                Map.of("businessKey", businessKey, "flowableProcessInstanceId", processInstance.getProcessInstanceId()));
        return jdbc.queryForMap("SELECT id, definition_id, version_no, business_key, status, flowable_process_instance_id FROM wf_instance WHERE id = ? AND tenant_id = ?", instanceId, user.tenantId());
    }

    public List<Map<String, Object>> inbox(AuthUser user) {
        return jdbc.queryForList("SELECT t.id, t.instance_id, t.task_key, t.node_id, t.task_type, t.status, COALESCE(t.assignee_name, u.display_name) AS assignee_name, t.created_at, i.business_key, i.status AS instance_status FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id LEFT JOIN sys_user u ON u.id = t.assignee_id AND u.tenant_id = t.tenant_id WHERE t.tenant_id = ? AND t.assignee_id = ? AND i.deleted = 0 AND t.flowable_task_id IS NOT NULL AND t.status IN ('PENDING', 'SENT') ORDER BY t.id DESC", user.tenantId(), user.id());
    }

    @Transactional
    public void decide(long taskId, String action, String comment, Long targetUserId, List<Long> ccUserIds, AuthUser user) {
        decide(taskId, action, comment, targetUserId, ccUserIds, false, user);
    }

    @Transactional
    public void decide(long taskId, String action, String comment, Long targetUserId, List<Long> ccUserIds,
                       boolean signatureConfirmed, AuthUser user) {
        Map<String, Object> appTask = findPendingTask(taskId, user);
        String normalized = requireText(action, "审批动作").toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT", "RETURN", "ADD_SIGN", "CC", "TRANSFER", "DELEGATE").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的审批动作: " + normalized);
        }
        long instanceId = ((Number) appTask.get("instance_id")).longValue();
        if (signatureService != null) signatureService.confirmIfRequired(taskId, normalized, comment, signatureConfirmed, user);
        String flowableTaskId = String.valueOf(appTask.get("flowable_task_id"));
        Task flowableTask = taskService.createTaskQuery().taskId(flowableTaskId).singleResult();
        if (flowableTask == null && !Set.of("ADD_SIGN", "CC").contains(normalized)) throw new BusinessException(ErrorCode.CONFLICT, "流程任务已结束或不存在");

        if ("CC".equals(normalized)) {
            createCcTasks(instanceId, user.tenantId(), ccUserIds, user.id(), comment);
            recordAction(appTask, normalized, user, null, comment, Map.of("userIds", ccUserIds == null ? List.of() : ccUserIds));
            auditService.record(user, "TASK_CC", definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of());
            return;
        }
        if ("ADD_SIGN".equals(normalized)) {
            long target = requireTarget(targetUserId);
            ensureActiveUser(target, user.tenantId());
            insertAddSignTask(appTask, target, user);
            recordAction(appTask, normalized, user, target, comment, Map.of("targetUserId", target));
            auditService.record(user, "TASK_ADD_SIGN", definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of("targetUserId", target));
            return;
        }
        if ("TRANSFER".equals(normalized) || "DELEGATE".equals(normalized)) {
            long target = requireTarget(targetUserId);
            ensureActiveUser(target, user.tenantId());
            if ("TRANSFER".equals(normalized)) taskService.setAssignee(flowableTaskId, String.valueOf(target));
            else taskService.delegateTask(flowableTaskId, String.valueOf(target));
            jdbc.update("UPDATE wf_task SET assignee_id = ?, assignee_name = (SELECT display_name FROM sys_user WHERE id = ? AND tenant_id = ?) WHERE id = ? AND tenant_id = ? AND status = 'PENDING'", target, target, user.tenantId(), taskId, user.tenantId());
            assigned(user.tenantId(), instanceId, taskId, target, user.id());
            recordAction(appTask, normalized, user, target, comment, Map.of("targetUserId", target));
            auditService.record(user, "TASK_" + normalized, definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of("targetUserId", target));
            return;
        }

        claimIfNeeded(flowableTask, user);
        if ("REJECT".equals(normalized) || "RETURN".equals(normalized)) {
            runtimeService.deleteProcessInstance(flowableTask.getProcessInstanceId(), normalized);
            jdbc.update("UPDATE wf_task SET status = ?, comment = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND status = 'PENDING'", "REJECT".equals(normalized) ? "REJECTED" : "RETURNED", comment, taskId, user.tenantId());
            jdbc.update("UPDATE wf_task SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP WHERE instance_id = ? AND tenant_id = ? AND id <> ? AND status = 'PENDING'", instanceId, user.tenantId(), taskId);
            jdbc.update("UPDATE wf_instance SET status = ? WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", "REJECT".equals(normalized) ? "REJECTED" : "RETURNED", instanceId, user.tenantId());
            emit(instanceId, "REJECT".equals(normalized) ? WorkflowLifecycleEventType.REJECTED : WorkflowLifecycleEventType.RETURNED, user);
        } else {
            taskService.complete(flowableTaskId);
            jdbc.update("UPDATE wf_task SET status = 'APPROVED', comment = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND status = 'PENDING'", comment, taskId, user.tenantId());
            syncTasks(instanceId, definitionId(appTask), versionNo(appTask), flowableTask.getProcessInstanceId(), user.tenantId(), user);
            refreshInstanceStatus(instanceId, user.tenantId());
            String status = jdbc.queryForObject("SELECT status FROM wf_instance WHERE id = ? AND tenant_id = ?", String.class, instanceId, user.tenantId());
            if ("APPROVED".equals(status)) emit(instanceId, WorkflowLifecycleEventType.APPROVED, user);
        }
        recordAction(appTask, normalized, user, null, comment, Map.of());
        auditService.record(user, "TASK_" + normalized, definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of());
    }

    public List<Map<String, Object>> instances(AuthUser user) {
        return jdbc.queryForList("SELECT i.id, i.definition_id, d.name AS definition_name, i.version_no, i.business_key, i.status, i.starter_id, u.display_name AS starter_name, i.created_at FROM wf_instance i JOIN wf_definition d ON d.id = i.definition_id AND d.tenant_id = i.tenant_id LEFT JOIN sys_user u ON u.id = i.starter_id AND u.tenant_id = i.tenant_id WHERE i.tenant_id = ? ORDER BY i.id DESC", user.tenantId());
    }

    public List<Map<String, Object>> timeline(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        return jdbc.queryForList("SELECT a.id, a.event_type, a.operator_id, u.display_name AS operator_name, a.reason, a.payload_json, a.created_at FROM wf_audit_event a LEFT JOIN sys_user u ON u.id = a.operator_id AND u.tenant_id = a.tenant_id WHERE a.instance_id = ? AND a.tenant_id = ? ORDER BY a.created_at, a.id", instanceId, user.tenantId());
    }

    private void syncTasks(long instanceId, long definitionId, int versionNo, String processInstanceId, long tenantId, AuthUser operator) {
        if (runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult() == null) {
            jdbc.update("UPDATE wf_instance SET status = 'APPROVED' WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", instanceId, tenantId);
            return;
        }
        Map<String, Object> version = jdbc.queryForMap("SELECT definition_json, node_mapping_json FROM wf_version WHERE definition_id = ? AND version_no = ? AND tenant_id = ?", definitionId, versionNo, tenantId);
        WorkflowDefinitionValidator.WorkflowGraph graph = graph(String.valueOf(version.get("definition_json")));
        Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
        Map<String, String> reverseMapping = reverseMapping(String.valueOf(version.get("node_mapping_json")));
        boolean progressed;
        do {
            progressed = false;
            List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
            for (Task task : tasks) {
                String sourceNodeId = reverseMapping.getOrDefault(task.getTaskDefinitionKey(), task.getTaskDefinitionKey());
                WorkflowDefinitionValidator.WorkflowNode node = graph.node(sourceNodeId);
                if (node == null) continue;
                if ("CCB_CC".equals(task.getCategory()) || "CC".equals(node.type())) {
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM wf_task WHERE tenant_id = ? AND flowable_task_id = ?", Integer.class, tenantId, task.getId()) == 0) {
                        createCcTasks(instanceId, tenantId, ids(node.config().path("userIds")), operator == null ? 0 : operator.id(), "流程节点抄送");
                        taskService.complete(task.getId());
                        progressed = true;
                    }
                    continue;
                }
                WorkflowNodeModel nodeModel = new WorkflowNodeModel(node.id(), node.type(), node.label(), null, node.config());
                List<WorkflowAssigneeResolver.ResolvedAssignee> assignees = assigneesForTask(task, nodeModel, tenantId, graph, variables);
                if (assignees.isEmpty()) continue;
                for (WorkflowAssigneeResolver.ResolvedAssignee assignee : assignees) {
                    Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM wf_task WHERE tenant_id = ? AND flowable_task_id = ? AND assignee_id = ?", Integer.class, tenantId, task.getId(), assignee.id());
                    if (exists != null && exists > 0) continue;
                    long taskId = nextId();
                    jdbc.update("INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, flowable_task_id, action_key) VALUES (?, ?, ?, ?, ?, 'APPROVAL', ?, ?, ?, ?, 'PENDING', ?, ?)",
                            taskId, tenantId, instanceId, sourceNodeId, sourceNodeId, task.getId(), nodeModel.config().path("assigneeType").asText("USER"), assignee.name(), assignee.id(), task.getId(), sourceNodeId);
                    assigned(tenantId, instanceId, taskId, assignee.id(), operator == null ? 0 : operator.id());
                }
            }
        } while (progressed);
    }

    private List<WorkflowAssigneeResolver.ResolvedAssignee> assigneesForTask(Task task, WorkflowNodeModel node,
                                                                              long tenantId, WorkflowDefinitionValidator.WorkflowGraph graph,
                                                                              Map<String, Object> variables) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            try {
                long id = Long.parseLong(task.getAssignee());
                return jdbc.query("SELECT id, display_name FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0 AND status = 1", (rs, row) -> List.of(new WorkflowAssigneeResolver.ResolvedAssignee(rs.getLong("id"), rs.getString("display_name"))), id, tenantId).stream().flatMap(List::stream).toList();
            } catch (NumberFormatException ignored) { return List.of(); }
        }
        return assigneeResolver.resolveNode(node, tenantId, starterId(task.getProcessInstanceId(), tenantId), variables);
    }

    private long starterId(String processInstanceId, long tenantId) {
        Long starter = jdbc.query("SELECT starter_id FROM wf_instance WHERE flowable_process_instance_id = ? AND tenant_id = ?", rs -> rs.next() ? rs.getLong(1) : null, processInstanceId, tenantId);
        return starter == null ? 0 : starter;
    }

    private void refreshInstanceStatus(long instanceId, long tenantId) {
        String flowableId = jdbc.query("SELECT flowable_process_instance_id FROM wf_instance WHERE id = ? AND tenant_id = ?", rs -> rs.next() ? rs.getString(1) : null, instanceId, tenantId);
        if (flowableId == null) return;
        if (runtimeService.createProcessInstanceQuery().processInstanceId(flowableId).singleResult() == null) {
            jdbc.update("UPDATE wf_instance SET status = 'APPROVED' WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", instanceId, tenantId);
        }
    }

    private void claimIfNeeded(Task task, AuthUser user) {
        if (task.getAssignee() == null || task.getAssignee().isBlank()) {
            taskService.claim(task.getId(), String.valueOf(user.id()));
        } else if (!String.valueOf(user.id()).equals(task.getAssignee())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户不是该流程任务的审批人");
        }
    }

    private void emit(long instanceId, WorkflowLifecycleEventType type, AuthUser user) {
        if (lifecycleEvents != null) lifecycleEvents.emit(instanceId, type, user);
    }

    private Map<String, Object> findPendingTask(long taskId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id, t.instance_id, i.definition_id, i.version_no, t.flowable_task_id, t.task_key, t.assignee_id FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id WHERE t.id = ? AND t.tenant_id = ? AND t.assignee_id = ? AND t.flowable_task_id IS NOT NULL AND t.status = 'PENDING'", taskId, user.tenantId(), user.id());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户没有该流程任务的审批权限");
        return rows.get(0);
    }

    private Map<String, Object> findDefinition(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, code, name, status, current_version, deployment_id FROM wf_definition WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程定义不存在");
        return rows.get(0);
    }

    private Map<String, Object> findPublishedDefinition(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.id, d.code, d.name, d.current_version, d.deployment_id, v.definition_json FROM wf_definition d JOIN wf_version v ON v.definition_id = d.id AND v.tenant_id = d.tenant_id AND v.version_no = d.current_version WHERE d.id = ? AND d.tenant_id = ? AND d.deleted = 0 AND d.status = 'PUBLISHED' AND v.status = 'PUBLISHED'", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程尚未发布或不存在");
        return rows.get(0);
    }

    private ProcessDefinition processDefinition(String deploymentId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId).singleResult();
        if (definition == null) throw new BusinessException(ErrorCode.CONFLICT, "Flowable流程定义不存在");
        return definition;
    }

    private WorkflowDefinitionValidator.WorkflowGraph graph(String json) {
        WorkflowDefinitionModel model = modelAdapter.adapt(json);
        List<WorkflowDefinitionValidator.WorkflowNode> nodes = model.nodes().stream().map(node -> new WorkflowDefinitionValidator.WorkflowNode(node.id(), node.type(), node.label(), new WorkflowDefinitionValidator.Position(node.position().x(), node.position().y()), node.config())).toList();
        List<WorkflowDefinitionValidator.WorkflowEdge> edges = model.edges().stream().map(edge -> new WorkflowDefinitionValidator.WorkflowEdge(edge.id(), edge.source(), edge.target(), edge.condition(), edge.defaultFlow())).toList();
        return new WorkflowDefinitionValidator.WorkflowGraph(model.schemaVersion(), nodes, edges);
    }

    private Map<String, String> reverseMapping(String json) {
        try {
            Map<String, String> mapping = objectMapper.readValue(json == null || "null".equals(json) ? "{}" : json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            Map<String, String> reverse = new LinkedHashMap<>();
            mapping.forEach((source, mapped) -> reverse.put(mapped, source));
            return reverse;
        } catch (JsonProcessingException exception) { return Map.of(); }
    }

    private List<Long> ids(com.fasterxml.jackson.databind.JsonNode node) {
        if (!node.isArray()) return List.of();
        List<Long> result = new ArrayList<>();
        node.forEach(item -> { if (item.canConvertToLong() && item.asLong() > 0 && !result.contains(item.asLong())) result.add(item.asLong()); });
        return result;
    }

    private void createCcTasks(long instanceId, long tenantId, List<Long> userIds, long operatorId, String comment) {
        if (userIds == null || userIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "抄送节点未配置抄送人员");
        for (Long userId : userIds.stream().distinct().toList()) {
            Map<String, Object> user = activeUser(userId, tenantId);
            long taskId = nextId();
            jdbc.update("INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, assignee_type, assignee_name, assignee_id, status, comment, completed_at) VALUES (?, ?, ?, ?, ?, 'CC', ?, 'USER', ?, ?, 'SENT', ?, CURRENT_TIMESTAMP)", taskId, tenantId, instanceId, "cc", "cc", UUID.randomUUID().toString(), user.get("display_name"), userId, comment);
            jdbc.update("INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, target_user_id, comment) VALUES (?, ?, ?, ?, 'CC', ?, ?, ?)", nextId(), tenantId, instanceId, taskId, operatorId, userId, comment);
        }
    }

    private void insertAddSignTask(Map<String, Object> task, long targetUserId, AuthUser operator) {
        long taskId = nextId();
        jdbc.update("INSERT INTO wf_task (id, tenant_id, instance_id, task_key, node_id, task_type, task_group_key, parent_task_id, assignee_type, assignee_name, assignee_id, status) SELECT ?, instance_id, instance_id, task_key, node_id, 'ADD_SIGN', ?, id, 'USER', (SELECT display_name FROM sys_user WHERE id = ? AND tenant_id = ?), ?, 'PENDING' FROM wf_task WHERE id = ? AND tenant_id = ?",
                taskId, UUID.randomUUID().toString(), targetUserId, operator.tenantId(), targetUserId, task.get("id"), operator.tenantId());
        assigned(operator.tenantId(), ((Number) task.get("instance_id")).longValue(), taskId, targetUserId, operator.id());
    }

    private void assigned(long tenantId, long instanceId, long taskId, long assigneeId, long operatorId) {
        if (taskAssignments != null) taskAssignments.assigned(tenantId, instanceId, taskId, assigneeId, operatorId);
    }

    private void recordAction(Map<String, Object> task, String action, AuthUser user, Long target, String comment, Map<String, Object> payload) {
        jdbc.update("INSERT INTO wf_task_action (id, tenant_id, instance_id, task_id, action_code, operator_id, target_user_id, comment, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), task.get("instance_id"), task.get("id"), action, user.id(), target, comment, writeJson(payload));
    }

    private long definitionId(Map<String, Object> task) { return ((Number) task.get("definition_id")).longValue(); }
    private int versionNo(Map<String, Object> task) { return ((Number) task.get("version_no")).intValue(); }
    private long requireTarget(Long target) { if (target == null || target <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择目标用户"); return target; }
    private void ensureActiveUser(long id, long tenantId) { activeUser(id, tenantId); }
    private Map<String, Object> activeUser(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, display_name FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0 AND status = 1", id, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "目标用户不存在或已停用");
        return rows.get(0);
    }
    private void ensureInstance(long id, long tenantId) { if (jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE id = ? AND tenant_id = ?", Integer.class, id, tenantId) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在"); }
    private String writeJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "流程数据序列化失败"); } }
    private String requireText(String value, String field) { if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空"); return value.trim(); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
