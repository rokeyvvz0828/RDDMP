package com.ccb.system.audit;

import java.time.LocalDate;

public record LoginAuditQuery(
        LocalDate startDate,
        LocalDate endDate,
        Boolean success,
        String keyword,
        long page,
        long size) {
}
