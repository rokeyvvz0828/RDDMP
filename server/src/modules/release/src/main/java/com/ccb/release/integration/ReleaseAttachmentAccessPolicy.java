package com.ccb.release.integration;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.application.service.ReleaseSubmissionService;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ReleaseAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final ReleaseApplicationStore applications;

    public ReleaseAttachmentAccessPolicy(ReleaseApplicationStore applications) {
        this.applications = applications;
    }

    @Override
    public String businessType() {
        return ReleaseSubmissionService.BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null || businessKey.isBlank()) return false;
        return applications.findByCode(businessKey.trim(), user.tenantId()).map(application -> {
            if (operation != AttachmentOperation.DELETE) return true;
            return Set.of(Status.DRAFT, Status.RETURNED, Status.WITHDRAWN).contains(application.status());
        }).orElse(false);
    }
}
