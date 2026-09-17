package com.ccb.workflow.service;

import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowSignatureService {
    private final WorkflowSignatureRepository repository;

    public WorkflowSignatureService(WorkflowSignatureRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
    }

    public boolean required(long taskId, long tenantId) {
        return false;
    }

    public void confirmIfRequired(long taskId, String action, String comment, boolean confirmed, AuthUser operator) {
        // Internal signing is temporarily disabled; keep the method for API compatibility.
    }

    public List<Map<String, Object>> signatures(long instanceId, long tenantId) {
        return repository.signatures(instanceId, tenantId);
    }

}
