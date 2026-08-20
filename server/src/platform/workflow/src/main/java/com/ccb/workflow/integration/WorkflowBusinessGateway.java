package com.ccb.workflow.integration;

import com.ccb.security.model.AuthUser;

public interface WorkflowBusinessGateway {
    WorkflowStartResult startByCode(WorkflowStartCommand command, AuthUser operator);

    WorkflowStartResult startByDefinitionId(WorkflowStartDefinitionCommand command, AuthUser operator);

    void terminate(WorkflowTerminateCommand command, AuthUser operator);

    WorkflowProgress progress(long instanceId, AuthUser operator);
}
