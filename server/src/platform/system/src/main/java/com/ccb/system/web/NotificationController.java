package com.ccb.system.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import com.ccb.system.notification.NotificationArchiveResult;
import com.ccb.system.notification.NotificationReadAllResult;
import com.ccb.system.notification.NotificationModuleSummary;
import com.ccb.system.notification.NotificationUnreadCount;
import com.ccb.system.notification.NotificationView;
import com.ccb.system.notification.SystemNotificationItem;
import com.ccb.system.service.SystemNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final SystemNotificationService service;

    public NotificationController(SystemNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SystemPage<SystemNotificationItem>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String moduleCode,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(new PageQuery(page, size), NotificationView.resolve(view, unreadOnly), moduleCode, user), TraceId.getOrCreate());
    }

    @GetMapping("/modules")
    public ApiResponse<List<NotificationModuleSummary>> modules(
            @RequestParam(required = false) String view,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.modules(NotificationView.resolve(view, false), user), TraceId.getOrCreate());
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCount> unreadCount(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.unreadCount(user), TraceId.getOrCreate());
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable long notificationId, @AuthenticationPrincipal AuthUser user) {
        service.markRead(notificationId, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationReadAllResult> markAllRead(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.markAllRead(user), TraceId.getOrCreate());
    }

    @PatchMapping("/{notificationId}/archive")
    public ApiResponse<Void> archive(@PathVariable long notificationId, @AuthenticationPrincipal AuthUser user) {
        service.archive(notificationId, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PatchMapping("/{notificationId}/restore")
    public ApiResponse<Void> restore(@PathVariable long notificationId, @AuthenticationPrincipal AuthUser user) {
        service.restore(notificationId, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PatchMapping("/archive-read")
    public ApiResponse<NotificationArchiveResult> archiveRead(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.archiveRead(user), TraceId.getOrCreate());
    }
}
