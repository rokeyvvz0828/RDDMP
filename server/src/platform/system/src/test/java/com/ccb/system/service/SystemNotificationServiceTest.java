package com.ccb.system.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
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
        verify(jdbc).update(contains("user_id = ? AND is_read = 0"), any(LocalDateTime.class), eq(1L), eq(7L));
    }

    private NotificationPublishCommand command(List<Long> recipients, String actionPath) {
        return new NotificationPublishCommand(
                1L,
                "event-001",
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
}
