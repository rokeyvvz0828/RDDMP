package com.ccb.architecture.integration;

import java.util.Objects;

/**
 * 引用检查的中性输入值；租户和目标值均由服务端调用方提供。
 */
public record ReferenceCheckRequest(
        long tenantId,
        SubsystemKind subsystemKind,
        long subsystemId,
        Operation operation) {

    public ReferenceCheckRequest {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须为正数");
        }
        subsystemKind = Objects.requireNonNull(subsystemKind, "subsystemKind 不能为空");
        if (subsystemId <= 0) {
            throw new IllegalArgumentException("subsystemId 必须为正数");
        }
        operation = Objects.requireNonNull(operation, "operation 不能为空");
    }

    /** 子系统目标类型，避免向 SPI 泄露内部领域模型。 */
    public enum SubsystemKind {
        LOGICAL,
        PHYSICAL
    }

    /** 触发引用检查的业务操作。 */
    public enum Operation {
        CREATE_REFERENCE,
        OFFLINE,
        VOID
    }
}
