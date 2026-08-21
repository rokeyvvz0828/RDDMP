package com.ccb.workflow.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowMonitorService {
    private final JdbcTemplate jdbc;
    private final RuntimeService runtimeService;
    private final WorkflowAuditService auditService;
    private final WorkflowNodeLabelResolver nodeLabelResolver;
    private WorkflowLifecycleEventService lifecycleEvents;
    private WorkflowSignatureService signatureService;

    public WorkflowMonitorService(JdbcTemplate jdbc, RuntimeService runtimeService, WorkflowAuditService auditService,
                                  WorkflowNodeLabelResolver nodeLabelResolver) {
        this.jdbc = jdbc;
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

    public PageResult<Map<String, Object>> instances(PageQuery pageQuery, String businessKey, String definitionKeyword,
                                                     String status, String starterKeyword, String createdFrom,
                                                     String createdTo, AuthUser user) {
        StringBuilder where = new StringBuilder(" WHERE i.tenant_id = ? AND i.deleted = 0");
        List<Object> args = new java.util.ArrayList<>();
        args.add(user.tenantId());
        appendLike(where, args, "i.business_key", businessKey);
        if (hasText(definitionKeyword)) {
            where.append(" AND (d.name LIKE ? OR d.code LIKE ?)");
            String like = like(definitionKeyword);
            args.add(like);
            args.add(like);
        }
        if (hasText(status)) {
            where.append(" AND i.status = ?");
            args.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (hasText(starterKeyword)) {
            where.append(" AND (u.display_name LIKE ? OR u.username LIKE ?)");
            String like = like(starterKeyword);
            args.add(like);
            args.add(like);
        }
        if (hasText(createdFrom)) {
            where.append(" AND DATE(i.created_at) >= ?");
            args.add(createdFrom.trim());
        }
        if (hasText(createdTo)) {
            where.append(" AND DATE(i.created_at) <= ?");
            args.add(createdTo.trim());
        }
        String fromAndWhere = " FROM wf_instance i JOIN wf_definition d ON d.id = i.definition_id AND d.tenant_id = i.tenant_id"
                + " LEFT JOIN sys_user u ON u.id = i.starter_id AND u.tenant_id = i.tenant_id" + where;
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add((pageQuery.page() - 1) * pageQuery.size());
        pageArgs.add(pageQuery.size());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT i.id, i.definition_id, d.name AS definition_name, i.version_no, i.business_key, i.status,"
                        + " i.starter_id, u.display_name AS starter_name, i.created_at,"
                        + " COALESCE((SELECT GROUP_CONCAT(DISTINCT t.node_id ORDER BY t.id SEPARATOR ', ') FROM wf_task t"
                        + " WHERE t.instance_id = i.id AND t.tenant_id = i.tenant_id AND t.status = 'PENDING'), '') AS current_node"
                        + fromAndWhere + " ORDER BY i.id DESC LIMIT ?, ?", pageArgs.toArray());
        for (Map<String, Object> row : rows) {
            String labels = nodeLabelResolver.labelsForInstance(((Number) row.get("id")).longValue(), user.tenantId(), String.valueOf(row.get("current_node")));
            row.put("current_node", labels);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + fromAndWhere, Long.class, args.toArray());
        return new PageResult<>(rows, total == null ? 0 : total, pageQuery.page(), pageQuery.size());
    }

    private void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append(" LIKE ?");
        args.add(like(value));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    public Map<String, Object> detail(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        Map<String, Object> instance = jdbc.queryForMap("SELECT i.id, i.definition_id, d.name AS definition_name, d.code AS definition_code, i.version_no, i.business_key, i.business_type, i.business_title, i.business_round, i.project_ref, i.project_name, i.action_path, i.status, i.starter_id, u.display_name AS starter_name, i.created_at FROM wf_instance i JOIN wf_definition d ON d.id = i.definition_id AND d.tenant_id = i.tenant_id LEFT JOIN sys_user u ON u.id = i.starter_id AND u.tenant_id = i.tenant_id WHERE i.id = ? AND i.tenant_id = ? AND i.deleted = 0", instanceId, user.tenantId());
        Map<String, Object> version = jdbc.queryForMap("SELECT CAST(v.definition_json AS CHAR) AS definition_json FROM wf_version v WHERE v.definition_id = ? AND v.version_no = ? AND v.tenant_id = ?", instance.get("definition_id"), instance.get("version_no"), user.tenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", instance);
        result.put("definition_json", version.get("definition_json"));
        result.put("node_states", nodeStatesInternal(instanceId, user.tenantId()));
        result.put("timeline", timelineInternal(instanceId, user.tenantId()));
        result.put("signatures", signatureService == null ? List.of() : signatureService.signatures(instanceId, user.tenantId()));
        return result;
    }

    public List<Map<String, Object>> timeline(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        return timelineInternal(instanceId, user.tenantId());
    }

    public List<Map<String, Object>> nodeStates(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        return nodeStatesInternal(instanceId, user.tenantId());
    }

    @Transactional
    public void delete(long instanceId, AuthUser operator) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status FROM wf_instance WHERE id = ? AND tenant_id = ? AND deleted = 0", instanceId, operator.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在");
        String status = String.valueOf(rows.get(0).get("status"));
        if ("RUNNING".equals(status)) throw new BusinessException(ErrorCode.CONFLICT, "运行中的流程实例请先终止后再删除");
        jdbc.update("UPDATE wf_instance SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", instanceId, operator.tenantId());
        auditService.record(operator, "INSTANCE_DELETED", null, null, instanceId, null, "删除流程实例", Map.of("administrator", operator.username()));
    }

    @Transactional
    public void terminate(long instanceId, String reason, AuthUser operator) {
        Map<String, Object> instance = jdbc.queryForMap("SELECT flowable_process_instance_id FROM wf_instance WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", instanceId, operator.tenantId());
        String processInstanceId = String.valueOf(instance.get("flowable_process_instance_id"));
        if (processInstanceId != null && !"null".equals(processInstanceId) && runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult() != null) {
            runtimeService.deleteProcessInstance(processInstanceId, "管理员终止: " + (reason == null ? "" : reason));
        }
        jdbc.update("UPDATE wf_task SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP WHERE instance_id = ? AND tenant_id = ? AND status = 'PENDING'", instanceId, operator.tenantId());
        jdbc.update("UPDATE wf_instance SET status = 'TERMINATED' WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'", instanceId, operator.tenantId());
        if (lifecycleEvents != null) lifecycleEvents.emit(instanceId, WorkflowLifecycleEventType.TERMINATED, operator);
        auditService.record(operator, "INSTANCE_TERMINATED", null, null, instanceId, null, reason, Map.of("administrator", operator.username()));
    }

    private void ensureInstance(long instanceId, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, instanceId, tenantId);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在");
    }

    private List<Map<String, Object>> nodeStatesInternal(long instanceId, long tenantId) {
        return nodeLabelResolver.decorateTasks(jdbc.queryForList("SELECT t.id, t.instance_id, t.node_id, t.task_key, t.task_type, t.assignee_name, t.status, t.comment, t.created_at, t.completed_at FROM wf_task t WHERE t.instance_id = ? AND t.tenant_id = ? ORDER BY t.created_at, t.id", instanceId, tenantId), tenantId);
    }

    private List<Map<String, Object>> timelineInternal(long instanceId, long tenantId) {
        return jdbc.queryForList("SELECT a.id, a.event_type, a.operator_id, u.display_name AS operator_name, a.reason, a.payload_json, a.created_at FROM wf_audit_event a LEFT JOIN sys_user u ON u.id = a.operator_id AND u.tenant_id = a.tenant_id WHERE a.instance_id = ? AND a.tenant_id = ? ORDER BY a.created_at, a.id", instanceId, tenantId);
    }
}
