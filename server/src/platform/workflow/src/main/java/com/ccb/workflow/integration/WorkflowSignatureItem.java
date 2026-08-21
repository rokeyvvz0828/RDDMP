package com.ccb.workflow.integration;

import java.time.LocalDateTime;

public record WorkflowSignatureItem(
        long id,
        long instanceId,
        long taskId,
        int businessRound,
        String action,
        String comment,
        String dataDigest,
        long signerId,
        String signerUsername,
        String signerDisplayName,
        LocalDateTime signedAt
) {
}
