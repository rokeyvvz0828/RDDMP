package com.ccb.system.notification;

import java.util.List;

public record NotificationPublishCommand(
        long tenantId,
        String eventId,
        String moduleCode,
        String moduleName,
        String businessType,
        String businessKey,
        List<Long> recipientUserIds,
        String title,
        String content,
        NotificationLevel level,
        String sourceName,
        String actionPath,
        Long actorUserId,
        String projectRef,
        String projectName) {

    public NotificationPublishCommand(
            long tenantId,
            String eventId,
            String moduleCode,
            String moduleName,
            String businessType,
            String businessKey,
            List<Long> recipientUserIds,
            String title,
            String content,
            NotificationLevel level,
            String sourceName,
            String actionPath,
            Long actorUserId) {
        this(tenantId, eventId, moduleCode, moduleName, businessType, businessKey, recipientUserIds,
                title, content, level, sourceName, actionPath, actorUserId, null, null);
    }
}
