package com.ccb.system.audit;

import com.ccb.system.capability.SystemOperationLogCommand;
import com.ccb.system.capability.SystemOperationLogWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OperationAuditWriter implements SystemOperationLogWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OperationAuditWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void record(SystemOperationLogCommand command) {
        ProjectSnapshot project = findProject(command.actor().tenantId(), command.projectReference());
        jdbc.update("""
                        INSERT INTO sys_operation_log
                            (id, tenant_id, operator_id, operator_name, operation_code,
                             module_code, module_name, operation_type, target_type, target_id,
                             project_id, project_name, request_method, request_path, success,
                             http_status, duration_ms, error_message, client_ip, user_agent,
                             changed_fields, trace_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                nextId(), command.actor().tenantId(), command.actor().id(),
                truncate(command.actor().displayName(), 128), required(command.operationCode(), 128),
                truncate(command.moduleCode(), 64), truncate(command.moduleName(), 128),
                truncate(command.operationType(), 32), truncate(command.targetType(), 64),
                truncate(command.targetId(), 128), project.id(), project.name(),
                truncate(command.requestMethod(), 16), truncate(command.requestPath(), 255),
                command.success() ? 1 : 0, command.httpStatus(), Math.max(0, command.durationMs()),
                truncate(command.errorMessage(), 255), truncate(command.clientIp(), 64),
                truncate(command.userAgent(), 512), changedFields(command), truncate(command.traceId(), 64));
    }

    private ProjectSnapshot findProject(long tenantId, String reference) {
        if (reference == null || reference.isBlank()) {
            return ProjectSnapshot.empty();
        }
        String normalized = reference.trim();
        List<Map<String, Object>> rows;
        if (normalized.matches("[0-9]+")) {
            rows = jdbc.queryForList("SELECT id, project_name FROM pm_project WHERE tenant_id = ? AND id = ? AND deleted = 0",
                    tenantId, Long.parseLong(normalized));
        } else {
            rows = jdbc.queryForList("SELECT id, project_name FROM pm_project WHERE tenant_id = ? AND project_code = ? AND deleted = 0",
                    tenantId, truncate(normalized, 64));
        }
        if (rows.isEmpty()) {
            return ProjectSnapshot.empty();
        }
        Map<String, Object> row = rows.get(0);
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
