package com.ccb.workflow.integration;

public record WorkflowBusinessContext(
        String moduleCode,
        String moduleName,
        String businessType,
        String businessKey,
        String businessTitle,
        int businessRound,
        String projectRef,
        String projectName,
        String actionPath,
        String dataDigest
) {
}
