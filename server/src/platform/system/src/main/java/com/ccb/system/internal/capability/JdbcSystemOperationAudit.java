package com.ccb.system.internal.capability;

import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class JdbcSystemOperationAudit implements SystemOperationAudit {
    private final JdbcTemplate jdbc;

    public JdbcSystemOperationAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(SystemOperationAuditCommand command) {
        insert(command, 1, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(SystemOperationAuditCommand command) {
        insert(command, 0, command.errorMessage());
    }

    private void insert(SystemOperationAuditCommand command, int success, String errorMessage) {
        jdbc.update("""
                        INSERT INTO sys_operation_log
                            (id, tenant_id, operator_id, operation_code, request_method, request_path, success, error_message, trace_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                nextId(),
                command.actor().tenantId(),
                command.actor().id(),
                command.operationCode(),
                command.requestMethod(),
                command.requestPath(),
                success,
                errorMessage,
                command.traceId());
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
