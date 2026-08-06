package com.ccb.system.notification;

public interface SystemNotificationPublisher {
    long publish(NotificationPublishCommand command);
}
