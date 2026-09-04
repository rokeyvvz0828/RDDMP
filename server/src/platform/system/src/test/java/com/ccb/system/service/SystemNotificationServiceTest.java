package com.ccb.system.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.api.PageQuery;
import com.ccb.security.model.AuthUser;
import com.ccb.system.notification.NotificationArchiveResult;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.NotificationView;
import com.ccb.system.notification.SystemNotificationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemNotificationServiceTest {
    @Mock
    private JdbcTemplate jdbc;

    private SystemNotificationService service;
    private final AuthUser user = new AuthUser(7L, 1L, "tester", "", "测试用户", 1L, true);

    @BeforeEach
    void setUp() {
        service = new SystemNotificationService(jdbc);
    }

    @Test
    void rejectsEmptyRecipients() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command(List.of(), "/dashboard")));
        assertEquals("通知接收人不能为空", exception.getMessage());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsExternalActionPath() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command(List.of(7L), "https://example.com")));
        assertEquals("通知只允许使用以 / 开头的站内路由", exception.getMessage());
    }

    @Test
    void rejectsIncompleteProjectContext() {
        NotificationPublishCommand command = projectCommand("P1", null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command));

        assertEquals("项目标识和项目名称必须同时提供", exception.getMessage());
        verify(jdbc, never()).update(startsWith("INSERT INTO sys_notification"), any(Object[].class));
    }

    @Test
    void rejectsOversizedProjectContext() {
        NotificationPublishCommand command = projectCommand("P".repeat(65), "项目一");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command));

        assertEquals("项目标识不能超过 64 个字符", exception.getMessage());
        verify(jdbc, never()).update(startsWith("INSERT INTO sys_notification"), any(Object[].class));
    }

    @Test
    void persistsProjectContextWithNotification() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(7L));
        when(jdbc.update(startsWith("INSERT INTO sys_notification"), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("FROM sys_notification"), eq(Long.class), any(Object[].class))).thenReturn(99L);

        service.publish(projectCommand("P1", "项目一"));

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(startsWith("INSERT INTO sys_notification"), parameters.capture());
        assertEquals("P1", parameters.getValue()[12]);
        assertEquals("项目一", parameters.getValue()[13]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsProjectContextFromPersistedNotification() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(anyString())).thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
            case "notification_level" -> "INFO";
            case "project_ref" -> "P1";
            case "project_name" -> "项目一";
            default -> null;
        });
        when(resultSet.getTimestamp(anyString())).thenAnswer(invocation ->
                "created_at".equals(invocation.getArgument(0, String.class))
                        ? Timestamp.valueOf("2026-09-01 10:00:00")
                        : null);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<SystemNotificationItem> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });

        SystemNotificationItem item = service.list(new PageQuery(1, 20), NotificationView.ALL, null, user).records().get(0);

        assertEquals("P1", item.projectRef());
        assertEquals("项目一", item.projectName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsUnreadNotificationsBeforeReadNotifications() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.list(new PageQuery(1, 20), NotificationView.ALL, null, user);

        verify(jdbc).query(contains("ORDER BY un.is_read ASC, n.created_at DESC"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void legacyConstructorDefaultsToPlatformScope() {
        NotificationPublishCommand command = command(List.of(7L), "/dashboard");

        assertNull(command.projectRef());
        assertNull(command.projectName());
    }

    @Test
    void returnsExistingNotificationForRepeatedEvent() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(7L));
        when(jdbc.update(startsWith("INSERT INTO sys_notification"), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("FROM sys_notification"), eq(Long.class), any(Object[].class))).thenReturn(99L);

        assertEquals(99L, service.publish(command(List.of(7L), "/dashboard")));
        verify(jdbc, never()).update(startsWith("INSERT INTO sys_user_notification"), any(Object[].class));
    }

    @Test
    void rejectsRecipientsOutsideTenant() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(command(List.of(7L), "/dashboard")));

        assertEquals("通知接收人或操作人不存在、已停用或不属于当前租户", exception.getMessage());
        verify(jdbc, never()).update(startsWith("INSERT INTO sys_notification"), any(Object[].class));
    }

    @Test
    void readUpdatesAreScopedToAuthenticatedTenantAndUser() {
        service.markRead(88L, user);
        service.markAllRead(user);

        verify(jdbc).update(contains("notification_id = ?"), any(LocalDateTime.class), eq(1L), eq(7L), eq(88L));
        verify(jdbc).update(contains("user_id = ? AND is_read = 0 AND archived_at IS NULL"), any(LocalDateTime.class), eq(1L), eq(7L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificationViewsApplyConsistentArchiveFilters() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.list(new PageQuery(1, 20), NotificationView.ALL, null, user);
        service.list(new PageQuery(1, 20), NotificationView.UNREAD, null, user);
        service.list(new PageQuery(1, 20), NotificationView.ARCHIVED, null, user);

        verify(jdbc, atLeastOnce()).queryForObject(contains("un.archived_at IS NULL"), eq(Long.class), any(Object[].class));
        verify(jdbc, atLeastOnce()).queryForObject(contains("un.archived_at IS NULL AND un.is_read = 0"), eq(Long.class), any(Object[].class));
        verify(jdbc, atLeastOnce()).queryForObject(contains("un.archived_at IS NOT NULL"), eq(Long.class), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void moduleSummaryUsesSelectedArchiveView() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.modules(NotificationView.ARCHIVED, user);

        verify(jdbc).query(contains("un.archived_at IS NOT NULL"), any(RowMapper.class), eq(1L), eq(7L));
    }

    @Test
    void unreadCountExcludesArchivedRows() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);

        assertEquals(2L, service.unreadCount(user).count());

        verify(jdbc).queryForObject(contains("archived_at IS NULL"), eq(Long.class), eq(1L), eq(7L));
    }

    @Test
    void archiveRejectsUnreadNotification() {
        when(jdbc.update(contains("SET archived_at = ?"), any(LocalDateTime.class), eq(1L), eq(7L), eq(88L))).thenReturn(0);
        when(jdbc.queryForObject(contains("SELECT is_read"), eq(Boolean.class), eq(1L), eq(7L), eq(88L))).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.archive(88L, user));

        assertEquals(ErrorCode.CONFLICT, exception.code());
        assertEquals("请先阅读消息后再归档", exception.getMessage());
    }

    @Test
    void archiveAndRestoreAreScopedToAuthenticatedRecipient() {
        when(jdbc.update(contains("SET archived_at = ?"), any(LocalDateTime.class), eq(1L), eq(7L), eq(88L))).thenReturn(1);

        service.archive(88L, user);
        service.restore(88L, user);

        verify(jdbc).update(contains("is_read = 1 AND archived_at IS NULL"), any(LocalDateTime.class), eq(1L), eq(7L), eq(88L));
        verify(jdbc).update(contains("SET archived_at = NULL"), eq(1L), eq(7L), eq(88L));
    }

    @Test
    void repeatedArchiveIsIdempotent() {
        when(jdbc.update(contains("SET archived_at = ?"), any(LocalDateTime.class), eq(1L), eq(7L), eq(88L))).thenReturn(0);
        when(jdbc.queryForObject(contains("SELECT is_read"), eq(Boolean.class), eq(1L), eq(7L), eq(88L))).thenReturn(true);

        service.archive(88L, user);

        verify(jdbc).queryForObject(contains("SELECT is_read"), eq(Boolean.class), eq(1L), eq(7L), eq(88L));
    }

    @Test
    void archiveReadOnlyChangesActiveReadNotifications() {
        when(jdbc.update(contains("is_read = 1 AND archived_at IS NULL"), any(LocalDateTime.class), eq(1L), eq(7L))).thenReturn(3);

        NotificationArchiveResult result = service.archiveRead(user);

        assertEquals(3, result.changed());
    }

    @Test
    void notificationViewPreservesLegacyUnreadOnlyAndRejectsUnknownValues() {
        assertEquals(NotificationView.ALL, NotificationView.resolve(null, false));
        assertEquals(NotificationView.UNREAD, NotificationView.resolve(null, true));
        assertEquals(NotificationView.ARCHIVED, NotificationView.resolve("archived", false));

        BusinessException exception = assertThrows(BusinessException.class, () -> NotificationView.resolve("deleted", false));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
        assertEquals("通知视图参数不正确", exception.getMessage());
    }

    private NotificationPublishCommand command(List<Long> recipients, String actionPath) {
        return new NotificationPublishCommand(
                1L,
                "event-001",
                "delivery",
                "交付管理",
                "DELIVERY",
                "PRJ-001",
                recipients,
                "交付状态已更新",
                "项目已进入测试阶段",
                NotificationLevel.INFO,
                "交付管理",
                actionPath,
                7L);
    }

    private NotificationPublishCommand projectCommand(String projectRef, String projectName) {
        return new NotificationPublishCommand(
                1L,
                "event-project-001",
                "delivery",
                "交付管理",
                "DELIVERY",
                "PRJ-001",
                List.of(7L),
                "交付状态已更新",
                "项目已进入测试阶段",
                NotificationLevel.INFO,
                "交付管理",
                "/dashboard",
                7L,
                projectRef,
                projectName);
    }
}
