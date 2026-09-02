package com.ccb.workflow.integration;

public interface WorkflowPendingTaskQuery {
    long pendingTasks(long tenantId, long projectId, long userId);
}
