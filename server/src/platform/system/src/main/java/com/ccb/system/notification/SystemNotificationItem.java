package com.ccb.system.notification;

import java.time.LocalDateTime;

public record SystemNotificationItem(
        long id,
        String title,
        String content,
        NotificationLevel level,
        String sourceName,
        String businessType,
        String businessKey,
        String actionPath,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt) {
}
