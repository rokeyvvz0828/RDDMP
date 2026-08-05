package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowMonitorService {
    private final JdbcTemplate jdbc;
    private final RuntimeService runtimeService;
    private final WorkflowAuditService auditService;

    public WorkflowMonitorService(JdbcTemplate jdbc, RuntimeService runtimeService, WorkflowAuditService auditService) {
        this.jdbc = jdbc;
        this.runtimeService = runtimeService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> instances(AuthUser user) {
        return jdbc.queryForList("SELECT i.id, i.definition_id, d.name AS definition_name, i.version_no, i.business_key, i.status, i.starter_id, u.display_name AS starter_name, i.created_at FROM wf_instance i JOIN wf_definition d ON d.id = i.definition_id AND d.tenant_id = i.tenant_id LEFT JOIN sys_user u ON u.id = i.starter_id AND u.tenant_id = i.tenant_id WHERE i.tenant_id = ? ORDER BY i.id DESC", user.tenantId());
    }

    public List<Map<String, Object>> timeline(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        return jdbc.queryForList("SELECT a.id, a.event_type, a.operator_id, u.display_name AS operator_name, a.reason, a.payload_json, a.created_at FROM wf_audit_event a LEFT JOIN sys_user u ON u.id = a.operator_id AND u.tenant_id = a.tenant_id WHERE a.instance_id = ? AND a.tenant_id = ? ORDER BY a.created_at, a.id", instanceId, user.tenantId());
    }

    public List<Map<String, Object>> nodeStates(long instanceId, AuthUser user) {
        ensureInstance(instanceId, user.tenantId());
        return jdbc.queryForList("SELECT t.id, t.node_id, t.task_key, t.task_type, t.assignee_name, t.status, t.comment, t.created_at, t.completed_at FROM wf_task t WHERE t.instance_id = ? AND t.tenant_id = ? ORDER BY t.created_at, t.id", instanceId, user.tenantId());
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
        auditService.record(operator, "INSTANCE_TERMINATED", null, null, instanceId, null, reason, Map.of("administrator", operator.username()));
    }

    private void ensureInstance(long instanceId, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE id = ? AND tenant_id = ?", Integer.class, instanceId, tenantId);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "流程实例不存在");
    }
}