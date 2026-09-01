package com.ccb.architecture.model;

/**
 * 部署单元级联选择用的物理子系统选项（仅已发布可用的物理子系统）。
 */
public record PhysicalSubsystemOption(long id, String code, String shortName, String name,
                                      String logicalSubsystemName, String businessComponentCode,
                                      String businessGroupName, String deploymentPlatform,
                                      String disasterRecoveryMode, String systemLevelCode, String status) {
}
