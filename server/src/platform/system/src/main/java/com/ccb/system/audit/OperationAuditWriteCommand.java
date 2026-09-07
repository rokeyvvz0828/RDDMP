package com.ccb.system.audit;

import com.ccb.security.model.AuthUser;

import java.util.Set;

public record OperationAuditWriteCommand(
        AuthUser actor,
        String operationCode,
        String moduleCode,
        String moduleName,
        String operationType,
        String targetType,
        String targetId,
        String projectReference,
        String requestMethod,
        String requestPath,
        boolean success,
        int httpStatus,
        String errorMessage,
        String clientIp,
        String userAgent,
        String traceId,
        long durationMs,
        Set<String> changedFields) {
}
