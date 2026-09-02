package com.ccb.system.notification;

import java.time.LocalDateTime;

public record SystemNotificationItem(
        long id,
        String title,
        String content,
        NotificationLevel level,
        String sourceName,
        String moduleCode,
        String moduleName,
        String businessType,
        String businessKey,
        String actionPath,
        String projectRef,
        String projectName,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt) {
}
