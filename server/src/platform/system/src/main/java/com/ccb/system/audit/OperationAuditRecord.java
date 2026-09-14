package com.ccb.system.audit;

import java.time.LocalDateTime;
import java.util.List;

public record OperationAuditRecord(
        long id,
        long operatorId,
        String operatorName,
        String operationCode,
        String moduleCode,
        String moduleName,
        String operationType,
        String targetType,
        String targetId,
        Long projectId,
        String projectName,
        String requestMethod,
        String requestPath,
        boolean success,
        Integer httpStatus,
        long durationMs,
        String errorMessage,
        String clientIp,
        String userAgent,
        List<String> changedFields,
        String traceId,
        LocalDateTime createdAt) {
}
