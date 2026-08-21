package com.ccb.system.notification;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;

import java.util.Locale;

public enum NotificationView {
    ALL,
    UNREAD,
    ARCHIVED;

    public static NotificationView resolve(String value, boolean unreadOnly) {
        if (value == null || value.isBlank()) return unreadOnly ? UNREAD : ALL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "通知视图参数不正确");
        }
    }
}
