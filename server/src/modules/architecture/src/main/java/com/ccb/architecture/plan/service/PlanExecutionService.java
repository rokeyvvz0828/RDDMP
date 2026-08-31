package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.Block;
import com.ccb.architecture.plan.model.PlanModels.CancelSuggestion;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.EventType;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanEvent;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.TargetType;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskWorkOrder;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 任务执行、检查项操作与取消豁免（REQ-20260830-056）。 */
@Service
public class PlanExecutionService {
    private final PlanStore store;
    private final PlanEngine engine;
    private final PlanNotificationService notificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LongSupplier idSupplier;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanExecutionService(PlanStore store, PlanEngine engine,
                                PlanNotificationService notificationService,
                                com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(store, engine, notificationService, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                java.time.Clock.systemDefaultZone());
    }

    PlanExecutionService(PlanStore store, PlanEngine engine,
                         PlanNotificationService notificationService,
                         com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                         LongSupplier idSupplier, java.time.Clock clock) {
        this.store = Objects.requireNonNull(store, "计划存储不能为空");
        this.engine = Objects.requireNonNull(engine, "计算引擎不能为空");
        this.notificationService = Objects.requireNonNull(notificationService, "通知服务不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 能力不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /** 开始任务：前置满足、未取消、未完成、无未解除阻塞时允许。 */
    @Transactional
    public Task startTask(AuthUser actor, long taskId, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        engine.requireTaskExecutor(actor, task, isAdmin);
        if (task.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务已取消，不能开始");
        }
        if (task.status() == TaskStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务已完成，不能重复开始；如需调整请重新打开检查项");
        }
        if (task.status() == TaskStatus.BLOCKED
                || store.findBlocks(actor.tenantId(), taskId).stream().anyMatch(b -> !b.resolved())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务存在未解除阻塞，不能开始");
        }
        if (hasMissingPreceding(actor, task)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在未完成的前置任务，不能开始");
        }
        if (task.actualStart() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务已开始");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "TASK", taskId,
                EventType.START, now, actor.id(), null, null));
        engine.recompute(actor.tenantId(), task.planId(), now);
        return store.findTask(actor.tenantId(), taskId).orElseThrow();
    }

    /** 完成检查项：任务责任人或参与人；记录备注与事件。 */
    @Transactional
    public CheckItem completeCheckItem(AuthUser actor, long checkItemId, String remark, boolean isAdmin) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        engine.requireTaskExecutor(actor, task, isAdmin);
        if (task.cancelled() || task.status() == TaskStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务已取消或已完成，不能操作检查项");
        }
        if (hasMissingPreceding(actor, task)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "存在未完成的前置任务，任务为等待前置状态，不能完成检查项");
        }
        if (item.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检查项已取消，不能完成");
        }
        if (item.status() == com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "检查项已完成");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateCheckItemCompletion(actor.tenantId(), checkItemId,
                com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED, trimToNull(remark),
                actor.id(), now, actor.id());
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "CHECK_ITEM",
                checkItemId, EventType.COMPLETE, now, actor.id(), null, null));
        engine.recompute(actor.tenantId(), task.planId(), now);
        return store.findCheckItem(actor.tenantId(), checkItemId).orElseThrow();
    }

    /** 重新打开检查项：任务责任人或参与人；记录事件并触发上级重算。 */
    @Transactional
    public CheckItem reopenCheckItem(AuthUser actor, long checkItemId, String reason, boolean isAdmin) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        engine.requireTaskExecutor(actor, task, isAdmin);
        if (task.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务已取消，不能重新打开检查项");
        }
        if (item.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已取消检查项请通过恢复重新纳入，不能直接重新打开");
        }
        if (item.status() != com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "检查项尚未完成，无需重新打开");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateCheckItemCompletion(actor.tenantId(), checkItemId,
                com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.PENDING, item.remark(),
                null, null, actor.id());
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "CHECK_ITEM",
                checkItemId, EventType.REOPEN, now, actor.id(), trimToNull(reason), null));
        engine.recompute(actor.tenantId(), task.planId(), now);
        return store.findCheckItem(actor.tenantId(), checkItemId).orElseThrow();
    }

    /** 计划责任人取消检查项（豁免）。 */
    @Transactional
    public CheckItem cancelCheckItem(AuthUser actor, long checkItemId, String reason, boolean isAdmin) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String cancelReason = requireReason(reason);
        if (item.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "检查项已取消");
        }
        if (item.status() == com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成检查项不能取消，请先重新打开");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateCheckItemCancel(actor.tenantId(), checkItemId, true, cancelReason, actor.id(), now);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "CHECK_ITEM",
                checkItemId, EventType.CANCEL, now, actor.id(), cancelReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "CHECK_ITEM",
                checkItemId, "CHECK_ITEM_CANCELLED", actor.id(), cancelReason,
                toJson(item.name()), null);
        engine.recompute(actor.tenantId(), task.planId(), now);
        return store.findCheckItem(actor.tenantId(), checkItemId).orElseThrow();
    }

    /** 计划责任人恢复已取消检查项。 */
    @Transactional
    public CheckItem restoreCheckItem(AuthUser actor, long checkItemId, String reason, boolean isAdmin) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String restoreReason = requireReason(reason);
        if (!item.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "检查项未被取消，无需恢复");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateCheckItemCancel(actor.tenantId(), checkItemId, false, null, null, null);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "CHECK_ITEM",
                checkItemId, EventType.RESTORE, now, actor.id(), restoreReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "CHECK_ITEM",
                checkItemId, "CHECK_ITEM_RESTORED", actor.id(), restoreReason, null, null);
        engine.recompute(actor.tenantId(), task.planId(), now);
        return store.findCheckItem(actor.tenantId(), checkItemId).orElseThrow();
    }

    /** 任务责任人或参与人提交检查项取消建议（附原因，计划责任人处理）。 */
    @Transactional
    public CancelSuggestion suggestCancelCheckItem(AuthUser actor, long checkItemId, String reason) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        engine.requireTaskExecutor(actor, task, false);
        String suggestReason = requireReason(reason);
        if (item.cancelled() || item.status() == com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已取消或已完成检查项无需提交取消建议");
        }
        if (!store.findPendingSuggestions(actor.tenantId(), checkItemId).isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该检查项已有待处理取消建议");
        }
        long suggestionId = nextId();
        store.insertCancelSuggestion(actor.tenantId(), new CancelSuggestion(suggestionId, checkItemId,
                suggestReason, actor.id(), "PENDING", null, null, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "SUGGESTION",
                suggestionId, "SUGGESTION_SUBMITTED", actor.id(), suggestReason, null, null);
        return requireSuggestion(actor, suggestionId);
    }

    /** 计划责任人接受取消建议：同步取消检查项。 */
    @Transactional
    public CheckItem acceptSuggestion(AuthUser actor, long suggestionId, String note, boolean isAdmin) {
        CancelSuggestion suggestion = requireSuggestion(actor, suggestionId);
        Plan plan = planOfCheckItem(actor, suggestion.checkItemId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        if (!"PENDING".equals(suggestion.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "取消建议已被处理");
        }
        store.handleSuggestion(actor.tenantId(), suggestionId, "ACCEPTED", actor.id(), trimToNull(note));
        CheckItem item = cancelCheckItemInternal(actor, suggestion.checkItemId(), suggestion.reason());
        return item;
    }

    /** 计划责任人拒绝取消建议。 */
    @Transactional
    public CancelSuggestion rejectSuggestion(AuthUser actor, long suggestionId, String note, boolean isAdmin) {
        CancelSuggestion suggestion = requireSuggestion(actor, suggestionId);
        Plan plan = planOfCheckItem(actor, suggestion.checkItemId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        if (!"PENDING".equals(suggestion.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "取消建议已被处理");
        }
        store.handleSuggestion(actor.tenantId(), suggestionId, "REJECTED", actor.id(), trimToNull(note));
        return requireSuggestion(actor, suggestionId);
    }

    private CheckItem cancelCheckItemInternal(AuthUser actor, long checkItemId, String cancelReason) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateCheckItemCancel(actor.tenantId(), checkItemId, true, cancelReason, actor.id(), now);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "CHECK_ITEM",
                checkItemId, EventType.CANCEL, now, actor.id(), cancelReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "CHECK_ITEM",
                checkItemId, "CHECK_ITEM_CANCELLED", actor.id(), cancelReason, toJson(item.name()), null);
        engine.recompute(actor.tenantId(), task.planId(), now);
        return store.findCheckItem(actor.tenantId(), checkItemId).orElseThrow();
    }

    @Transactional
    public Task cancelTask(AuthUser actor, long taskId, String reason, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String cancelReason = requireReason(reason);
        if (task.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务已取消");
        }
        if (task.status() == TaskStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成任务不能直接取消，请先重新打开检查项");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateTaskCancel(actor.tenantId(), taskId, true, cancelReason, actor.id(), now);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "TASK", taskId,
                EventType.CANCEL, now, actor.id(), cancelReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "TASK", taskId,
                "TASK_CANCELLED", actor.id(), cancelReason, toJson(task.name()), null);
        engine.recompute(actor.tenantId(), task.planId(), now);
        return requireTask(actor, taskId);
    }

    @Transactional
    public Task restoreTask(AuthUser actor, long taskId, String reason, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String restoreReason = requireReason(reason);
        if (!task.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务未被取消，无需恢复");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateTaskCancel(actor.tenantId(), taskId, false, null, null, null);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), task.planId(), "TASK", taskId,
                EventType.RESTORE, now, actor.id(), restoreReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "TASK", taskId,
                "TASK_RESTORED", actor.id(), restoreReason, null, null);
        engine.recompute(actor.tenantId(), task.planId(), now);
        return requireTask(actor, taskId);
    }

    @Transactional
    public Stage cancelStage(AuthUser actor, long stageId, String reason, boolean isAdmin) {
        Stage stage = engine.requireStage(actor, stageId);
        Plan plan = engine.requirePlan(actor, stage.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String cancelReason = requireReason(reason);
        if (stage.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "环节已取消");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateStageStatus(actor.tenantId(), stageId, PlanStatus.CANCELLED, true, cancelReason,
                actor.id(), now);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), stage.planId(), "STAGE", stageId,
                EventType.CANCEL, now, actor.id(), cancelReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", stage.planId(), "STAGE", stageId,
                "STAGE_CANCELLED", actor.id(), cancelReason, toJson(stage.name()), null);
        engine.recompute(actor.tenantId(), stage.planId(), now);
        return store.findStage(actor.tenantId(), stageId).orElseThrow();
    }

    @Transactional
    public Stage restoreStage(AuthUser actor, long stageId, String reason, boolean isAdmin) {
        Stage stage = engine.requireStage(actor, stageId);
        Plan plan = engine.requirePlan(actor, stage.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String restoreReason = requireReason(reason);
        if (!stage.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "环节未被取消，无需恢复");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updateStageStatus(actor.tenantId(), stageId, PlanStatus.NOT_STARTED, false, null, null, null);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), stage.planId(), "STAGE", stageId,
                EventType.RESTORE, now, actor.id(), restoreReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", stage.planId(), "STAGE", stageId,
                "STAGE_RESTORED", actor.id(), restoreReason, null, null);
        engine.recompute(actor.tenantId(), stage.planId(), now);
        return store.findStage(actor.tenantId(), stageId).orElseThrow();
    }

    @Transactional
    public Plan cancelPlan(AuthUser actor, long planId, String reason, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        String cancelReason = requireReason(reason);
        if (plan.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "计划已取消");
        }
        if (plan.status() == PlanStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划已完成，不能取消");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updatePlanStatus(actor.tenantId(), planId, PlanStatus.CANCELLED, true, cancelReason,
                actor.id(), now);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), planId, "PLAN", planId,
                EventType.CANCEL, now, actor.id(), cancelReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "PLAN", planId,
                "PLAN_CANCELLED", actor.id(), cancelReason, toJson(plan.name()), null);
        return store.findPlan(actor.tenantId(), planId).orElseThrow();
    }

    @Transactional
    public Plan restorePlan(AuthUser actor, long planId, String reason, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        String restoreReason = requireReason(reason);
        if (!plan.cancelled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "计划未被取消，无需恢复");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.updatePlanStatus(actor.tenantId(), planId, PlanStatus.NOT_STARTED, false, null, null, null);
        store.insertEvent(actor.tenantId(), new PlanEvent(nextId(), planId, "PLAN", planId,
                EventType.RESTORE, now, actor.id(), restoreReason, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "PLAN", planId,
                "PLAN_RESTORED", actor.id(), restoreReason, null, null);
        engine.recompute(actor.tenantId(), planId, now);
        return store.findPlan(actor.tenantId(), planId).orElseThrow();
    }

    /** 已有完成检查项的任务中，是否有检查项未完成（重开任务检查项时的完成状态校验）。 */
    public boolean hasMissingPreceding(AuthUser actor, Task task) {
        return engine.hasMissingPreceding(actor.tenantId(), task);
    }

    private Plan planOfCheckItem(AuthUser actor, long checkItemId) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        return engine.requirePlan(actor, task.planId());
    }

    private CancelSuggestion requireSuggestion(AuthUser actor, long suggestionId) {
        return store.findSuggestion(actor.tenantId(), suggestionId)
                .orElseThrow(() -> new ArchitectureNotFoundException("取消建议不存在"));
    }

    private Task requireTask(AuthUser actor, long taskId) {
        return store.findTask(actor.tenantId(), taskId).orElseThrow();
    }

    private static String requireReason(String reason) {
        String text = reason == null || reason.isBlank() ? null : reason.trim();
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原因不能为空");
        }
        if (text.length() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原因长度不能超过 1000");
        }
        return text;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("执行标识生成器返回无效值");
        }
        return value;
    }
}
