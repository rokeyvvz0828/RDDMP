package com.ccb.system.notification;

import java.util.List;

public record NotificationPublishCommand(
        long tenantId,
        String eventId,
        String businessType,
        String businessKey,
        List<Long> recipientUserIds,
        String title,
        String content,
        NotificationLevel level,
        String sourceName,
        String actionPath,
        Long actorUserId) {
}
