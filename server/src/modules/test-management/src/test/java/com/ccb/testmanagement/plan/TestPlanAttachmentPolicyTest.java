package com.ccb.testmanagement.plan;

import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.testmanagement.persistence.TestAttachmentAccessMapper;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanAttachmentPolicyTest {
    @Mock TestAttachmentAccessMapper mapper;
    private final AuthUser reader = new AuthUser(1, 1, "reader", "", "方案阅读者", 1, true);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMalformedVersionKeyWithoutDatabaseLookup() {
        TestPlanAttachmentPolicy policy = new TestPlanAttachmentPolicy(mapper);

        assertFalse(policy.canAccess(reader, "not-a-version", AttachmentOperation.PREVIEW));

        verifyNoInteractions(mapper);
    }

    @Test
    void grantsPreviewOnlyWhenCurrentDomainPlanPermissionExists() {
        TestPlanAttachmentPolicy policy = new TestPlanAttachmentPolicy(mapper);
        when(mapper.planDomain(any())).thenReturn("application-assembly");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                reader, "", List.of(new SimpleGrantedAuthority("test-management:application-assembly:plans"))));

        assertTrue(policy.canAccess(reader, "101", AttachmentOperation.PREVIEW));
    }
}
