package com.ccb.architecture.model;

/** 逻辑父记录的并发锁快照；即使记录已软删除也必须可见。 */
public record LogicalSubsystemLock(long id, boolean deleted) {
}
