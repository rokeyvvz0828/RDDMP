package com.ccb.system.audit;

import com.ccb.system.capability.SystemOperationLogCommand;
import com.ccb.system.capability.SystemOperationLogWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OperationAuditWriter implements SystemOperationLogWriter {
    private final OperationAuditRepository repository;
    private final ObjectMapper objectMapper;

    public OperationAuditWriter(OperationAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void record(SystemOperationLogCommand command) {
        ProjectSnapshot project = findProject(command.actor().tenantId(), command.projectReference());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", nextId()); row.put("tenantId", command.actor().tenantId()); row.put("operatorId", command.actor().id());
        row.put("operatorName", truncate(command.actor().displayName(), 128)); row.put("operationCode", required(command.operationCode(), 128));
        row.put("moduleCode", truncate(command.moduleCode(), 64)); row.put("moduleName", truncate(command.moduleName(), 128));
        row.put("operationType", truncate(command.operationType(), 32)); row.put("targetType", truncate(command.targetType(), 64));
        row.put("targetId", truncate(command.targetId(), 128)); row.put("projectId", project.id()); row.put("projectName", project.name());
        row.put("requestMethod", truncate(command.requestMethod(), 16)); row.put("requestPath", truncate(command.requestPath(), 255));
        row.put("success", command.success() ? 1 : 0); row.put("httpStatus", command.httpStatus()); row.put("durationMs", Math.max(0, command.durationMs()));
        row.put("errorMessage", truncate(command.errorMessage(), 255)); row.put("clientIp", truncate(command.clientIp(), 64));
        row.put("userAgent", truncate(command.userAgent(), 512)); row.put("changedFields", changedFields(command)); row.put("traceId", truncate(command.traceId(), 64));
        repository.insertLog(row);
    }

    private ProjectSnapshot findProject(long tenantId, String reference) {
        if (reference == null || reference.isBlank()) {
            return ProjectSnapshot.empty();
        }
        String normalized = reference.trim();
        Map<String, Object> row;
        if (normalized.matches("[0-9]+")) {
            row = repository.findProjectById(tenantId, Long.parseLong(normalized));
        } else {
            row = repository.findProjectByCode(tenantId, truncate(normalized, 64));
        }
        if (row == null || row.isEmpty()) {
            return ProjectSnapshot.empty();
        }
        return new ProjectSnapshot(((Number) row.get("id")).longValue(), truncate(String.valueOf(row.get("project_name")), 128));
    }

    private String changedFields(SystemOperationLogCommand command) {
        if (command.changedFields() == null || command.changedFields().isEmpty()) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(command.changedFields().stream().sorted().toList()), 1000);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String required(String value, int maxLength) {
        String normalized = truncate(value, maxLength);
        return normalized == null ? "audit:unknown" : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private record ProjectSnapshot(Long id, String name) {
        private static ProjectSnapshot empty() {
            return new ProjectSnapshot(null, null);
        }
    }
}
