package com.ccb.workflow.integration;

public interface WorkflowLifecycleConsumer {
    String subscriberKey();

    boolean supports(String businessType);

    void consume(WorkflowLifecycleEvent event);
}
