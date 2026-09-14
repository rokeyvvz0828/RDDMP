package com.ccb.boot.audit;

import com.ccb.common.audit.OperationAuditContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationAuditContextTest {
    @AfterEach
    void clear() {
        OperationAuditContext.clear();
    }

    @Test
    void retainsOnlySafeFieldNamesAndSanitizesFailureText() {
        OperationAuditContext.begin();
        OperationAuditContext.addChangedFields(List.of(
                "displayName", "status", "password", "accessToken", "filePath", "approvalComment", "remark"));
        OperationAuditContext.capture("system:user:update", "user", "7", "包含业务值的失败原因");

        OperationAuditContext.Snapshot snapshot = OperationAuditContext.snapshot();

        assertEquals("system:user:update", snapshot.operationCode());
        assertEquals("Business operation failed", snapshot.errorMessage());
        assertEquals(java.util.Set.of("displayName", "status"), snapshot.changedFields());
    }

    @Test
    void clearRemovesRequestState() {
        OperationAuditContext.begin();
        OperationAuditContext.capture("system:user:update", "user", "7", null);

        OperationAuditContext.clear();

        assertEquals(OperationAuditContext.Snapshot.class, OperationAuditContext.snapshot().getClass());
        assertEquals(java.util.Set.of(), OperationAuditContext.snapshot().changedFields());
        assertEquals(null, OperationAuditContext.snapshot().operationCode());
    }
}
