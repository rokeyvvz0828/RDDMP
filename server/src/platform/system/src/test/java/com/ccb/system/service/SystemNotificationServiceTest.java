package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.notification.NotificationArchiveResult;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.NotificationView;
import com.ccb.system.notification.SystemNotificationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemNotificationServiceTest {
    @Mock private SystemNotificationRepository repository;
    private SystemNotificationService service;
    private final AuthUser user = new AuthUser(7L, 1L, "tester", "", "测试用户", 1L, true);

    @BeforeEach void setUp() { service = new SystemNotificationService(repository); }

    @Test void rejectsEmptyRecipients() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command(List.of(), "/dashboard")));
        assertEquals("通知接收人不能为空", exception.getMessage());
        verify(repository, never()).insertNotification(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any(), any(), any());
    }

    @Test void rejectsExternalActionPath() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command(List.of(7L), "https://example.com")));
        assertEquals("通知只允许使用以 / 开头的站内路由", exception.getMessage());
    }

    @Test void rejectsIncompleteProjectContext() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(projectCommand("P1", null)));
        assertEquals("项目标识和项目名称必须同时提供", exception.getMessage());
    }

    @Test void persistsProjectContextWithNotification() {
        when(repository.findActiveUserIds(eq(1L), any())).thenReturn(List.of(7L));
        when(repository.findNotificationIdForUpdate(1L, "DELIVERY", "event-project-001")).thenReturn(99L);
        service.publish(projectCommand("P1", "项目一"));
        ArgumentCaptor<String> projectRef = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> projectName = ArgumentCaptor.forClass(String.class);
        verify(repository).insertNotification(anyLong(), eq(1L), eq("event-project-001"), eq("delivery"), eq("交付管理"), eq("DELIVERY"), eq("PRJ-001"), eq("交付状态已更新"), eq("项目已进入测试阶段"), eq(NotificationLevel.INFO), eq("交付管理"), eq("/dashboard"), projectRef.capture(), projectName.capture(), eq(7L), any(LocalDateTime.class));
        assertEquals("P1", projectRef.getValue()); assertEquals("项目一", projectName.getValue());
    }

    @Test void returnsExistingNotificationForRepeatedEvent() {
        when(repository.findActiveUserIds(eq(1L), any())).thenReturn(List.of(7L));
        when(repository.findNotificationIdForUpdate(1L, "DELIVERY", "event-001")).thenReturn(99L);
        assertEquals(99L, service.publish(command(List.of(7L), "/dashboard")));
        verify(repository, never()).insertUserNotification(anyLong(), anyLong(), anyLong());
    }

    @Test void rejectsRecipientsOutsideTenant() {
        when(repository.findActiveUserIds(eq(1L), any())).thenReturn(List.of());
        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command(List.of(7L), "/dashboard")));
        assertEquals("通知接收人或操作人不存在、已停用或不属于当前租户", exception.getMessage());
    }

    @Test void listDelegatesSelectedViewAndProjectContext() {
        SystemNotificationItem item = new SystemNotificationItem(1L, "标题", "内容", NotificationLevel.INFO, "来源", "delivery", "交付", "DELIVERY", "P1", "/dashboard", "P1", "项目一", false, null, null, LocalDateTime.now());
        when(repository.countNotifications(1L, 7L, "UNREAD", "delivery")).thenReturn(1L);
        when(repository.listNotifications(1L, 7L, "UNREAD", "delivery", 0L, 20L)).thenReturn(List.of(item));
        assertEquals("项目一", service.list(new PageQuery(1, 20), NotificationView.UNREAD, "delivery", user).records().get(0).projectName());
    }

    @Test void readAndArchiveOperationsAreAuthenticatedRecipientScoped() {
        when(repository.archiveReadNotification(any(), eq(1L), eq(7L), eq(88L))).thenReturn(0);
        when(repository.findReadState(1L, 7L, 88L)).thenReturn(false);
        service.markRead(88L, user); service.markAllRead(user);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.archive(88L, user));
        assertEquals(ErrorCode.CONFLICT, exception.code());
        verify(repository).markRead(any(), eq(1L), eq(7L), eq(88L));
        verify(repository).markAllRead(any(), eq(1L), eq(7L));
    }

    @Test void archiveRestoreAndBulkArchiveUseRepository() {
        when(repository.archiveReadNotification(any(), eq(1L), eq(7L), eq(88L))).thenReturn(1);
        when(repository.archiveAllRead(any(), eq(1L), eq(7L))).thenReturn(3);
        service.archive(88L, user); service.restore(88L, user);
        NotificationArchiveResult result = service.archiveRead(user);
        assertEquals(3, result.changed());
        verify(repository).restore(1L, 7L, 88L);
    }

    @Test void legacyConstructorDefaultsToPlatformScope() {
        NotificationPublishCommand command = command(List.of(7L), "/dashboard");
        assertNull(command.projectRef()); assertNull(command.projectName());
    }

    @Test void notificationViewPreservesLegacyUnreadOnlyAndRejectsUnknownValues() {
        assertEquals(NotificationView.ALL, NotificationView.resolve(null, false));
        assertEquals(NotificationView.UNREAD, NotificationView.resolve(null, true));
        assertEquals(NotificationView.ARCHIVED, NotificationView.resolve("archived", false));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class, () -> NotificationView.resolve("deleted", false)).code());
    }

    private NotificationPublishCommand command(List<Long> recipients, String actionPath) { return new NotificationPublishCommand(1L, "event-001", "delivery", "交付管理", "DELIVERY", "PRJ-001", recipients, "交付状态已更新", "项目已进入测试阶段", NotificationLevel.INFO, "交付管理", actionPath, 7L); }
    private NotificationPublishCommand projectCommand(String projectRef, String projectName) { return new NotificationPublishCommand(1L, "event-project-001", "delivery", "交付管理", "DELIVERY", "PRJ-001", List.of(7L), "交付状态已更新", "项目已进入测试阶段", NotificationLevel.INFO, "交付管理", "/dashboard", 7L, projectRef, projectName); }
}
