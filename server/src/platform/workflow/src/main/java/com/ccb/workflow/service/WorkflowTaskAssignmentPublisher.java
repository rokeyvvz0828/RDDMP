package com.ccb.workflow.service;

import com.ccb.workflow.integration.WorkflowTaskAssignedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTaskAssignmentPublisher {
    private final ApplicationEventPublisher events;

    public WorkflowTaskAssignmentPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void assigned(long tenantId, long instanceId, long taskId, long assigneeId, long operatorId) {
        events.publishEvent(new WorkflowTaskAssignedEvent(tenantId, instanceId, taskId, assigneeId, operatorId));
    }
}
