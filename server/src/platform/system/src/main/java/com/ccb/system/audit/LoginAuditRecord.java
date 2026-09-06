package com.ccb.system.audit;

import java.time.LocalDateTime;

public record LoginAuditRecord(
        long id,
        String username,
        boolean success,
        String failureReason,
        String clientIp,
        String userAgent,
        LocalDateTime createdAt) {
}
