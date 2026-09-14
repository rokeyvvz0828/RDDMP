package com.ccb.requirement.support;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowProgress;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartDefinitionCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import com.ccb.workflow.integration.WorkflowTerminateCommand;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用工作流业务网关：返回模拟审批流实例 ID，
 * 不触发 Flowable/JDBC/事件链路。用于 RequirementDifferenceService/LegacyService 单测。
 */
public class StubWorkflowService implements WorkflowBusinessGateway {
    private final AtomicLong nextInstanceId = new AtomicLong(1000L);
    private final List<String> started = new java.util.ArrayList<>();

    @Override
    public WorkflowStartResult startByDefinitionId(WorkflowStartDefinitionCommand command, AuthUser operator) {
        started.add(command.context().businessKey());
        long instanceId = nextInstanceId.getAndIncrement();
        return new WorkflowStartResult(instanceId, command.definitionId(), 1, "RUNNING", command.context());
    }

    @Override
    public WorkflowStartResult startByCode(WorkflowStartCommand command, AuthUser operator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void terminate(WorkflowTerminateCommand command, AuthUser operator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowProgress progress(long instanceId, AuthUser operator) {
        throw new UnsupportedOperationException();
    }

    public List<String> started() {
        return started;
    }
}
