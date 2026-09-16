package com.ccb.system.capability;

/** 项目删除前的跨模块引用保护。 */
public interface ProjectDeletionGuard {
    void requireNoReferences(long tenantId, long projectId);
}
