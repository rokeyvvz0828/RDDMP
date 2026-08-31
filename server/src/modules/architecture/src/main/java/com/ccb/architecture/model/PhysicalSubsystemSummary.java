package com.ccb.architecture.model;

/** 逻辑子系统详情中的已发布物理子系统摘要。 */
public record PhysicalSubsystemSummary(
        long id,
        String code,
        String shortName,
        String name,
        String numberSlot,
        String englishName,
        String status,
        long rowVersion) {
}
