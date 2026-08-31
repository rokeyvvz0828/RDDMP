package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.SystemNotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搭建计划站内通知（REQ-20260830-056）：任务分派、关键状态变更、阻塞登记与解除、临期与逾期。
 * 通知失败不改变业务事实，只记录日志。
 */
@Service
public class PlanNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PlanNotificationService.class);
    private static final String MODULE_CODE = "architecture";
    private static final String MODULE_NAME = "架构管理";
    private static final String SOURCE_NAME = "环境搭建计划";

    private final SystemNotificationPublisher publisher;
    private final PlanStore store;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanNotificationService(SystemNotificationPublisher publisher, PlanStore store) {
        this.publisher = publisher;
        this.store = store;
    }

    public void notifyTaskAssigned(long tenantId, String planNo, String taskName,
                                   List<Long> recipientUserIds) {
        publish(tenantId, "plan.task.assigned", "任务分派", planNo, recipientUserIds,
                "新任务分派：" + taskName,
                "搭建计划「" + planNo + "」分配了任务「" + taskName + "」，请及时处理。",
                NotificationLevel.INFO);
    }

    public void notifyBlockRegistered(long tenantId, String planNo, String taskName,
                                      List<Long> recipientUserIds) {
        publish(tenantId, "plan.block.registered", "阻塞登记", planNo, recipientUserIds,
                "任务阻塞：" + taskName,
                "搭建计划「" + planNo + "」的任务「" + taskName + "」登记了阻塞，请关注处理。",
                NotificationLevel.WARNING);
    }

    public void notifyBlockResolved(long tenantId, String planNo, String taskName,
                                    List<Long> recipientUserIds) {
        publish(tenantId, "plan.block.resolved", "阻塞解除", planNo, recipientUserIds,
                "阻塞解除：" + taskName,
                "搭建计划「" + planNo + "」的任务「" + taskName + "」阻塞已解除。",
                NotificationLevel.INFO);
    }

    public void notifyStateChanged(long tenantId, String planNo, String title, String content,
                                   List<Long> recipientUserIds, NotificationLevel level) {
        publish(tenantId, "plan.state.changed", "状态变更", planNo, recipientUserIds, title, content, level);
    }

    public void notifyWorkOrderGated(long tenantId, String planNo, String taskName, String workOrderNo,
                                     List<Long> recipientUserIds) {
        publish(tenantId, "plan.work-order.gate", "工单门禁", planNo, recipientUserIds,
                "工单未结束：" + taskName,
                "任务「" + taskName + "」关联工单「" + workOrderNo + "」尚未办结，任务暂不能完成。",
                NotificationLevel.WARNING);
    }

    public void notifyWorkOrderClosed(long tenantId, String planNo, String taskName, String workOrderNo,
                                      List<Long> recipientUserIds) {
        publish(tenantId, "plan.work-order.closed", "工单办结", planNo, recipientUserIds,
                "工单办结：" + workOrderNo,
                "任务「" + taskName + "」关联工单「" + workOrderNo + "」已办结，请确认任务完成条件。",
                NotificationLevel.INFO);
    }

    /** 每日扫描临期/逾期任务：对存在逾期的计划通知其计划责任人（失败不影响业务）。 */
    @Scheduled(cron = "${ccb.architecture.plan.alert-cron:0 30 9 * * *}")
    public void scanOverdueAlerts() {
        for (PlanStore.AlertPlan alert : store.planIdsNeedingAlert()) {
            try {
                long overdueCount = store.countOverdueTasks(alert.tenantId(), alert.planId(),
                        java.time.LocalDateTime.now());
                if (overdueCount > 0) {
                    publish(alert.tenantId(), "plan.overdue", "逾期提醒", alert.planNo(),
                            List.of(alert.planOwnerUserId()), "搭建计划逾期提醒",
                            "搭建计划「" + alert.planNo() + "」存在 " + overdueCount + " 个逾期任务，请尽快处理。",
                            NotificationLevel.WARNING);
                }
            } catch (RuntimeException failure) {
                log.warn("搭建计划临期/逾期扫描失败 tenantId={} planId={}",
                        alert.tenantId(), alert.planId(), failure);
            }
        }
    }

    private void publish(long tenantId, String eventId, String businessType, String businessKey,
                         List<Long> recipients, String title, String content, NotificationLevel level) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        try {
            publisher.publish(new NotificationPublishCommand(tenantId, eventId + "-" + businessKey,
                    MODULE_CODE, MODULE_NAME, businessType, businessKey,
                    recipients.stream().distinct().toList(), title, content, level, SOURCE_NAME,
                    null, null));
        } catch (RuntimeException failure) {
            log.warn("搭建计划通知发送失败 eventId={} businessKey={}", eventId, businessKey, failure);
        }
    }
}
