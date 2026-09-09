package com.ccb.system.notification;

import java.time.Instant;

public record NotificationStreamTicket(String ticket, Instant expiresAt) {
}
