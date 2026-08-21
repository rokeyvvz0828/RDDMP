package com.ccb.boot.integration;

import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.SystemNotificationPublisher;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import com.ccb.workflow.integration.WorkflowTaskAssignedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class WorkflowSystemNotificationBridge implements WorkflowLifecycleConsumer {
    static final String SUBSCRIBER_KEY = "platform.workflow.notification.v1";
    private static final Logger log = LoggerFactory.getLogger(WorkflowSystemNotificationBridge.class);
    private static final String SOURCE_NAME = "审批中心";
    private static final Pattern MODULE_CODE = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final JdbcTemplate jdbc;
    private final SystemNotificationPublisher notifications;

    public WorkflowSystemNotificationBridge(JdbcTemplate jdbc, SystemNotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return businessType != null && !businessType.isBlank();
    }

    @Override
    public void consume(WorkflowLifecycleEvent event) {
        if (event.eventType() == WorkflowLifecycleEventType.STARTED) {
            pendingTasks(event.tenantId(), event.instanceId()).forEach(this::publishPending);
            return;
        }
        publishResult(event);
    }

    @EventListener
    public void onTaskAssigned(WorkflowTaskAssignedEvent event) {
        pendingTask(event.tenantId(), event.instanceId(), event.taskId(), event.assigneeId())
                .forEach(this::publishPending);
    }

    @Scheduled(
            initialDelayString = "${ccb.workflow.notification-reconcile-initial-delay-ms:3000}",
            fixedDelayString = "${ccb.workflow.notification-reconcile-delay-ms:60000}")
    public void reconcilePendingTasks() {
        for (Map<String, Object> task : missingPendingNotifications()) {
            try {
                publishPending(task);
            } catch (RuntimeException exception) {
                log.warn("Workflow pending notification reconciliation failed for task {}", task.get("task_id"), exception);
            }
        }
    }

    private List<Map<String, Object>> pendingTask(long tenantId, long instanceId, long taskId, long assigneeId) {
        return jdbc.queryForList(pendingProjection()
                        + " WHERE t.tenant_id = ? AND t.instance_id = ? AND t.id = ? AND t.assignee_id = ?"
                        + " AND t.status = 'PENDING' AND i.deleted = 0 AND i.business_type IS NOT NULL",
                tenantId, instanceId, taskId, assigneeId);
    }

    private List<Map<String, Object>> pendingTasks(long tenantId, long instanceId) {
        return jdbc.queryForList(pendingProjection()
                        + " WHERE t.tenant_id = ? AND t.instance_id = ? AND t.status = 'PENDING'"
                        + " AND i.deleted = 0 AND i.business_type IS NOT NULL ORDER BY t.id",
                tenantId, instanceId);
    }

    private List<Map<String, Object>> missingPendingNotifications() {
        return jdbc.queryForList(pendingProjection()
                + " LEFT JOIN sys_notification n ON n.tenant_id = t.tenant_id"
                + " AND n.business_type = i.business_type"
                + " AND n.event_id = CONCAT('workflow-task:', t.id, ':assignee:', t.assignee_id)"
                + " WHERE t.status = 'PENDING' AND t.assignee_id IS NOT NULL AND i.deleted = 0"
                + " AND i.business_type IS NOT NULL AND n.id IS NULL ORDER BY t.created_at, t.id LIMIT 200");
    }

    private String pendingProjection() {
        return "SELECT t.id AS task_id, t.assignee_id, t.task_type, t.task_key,"
                + " i.tenant_id, i.business_module_code, i.business_module_name, i.business_type, i.business_key, i.business_title, i.action_path"
                + " FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id";
    }

    private void publishPending(Map<String, Object> task) {
        long taskId = number(task, "task_id");
        long assigneeId = number(task, "assignee_id");
        String businessTitle = text(task, "business_title");
        notifications.publish(new NotificationPublishCommand(
                number(task, "tenant_id"),
                "workflow-task:" + taskId + ":assignee:" + assigneeId,
                moduleCode(task),
                moduleName(task),
                text(task, "business_type"),
                text(task, "business_key"),
                List.of(assigneeId),
                "待审批：" + businessTitle,
                "您有一项新的审批任务，请进入业务单据查看详情并完成审批。",
                NotificationLevel.INFO,
                SOURCE_NAME,
                nullableText(task, "action_path"),
                null));
    }

    private void publishResult(WorkflowLifecycleEvent event) {
        ResultMeta meta = resultMeta(event.eventType());
        List<Long> starters = jdbc.queryForList(
                "SELECT starter_id FROM wf_instance WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Long.class, event.instanceId(), event.tenantId());
        if (starters.isEmpty() || starters.get(0) == null || starters.get(0) <= 0) return;
        notifications.publish(new NotificationPublishCommand(
                event.tenantId(),
                "workflow-lifecycle:" + event.eventId(),
                moduleCode(event.context().moduleCode(), event.context().businessType()),
                moduleName(event.context().moduleName(), event.context().businessType()),
                event.context().businessType(),
                event.context().businessKey(),
                List.of(starters.get(0)),
                meta.titlePrefix() + event.context().businessTitle(),
                meta.content(),
                meta.level(),
                SOURCE_NAME,
                event.context().actionPath(),
                null));
    }

    private ResultMeta resultMeta(WorkflowLifecycleEventType type) {
        return switch (type) {
            case APPROVED -> new ResultMeta("审批已通过：", "您的业务申请已完成审批并通过。", NotificationLevel.SUCCESS);
            case RETURNED -> new ResultMeta("审批已退回：", "您的业务申请已被退回，请进入业务单据查看审批意见。", NotificationLevel.WARNING);
            case REJECTED -> new ResultMeta("审批未通过：", "您的业务申请未通过审批，请进入业务单据查看审批意见。", NotificationLevel.WARNING);
            case TERMINATED -> new ResultMeta("审批已终止：", "您的业务申请审批流程已终止。", NotificationLevel.WARNING);
            case STARTED -> throw new IllegalArgumentException("STARTED is not a result event");
        };
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String moduleCode(Map<String, Object> row) {
        return moduleCode(nullableText(row, "business_module_code"), text(row, "business_type"));
    }

    private String moduleName(Map<String, Object> row) {
        return moduleName(nullableText(row, "business_module_name"), text(row, "business_type"));
    }

    private String moduleCode(String value, String businessType) {
        if (value != null && !value.isBlank()) return value;
        String fallback = switch (businessType) {
            case "release", "release_application" -> "release";
            case "delivery" -> "delivery";
            case "system" -> "system";
            default -> legacyModuleCode(businessType);
        };
        log.warn("Workflow business module metadata is missing; using legacy fallback {} for business type {}",
                fallback, businessType);
        return fallback;
    }

    private String moduleName(String value, String businessType) {
        if (value != null && !value.isBlank()) return value;
        return switch (businessType) {
            case "release", "release_application" -> "配置管理";
            case "delivery" -> "交付示范中心";
            case "system" -> "系统管理";
            default -> businessType == null || businessType.isBlank() ? "未知业务" : businessType;
        };
    }

    private String legacyModuleCode(String businessType) {
        String suffix = businessType == null ? "" : businessType.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        String candidate = "business_" + (suffix.isBlank() ? "legacy" : suffix);
        if (candidate.length() > 64) candidate = candidate.substring(0, 64);
        return MODULE_CODE.matcher(candidate).matches() ? candidate : "business_legacy";
    }

    private record ResultMeta(String titlePrefix, String content, NotificationLevel level) {
    }
}
