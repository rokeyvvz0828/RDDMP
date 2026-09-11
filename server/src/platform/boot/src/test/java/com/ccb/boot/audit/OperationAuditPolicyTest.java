package com.ccb.boot.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationAuditPolicyTest {
    private final OperationAuditPolicy policy = new OperationAuditPolicy();

    @Test
    void includesWritesAndClassifiesBusinessActions() {
        assertEquals("CREATE", policy.decide("POST", "/api/release/applications").operationType());
        assertEquals("UPDATE", policy.decide("PATCH", "/api/project/1/settings").operationType());
        assertEquals("DELETE", policy.decide("DELETE", "/api/system/users/2").operationType());
        assertEquals("APPROVAL", policy.decide("POST", "/api/workflows/tasks/3/approve").operationType());
        assertEquals("IMPORT", policy.decide("POST", "/api/requirements/imports/commit").operationType());
        assertEquals("EXPORT", policy.decide("GET", "/api/data-migration/components/export").operationType());
    }

    @Test
    void excludesOrdinaryReadsAndPreviewOnlyWrites() {
        assertFalse(policy.decide("GET", "/api/release/applications").included());
        assertFalse(policy.decide("GET", "/actuator/health").included());
        assertFalse(policy.decide("POST", "/api/release/applications/conflicts/preview").included());
        assertFalse(policy.decide("POST", "/api/test-management/unit/cases/import/preview").included());
    }

    @Test
    void includesSensitiveReadsWithoutIncludingOptionLookups() {
        assertTrue(policy.decide("GET", "/api/system/users").included());
        assertTrue(policy.decide("GET", "/api/workflows/tasks/123/context").included());
        assertTrue(policy.decide("GET", "/api/attachments/123/preview").included());
        assertTrue(policy.decide("GET", "/api/system/audit/operations").included());
        assertFalse(policy.decide("GET", "/api/workflows/project-options").included());
        assertFalse(policy.decide("GET", "/api/system/audit/projects").included());
        assertFalse(policy.decide("GET", "/api/system/audit/capabilities").included());
    }
}
