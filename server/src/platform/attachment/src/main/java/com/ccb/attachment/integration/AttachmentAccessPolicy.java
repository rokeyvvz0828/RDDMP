package com.ccb.attachment.integration;

import com.ccb.security.model.AuthUser;

public interface AttachmentAccessPolicy {
    String businessType();

    boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation);
}
