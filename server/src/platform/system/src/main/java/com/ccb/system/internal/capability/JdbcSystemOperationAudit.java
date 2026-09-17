package com.ccb.system.internal.capability;

import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.common.audit.OperationAuditContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class JdbcSystemOperationAudit implements SystemOperationAudit {
    private final SystemOperationAuditRepository repository;

    public JdbcSystemOperationAudit(SystemOperationAuditRepository repository) {
        this.repository = repository;
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
        String safeError = success == 1 || errorMessage == null || errorMessage.isBlank()
                ? null : "Business operation failed";
        if (OperationAuditContext.capture(command.operationCode(), targetType(command.operationCode()), null,
                safeError)) {
            return;
        }
        repository.insert(nextId(), command.actor().tenantId(), command.actor().id(), command.operationCode(),
                command.requestMethod(), command.requestPath(), success, safeError, command.traceId());
    }

    private String targetType(String operationCode) {
        String[] segments = operationCode.split("[:.]", 4);
        return segments.length > 1 ? segments[1] : null;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
