package com.ccb.system.audit;

import java.time.LocalDate;

public record OperationAuditQuery(
        LocalDate startDate,
        LocalDate endDate,
        String moduleCode,
        String operationType,
        Boolean success,
        Long projectId,
        String keyword,
        long page,
        long size) {
}
