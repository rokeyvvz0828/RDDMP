package com.ccb.architecture.persistence;

/**
 * 交付单元编号容量耗尽。
 */
public class DeliveryUnitNumberCapacityExceededException extends RuntimeException {
    public DeliveryUnitNumberCapacityExceededException(String message) {
        super(message);
    }
}
