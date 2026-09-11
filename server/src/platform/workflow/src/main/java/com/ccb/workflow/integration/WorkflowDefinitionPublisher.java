package com.ccb.workflow.integration;

import com.ccb.security.model.AuthUser;

/** 发布已配置的流程定义，供组合根完成受控的本地初始化。 */
public interface WorkflowDefinitionPublisher {
    void publish(long definitionId, AuthUser operator);
}
