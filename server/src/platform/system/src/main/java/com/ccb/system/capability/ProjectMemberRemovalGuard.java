package com.ccb.system.capability;

public interface ProjectMemberRemovalGuard {
    void requireNoPendingTasks(long tenantId, long projectId, long userId);
}
