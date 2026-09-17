package com.ccb.system.internal.capability;

import org.springframework.stereotype.Repository;

@Repository
public class SystemOperationAuditRepository {
    private final SystemOperationAuditMapper mapper;
    public SystemOperationAuditRepository(SystemOperationAuditMapper mapper) { this.mapper = mapper; }
    public void insert(long id, long tenantId, long operatorId, String operationCode, String requestMethod, String requestPath, int success, String errorMessage, String traceId) {
        mapper.insert(id, tenantId, operatorId, operationCode, requestMethod, requestPath, success, errorMessage, traceId);
    }
}
