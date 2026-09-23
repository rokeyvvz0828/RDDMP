package com.ccb.datamigration.lifecycle.model;

/** 组件选择器选项：仅返回可用组件（dm_component.deleted=0，D2）。 */
public record LifecycleComponentOption(long id, long tenantId, long projectId, String projectCode, String projectName,
                                       String physicalSubsystemCode, String systemShortName, String systemName,
                                       String businessGroupName, long ownerId) {
}
