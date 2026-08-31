package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.Block;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.CheckItemStatus;
import com.ccb.architecture.plan.model.PlanModels.Dependency;
import com.ccb.architecture.plan.model.PlanModels.EventType;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanEvent;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.PlanTarget;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 计划计算引擎（REQ-20260830-056）：任务/环节/计划状态计算、实际时间聚合、实体授权与通用业务校验。
 * 所有状态变更动作完成后调用 {@link #recompute(long, long)} 向上传播。
 */
@Service
public class PlanEngine {
    private final PlanStore store;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanEngine(PlanStore store) {
        this.store = store;
    }

    /** 重新计算任务、环节、计划的状态并聚合实际时间（按需调用，动作后传播）。 */
    @Transactional
    public void recompute(long tenantId, long planId, LocalDateTime now) {
        Plan plan = store.lockPlan(tenantId, planId)
                .orElseThrow(() -> new ArchitectureNotFoundException("搭建计划不存在"));
        if (plan.cancelled()) {
            return;
        }
        List<Stage> stages = store.findStages(tenantId, planId);
        List<Task> tasks = store.findTasks(tenantId, planId, null);
        Map<Long, List<CheckItem>> itemsByTask = new HashMap<>();
        Map<Long, List<Block>> blocksByTask = new HashMap<>();
        Map<Long, List<Dependency>> depsByTask = new HashMap<>();
        Map<Long, List<TaskWorkOrder>> workOrdersByTask = new HashMap<>();
        for (Task task : tasks) {
            itemsByTask.put(task.id(), store.findCheckItems(tenantId, task.id()));
            blocksByTask.put(task.id(), store.findBlocks(tenantId, task.id()));
            depsByTask.put(task.id(), store.findDependencies(tenantId, task.id(), false));
            workOrdersByTask.put(task.id(), store.findWorkOrders(tenantId, task.id()));
        }
        Map<Long, Task> taskById = new HashMap<>();
        for (Task task : tasks) {
            taskById.put(task.id(), task);
        }
        // 环节级前置依赖：stageId -> 前置 stageId 列表
        Map<Long, List<Long>> stageDeps = new HashMap<>();
        for (Long[] pair : store.findStageDependencies(tenantId, planId)) {
            stageDeps.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]);
        }
        Map<Long, List<Task>> tasksByStageId = new HashMap<>();
        for (Task task : tasks) {
            tasksByStageId.computeIfAbsent(task.stageId(), k -> new ArrayList<>()).add(task);
        }
        // 1) 任务状态重算
        for (Task task : tasks) {
            recomputeTask(tenantId, task, itemsByTask.get(task.id()), blocksByTask.get(task.id()),
                    depsByTask.get(task.id()), workOrdersByTask.get(task.id()), taskById,
                    stageDeps, tasksByStageId, now);
        }
        // 2) 环节状态与实际时间
        boolean anyStageActive = false;
        boolean allNonCancelledStagesCompleted = true;
        boolean hasNonCancelledStage = false;
        for (Stage stage : stages) {
            List<Task> stageTasks = tasks.stream().filter(t -> t.stageId() == stage.id()).toList();
            stageResults(tenantId, stage, stageTasks);
            Stage current = store.findStage(tenantId, stage.id()).orElse(stage);
            if (!current.cancelled()) {
                hasNonCancelledStage = true;
                if (current.status() != PlanStatus.COMPLETED) {
                    allNonCancelledStagesCompleted = false;
                }
                if (current.status() == PlanStatus.IN_PROGRESS) {
                    anyStageActive = true;
                }
            }
        }
        boolean anyWaived = store.findTasks(tenantId, planId, null).stream()
                .anyMatch(t -> !t.cancelled() && t.waivedAll());
        // 3) 计划状态与实际时间
        if (!hasNonCancelledStage) {
            allNonCancelledStagesCompleted = false;
        }
        List<Stage> freshStages = store.findStages(tenantId, planId);
        LocalDateTime planActualStart = aggregateActualStart(freshStages.stream()
                .map(Stage::actualStart).toList());
        LocalDateTime planActualEnd = aggregateActualEnd(freshStages.stream()
                .map(Stage::actualEnd).toList());
        PlanStatus planStatus;
        if (!hasNonCancelledStage) {
            planStatus = PlanStatus.NOT_STARTED;
        } else if (allNonCancelledStagesCompleted) {
            planStatus = PlanStatus.COMPLETED;
        } else if (anyStageActive || planActualStart != null) {
            planStatus = PlanStatus.IN_PROGRESS;
        } else {
            planStatus = PlanStatus.NOT_STARTED;
        }
        store.updatePlanStatus(tenantId, planId, planStatus, false, null, null, null);
        store.updatePlanActual(tenantId, planId, planActualStart, planActualEnd);
    }

    private void recomputeTask(long tenantId, Task task, List<CheckItem> checkItems, List<Block> blocks,
                               List<Dependency> dependencies, List<TaskWorkOrder> workOrders,
                               Map<Long, Task> taskById, Map<Long, List<Long>> stageDeps,
                               Map<Long, List<Task>> tasksByStageId, LocalDateTime now) {
        if (task.cancelled()) {
            return;
        }
        int total = checkItems.size();
        int cancelled = (int) checkItems.stream().filter(c -> c.cancelled()).count();
        int completed = (int) checkItems.stream()
                .filter(c -> !c.cancelled() && c.status() == CheckItemStatus.COMPLETED).count();
        boolean completionMet = total > 0 && cancelled + completed == total;
        boolean allCancelled = total > 0 && cancelled == total;
        boolean hasOpenBlock = blocks.stream().anyMatch(b -> !b.resolved());
        boolean hasOpenWorkOrder = !storeOpenWorkOrders(tenantId, workOrders).isEmpty();
        boolean missingPreceding = dependencies.stream().anyMatch(dep -> {
            Task predecessor = taskById.get(dep.predecessorId());
            return predecessor != null && predecessor.status() != TaskStatus.COMPLETED;
        }) || missingStagePreceding(tenantId, task, stageDeps, tasksByStageId);
        TaskActual actual = aggregateActualTimes(tenantId, task.planId(), "TASK", task.id());
        boolean started = actual.start() != null;
        PlanStatusCalculator.TaskComputed computed = PlanStatusCalculator.computeTask(
                new PlanStatusCalculator.TaskFact(task.cancelled(), completionMet, hasOpenBlock,
                        hasOpenWorkOrder, missingPreceding, started, total > 0, allCancelled,
                        task.plannedEnd()), now);
        store.updateTaskExecution(tenantId, task.id(), computed.status(), actual.start(), actual.end(),
                computed.waivedAll(), task.ownerUserId());
    }

    /**
     * 任务是否仍存在未完成前置（任务级依赖 + 环节级前置，目标对齐语义）。
     * 供动作校验（开始任务/完成检查项）与状态重算复用，保持同一判断口径。
     */
    public boolean hasMissingPreceding(long tenantId, Task task) {
        for (Dependency dep : store.findDependencies(tenantId, task.id(), false)) {
            Task predecessor = store.findTask(tenantId, dep.predecessorId()).orElse(null);
            if (predecessor == null || predecessor.status() != TaskStatus.COMPLETED) {
                return true;
            }
        }
        Map<Long, List<Long>> stageDeps = new HashMap<>();
        for (Long[] pair : store.findStageDependencies(tenantId, task.planId())) {
            stageDeps.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]);
        }
        Map<Long, List<Task>> tasksByStageId = new HashMap<>();
        for (Task candidate : store.findTasks(tenantId, task.planId(), null)) {
            tasksByStageId.computeIfAbsent(candidate.stageId(), k -> new ArrayList<>()).add(candidate);
        }
        return missingStagePreceding(tenantId, task, stageDeps, tasksByStageId);
    }

    /**
     * 环节级前置判断（目标对齐语义）：前置环节中与当前任务目标对应的任务必须全部完成（已取消任务
     * 视为豁免不参与）；当前任务为计划级（无目标）时要求前置环节全部有效任务完成；前置环节没有
     * 相匹配任务时按全部任务完成处理。
     */
    private boolean missingStagePreceding(long tenantId, Task task, Map<Long, List<Long>> stageDeps,
                                          Map<Long, List<Task>> tasksByStageId) {
        List<Long> predecessors = stageDeps.getOrDefault(task.stageId(), List.of());
        if (predecessors.isEmpty()) {
            return false;
        }
        for (Long predecessorStageId : predecessors) {
            List<Task> stageTasks = tasksByStageId.getOrDefault(predecessorStageId, List.of());
            List<Task> matched = task.targetType() == null || task.targetId() == null
                    ? stageTasks
                    : stageTasks.stream().filter(pre -> pre.targetType() == task.targetType()
                    && Objects.equals(pre.targetId(), task.targetId())).toList();
            if (matched.isEmpty()) {
                matched = stageTasks;
            }
            boolean unlocked = matched.isEmpty() || matched.stream().allMatch(pre -> {
                if (pre.cancelled()) {
                    return true;
                }
                return store.findTask(tenantId, pre.id())
                        .map(fresh -> fresh.status() == TaskStatus.COMPLETED)
                        .orElse(false);
            });
            if (!unlocked) {
                return true;
            }
        }
        return false;
    }

    private void stageResults(long tenantId, Stage stage, List<Task> stageTasks) {
        if (stage.cancelled()) {
            return;
        }
        List<Task> freshTasks = store.findTasks(tenantId, stage.planId(), stage.id());
        boolean hasTasks = !freshTasks.isEmpty();
        boolean allTasksCancelled = hasTasks && freshTasks.stream().allMatch(Task::cancelled);
        boolean allEffectiveCompleted = !hasTasks || freshTasks.stream()
                .allMatch(t -> t.cancelled() || t.status() == TaskStatus.COMPLETED);
        boolean anyActive = freshTasks.stream()
                .anyMatch(t -> !t.cancelled() && (t.status() == TaskStatus.IN_PROGRESS
                        || t.status() == TaskStatus.BLOCKED || t.actualStart() != null));
        LocalDateTime actualStart = aggregateActualStart(freshTasks.stream()
                .filter(t -> !t.cancelled()).map(Task::actualStart).toList());
        LocalDateTime actualEnd = aggregateActualEnd(freshTasks.stream()
                .filter(t -> !t.cancelled()).map(Task::actualEnd).toList());
        PlanStatus status;
        if (!hasTasks) {
            status = PlanStatus.NOT_STARTED;
        } else if (allEffectiveCompleted) {
            status = PlanStatus.COMPLETED;
        } else if (anyActive || actualStart != null) {
            status = PlanStatus.IN_PROGRESS;
        } else {
            status = PlanStatus.NOT_STARTED;
        }
        store.updateStageStatus(tenantId, stage.id(), status, false, null, null, null);
        store.updateStageActual(tenantId, stage.id(), actualStart, actualEnd);
    }

    /** 从执行事件聚合任务实际开始与完成时间（更正事件替换原事件，重开后实际完成置空）。 */
    public TaskActual aggregateActualTimes(long tenantId, long planId, String objectType, long objectId) {
        List<PlanEvent> events = store.findEvents(tenantId, planId, objectType, objectId);
        Map<Long, PlanEvent> byId = new HashMap<>();
        for (PlanEvent event : events) {
            byId.put(event.id(), event);
        }
        boolean[] corrected = new boolean[events.size()];
        for (PlanEvent event : events) {
            if (event.eventType() == EventType.TIME_CORRECT && event.correctOfEventId() != null) {
                corrected[indexOf(events, event.correctOfEventId())] = true;
            }
        }
        LocalDateTime actualStart = null;
        LocalDateTime actualEnd = null;
        for (int i = 0; i < events.size(); i++) {
            if (corrected[i]) {
                continue;
            }
            PlanEvent event = events.get(i);
            if (event.eventType() == EventType.START && actualStart == null) {
                actualStart = event.occurredAt();
            } else if (event.eventType() == EventType.COMPLETE) {
                actualEnd = event.occurredAt();
            } else if (event.eventType() == EventType.REOPEN) {
                if (actualEnd != null && !event.occurredAt().isBefore(actualEnd)) {
                    actualEnd = null;
                }
            }
        }
        return new TaskActual(actualStart, actualEnd);
    }

    private static int indexOf(List<PlanEvent> events, long eventId) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id() == eventId) {
                return i;
            }
        }
        return -1;
    }

    private List<Long> storeOpenWorkOrders(long tenantId, List<TaskWorkOrder> workOrders) {
        if (workOrders.isEmpty()) {
            return List.of();
        }
        List<Long> resourceIds = new ArrayList<>();
        List<Long> networkIds = new ArrayList<>();
        for (TaskWorkOrder workOrder : workOrders) {
            if (workOrder.workOrderType().name().startsWith("NETWORK_")) {
                networkIds.add(workOrder.workOrderId());
            } else {
                resourceIds.add(workOrder.workOrderId());
            }
        }
        List<Long> open = new ArrayList<>(store.openResourceRequestIds(tenantId, resourceIds));
        open.addAll(store.openNetworkWorkOrderIds(tenantId, networkIds));
        return open;
    }

    public List<TaskWorkOrder> openWorkOrderRefs(long tenantId, List<TaskWorkOrder> workOrders) {
        List<Long> openIds = storeOpenWorkOrders(tenantId, workOrders);
        return workOrders.stream().filter(w -> openIds.contains(w.workOrderId())).toList();
    }

    private static LocalDateTime aggregateActualStart(List<LocalDateTime> starts) {
        return starts.stream().filter(v -> v != null).min(LocalDateTime::compareTo).orElse(null);
    }

    private static LocalDateTime aggregateActualEnd(List<LocalDateTime> ends) {
        return ends.stream().filter(v -> v != null).max(LocalDateTime::compareTo).orElse(null);
    }

    public record TaskActual(LocalDateTime start, LocalDateTime end) {
    }

    // ---------- 实体授权 ----------

    public void requirePlanOwner(AuthUser actor, Plan plan, boolean isAdmin) {
        if (!isAdmin && actor.id() != plan.planOwnerUserId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅计划责任人可以执行该操作");
        }
    }

    public void requireStageOwner(AuthUser actor, Stage stage, boolean isAdmin) {
        if (!isAdmin && actor.id() != stage.ownerUserId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅环节责任人可以执行该操作");
        }
    }

    public void requireTaskExecutor(AuthUser actor, Task task, boolean isAdmin) {
        if (isAdmin || actor.id() == task.ownerUserId()
                || store.findParticipantUserIds(actor.tenantId(), task.id()).contains(actor.id())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务责任人、参与人或管理员可以执行该操作");
    }

    public Plan requirePlan(AuthUser actor, long planId) {
        return store.findPlan(actor.tenantId(), planId)
                .orElseThrow(() -> new ArchitectureNotFoundException("搭建计划不存在"));
    }

    public Task requireTask(AuthUser actor, long taskId) {
        return store.findTask(actor.tenantId(), taskId)
                .orElseThrow(() -> new ArchitectureNotFoundException("任务不存在"));
    }

    public Stage requireStage(AuthUser actor, long stageId) {
        return store.findStage(actor.tenantId(), stageId)
                .orElseThrow(() -> new ArchitectureNotFoundException("环节不存在"));
    }

    public CheckItem requireCheckItem(AuthUser actor, long checkItemId) {
        return store.findCheckItem(actor.tenantId(), checkItemId)
                .orElseThrow(() -> new ArchitectureNotFoundException("检查项不存在"));
    }

    public List<PlanTarget> findActiveTargets(long tenantId, long planId) {
        return store.findActiveTargets(tenantId, planId);
    }

    public PlanStore store() {
        return store;
    }
}
