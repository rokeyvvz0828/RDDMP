package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.EventType;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanEvent;
import com.ccb.architecture.plan.model.PlanModels.ScheduleCommand;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 计划时间管理（REQ-20260830-056）：计划时间维护与调整、执行事件更正与历史查询。 */
@Service
public class PlanTimeService {
    private final PlanStore store;
    private final PlanEngine engine;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LongSupplier idSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanTimeService(PlanStore store, PlanEngine engine,
                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(store, engine, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    PlanTimeService(PlanStore store, PlanEngine engine,
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper, LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "计划存储不能为空");
        this.engine = Objects.requireNonNull(engine, "计算引擎不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 能力不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Transactional
    public Plan updatePlanSchedule(AuthUser actor, long planId, ScheduleCommand cmd, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        if (plan.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划已取消，不能调整时间");
        }
        String reason = requiredReasonWhenStarted(cmd == null ? null : cmd.reason(),
                plan.actualStart() != null);
        validateRange(cmd == null ? null : cmd.plannedStart(), cmd == null ? null : cmd.plannedEnd());
        store.updatePlanSchedule(actor.tenantId(), planId, cmd.plannedStart(), cmd.plannedEnd());
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "SCHEDULE", planId,
                "PLAN_SCHEDULE_CHANGED", actor.id(), reason,
                toJson(Map.of("before", scheduleJson(plan.plannedStart(), plan.plannedEnd()),
                        "after", scheduleJson(cmd.plannedStart(), cmd.plannedEnd()))), null);
        engine.recompute(actor.tenantId(), planId, LocalDateTime.now());
        return store.findPlan(actor.tenantId(), planId).orElseThrow();
    }

    @Transactional
    public Stage updateStageSchedule(AuthUser actor, long stageId, ScheduleCommand cmd, boolean isAdmin) {
        Stage stage = engine.requireStage(actor, stageId);
        Plan plan = engine.requirePlan(actor, stage.planId());
        boolean allowed = isAdmin || actor.id() == plan.planOwnerUserId()
                || actor.id() == stage.ownerUserId();
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅计划责任人或环节责任人可以调整时间");
        }
        if (stage.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "环节已取消，不能调整时间");
        }
        String reason = requiredReasonWhenStarted(cmd == null ? null : cmd.reason(),
                stage.actualStart() != null);
        validateRange(cmd == null ? null : cmd.plannedStart(), cmd == null ? null : cmd.plannedEnd());
        store.updateStageSchedule(actor.tenantId(), stageId, cmd.plannedStart(), cmd.plannedEnd());
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", stage.planId(), "SCHEDULE", stageId,
                "STAGE_SCHEDULE_CHANGED", actor.id(), reason,
                toJson(Map.of("before", scheduleJson(stage.plannedStart(), stage.plannedEnd()),
                        "after", scheduleJson(cmd.plannedStart(), cmd.plannedEnd()))), null);
        engine.recompute(actor.tenantId(), stage.planId(), LocalDateTime.now());
        return store.findStage(actor.tenantId(), stageId).orElseThrow();
    }

    @Transactional
    public Task updateTaskSchedule(AuthUser actor, long taskId, ScheduleCommand cmd, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        Plan plan = engine.requirePlan(actor, task.planId());
        Stage stage = store.findStage(actor.tenantId(), task.stageId()).orElseThrow();
        boolean allowed = isAdmin || actor.id() == plan.planOwnerUserId()
                || actor.id() == task.ownerUserId() || actor.id() == stage.ownerUserId();
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅计划、环节或任务责任人可以调整时间");
        }
        if (task.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务已取消，不能调整时间");
        }
        String reason = requiredReasonWhenStarted(cmd == null ? null : cmd.reason(),
                task.actualStart() != null);
        validateRange(cmd == null ? null : cmd.plannedStart(), cmd == null ? null : cmd.plannedEnd());
        store.updateTaskSchedule(actor.tenantId(), taskId, cmd.plannedStart(), cmd.plannedEnd());
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "SCHEDULE", taskId,
                "TASK_SCHEDULE_CHANGED", actor.id(), reason,
                toJson(Map.of("before", scheduleJson(task.plannedStart(), task.plannedEnd()),
                        "after", scheduleJson(cmd.plannedStart(), cmd.plannedEnd()))), null);
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
        return store.findTask(actor.tenantId(), taskId).orElseThrow();
    }

    /** 事件更正：有权限人员附原因更正实际时间事件；原事件与更正记录全部保留，实际时间重算。 */
    @Transactional
    public PlanEvent correctEvent(AuthUser actor, long eventId, LocalDateTime newTime, String reason,
                                  boolean isAdmin) {
        PlanEvent event = store.findEvent(actor.tenantId(), eventId)
                .orElseThrow(() -> new ArchitectureNotFoundException("执行事件不存在"));
        if (event.eventType() != EventType.START && event.eventType() != EventType.COMPLETE
                && event.eventType() != EventType.REOPEN) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅开始、完成、重新打开事件允许更正");
        }
        if (event.correctOfEventId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更正事件不能再次更正；请更正原始事件");
        }
        Plan plan = store.findPlan(actor.tenantId(), event.planId())
                .orElseThrow(() -> new ArchitectureNotFoundException("搭建计划不存在"));
        boolean allowed = isAdmin || actor.id() == plan.planOwnerUserId();
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅计划责任人或管理员可以更正事件");
        }
        String correctReason = requireReason(reason);
        if (newTime == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更正时间不能为空");
        }
        long correctionId = nextId();
        store.insertEvent(actor.tenantId(), new PlanEvent(correctionId, event.planId(),
                event.objectType(), event.objectId(), EventType.TIME_CORRECT, newTime, actor.id(),
                correctReason, eventId));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", event.planId(), "EVENT", eventId,
                "EVENT_CORRECTED", actor.id(), correctReason,
                toJson(Map.of("eventType", event.eventType().name(), "from",
                        String.valueOf(event.occurredAt()), "to", String.valueOf(newTime))), null);
        engine.recompute(actor.tenantId(), event.planId(), LocalDateTime.now());
        return store.findEvent(actor.tenantId(), correctionId).orElseThrow();
    }

    public List<PlanEvent> listEvents(AuthUser actor, long planId, String objectType, long objectId) {
        return store.findEvents(actor.tenantId(), planId, objectType, objectId);
    }

    public List<PlanEvent> listPlanEvents(AuthUser actor, long planId) {
        List<PlanEvent> result = new java.util.ArrayList<>();
        for (long taskId : store.findTasks(actor.tenantId(), planId, null).stream()
                .map(Task::id).toList()) {
            result.addAll(store.findEvents(actor.tenantId(), planId, "TASK", taskId));
            for (com.ccb.architecture.plan.model.PlanModels.CheckItem item
                    : store.findCheckItems(actor.tenantId(), taskId)) {
                result.addAll(store.findEvents(actor.tenantId(), planId, "CHECK_ITEM", item.id()));
            }
        }
        result.addAll(store.findEvents(actor.tenantId(), planId, "STAGE", planId));
        result.addAll(store.findEvents(actor.tenantId(), planId, "PLAN", planId));
        result.sort(java.util.Comparator.comparing(PlanEvent::occurredAt));
        return result;
    }

    private static String requiredReasonWhenStarted(String reason, boolean started) {
        if (started) {
            return requireReason(reason);
        }
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private static void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划结束时间不能早于开始时间");
        }
    }

    private static String requireReason(String reason) {
        String text = reason == null || reason.isBlank() ? null : reason.trim();
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划开始后调整时间必须填写原因");
        }
        if (text.length() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原因长度不能超过 1000");
        }
        return text;
    }

    private static Map<String, String> scheduleJson(LocalDateTime start, LocalDateTime end) {
        Map<String, String> value = new HashMap<>();
        value.put("plannedStart", start == null ? null : start.toString());
        value.put("plannedEnd", end == null ? null : end.toString());
        return value;
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
            throw new IllegalStateException("时间标识生成器返回无效值");
        }
        return value;
    }
}
