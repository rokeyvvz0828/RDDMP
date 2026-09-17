package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkflowAuditService {
    private final WorkflowAuditRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowAuditService(WorkflowAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(AuthUser operator, String eventType, Long definitionId, Integer versionNo,
                       Long instanceId, Long taskId, String reason, Map<String, Object> payload) {
        String payloadJson = null;
        if (payload != null && !payload.isEmpty()) {
            try { payloadJson = objectMapper.writeValueAsString(payload); }
            catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "流程审计记录失败"); }
        }
        repository.insert(nextId(), operator == null ? 0 : operator.tenantId(), definitionId, versionNo, instanceId, taskId,
                eventType, operator == null ? null : operator.id(), reason, payloadJson);
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
