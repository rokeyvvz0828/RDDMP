package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;

import java.util.Objects;

/**
 * 操作审计命令。租户和操作者只能从认证主体派生，不接收独立 tenant 参数或业务请求正文。
 */
public record SystemOperationAuditCommand(
        AuthUser actor,
        String operationCode,
        String requestMethod,
        String requestPath,
        String errorMessage,
        String traceId) {

    public SystemOperationAuditCommand {
        actor = Objects.requireNonNull(actor, "actor 不能为空");
        operationCode = required(operationCode, "operationCode", 128);
        requestMethod = optional(requestMethod, 16);
        requestPath = optional(requestPath, 255);
        errorMessage = optional(errorMessage, 255);
        traceId = optional(traceId, 64);
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
