package com.ccb.release.integration;

import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseAttachmentAccessPolicyTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 1L, true);

    @Test
    void allowsTenantReadsButLimitsDeleteToEditableApplicationStates() {
        ReleaseApplicationStore applications = mock(ReleaseApplicationStore.class);
        ReleaseAttachmentAccessPolicy policy = new ReleaseAttachmentAccessPolicy(applications);
        when(applications.findByCode("SQ-001", 1L)).thenReturn(Optional.of(application(Status.DRAFT)));

        assertTrue(policy.canAccess(USER, "SQ-001", AttachmentOperation.READ));
        assertTrue(policy.canAccess(USER, "SQ-001", AttachmentOperation.PREVIEW));
        assertTrue(policy.canAccess(USER, "SQ-001", AttachmentOperation.DELETE));

        when(applications.findByCode("SQ-001", 1L)).thenReturn(Optional.of(application(Status.IN_REVIEW)));
        assertTrue(policy.canAccess(USER, "SQ-001", AttachmentOperation.DOWNLOAD));
        assertFalse(policy.canAccess(USER, "SQ-001", AttachmentOperation.DELETE));
    }

    @Test
    void rejectsMissingCrossTenantOrDisabledUserContext() {
        ReleaseApplicationStore applications = mock(ReleaseApplicationStore.class);
        ReleaseAttachmentAccessPolicy policy = new ReleaseAttachmentAccessPolicy(applications);
        when(applications.findByCode("SQ-001", 1L)).thenReturn(Optional.empty());

        assertFalse(policy.canAccess(USER, "SQ-001", AttachmentOperation.READ));
        assertFalse(policy.canAccess(new AuthUser(7L, 1L, "developer", "", "研发人员", 1L, false),
                "SQ-001", AttachmentOperation.READ));
        assertFalse(policy.canAccess(USER, " ", AttachmentOperation.READ));
    }

    private Application application(Status status) {
        return new Application(10L, 1L, "SQ-001", "P-001", "P001", "项目", false, 20L, null,
                "SYS-1", "SYS1", "用户中心", VersionType.REGULAR, Characteristic.STANDARD,
                "release.regular", status, 7L, "研发人员", "研发部", null, null, "说明", null, 3L,
                7L, 7L, LocalDateTime.now(), LocalDateTime.now(),
                List.of(new DeliverySnapshot(40L, "DU-1", "UNIT-A", "用户服务", ArtifactType.IMAGE, "v1")),
                List.of("REQ-001"));
    }
}
