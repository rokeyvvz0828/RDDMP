package com.ccb.system.service;

import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationModuleSummary;
import com.ccb.system.notification.SystemNotificationItem;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class SystemNotificationRepository {
    private final SystemNotificationMapper mapper;

    public SystemNotificationRepository(SystemNotificationMapper mapper) {
        this.mapper = mapper;
    }

    public void insertNotification(long id, long tenantId, String eventId, String moduleCode, String moduleName, String businessType,
                                   String businessKey, String title, String content, NotificationLevel level, String sourceName,
                                   String actionPath, String projectRef, String projectName, Long actorUserId, LocalDateTime createdAt) {
        mapper.insertNotification(id, tenantId, eventId, moduleCode, moduleName, businessType, businessKey, title, content,
                level.name(), sourceName, actionPath, projectRef, projectName, actorUserId, createdAt);
    }

    public Long findNotificationIdForUpdate(long tenantId, String businessType, String eventId) { return mapper.selectNotificationIdForUpdate(tenantId, businessType, eventId); }
    public void insertUserNotification(long notificationId, long tenantId, long userId) { mapper.insertUserNotification(notificationId, tenantId, userId); }
    public long countNotifications(long tenantId, long userId, String view, String moduleCode) { return mapper.countNotifications(tenantId, userId, view, moduleCode); }
    public List<SystemNotificationItem> listNotifications(long tenantId, long userId, String view, String moduleCode, long offset, long limit) {
        return mapper.selectNotifications(tenantId, userId, view, moduleCode, offset, limit).stream().map(this::toItem).toList();
    }
    public List<NotificationModuleSummary> listModules(long tenantId, long userId, String view) {
        return mapper.selectModules(tenantId, userId, view).stream().map(row -> new NotificationModuleSummary((String) row.get("module_code"),
                (String) row.get("module_name"), number(row, "total_count"), number(row, "unread_count"))).toList();
    }
    public long unreadCount(long tenantId, long userId) { Long count = mapper.countUnread(tenantId, userId); return count == null ? 0 : count; }
    public int markRead(LocalDateTime now, long tenantId, long userId, long notificationId) { return mapper.markRead(now, tenantId, userId, notificationId); }
    public int markAllRead(LocalDateTime now, long tenantId, long userId) { return mapper.markAllRead(now, tenantId, userId); }
    public int archiveReadNotification(LocalDateTime now, long tenantId, long userId, long notificationId) { return mapper.archiveReadNotification(now, tenantId, userId, notificationId); }
    public Boolean findReadState(long tenantId, long userId, long notificationId) { return mapper.selectReadState(tenantId, userId, notificationId); }
    public int restore(long tenantId, long userId, long notificationId) { return mapper.restore(tenantId, userId, notificationId); }
    public int archiveAllRead(LocalDateTime now, long tenantId, long userId) { return mapper.archiveAllRead(now, tenantId, userId); }
    public List<Long> findActiveUserIds(long tenantId, List<Long> userIds) { return mapper.selectActiveUserIds(tenantId, userIds); }
    public void insertAudit(long id, long tenantId, long operatorId, String requestPath) { mapper.insertAudit(id, tenantId, operatorId, requestPath); }

    private SystemNotificationItem toItem(Map<String, Object> row) {
        return new SystemNotificationItem(number(row, "id"), (String) row.get("title"), (String) row.get("content"),
                NotificationLevel.valueOf((String) row.get("notification_level")), (String) row.get("source_name"),
                (String) row.get("module_code"), (String) row.get("module_name"), (String) row.get("business_type"),
                (String) row.get("business_key"), (String) row.get("action_path"), (String) row.get("project_ref"),
                (String) row.get("project_name"), Boolean.TRUE.equals(row.get("is_read")), time(row.get("read_at")),
                time(row.get("archived_at")), time(row.get("created_at")));
    }

    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private LocalDateTime time(Object value) { return value == null ? null : ((Timestamp) value).toLocalDateTime(); }
}
