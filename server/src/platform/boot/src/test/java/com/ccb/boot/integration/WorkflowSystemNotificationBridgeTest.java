package com.ccb.boot.integration;

import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.SystemNotificationPublisher;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import com.ccb.workflow.integration.WorkflowTaskAssignedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowSystemNotificationBridgeTest {
    @Test
    void publishesPendingTaskToCurrentAssignee() {
        RecordingPublisher notifications = new RecordingPublisher();
        WorkflowSystemNotificationBridge bridge = new WorkflowSystemNotificationBridge(new NotificationJdbcTemplate(), notifications);

        bridge.onTaskAssigned(new WorkflowTaskAssignedEvent(1L, 21L, 31L, 7L, 1L));

        NotificationPublishCommand command = notifications.onlyCommand();
        assertEquals("workflow-task:31:assignee:7", command.eventId());
        assertEquals("release", command.moduleCode());
        assertEquals("配置管理", command.moduleName());
        assertEquals("release_application", command.businessType());
        assertEquals(List.of(7L), command.recipientUserIds());
        assertEquals("待审批：版本申请 SQ-001", command.title());
        assertEquals("/release/applications/SQ-001", command.actionPath());
    }

    @Test
    void publishesFinalApprovalResultToWorkflowStarter() {
        RecordingPublisher notifications = new RecordingPublisher();
        WorkflowSystemNotificationBridge bridge = new WorkflowSystemNotificationBridge(new NotificationJdbcTemplate(), notifications);

        bridge.consume(event(WorkflowLifecycleEventType.APPROVED));

        NotificationPublishCommand command = notifications.onlyCommand();
        assertEquals(List.of(1L), command.recipientUserIds());
        assertEquals("release", command.moduleCode());
        assertEquals("配置管理", command.moduleName());
        assertEquals("审批已通过：版本申请 SQ-001", command.title());
        assertEquals("workflow-lifecycle:event-1", command.eventId());
    }

    @Test
    void reconcilesPendingTasksCreatedBeforeBridgeWasAvailable() {
        RecordingPublisher notifications = new RecordingPublisher();
        WorkflowSystemNotificationBridge bridge = new WorkflowSystemNotificationBridge(new NotificationJdbcTemplate(), notifications);

        bridge.reconcilePendingTasks();

        assertEquals("workflow-task:31:assignee:7", notifications.onlyCommand().eventId());
    }

    private WorkflowLifecycleEvent event(WorkflowLifecycleEventType type) {
        return new WorkflowLifecycleEvent("event-1", 1L, 21L, type,
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-001", "版本申请 SQ-001", 1,
                        "P1", "项目一", "/release/applications/SQ-001", "a".repeat(64)),
                7L, LocalDateTime.of(2026, 8, 17, 16, 0));
    }

    private static final class RecordingPublisher implements SystemNotificationPublisher {
        private final List<NotificationPublishCommand> commands = new ArrayList<>();

        @Override
        public long publish(NotificationPublishCommand command) {
            commands.add(command);
            return commands.size();
        }

        private NotificationPublishCommand onlyCommand() {
            assertEquals(1, commands.size());
            return commands.get(0);
        }
    }

    private static final class NotificationJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return pendingTask();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return pendingTask();
        }

        private List<Map<String, Object>> pendingTask() {
            return List.of(Map.ofEntries(
                    Map.entry("task_id", 31L),
                    Map.entry("assignee_id", 7L),
                    Map.entry("task_type", "APPROVAL"),
                    Map.entry("task_key", "review"),
                    Map.entry("tenant_id", 1L),
                    Map.entry("business_module_code", "release"),
                    Map.entry("business_module_name", "配置管理"),
                    Map.entry("business_type", "release_application"),
                    Map.entry("business_key", "SQ-001"),
                    Map.entry("business_title", "版本申请 SQ-001"),
                    Map.entry("action_path", "/release/applications/SQ-001")));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return (List<T>) List.of(1L);
        }
    }
}
