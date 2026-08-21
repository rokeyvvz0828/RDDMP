package com.ccb.system.capability;

/** 统一操作审计契约。 */
public interface SystemOperationAudit {
    void recordSuccess(SystemOperationAuditCommand command);

    void recordFailure(SystemOperationAuditCommand command);
}
