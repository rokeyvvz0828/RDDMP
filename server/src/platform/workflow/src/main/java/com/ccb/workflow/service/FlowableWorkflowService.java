package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.model.WorkflowDefinitionModel;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
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
    private final FlowableWorkflowRepository workflowRepository;
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
    private WorkflowProjectAccessGateway projectAccess;
    private WorkflowDefinitionSummaryProjector definitionSummaryProjector;

    @Autowired
    public FlowableWorkflowService(ObjectMapper objectMapper,
                                   RepositoryService repositoryService, RuntimeService runtimeService,
                                   TaskService taskService, WorkflowAssigneeResolver assigneeResolver,
                                   WorkflowAuditService auditService, FlowableWorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
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

    @Autowired(required = false)
    void setProjectAccess(WorkflowProjectAccessGateway projectAccess) {
        this.projectAccess = projectAccess;
    }

    @Autowired(required = false)
    void setDefinitionSummaryProjector(WorkflowDefinitionSummaryProjector definitionSummaryProjector) {
        this.definitionSummaryProjector = definitionSummaryProjector;
    }

    public boolean isEnterpriseDefinition(String definitionJson) {
        return modelAdapter.adapt(definitionJson).schemaVersion() == 2;
    }

    @Transactional
    public Map<String, Object> createDefinition(String code, String name, String definitionJson, AuthUser user) {
        return createDefinition(code, name, definitionJson, "PLATFORM", null, user);
    }

    @Transactional
    public Map<String, Object> createDefinition(String code, String name, String definitionJson, String scopeType,
                                                Long projectId, AuthUser user) {
        WorkflowDefinitionModel model = modelValidator.requireValid(definitionJson);
        long id = nextId();
        Map<String, Object> definitionParams = new LinkedHashMap<>();
        definitionParams.put("id", id); definitionParams.put("tenantId", user.tenantId());
        definitionParams.put("code", requireText(code, "流程编码")); definitionParams.put("name", requireText(name, "流程名称"));
        definitionParams.put("scopeType", scopeType); definitionParams.put("projectId", projectId); definitionParams.put("modelSchemaVersion", model.schemaVersion());
        workflowRepository.insertDefinition(definitionParams);
        Map<String, Object> versionParams = new LinkedHashMap<>();
        versionParams.put("id", nextId()); versionParams.put("tenantId", user.tenantId()); versionParams.put("definitionId", id);
        versionParams.put("definitionJson", definitionJson); versionParams.put("modelSchemaVersion", model.schemaVersion());
        workflowRepository.insertVersion(versionParams);
        refreshDefinitionSummary(id, user.tenantId(), scopeType, 1, definitionJson);
        auditService.record(user, "DEFINITION_CREATED", id, 1, null, null, null, Map.of("code", code));
        return workflowRepository.definition(id, user.tenantId());
    }

    @Transactional
    public void updateDefinition(long definitionId, String code, String name, String definitionJson, AuthUser user) {
        WorkflowDefinitionModel model = modelValidator.requireValid(definitionJson);
        Map<String, Object> definition = findDefinition(definitionId, user.tenantId());
        if (!"DRAFT".equals(String.valueOf(definition.get("status")))) throw new BusinessException(ErrorCode.CONFLICT, "已发布流程不能编辑，请复制后创建新版本");
        Map<String, Object> version = workflowRepository.latestVersion(definitionId, user.tenantId());
        Map<String, Object> definitionParams = new LinkedHashMap<>();
        definitionParams.put("code", requireText(code, "流程编码")); definitionParams.put("name", requireText(name, "流程名称"));
        definitionParams.put("modelSchemaVersion", model.schemaVersion()); definitionParams.put("definitionId", definitionId); definitionParams.put("tenantId", user.tenantId());
        workflowRepository.updateDefinition(definitionParams);
        Map<String, Object> versionParams = new LinkedHashMap<>();
        versionParams.put("definitionJson", definitionJson); versionParams.put("modelSchemaVersion", model.schemaVersion());
        versionParams.put("definitionId", definitionId); versionParams.put("tenantId", user.tenantId()); versionParams.put("versionNo", version.get("version_no"));
        workflowRepository.updateVersion(versionParams);
        refreshDefinitionSummary(definitionId, user.tenantId(), String.valueOf(definition.get("scope_type")), ((Number) version.get("version_no")).intValue(), definitionJson);
        auditService.record(user, "DEFINITION_UPDATED", definitionId, ((Number) version.get("version_no")).intValue(), null, null, null, Map.of("code", code));
    }

    @Transactional
    public void publish(long definitionId, AuthUser user) {
        Map<String, Object> definition = findDefinition(definitionId, user.tenantId());
        Map<String, Object> version = workflowRepository.latestVersion(definitionId, user.tenantId());
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
        Map<String, Object> publishParams = new LinkedHashMap<>();
        publishParams.put("bpmnXml", compiled.xml()); publishParams.put("deploymentId", deployment.getId()); publishParams.put("nodeMappingJson", mappingJson);
        publishParams.put("definitionId", definitionId); publishParams.put("tenantId", user.tenantId()); publishParams.put("versionNo", versionNo);
        workflowRepository.publishVersion(publishParams);
        publishParams.put("modelSchemaVersion", 2);
        workflowRepository.publishDefinition(publishParams);
        refreshDefinitionSummary(definitionId, user.tenantId(), String.valueOf(definition.get("scope_type")), versionNo, json);
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
        Map<String, Object> version = workflowRepository.version(definitionId, currentVersion, user.tenantId());
        Map<String, Object> latest = workflowRepository.latestVersion(definitionId, user.tenantId());
        Integer nextVersion = latest == null || latest.get("version_no") == null ? null : ((Number) latest.get("version_no")).intValue() + 1;
        int draftVersion = nextVersion == null ? currentVersion + 1 : nextVersion;
        Map<String, Object> draftParams = new LinkedHashMap<>();
        draftParams.put("id", nextId()); draftParams.put("tenantId", user.tenantId()); draftParams.put("definitionId", definitionId); draftParams.put("versionNo", draftVersion);
        draftParams.put("definitionJson", version.get("definition_json")); draftParams.put("modelSchemaVersion", version.get("model_schema_version"));
        workflowRepository.insertDraftVersion(draftParams);
        Map<String, Object> unpublishParams = new LinkedHashMap<>();
        unpublishParams.put("versionNo", draftVersion); unpublishParams.put("definitionId", definitionId); unpublishParams.put("tenantId", user.tenantId());
        workflowRepository.unpublishDefinition(unpublishParams);
        refreshDefinitionSummary(definitionId, user.tenantId(), String.valueOf(definition.get("scope_type")), draftVersion, String.valueOf(version.get("definition_json")));
        auditService.record(user, "DEFINITION_UNPUBLISHED", definitionId, draftVersion, null, null, null, Map.of("previousVersion", currentVersion));
    }
    @Transactional
    public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> inputVariables, AuthUser user) {
        return start(definitionId, businessKey, inputVariables, null, null, user);
    }

    @Transactional
    public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> inputVariables,
                                     WorkflowBusinessContext context, Long projectId, AuthUser user) {
        Map<String, Object> definition = findPublishedDefinition(definitionId, user.tenantId());
        String json = String.valueOf(definition.get("definition_json"));
        WorkflowDefinitionValidator.WorkflowGraph graph = graph(json);
        WorkflowAssigneeResolver.ProcessVariables prepared = assigneeResolver.prepareProcessVariables(graph, user.tenantId(), user.id(), inputVariables);
        ProcessDefinition processDefinition = processDefinition(String.valueOf(definition.get("deployment_id")));
        org.flowable.engine.runtime.ProcessInstance processInstance = runtimeService.startProcessInstanceById(processDefinition.getId(), requireText(businessKey, "业务单号"), prepared.values());
        long instanceId = nextId();
        String variablesJson = writeJson(prepared.values());
        Map<String, Object> instanceParams = new LinkedHashMap<>();
        instanceParams.put("id", instanceId); instanceParams.put("tenantId", user.tenantId()); instanceParams.put("definitionId", definitionId);
        instanceParams.put("versionNo", definition.get("current_version")); instanceParams.put("businessKey", requireText(businessKey, "业务单号"));
        instanceParams.put("businessModuleCode", contextValue(context, WorkflowBusinessContext::moduleCode)); instanceParams.put("businessModuleName", contextValue(context, WorkflowBusinessContext::moduleName));
        instanceParams.put("businessType", contextValue(context, WorkflowBusinessContext::businessType)); instanceParams.put("businessTitle", contextValue(context, WorkflowBusinessContext::businessTitle));
        instanceParams.put("businessRound", context == null ? null : context.businessRound()); instanceParams.put("projectId", projectId);
        instanceParams.put("projectRef", contextValue(context, WorkflowBusinessContext::projectRef)); instanceParams.put("projectName", contextValue(context, WorkflowBusinessContext::projectName));
        instanceParams.put("actionPath", contextValue(context, WorkflowBusinessContext::actionPath)); instanceParams.put("dataDigest", contextValue(context, WorkflowBusinessContext::dataDigest));
        instanceParams.put("starterId", user.id()); instanceParams.put("flowableProcessInstanceId", processInstance.getProcessInstanceId());
        instanceParams.put("flowableProcessDefinitionId", processInstance.getProcessDefinitionId()); instanceParams.put("variablesJson", variablesJson);
        workflowRepository.insertInstance(instanceParams);
        syncTasks(instanceId, definitionId, ((Number) definition.get("current_version")).intValue(), processInstance.getProcessInstanceId(), user.tenantId(), user);
        auditService.record(user, "INSTANCE_STARTED", definitionId, ((Number) definition.get("current_version")).intValue(), instanceId, null, null,
                Map.of("businessKey", businessKey, "flowableProcessInstanceId", processInstance.getProcessInstanceId()));
        return workflowRepository.instance(instanceId, user.tenantId());
    }

    public List<Map<String, Object>> inbox(AuthUser user) {
        return workflowRepository.inbox(user.tenantId(), user.id());
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
            requireProjectTargets(instanceId, ccUserIds, user);
            createCcTasks(instanceId, user.tenantId(), ccUserIds, user.id(), comment);
            recordAction(appTask, normalized, user, null, comment, Map.of("userIds", ccUserIds == null ? List.of() : ccUserIds));
            auditService.record(user, "TASK_CC", definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of());
            return;
        }
        if ("ADD_SIGN".equals(normalized)) {
            long target = requireTarget(targetUserId);
            requireProjectTargets(instanceId, List.of(target), user);
            ensureActiveUser(target, user.tenantId());
            insertAddSignTask(appTask, target, user);
            recordAction(appTask, normalized, user, target, comment, Map.of("targetUserId", target));
            auditService.record(user, "TASK_ADD_SIGN", definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of("targetUserId", target));
            return;
        }
        if ("TRANSFER".equals(normalized) || "DELEGATE".equals(normalized)) {
            long target = requireTarget(targetUserId);
            requireProjectTargets(instanceId, List.of(target), user);
            ensureActiveUser(target, user.tenantId());
            if ("TRANSFER".equals(normalized)) taskService.setAssignee(flowableTaskId, String.valueOf(target));
            else taskService.delegateTask(flowableTaskId, String.valueOf(target));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("targetUserId", target); params.put("tenantId", user.tenantId()); params.put("taskId", taskId);
            workflowRepository.updateTaskAssignee(params);
            assigned(user.tenantId(), instanceId, taskId, target, user.id());
            recordAction(appTask, normalized, user, target, comment, Map.of("targetUserId", target));
            auditService.record(user, "TASK_" + normalized, definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of("targetUserId", target));
            return;
        }

        claimIfNeeded(flowableTask, user);
        if ("REJECT".equals(normalized) || "RETURN".equals(normalized)) {
            runtimeService.deleteProcessInstance(flowableTask.getProcessInstanceId(), normalized);
            String decisionStatus = "REJECT".equals(normalized) ? "REJECTED" : "RETURNED";
            Map<String, Object> decisionParams = new LinkedHashMap<>();
            decisionParams.put("status", decisionStatus); decisionParams.put("comment", comment); decisionParams.put("taskId", taskId); decisionParams.put("tenantId", user.tenantId());
            workflowRepository.updateTaskDecision(decisionParams);
            Map<String, Object> cancelParams = new LinkedHashMap<>();
            cancelParams.put("instanceId", instanceId); cancelParams.put("tenantId", user.tenantId()); cancelParams.put("taskId", taskId);
            workflowRepository.cancelPendingTasks(cancelParams);
            Map<String, Object> instanceParams = new LinkedHashMap<>();
            instanceParams.put("status", decisionStatus); instanceParams.put("instanceId", instanceId); instanceParams.put("tenantId", user.tenantId());
            workflowRepository.updateInstanceStatus(instanceParams);
            emit(instanceId, "REJECT".equals(normalized) ? WorkflowLifecycleEventType.REJECTED : WorkflowLifecycleEventType.RETURNED, user);
        } else {
            taskService.complete(flowableTaskId);
            Map<String, Object> approvalParams = new LinkedHashMap<>();
            approvalParams.put("status", "APPROVED"); approvalParams.put("comment", comment); approvalParams.put("taskId", taskId); approvalParams.put("tenantId", user.tenantId());
            workflowRepository.updateTaskDecision(approvalParams);
            syncTasks(instanceId, definitionId(appTask), versionNo(appTask), flowableTask.getProcessInstanceId(), user.tenantId(), user);
            refreshInstanceStatus(instanceId, user.tenantId());
            String status = workflowRepository.instanceStatus(instanceId, user.tenantId());
            if ("APPROVED".equals(status)) emit(instanceId, WorkflowLifecycleEventType.APPROVED, user);
        }
        recordAction(appTask, normalized, user, null, comment, Map.of());
        auditService.record(user, "TASK_" + normalized, definitionId(appTask), versionNo(appTask), instanceId, taskId, comment, Map.of());
    }

    public List<Map<String, Object>> instances(AuthUser user) {
        return workflowRepository.instances(user.tenantId());
    }

    public List<Map<String, Object>> timeline(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        return workflowRepository.timeline(instanceId, user.tenantId());
    }

    private void syncTasks(long instanceId, long definitionId, int versionNo, String processInstanceId, long tenantId, AuthUser operator) {
        if (runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult() == null) {
            Map<String, Object> statusParams = new LinkedHashMap<>();
            statusParams.put("status", "APPROVED"); statusParams.put("instanceId", instanceId); statusParams.put("tenantId", tenantId);
            workflowRepository.updateInstanceStatus(statusParams);
            return;
        }
        Map<String, Object> version = workflowRepository.version(definitionId, versionNo, tenantId);
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
                    if (workflowRepository.countTask(tenantId, task.getId(), null) == 0) {
                        List<Long> recipients = ids(node.config().path("userIds"));
                        if (operator != null) requireProjectTargets(instanceId, recipients, operator);
                        createCcTasks(instanceId, tenantId, recipients, operator == null ? 0 : operator.id(), "流程节点抄送");
                        taskService.complete(task.getId());
                        progressed = true;
                    }
                    continue;
                }
                WorkflowNodeModel nodeModel = new WorkflowNodeModel(node.id(), node.type(), node.label(), null, node.config());
                List<WorkflowAssigneeResolver.ResolvedAssignee> assignees = assigneesForTask(task, nodeModel, instanceId, tenantId, graph, variables, operator);
                if (assignees.isEmpty()) continue;
                for (WorkflowAssigneeResolver.ResolvedAssignee assignee : assignees) {
                    if (workflowRepository.countTask(tenantId, task.getId(), assignee.id()) > 0) continue;
                    long taskId = nextId();
                    Map<String, Object> taskParams = new LinkedHashMap<>();
                    taskParams.put("taskId", taskId); taskParams.put("tenantId", tenantId); taskParams.put("instanceId", instanceId); taskParams.put("taskKey", sourceNodeId); taskParams.put("nodeId", sourceNodeId);
                    taskParams.put("taskGroupKey", task.getId()); taskParams.put("assigneeType", nodeModel.config().path("assigneeType").asText("USER")); taskParams.put("assigneeName", assignee.name());
                    taskParams.put("assigneeId", assignee.id()); taskParams.put("flowableTaskId", task.getId()); taskParams.put("actionKey", sourceNodeId);
                    workflowRepository.insertApprovalTask(taskParams);
                    assigned(tenantId, instanceId, taskId, assignee.id(), operator == null ? 0 : operator.id());
                }
            }
        } while (progressed);
    }

    private List<WorkflowAssigneeResolver.ResolvedAssignee> assigneesForTask(Task task, WorkflowNodeModel node,
                                                                              long instanceId, long tenantId,
                                                                              WorkflowDefinitionValidator.WorkflowGraph graph,
                                                                              Map<String, Object> variables, AuthUser operator) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            try {
                long id = Long.parseLong(task.getAssignee());
                Map<String, Object> active = workflowRepository.activeUser(id, tenantId);
                return active == null ? List.of() : List.of(new WorkflowAssigneeResolver.ResolvedAssignee(((Number) active.get("id")).longValue(), String.valueOf(active.get("display_name"))));
            } catch (NumberFormatException ignored) { return List.of(); }
        }
        Map<String, Object> instance = workflowRepository.instance(instanceId, tenantId);
        Long projectId = instance == null || instance.get("project_id") == null ? null : ((Number) instance.get("project_id")).longValue();
        return assigneeResolver.resolveNode(node, tenantId, starterId(task.getProcessInstanceId(), tenantId), projectId, operator, variables);
    }

    private long starterId(String processInstanceId, long tenantId) {
        Map<String, Object> instance = workflowRepository.instanceByProcess(processInstanceId, tenantId);
        return instance == null || instance.get("starter_id") == null ? 0 : ((Number) instance.get("starter_id")).longValue();
    }

    private void refreshInstanceStatus(long instanceId, long tenantId) {
        Map<String, Object> instance = workflowRepository.instance(instanceId, tenantId);
        String flowableId = instance == null ? null : String.valueOf(instance.get("flowable_process_instance_id"));
        if ("null".equals(flowableId)) flowableId = null;
        if (flowableId == null) return;
        if (runtimeService.createProcessInstanceQuery().processInstanceId(flowableId).singleResult() == null) {
            Map<String, Object> statusParams = new LinkedHashMap<>();
            statusParams.put("status", "APPROVED"); statusParams.put("instanceId", instanceId); statusParams.put("tenantId", tenantId);
            workflowRepository.updateInstanceStatus(statusParams);
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
        Map<String, Object> task = workflowRepository.pendingTask(taskId, user.tenantId(), user.id());
        if (task == null) throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户没有该流程任务的审批权限");
        Object projectId = task.get("project_id");
        if (projectId instanceof Number number) requireProjectAccess(number.longValue(), user);
        return task;
    }

    private Map<String, Object> findDefinition(long id, long tenantId) {
        Map<String, Object> definition = workflowRepository.definition(id, tenantId);
        if (definition == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程定义不存在");
        return definition;
    }

    private void refreshDefinitionSummary(long definitionId, long tenantId, String scopeType, int versionNo, String definitionJson) {
        if (definitionSummaryProjector != null) definitionSummaryProjector.refresh(definitionId, tenantId, scopeType, versionNo, definitionJson);
    }

    private Map<String, Object> findPublishedDefinition(long id, long tenantId) {
        Map<String, Object> definition = workflowRepository.publishedDefinition(id, tenantId);
        if (definition == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程尚未发布或不存在");
        return definition;
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
            Map<String, Object> taskParams = new LinkedHashMap<>();
            taskParams.put("taskId", taskId); taskParams.put("tenantId", tenantId); taskParams.put("instanceId", instanceId); taskParams.put("taskGroupKey", UUID.randomUUID().toString());
            taskParams.put("assigneeName", user.get("display_name")); taskParams.put("assigneeId", userId); taskParams.put("comment", comment);
            workflowRepository.insertCcTask(taskParams);
            Map<String, Object> actionParams = new LinkedHashMap<>();
            actionParams.put("id", nextId()); actionParams.put("tenantId", tenantId); actionParams.put("instanceId", instanceId); actionParams.put("taskId", taskId);
            actionParams.put("actionCode", "CC"); actionParams.put("operatorId", operatorId); actionParams.put("targetUserId", userId); actionParams.put("comment", comment); actionParams.put("payloadJson", null);
            workflowRepository.insertTaskAction(actionParams);
        }
    }

    private void insertAddSignTask(Map<String, Object> task, long targetUserId, AuthUser operator) {
        long taskId = nextId();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskId", taskId); params.put("taskGroupKey", UUID.randomUUID().toString()); params.put("targetUserId", targetUserId);
        params.put("tenantId", operator.tenantId()); params.put("sourceTaskId", task.get("id"));
        workflowRepository.insertAddSignTask(params);
        assigned(operator.tenantId(), ((Number) task.get("instance_id")).longValue(), taskId, targetUserId, operator.id());
    }

    private void assigned(long tenantId, long instanceId, long taskId, long assigneeId, long operatorId) {
        if (taskAssignments != null) taskAssignments.assigned(tenantId, instanceId, taskId, assigneeId, operatorId);
    }

    private void recordAction(Map<String, Object> task, String action, AuthUser user, Long target, String comment, Map<String, Object> payload) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", nextId()); params.put("tenantId", user.tenantId()); params.put("instanceId", task.get("instance_id")); params.put("taskId", task.get("id"));
        params.put("actionCode", action); params.put("operatorId", user.id()); params.put("targetUserId", target); params.put("comment", comment); params.put("payloadJson", writeJson(payload));
        workflowRepository.insertTaskAction(params);
    }

    private long definitionId(Map<String, Object> task) { return ((Number) task.get("definition_id")).longValue(); }
    private int versionNo(Map<String, Object> task) { return ((Number) task.get("version_no")).intValue(); }
    private long requireTarget(Long target) { if (target == null || target <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择目标用户"); return target; }
    private void ensureActiveUser(long id, long tenantId) { activeUser(id, tenantId); }
    private Map<String, Object> activeUser(long id, long tenantId) {
        Map<String, Object> user = workflowRepository.activeUser(id, tenantId);
        if (user == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "目标用户不存在或已停用");
        return user;
    }
    private void ensureInstance(long id, long tenantId) { if (workflowRepository.countInstance(id, tenantId) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在"); }
    private void requireProjectTargets(long instanceId, List<Long> userIds, AuthUser actor) {
        Map<String, Object> instance = workflowRepository.instance(instanceId, actor.tenantId());
        Long projectId = instance == null || instance.get("project_id") == null ? null : ((Number) instance.get("project_id")).longValue();
        if (projectId == null) return;
        requireProjectGateway().requireMembers(projectId, userIds == null ? List.of() : userIds, actor);
    }
    private void requireProjectAccess(long projectId, AuthUser actor) { requireProjectGateway().requireAccessible(projectId, actor); }
    private WorkflowProjectAccessGateway requireProjectGateway() {
        if (projectAccess == null) throw new BusinessException(ErrorCode.CONFLICT, "项目工作流能力尚未就绪");
        return projectAccess;
    }
    private String contextValue(WorkflowBusinessContext context, java.util.function.Function<WorkflowBusinessContext, String> getter) {
        return context == null ? null : getter.apply(context);
    }
    private String writeJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "流程数据序列化失败"); } }
    private String requireText(String value, String field) { if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空"); return value.trim(); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
