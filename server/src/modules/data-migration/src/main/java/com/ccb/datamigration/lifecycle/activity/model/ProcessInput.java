package com.ccb.datamigration.lifecycle.activity.model;

/** 工序保存入参（配置工作台工序配置面板）。 */
public record ProcessInput(Long id, int seq, String processName, Long ownerRoleId, Boolean isRequired,
                           String entryConfig, String execConfig, String exitContent, String exitDeliverableList,
                           String qualifiedRule, Boolean mustAudit, Boolean mustSubmitDeliverable,
                           Long deliverableTemplateId) {
}
