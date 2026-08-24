package com.ccb.architecture.integration;

import java.util.Objects;

/**
 * 部署单元引用检查的中性输入值；租户与部署单元由服务端调用方提供。
 */
public record DeploymentUnitReferenceCheckRequest(long tenantId, long unitId) {

    public DeploymentUnitReferenceCheckRequest {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须为正数");
        }
        if (unitId <= 0) {
            throw new IllegalArgumentException("unitId 必须为正数");
        }
    }
}
