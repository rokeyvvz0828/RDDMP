package com.ccb.architecture.persistence;

/**
 * 部署单元编号容量耗尽。
 */
public class DeploymentUnitNumberCapacityExceededException extends RuntimeException {
    public DeploymentUnitNumberCapacityExceededException(String message) {
        super(message);
    }
}
