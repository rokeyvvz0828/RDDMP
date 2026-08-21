package com.ccb.attachment.integration;

import com.ccb.security.model.AuthUser;

public interface AttachmentGateway {
    void bind(AttachmentBindingCommand command, AuthUser operator);

    AttachmentItem get(long attachmentId, AuthUser operator);

    void deleteBound(long attachmentId, String businessType, String businessKey, AuthUser operator);
}
