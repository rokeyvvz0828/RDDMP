package com.ccb.architecture.plan.model;

import java.time.LocalDateTime;
import java.util.List;

/** 搭建计划领域模型（REQ-20260830-056）。状态与进度由系统计算，用户只执行业务动作。 */
public final class PlanModels {

    public enum PlanStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public enum TaskStatus {
        NOT_STARTED, WAITING_PRECEDING, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED
    }

    public enum CheckItemStatus {
        PENDING, COMPLETED, CANCELLED
    }

    public enum TargetType {
        PHYSICAL_SUBSYSTEM, DEPLOYMENT_UNIT
    }

    public enum WorkOrderType {
        RESOURCE_REQUEST, NETWORK_CLB, NETWORK_DNS, NETWORK_CERT, CRYPTO_POOL
    }

    public enum WorkOrderSource {
        CREATED_FROM_TASK, ATTACHED_LATER
    }

    public enum EventType {
        START, COMPLETE, REOPEN, CANCEL, RESTORE, TIME_CORRECT
    }

    /** 检查项当前值。 */
    public record CheckItem(long id, long taskId, int checkNo, String name, int sortNo, String guide,
                            CheckItemStatus status, String remark, Long completedBy,
                            LocalDateTime completedAt, boolean cancelled, String cancelReason,
                            Long cancelledBy, LocalDateTime cancelledAt, long rowVersion,
                            long createdBy) {
    }

    /** 任务当前值。 */
    public record Task(long id, long planId, long stageId, int taskNo, String name,
                       TargetType targetType, Long targetId, String targetNo, String targetName,
                       Long taskTemplateId, Integer taskTemplateVersionNo, String dimension,
                       String snapshotJson, long ownerUserId, LocalDateTime plannedStart,
                       LocalDateTime plannedEnd, LocalDateTime actualStart, LocalDateTime actualEnd,
                       TaskStatus status, boolean waivedAll, boolean cancelled, String cancelReason,
                       Long cancelledBy, LocalDateTime cancelledAt, long rowVersion) {
    }

    /** 环节当前值。 */
    public record Stage(long id, long planId, int stageNo, String name, int sortNo, long ownerUserId,
                        LocalDateTime plannedStart, LocalDateTime plannedEnd,
                        LocalDateTime actualStart, LocalDateTime actualEnd,
                        PlanStatus status, boolean cancelled, String cancelReason,
                        Long cancelledBy, LocalDateTime cancelledAt, String snapshotJson) {
    }

    /** 计划当前值。 */
    public record Plan(long id, String planNo, String name, long environmentId,
                       PlanStatus status, long templateId, int templateVersionNo,
                       long planOwnerUserId, LocalDateTime plannedStart, LocalDateTime plannedEnd,
                       LocalDateTime actualStart, LocalDateTime actualEnd, boolean cancelled,
                       String cancelReason, Long cancelledBy, LocalDateTime cancelledAt,
                       long rowVersion) {
    }

    /** 计划目标快照。 */
    public record PlanTarget(long id, long planId, TargetType targetType, long targetId,
                             String targetNo, String targetName, boolean removed,
                             String removeReason) {
    }

    /** 前置依赖。 */
    public record Dependency(long id, long taskId, long predecessorId, boolean removed, String removeReason) {
    }

    /** 阻塞记录。 */
    public record Block(long id, long taskId, String description, String impact, long ownerUserId,
                        LocalDateTime expectedResolveAt, boolean resolved, String resolvedNote,
                        Long resolvedBy, LocalDateTime resolvedAt, long createdBy) {
    }

    /** 检查项取消建议。 */
    public record CancelSuggestion(long id, long checkItemId, String reason, long submitterUserId,
                                   String status, Long handledByUserId, LocalDateTime handledAt,
                                   String handlerNote) {
    }

    /** 执行事件（实际时间事实源）。 */
    public record PlanEvent(long id, long planId, String objectType, long objectId, EventType eventType,
                            LocalDateTime occurredAt, long operatorUserId, String reason,
                            Long correctOfEventId) {
    }

    /** 任务-工单关联。 */
    public record TaskWorkOrder(long id, long taskId, long planId, WorkOrderType workOrderType,
                                long workOrderId, WorkOrderSource source, boolean removed) {
    }

    public record CreatePlanCommand(long environmentId, long templateId, String name, long planOwnerUserId,
                                    List<Long> physicalSubsystemIds, List<Long> deploymentUnitIds,
                                    List<Long> participantUserIds, LocalDateTime plannedStart,
                                    LocalDateTime plannedEnd) {
    }

    public record AddTargetCommand(List<Long> physicalSubsystemIds, List<Long> deploymentUnitIds,
                                   String reason) {
    }

    public record AddStageCommand(String name, long ownerUserId, LocalDateTime plannedStart,
                                  LocalDateTime plannedEnd) {
    }

    public record AddTaskCommand(long stageId, String name, Long targetId, long ownerUserId,
                                 List<Long> participantUserIds, List<String> checkItemNames,
                                 LocalDateTime plannedStart, LocalDateTime plannedEnd) {
    }

    public record AddCheckItemCommand(String name, String guide) {
        public AddCheckItemCommand(String name) {
            this(name, null);
        }
    }

    public record ScheduleCommand(LocalDateTime plannedStart, LocalDateTime plannedEnd, String reason) {
    }

    public record BlockCommand(String description, String impact, long ownerUserId,
                               LocalDateTime expectedResolveAt) {
    }

    public record WorkOrderCommand(WorkOrderType workOrderType, List<Long> workOrderIds,
                                   WorkOrderSource source) {
    }

    public record DependencyCommand(List<Long> predecessorTaskIds) {
    }

    private PlanModels() {
    }
}
