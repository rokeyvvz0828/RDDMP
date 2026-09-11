package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;

import java.util.Set;

/** 组合根采集并提交给 system 模块的完整 HTTP 操作日志。 */
public record SystemOperationLogCommand(
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
