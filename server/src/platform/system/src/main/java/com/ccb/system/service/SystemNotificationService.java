package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import com.ccb.common.audit.OperationAuditContext;
import com.ccb.system.notification.NotificationArchiveResult;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationModuleSummary;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.NotificationReadAllResult;
import com.ccb.system.notification.NotificationUnreadCount;
import com.ccb.system.notification.NotificationView;
import com.ccb.system.notification.SystemNotificationItem;
import com.ccb.system.notification.SystemNotificationPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class SystemNotificationService implements SystemNotificationPublisher {
    private static final int MAX_RECIPIENTS = 500;
    private static final Pattern MODULE_CODE = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final SystemNotificationRepository repository;

    public SystemNotificationService(SystemNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public long publish(NotificationPublishCommand command) {
        ValidatedNotification notification = validate(command);
        validateUsers(notification);

        long notificationId = nextId();
        repository.insertNotification(notificationId, notification.tenantId(), notification.eventId(), notification.moduleCode(), notification.moduleName(), notification.businessType(), notification.businessKey(), notification.title(), notification.content(), notification.level(), notification.sourceName(), notification.actionPath(), notification.projectRef(), notification.projectName(), notification.actorUserId(), now());
        long persistedId = repository.findNotificationIdForUpdate(notification.tenantId(), notification.businessType(), notification.eventId());
        if (persistedId != notificationId) return persistedId;

        for (Long userId : notification.recipientUserIds()) {
            repository.insertUserNotification(notificationId, notification.tenantId(), userId);
        }
        audit(notification, notificationId);
        return notificationId;
    }

    public SystemPage<SystemNotificationItem> list(PageQuery pageQuery, NotificationView view, String moduleCode, AuthUser user) {
        String normalizedModule = optionalModuleCode(moduleCode);
        String viewName = (view == null ? NotificationView.ALL : view).name();
        long total = repository.countNotifications(user.tenantId(), user.id(), viewName, normalizedModule);
        List<SystemNotificationItem> items = repository.listNotifications(user.tenantId(), user.id(), viewName, normalizedModule, (pageQuery.page() - 1) * pageQuery.size(), pageQuery.size());
        return new SystemPage<>(items, total, pageQuery.page(), pageQuery.size());
    }

    public List<NotificationModuleSummary> modules(NotificationView view, AuthUser user) {
        return repository.listModules(user.tenantId(), user.id(), (view == null ? NotificationView.ALL : view).name());
    }

    public NotificationUnreadCount unreadCount(AuthUser user) {
        return new NotificationUnreadCount(repository.unreadCount(user.tenantId(), user.id()));
    }

    @Transactional
    public void markRead(long notificationId, AuthUser user) {
        repository.markRead(now(), user.tenantId(), user.id(), notificationId);
    }

    @Transactional
    public NotificationReadAllResult markAllRead(AuthUser user) {
        int changed = repository.markAllRead(now(), user.tenantId(), user.id());
        return new NotificationReadAllResult(changed);
    }

    @Transactional
    public void archive(long notificationId, AuthUser user) {
        if (archiveReadNotification(notificationId, user) > 0) return;

        Boolean read = repository.findReadState(user.tenantId(), user.id(), notificationId);
        if (read == null) return;
        if (Boolean.FALSE.equals(read)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先阅读消息后再归档");
        }
        archiveReadNotification(notificationId, user);
    }

    @Transactional
    public void restore(long notificationId, AuthUser user) {
        repository.restore(user.tenantId(), user.id(), notificationId);
    }

    @Transactional
    public NotificationArchiveResult archiveRead(AuthUser user) {
        int changed = repository.archiveAllRead(now(), user.tenantId(), user.id());
        return new NotificationArchiveResult(changed);
    }

    private int archiveReadNotification(long notificationId, AuthUser user) {
        return repository.archiveReadNotification(now(), user.tenantId(), user.id(), notificationId);
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
        List<Long> validUsers = repository.findActiveUserIds(notification.tenantId(), List.copyOf(expected));
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
        if (OperationAuditContext.capture("system:notification:publish", "notification",
                String.valueOf(notificationId), null)) return;
        repository.insertAudit(nextId(), notification.tenantId(), notification.actorUserId() == null ? 0L : notification.actorUserId(), notification.businessType() + "/" + notificationId);
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
