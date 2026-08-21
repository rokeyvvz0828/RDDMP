package com.ccb.workflow.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 流程实例到达终态（APPROVED / REJECTED / RETURNED / TERMINATED）时由 WorkflowService 发布。
 * <p>业务模块可监听该事件，根据 businessKey 解析业务单号，按需回写业务字段。
 * <p>事件载荷尽量精简，重数据查询由监听方按需发起。
 */
public record WorkflowInstanceCompletedEvent(
        long tenantId,
        long instanceId,
        String businessKey,
        String status,
        JsonNode variables
) {
}
