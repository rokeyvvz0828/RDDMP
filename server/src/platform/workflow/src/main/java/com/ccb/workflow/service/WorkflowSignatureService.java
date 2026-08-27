package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowSignatureService {
    private final JdbcTemplate jdbc;

    public WorkflowSignatureService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
    }

    public boolean required(long taskId, long tenantId) {
        return false;
    }

    public void confirmIfRequired(long taskId, String action, String comment, boolean confirmed, AuthUser operator) {
        // Internal signing is temporarily disabled; keep the method for API compatibility.
    }

    public List<Map<String, Object>> signatures(long instanceId, long tenantId) {
        return jdbc.queryForList("SELECT id, instance_id, task_id, business_round, action_code, comment_text, data_digest, signer_id, signer_username, signer_display_name, signed_at FROM wf_signature WHERE instance_id = ? AND tenant_id = ? ORDER BY signed_at, id", instanceId, tenantId);
    }

}
