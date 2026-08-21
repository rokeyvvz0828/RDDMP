package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkflowSignatureService {
    private static final Set<String> SIGNED_ACTIONS = Set.of("APPROVE", "REJECT", "RETURN");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowSignatureService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public boolean required(long taskId, long tenantId) {
        Map<String, Object> row = taskContext(taskId, tenantId);
        return nodeRequiresSignature(String.valueOf(row.get("definition_json")), String.valueOf(row.get("node_id")));
    }

    public void confirmIfRequired(long taskId, String action, String comment, boolean confirmed, AuthUser operator) {
        String normalizedAction = action == null ? "" : action.trim().toUpperCase();
        if (!SIGNED_ACTIONS.contains(normalizedAction)) return;
        Map<String, Object> row = taskContext(taskId, operator.tenantId());
        if (!nodeRequiresSignature(String.valueOf(row.get("definition_json")), String.valueOf(row.get("node_id")))) return;
        if (!confirmed) throw new BusinessException(ErrorCode.CONFLICT, "该审批节点要求确认内部电子签名");
        if (((Number) row.get("assignee_id")).longValue() != operator.id() || !"PENDING".equals(String.valueOf(row.get("task_status")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户不能签署该审批任务");
        }
        if (row.get("business_round") == null || ((Number) row.get("business_round")).intValue() <= 0 || row.get("data_digest") == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "流程业务轮次或数据摘要缺失，不能签署");
        }
        jdbc.update("INSERT INTO wf_signature (id, tenant_id, instance_id, task_id, business_round, action_code, comment_text, data_digest, signer_id, signer_username, signer_display_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), operator.tenantId(), row.get("instance_id"), taskId, row.get("business_round"), normalizedAction, comment,
                row.get("data_digest"), operator.id(), operator.username(), operator.displayName());
    }

    public List<Map<String, Object>> signatures(long instanceId, long tenantId) {
        return jdbc.queryForList("SELECT id, instance_id, task_id, business_round, action_code, comment_text, data_digest, signer_id, signer_username, signer_display_name, signed_at FROM wf_signature WHERE instance_id = ? AND tenant_id = ? ORDER BY signed_at, id", instanceId, tenantId);
    }

    private Map<String, Object> taskContext(long taskId, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id, t.instance_id, t.node_id, t.assignee_id, t.status AS task_status, i.business_round, i.data_digest, CAST(v.definition_json AS CHAR) AS definition_json FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id JOIN wf_version v ON v.definition_id = i.definition_id AND v.version_no = i.version_no AND v.tenant_id = i.tenant_id WHERE t.id = ? AND t.tenant_id = ? AND i.deleted = 0", taskId, tenantId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "审批任务不存在");
        return rows.get(0);
    }

    private boolean nodeRequiresSignature(String definitionJson, String nodeId) {
        try {
            JsonNode nodes = objectMapper.readTree(definitionJson).path("nodes");
            if (!nodes.isArray()) return false;
            for (JsonNode node : nodes) {
                if (nodeId.equals(node.path("id").asText()) && "APPROVAL".equalsIgnoreCase(node.path("type").asText())) {
                    return node.path("config").path("signatureRequired").asBoolean(false);
                }
            }
            return false;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "流程节点签名配置无法读取");
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
