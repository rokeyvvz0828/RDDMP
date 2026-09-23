package com.ccb.datamigration.lifecycle.activity.model;

import java.util.List;

/** 工序视图（含前置工序 id 列表，用于配置工作台）。 */
public record ProcessView(long id, long activityId, int seq, String processName, Long ownerRoleId,
                          boolean isRequired, String entryConfig, boolean noPredecessor, String execConfig,
                          String exitContent, String exitDeliverableList, String qualifiedRule,
                          boolean mustAudit, boolean mustSubmitDeliverable, Long deliverableTemplateId,
                          String configStatus, List<Long> predecessorProcessIds) {
}
