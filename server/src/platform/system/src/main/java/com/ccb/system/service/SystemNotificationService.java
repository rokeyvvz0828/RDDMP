package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import com.ccb.system.notification.NotificationArchiveResult;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationModuleSummary;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.NotificationReadAllResult;
import com.ccb.system.notification.NotificationUnreadCount;
import com.ccb.system.notification.NotificationView;
import com.ccb.system.notification.SystemNotificationItem;
import com.ccb.system.notification.SystemNotificationPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class SystemNotificationService implements SystemNotificationPublisher {
    private static final int MAX_RECIPIENTS = 500;
    private static final Pattern MODULE_CODE = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final JdbcTemplate jdbc;

    public SystemNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long publish(NotificationPublishCommand command) {
        ValidatedNotification notification = validate(command);
        validateUsers(notification);

        long notificationId = nextId();
        jdbc.update(
                "INSERT INTO sys_notification (id, tenant_id, event_id, module_code, module_name, business_type, business_key, title, content, notification_level, source_name, action_path, project_ref, project_name, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE id = id",
                notificationId,
                notification.tenantId(),
                notification.eventId(),
                notification.moduleCode(),
                notification.moduleName(),
                notification.businessType(),
                notification.businessKey(),
                notification.title(),
                notification.content(),
                notification.level().name(),
                notification.sourceName(),
                notification.actionPath(),
                notification.projectRef(),
                notification.projectName(),
                notification.actorUserId(),
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        long persistedId = jdbc.queryForObject(
                "SELECT id FROM sys_notification WHERE tenant_id = ? AND business_type = ? AND event_id = ? FOR UPDATE",
                Long.class,
                notification.tenantId(),
                notification.businessType(),
                notification.eventId());
        if (persistedId != notificationId) return persistedId;

        for (Long userId : notification.recipientUserIds()) {
            jdbc.update(
                    "INSERT INTO sys_user_notification (notification_id, tenant_id, user_id) VALUES (?, ?, ?)",
                    notificationId,
                    notification.tenantId(),
                    userId);
        }
        audit(notification, notificationId);
        return notificationId;
    }

    public SystemPage<SystemNotificationItem> list(PageQuery pageQuery, NotificationView view, String moduleCode, AuthUser user) {
        String viewFilter = viewFilter(view);
        String normalizedModule = optionalModuleCode(moduleCode);
        String moduleFilter = normalizedModule == null ? "" : " AND n.module_code = ?";
        List<Object> queryArgs = new ArrayList<>(List.of(user.tenantId(), user.id()));
        if (normalizedModule != null) queryArgs.add(normalizedModule);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_notification un JOIN sys_notification n ON n.id = un.notification_id AND n.tenant_id = un.tenant_id WHERE un.tenant_id = ? AND un.user_id = ?" + viewFilter + moduleFilter,
                Long.class,
                queryArgs.toArray());
        queryArgs.add((pageQuery.page() - 1) * pageQuery.size());
        queryArgs.add(pageQuery.size());
        List<SystemNotificationItem> items = jdbc.query(
                "SELECT n.id, n.title, n.content, n.notification_level, n.source_name, n.module_code, n.module_name, n.business_type, n.business_key, n.action_path, n.project_ref, n.project_name, un.is_read, un.read_at, un.archived_at, n.created_at " +
                        "FROM sys_user_notification un JOIN sys_notification n ON n.id = un.notification_id AND n.tenant_id = un.tenant_id " +
                        "WHERE un.tenant_id = ? AND un.user_id = ?" + viewFilter + moduleFilter +
                        " ORDER BY un.is_read ASC, n.created_at DESC, n.id DESC LIMIT ?, ?",
                (resultSet, rowNum) -> new SystemNotificationItem(
                        resultSet.getLong("id"),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        NotificationLevel.valueOf(resultSet.getString("notification_level")),
                        resultSet.getString("source_name"),
                        resultSet.getString("module_code"),
                        resultSet.getString("module_name"),
                        resultSet.getString("business_type"),
                        resultSet.getString("business_key"),
                        resultSet.getString("action_path"),
                        resultSet.getString("project_ref"),
                        resultSet.getString("project_name"),
                        resultSet.getBoolean("is_read"),
                        resultSet.getTimestamp("read_at") == null ? null : resultSet.getTimestamp("read_at").toLocalDateTime(),
                        resultSet.getTimestamp("archived_at") == null ? null : resultSet.getTimestamp("archived_at").toLocalDateTime(),
                        resultSet.getTimestamp("created_at").toLocalDateTime()),
                queryArgs.toArray());
        return new SystemPage<>(items, total, pageQuery.page(), pageQuery.size());
    }

    public List<NotificationModuleSummary> modules(NotificationView view, AuthUser user) {
        return jdbc.query(
                "SELECT n.module_code, n.module_name, COUNT(*) AS total_count, SUM(CASE WHEN un.is_read = 0 THEN 1 ELSE 0 END) AS unread_count FROM sys_user_notification un JOIN sys_notification n ON n.id = un.notification_id AND n.tenant_id = un.tenant_id WHERE un.tenant_id = ? AND un.user_id = ?" + viewFilter(view) + " GROUP BY n.module_code, n.module_name ORDER BY MAX(n.created_at) DESC, n.module_code",
                (resultSet, rowNum) -> new NotificationModuleSummary(resultSet.getString("module_code"), resultSet.getString("module_name"), resultSet.getLong("total_count"), resultSet.getLong("unread_count")),
                user.tenantId(), user.id());
    }

    public NotificationUnreadCount unreadCount(AuthUser user) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_notification WHERE tenant_id = ? AND user_id = ? AND is_read = 0 AND archived_at IS NULL",
                Long.class,
                user.tenantId(),
                user.id());
        return new NotificationUnreadCount(count == null ? 0 : count);
    }

    @Transactional
    public void markRead(long notificationId, AuthUser user) {
        jdbc.update(
                "UPDATE sys_user_notification SET is_read = 1, read_at = COALESCE(read_at, ?) WHERE tenant_id = ? AND user_id = ? AND notification_id = ? AND is_read = 0 AND archived_at IS NULL",
                now(),
                user.tenantId(),
                user.id(),
                notificationId);
    }

    @Transactional
    public NotificationReadAllResult markAllRead(AuthUser user) {
        int changed = jdbc.update(
                "UPDATE sys_user_notification SET is_read = 1, read_at = ? WHERE tenant_id = ? AND user_id = ? AND is_read = 0 AND archived_at IS NULL",
                now(),
                user.tenantId(),
                user.id());
        return new NotificationReadAllResult(changed);
    }

    @Transactional
    public void archive(long notificationId, AuthUser user) {
        if (archiveReadNotification(notificationId, user) > 0) return;

        Boolean read;
        try {
            read = jdbc.queryForObject(
                    "SELECT is_read FROM sys_user_notification WHERE tenant_id = ? AND user_id = ? AND notification_id = ?",
                    Boolean.class,
                    user.tenantId(),
                    user.id(),
                    notificationId);
        } catch (EmptyResultDataAccessException exception) {
            return;
        }
        if (Boolean.FALSE.equals(read)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先阅读消息后再归档");
        }
        archiveReadNotification(notificationId, user);
    }

    @Transactional
    public void restore(long notificationId, AuthUser user) {
        jdbc.update(
                "UPDATE sys_user_notification SET archived_at = NULL WHERE tenant_id = ? AND user_id = ? AND notification_id = ? AND archived_at IS NOT NULL",
                user.tenantId(),
                user.id(),
                notificationId);
    }

    @Transactional
    public NotificationArchiveResult archiveRead(AuthUser user) {
        int changed = jdbc.update(
                "UPDATE sys_user_notification SET archived_at = ? WHERE tenant_id = ? AND user_id = ? AND is_read = 1 AND archived_at IS NULL",
                now(),
                user.tenantId(),
                user.id());
        return new NotificationArchiveResult(changed);
    }

    private int archiveReadNotification(long notificationId, AuthUser user) {
        return jdbc.update(
                "UPDATE sys_user_notification SET archived_at = ? WHERE tenant_id = ? AND user_id = ? AND notification_id = ? AND is_read = 1 AND archived_at IS NULL",
                now(),
                user.tenantId(),
                user.id(),
                notificationId);
    }

    private String viewFilter(NotificationView view) {
        return switch (view == null ? NotificationView.ALL : view) {
            case ALL -> " AND un.archived_at IS NULL";
            case UNREAD -> " AND un.archived_at IS NULL AND un.is_read = 0";
            case ARCHIVED -> " AND un.archived_at IS NOT NULL";
        };
    }

    private ValidatedNotification validate(NotificationPublishCommand command) {
        if (command == null) throw badRequest("通知发布命令不能为空");
        if (command.tenantId() <= 0) throw badRequest("租户编号无效");
        Set<Long> recipients = new LinkedHashSet<>();
        if (command.recipientUserIds() != null) {
            command.recipientUserIds().stream().filter(id -> id != null && id > 0).forEach(recipients::add);
        }
        if (recipients.isEmpty()) throw badRequest("通知接收人不能为空");
        if (recipients.size() > MAX_RECIPIENTS) throw badRequest("单次通知接收人不能超过 " + MAX_RECIPIENTS + " 人");
        if (command.actorUserId() != null && command.actorUserId() <= 0) throw badRequest("操作人编号无效");

        String actionPath = optional(command.actionPath(), 512, "站内路由");
        if (actionPath != null && (!actionPath.startsWith("/") || actionPath.startsWith("//") || actionPath.contains("://") || actionPath.contains("\\") || actionPath.contains("\r") || actionPath.contains("\n"))) {
            throw badRequest("通知只允许使用以 / 开头的站内路由");
        }
        String projectRef = optional(command.projectRef(), 64, "项目标识");
        String projectName = optional(command.projectName(), 128, "项目名称");
        if ((projectRef == null) != (projectName == null)) {
            throw badRequest("项目标识和项目名称必须同时提供");
        }
        return new ValidatedNotification(
                command.tenantId(),
                required(command.eventId(), 128, "事件标识"),
                requiredModuleCode(command.moduleCode()),
                required(command.moduleName(), 128, "业务板块名称"),
                required(command.businessType(), 64, "业务类型"),
                required(command.businessKey(), 128, "业务主键"),
                List.copyOf(recipients),
                required(command.title(), 200, "通知标题"),
                required(command.content(), 2000, "通知内容"),
                command.level() == null ? NotificationLevel.INFO : command.level(),
                required(command.sourceName(), 128, "通知来源"),
                actionPath,
                command.actorUserId(),
                projectRef,
                projectName);
    }

    private void validateUsers(ValidatedNotification notification) {
        Set<Long> expected = new LinkedHashSet<>(notification.recipientUserIds());
        if (notification.actorUserId() != null) expected.add(notification.actorUserId());
        String placeholders = String.join(", ", expected.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(notification.tenantId());
        args.addAll(expected);
        List<Long> validUsers = jdbc.queryForList(
                "SELECT id FROM sys_user WHERE tenant_id = ? AND status = 1 AND deleted = 0 AND id IN (" + placeholders + ")",
                Long.class,
                args.toArray());
        if (!new LinkedHashSet<>(validUsers).equals(expected)) {
            throw badRequest("通知接收人或操作人不存在、已停用或不属于当前租户");
        }
    }

    private String required(String value, int maxLength, String label) {
        String normalized = optional(value, maxLength, label);
        if (normalized == null) throw badRequest(label + "不能为空");
        return normalized;
    }

    private String optional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw badRequest(label + "不能超过 " + maxLength + " 个字符");
        return normalized;
    }

    private String requiredModuleCode(String value) {
        String normalized = required(value, 64, "业务板块编码");
        if (!MODULE_CODE.matcher(normalized).matches()) throw badRequest("业务板块编码格式不正确");
        return normalized;
    }

    private String optionalModuleCode(String value) {
        if (value == null || value.isBlank()) return null;
        return requiredModuleCode(value);
    }

    private void audit(ValidatedNotification notification, long notificationId) {
        jdbc.update(
                "INSERT INTO sys_operation_log (id, tenant_id, operator_id, operation_code, request_method, request_path, success) VALUES (?, ?, ?, 'system:notification:publish', 'SYSTEM', ?, 1)",
                nextId(),
                notification.tenantId(),
                notification.actorUserId() == null ? 0L : notification.actorUserId(),
                notification.businessType() + "/" + notificationId);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    private record ValidatedNotification(
            long tenantId,
            String eventId,
            String moduleCode,
            String moduleName,
            String businessType,
            String businessKey,
            List<Long> recipientUserIds,
            String title,
            String content,
            NotificationLevel level,
            String sourceName,
            String actionPath,
            Long actorUserId,
            String projectRef,
            String projectName) {
    }
}
