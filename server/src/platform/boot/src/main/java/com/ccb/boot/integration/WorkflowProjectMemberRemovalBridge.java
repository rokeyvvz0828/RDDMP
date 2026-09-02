package com.ccb.boot.integration;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.capability.ProjectMemberRemovalGuard;
import com.ccb.workflow.integration.WorkflowPendingTaskQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class WorkflowProjectMemberRemovalBridge implements ProjectMemberRemovalGuard {
    private final ObjectProvider<WorkflowPendingTaskQuery> pendingTasks;

    public WorkflowProjectMemberRemovalBridge(ObjectProvider<WorkflowPendingTaskQuery> pendingTasks) {
        this.pendingTasks = pendingTasks;
    }

    @Override
    public void requireNoPendingTasks(long tenantId, long projectId, long userId) {
        WorkflowPendingTaskQuery query = pendingTasks.getIfAvailable();
        if (query != null && query.pendingTasks(tenantId, projectId, userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该成员仍有项目审批待办，请先转交、撤销申请或终止流程");
        }
    }
}
