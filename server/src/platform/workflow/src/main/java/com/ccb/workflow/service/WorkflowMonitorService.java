package com.ccb.workflow.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.model.WorkflowCursorPage;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowMonitorService {
    private final WorkflowMonitorRepository repository;
    private final RuntimeService runtimeService;
    private final WorkflowAuditService auditService;
    private final WorkflowNodeLabelResolver nodeLabelResolver;
    private WorkflowLifecycleEventService lifecycleEvents;
    private WorkflowSignatureService signatureService;
    private WorkflowProjectAccessGateway projectAccess;

    public WorkflowMonitorService(WorkflowMonitorRepository repository, RuntimeService runtimeService, WorkflowAuditService auditService,
                                  WorkflowNodeLabelResolver nodeLabelResolver) {
        this.repository = repository;
        this.runtimeService = runtimeService;
        this.auditService = auditService;
        this.nodeLabelResolver = nodeLabelResolver;
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
    void setProjectAccess(WorkflowProjectAccessGateway projectAccess) {
        this.projectAccess = projectAccess;
    }

    public PageResult<Map<String, Object>> instances(PageQuery pageQuery, String businessKey, String definitionKeyword,
                                                     String status, String starterKeyword, String createdFrom,
                                                     String createdTo, AuthUser user) {
        return instances(pageQuery, businessKey, definitionKeyword, status, starterKeyword, createdFrom, createdTo, null, user);
    }

    public PageResult<Map<String, Object>> instances(PageQuery pageQuery, String businessKey, String definitionKeyword,
                                                     String status, String starterKeyword, String createdFrom,
                                                     String createdTo, String projectRef, AuthUser user) {
        Map<String, Object> params = monitorParams(user, projectRef, businessKey, definitionKeyword, status, starterKeyword, createdFrom, createdTo);
        params.put("offset", (pageQuery.page() - 1) * pageQuery.size());
        params.put("size", pageQuery.size());
        List<Map<String, Object>> rows = repository.instances(params);
        for (Map<String, Object> row : rows) {
            String labels = nodeLabelResolver.labelsForInstance(((Number) row.get("id")).longValue(), user.tenantId(), String.valueOf(row.get("current_node")));
            row.put("current_node", labels);
        }
        return new PageResult<>(rows, repository.countInstances(params), pageQuery.page(), pageQuery.size());
    }

    public WorkflowCursorPage<Map<String, Object>> instancesSeek(String cursor, int requestedSize, String businessKey,
                                                                  String definitionKeyword, String status, String starterKeyword,
                                                                  String createdFrom, String createdTo, String projectRef, AuthUser user) {
        Map<String, Object> params = monitorParams(user, projectRef, businessKey, definitionKeyword, status, starterKeyword, createdFrom, createdTo);
        WorkflowCursorCodec codec = new WorkflowCursorCodec();
        WorkflowCursorCodec.Position position = codec.decode(cursor);
        if (position != null) {
            params.put("cursorCreatedAt", position.createdAt());
            params.put("cursorId", position.id());
        }
        int size = Math.max(1, Math.min(requestedSize, 100));
        params.put("size", size + 1);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(repository.instancesSeek(params));
        boolean hasMore = rows.size() > size;
        if (hasMore) rows.remove(rows.size() - 1);
        for (Map<String, Object> row : rows) row.put("current_node", nodeLabelResolver.labelsForInstance(((Number) row.get("id")).longValue(), user.tenantId(), String.valueOf(row.get("current_node"))));
        String nextCursor = hasMore && !rows.isEmpty() ? codec.encode(rows.get(rows.size() - 1).get("created_at"), ((Number) rows.get(rows.size() - 1).get("id")).longValue()) : null;
        return new WorkflowCursorPage<>(rows, nextCursor, hasMore);
    }

    private Map<String, Object> monitorParams(AuthUser user, String projectRef, String businessKey,
                                              String definitionKeyword, String status, String starterKeyword,
                                              String createdFrom, String createdTo) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("tenantId", user.tenantId());
        params.put("businessKey", hasText(businessKey) ? like(businessKey) : null);
        params.put("definitionKeyword", hasText(definitionKeyword) ? like(definitionKeyword) : null);
        params.put("status", hasText(status) ? status.trim().toUpperCase(java.util.Locale.ROOT) : null);
        params.put("starterKeyword", hasText(starterKeyword) ? like(starterKeyword) : null);
        params.put("createdFrom", hasText(createdFrom) ? createdFrom.trim() : null);
        params.put("createdTo", hasText(createdTo) ? createdTo.trim() : null);
        if (projectAccess == null) {
            params.put("projectMode", "nullOnly");
        } else if (projectRef != null && !projectRef.isBlank()) {
            params.put("projectMode", "specific");
            params.put("projectId", projectAccess.requireAccessible(projectRef, user).id());
        } else {
            List<Long> ids = projectAccess.accessibleProjectIds(user);
            params.put("projectMode", ids.isEmpty() ? "nullOnly" : "accessible");
            params.put("projectIds", ids);
        }
        return params;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    public Map<String, Object> detail(long instanceId, AuthUser user) {
        requireInstance(instanceId, user, false);
        Map<String, Object> instance = repository.detail(instanceId, user.tenantId());
        Map<String, Object> version = repository.definitionJson(instance.get("definition_id"), instance.get("version_no"), user.tenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", instance);
        result.put("definition_json", version.get("definition_json"));
        result.put("node_states", nodeStatesInternal(instanceId, user.tenantId()));
        result.put("timeline", timelineInternal(instanceId, user.tenantId()));
        result.put("signatures", signatureService == null ? List.of() : signatureService.signatures(instanceId, user.tenantId()));
        return result;
    }

    public List<Map<String, Object>> timeline(long instanceId, AuthUser user) {
        requireInstance(instanceId, user, false);
        return timelineInternal(instanceId, user.tenantId());
    }

    public List<Map<String, Object>> nodeStates(long instanceId, AuthUser user) {
        requireInstance(instanceId, user, false);
        return nodeStatesInternal(instanceId, user.tenantId());
    }

    @Transactional
    public void delete(long instanceId, AuthUser operator) {
        requireInstance(instanceId, operator, true);
        List<Map<String, Object>> rows = repository.instanceStatus(instanceId, operator.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在");
        String status = String.valueOf(rows.get(0).get("status"));
        if ("RUNNING".equals(status)) throw new BusinessException(ErrorCode.CONFLICT, "运行中的流程实例请先终止后再删除");
        repository.softDelete(instanceId, operator.tenantId());
        auditService.record(operator, "INSTANCE_DELETED", null, null, instanceId, null, "删除流程实例", Map.of("administrator", operator.username()));
    }

    @Transactional
    public void terminate(long instanceId, String reason, AuthUser operator) {
        requireInstance(instanceId, operator, true);
        Map<String, Object> instance = repository.runningInstance(instanceId, operator.tenantId());
        String processInstanceId = String.valueOf(instance.get("flowable_process_instance_id"));
        if (processInstanceId != null && !"null".equals(processInstanceId) && runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult() != null) {
            runtimeService.deleteProcessInstance(processInstanceId, "管理员终止: " + (reason == null ? "" : reason));
        }
        repository.cancelTasks(instanceId, operator.tenantId());
        repository.terminateInstance(instanceId, operator.tenantId());
        if (lifecycleEvents != null) lifecycleEvents.emit(instanceId, WorkflowLifecycleEventType.TERMINATED, operator);
        auditService.record(operator, "INSTANCE_TERMINATED", null, null, instanceId, null, reason, Map.of("administrator", operator.username()));
    }

    private void requireInstance(long instanceId, AuthUser user, boolean manage) {
        List<Map<String, Object>> rows = repository.projectScope(instanceId, user.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在");
        Object value = rows.get(0).get("project_id");
        if (value instanceof Number number) {
            if (projectAccess == null) throw new BusinessException(ErrorCode.CONFLICT, "项目工作流能力尚未就绪");
            if (manage) projectAccess.requireManageable(number.longValue(), user);
            else projectAccess.requireAccessible(number.longValue(), user);
        }
    }

    private List<Map<String, Object>> nodeStatesInternal(long instanceId, long tenantId) {
        return nodeLabelResolver.decorateTasks(repository.nodeStates(instanceId, tenantId), tenantId);
    }

    private List<Map<String, Object>> timelineInternal(long instanceId, long tenantId) {
        return repository.timeline(instanceId, tenantId);
    }
}
