package com.ccb.attachment.service;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentAccessPolicyRegistryTest {
    private static final AuthUser USER = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);

    @Test
    void failsClosedWhenPolicyIsMissingOrThrows() {
        assertThrows(BusinessException.class, () -> new AttachmentAccessPolicyRegistry(List.of()).requireAccess("release", "SQ-1", AttachmentOperation.READ, USER));
        AttachmentAccessPolicy broken = policy("release", true);
        assertThrows(BusinessException.class, () -> new AttachmentAccessPolicyRegistry(List.of(broken)).requireAccess("release", "SQ-1", AttachmentOperation.READ, USER));
    }

    @Test
    void allowsOnlyExplicitPolicyGrant() {
        AttachmentAccessPolicy allowed = policy("release", false);
        assertDoesNotThrow(() -> new AttachmentAccessPolicyRegistry(List.of(allowed)).requireAccess("release", "SQ-1", AttachmentOperation.READ, USER));
    }

    private AttachmentAccessPolicy policy(String type, boolean fail) {
        return new AttachmentAccessPolicy() {
            public String businessType() { return type; }
            public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
                if (fail) throw new IllegalStateException("policy unavailable");
                return true;
            }
        };
    }
}
