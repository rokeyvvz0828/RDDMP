package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.Block;
import com.ccb.architecture.plan.model.PlanModels.CancelSuggestion;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.Dependency;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.PlanTarget;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.TargetType;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskWorkOrder;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.plan.persistence.PlanStore.EnvironmentRef;
import com.ccb.architecture.plan.persistence.PlanStore.PlanListRow;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 搭建计划查询与视图聚合（REQ-20260830-056）：列表、详情、看板、时间视图与报告。 */
@Service
public class PlanQueryService {
    private final PlanStore store;
    private final PlanEngine engine;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanQueryService(PlanStore store, PlanEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    public record PlanFilter(Long environmentId, PlanStatus status, Long ownerUserId, boolean hasBlocked,
                             boolean hasOverdue, boolean hasWaived, String keyword, TargetType targetType,
                             Long targetId) {
    }

    public record PlanRow(long id, String planNo, String name, String environmentCode,
                          String environmentName, PlanStatus status, Long progress, long taskCount,
                          boolean hasBlocked, boolean hasOverdue, boolean hasWaived,
                          LocalDateTime plannedEnd, long planOwnerUserId, LocalDateTime updatedAt) {
    }

    public PageResult<PlanRow> list(AuthUser actor, PlanFilter filter, long page, long size) {
        List<PlanListRow> rows = store.searchPlans(actor.tenantId(), filter.environmentId(), filter.status(),
                filter.ownerUserId(), filter.hasBlocked(), filter.hasOverdue(), filter.hasWaived(),
                filter.keyword(), filter.targetType(), filter.targetId(), (int) size, (int) ((page - 1) * size));
        LocalDateTime now = LocalDateTime.now();
        List<PlanRow> views = new ArrayList<>();
        for (PlanListRow row : rows) {
            boolean overdue = filter.hasOverdue() || planHasOverdueTask(actor, row.plan().id(), now);
            views.add(new PlanRow(row.plan().id(), row.plan().planNo(), row.plan().name(),
                    row.environmentCode(), row.environmentName(), row.plan().status(),
                    PlanStatusCalculator.progressPercent((int) row.completedCheckItems(),
                            (int) row.totalCheckItems(), (int) row.cancelledCheckItems()),
                    row.taskCount(), row.openBlocks() > 0, overdue,
                    row.cancelledCheckItems() > 0 || hasWaived(actor, row.plan().id(), row),
                    row.plan().plannedEnd(), row.plan().planOwnerUserId(), null));
        }
        long total = store.countPlans(actor.tenantId(), filter.environmentId(), filter.status(),
                filter.ownerUserId(), filter.hasBlocked(), filter.hasOverdue(), filter.hasWaived(),
                filter.keyword(), filter.targetType(), filter.targetId());
        return new PageResult<>(views, total, page, size);
    }

    public record TargetView(long id, TargetType targetType, long targetId, String targetNo,
                             String targetName, boolean removed, String snapshotName,
                             String currentName, boolean hasDiff) {
    }

    public record CheckItemView(long id, String name, String guide, String status, String remark,
                                Long completedBy, LocalDateTime completedAt, boolean cancelled,
                                String cancelReason, Long cancelledBy, LocalDateTime cancelledAt,
                                Long completedItemId) {
    }

    public record TaskView(long id, long stageId, int taskNo, String name, String targetName,
                           Long targetId, String targetType, String status, Long progress,
                           boolean waivedAll, boolean overdue, boolean hasBlocked, boolean hasOpenWorkOrder,
                           long ownerUserId, LocalDateTime plannedStart, LocalDateTime plannedEnd,
                           LocalDateTime actualStart, LocalDateTime actualEnd, boolean cancelled,
                           String cancelReason, List<Long> participantUserIds,
                           List<Dependency> dependencies, List<Block> blocks, List<TaskWorkOrder> workOrders,
                           List<CheckItemView> checkItems, List<PlanEventView> events) {
    }

    public record StageView(long id, int stageNo, String name, String status, boolean cancelled,
                            String cancelReason, long ownerUserId, LocalDateTime plannedStart,
                            LocalDateTime plannedEnd, LocalDateTime actualStart, LocalDateTime actualEnd,
                            Long progress, boolean hasWaived, List<TaskView> tasks) {
    }

    public record PlanEventView(long id, String objectType, long objectId, String eventType,
                                LocalDateTime occurredAt, long operatorUserId, String reason,
                                Long correctOfEventId) {
    }

    public record PlanDetailView(Plan plan, String environmentCode, String environmentName,
                                 List<TargetView> targets, List<StageView> stages, Long progress,
                                 boolean hasBlocked, boolean hasOverdue, boolean hasWaived,
                                 boolean uncompletable, long pendingSuggestionCount,
                                 List<Long[]> stageDependencies, List<PlanEventView> events) {
    }

    public PlanDetailView detail(AuthUser actor, long planId) {
        Plan plan = engine.requirePlan(actor, planId);
        // 惰性重算（幂等）：查看时按依赖/阻塞/检查项推导最新状态，
        // 同时自动修复历史数据（如生成时未重算导致的前置未完成却为 NOT_STARTED）
        engine.recompute(actor.tenantId(), planId, java.time.LocalDateTime.now());
        EnvironmentRef environment = store.envReference(actor.tenantId(), plan.environmentId())
                .orElse(new EnvironmentRef(0L, "", "", ""));
        List<TargetView> targets = new ArrayList<>();
        Map<Long, String> currentPhysicalNames = new HashMap<>();
        Map<Long, String> currentUnitNames = new HashMap<>();
        for (PlanTarget target : store.findTargets(actor.tenantId(), planId, true)) {
            if (target.targetType() == TargetType.PHYSICAL_SUBSYSTEM) {
                currentPhysicalNames.putIfAbsent(target.targetId(), currentName(actor,
                        TargetType.PHYSICAL_SUBSYSTEM, target.targetId()));
            } else {
                currentUnitNames.putIfAbsent(target.targetId(), currentName(actor,
                        TargetType.DEPLOYMENT_UNIT, target.targetId()));
            }
            String current = target.targetType() == TargetType.PHYSICAL_SUBSYSTEM
                    ? currentPhysicalNames.get(target.targetId())
                    : currentUnitNames.get(target.targetId());
            targets.add(new TargetView(target.id(), target.targetType(), target.targetId(),
                    target.targetNo(), target.targetName(), target.removed(), target.targetName(),
                    current, target.removed() ? false
                            : current != null && !current.equals(target.targetName())));
        }
        List<StageView> stages = new ArrayList<>();
        boolean hasBlocked = false;
        boolean hasWaived = false;
        boolean hasOverdue = false;
        int doneCounter = 0;
        int totalCounter = 0;
        int cancelledCounter = 0;
        for (Stage stage : store.findStages(actor.tenantId(), planId)) {
            List<TaskView> taskViews = new ArrayList<>();
            for (Task task : store.findTasks(actor.tenantId(), planId, stage.id())) {
                List<CheckItem> items = store.findCheckItems(actor.tenantId(), task.id());
                String targetName = TaskTargetName.of(task);
                List<CheckItemView> itemViews = items.stream()
                        .map(item -> new CheckItemView(item.id(), item.name(), item.guide(),
                        item.status().name(),
                                item.remark(), item.completedBy(), item.completedAt(), item.cancelled(),
                                item.cancelReason(), item.cancelledBy(), item.cancelledAt(), null))
                        .toList();
                int completed = (int) items.stream().filter(i -> !i.cancelled()
                        && i.status() == com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED)
                        .count();
                int cancelled = (int) items.stream().filter(i -> i.cancelled()).count();
                Long progress = PlanStatusCalculator.progressPercent(completed, items.size(), cancelled);
                if (!task.cancelled()) {
                    doneCounter += completed;
                    totalCounter += items.size();
                    cancelledCounter += cancelled;
                }
                List<Dependency> dependencies = store.findDependencies(actor.tenantId(), task.id(), true);
                List<Dependency> activeDependencies = store.findDependencies(actor.tenantId(), task.id(),
                        false);
                List<Block> blocks = store.findBlocks(actor.tenantId(), task.id());
                boolean taskBlocked = blocks.stream().anyMatch(b -> !b.resolved());
                List<TaskWorkOrder> workOrders = store.findWorkOrders(actor.tenantId(), task.id());
                boolean openWorkOrder = !engine.openWorkOrderRefs(actor.tenantId(), workOrders).isEmpty();
                boolean overdue = !task.cancelled() && task.status() != TaskStatus.COMPLETED
                        && task.plannedEnd() != null
                        && LocalDateTime.now().isAfter(task.plannedEnd());
                boolean taskWaived = !task.cancelled() && task.waivedAll();
                hasBlocked = hasBlocked || taskBlocked;
                hasWaived = hasWaived || taskWaived || cancelled > 0 || task.cancelled();
                hasOverdue = hasOverdue || overdue;
                taskViews.add(new TaskView(task.id(), stage.id(), task.taskNo(), task.name(), targetName,
                        task.targetId(), task.targetType() == null ? null : task.targetType().name(),
                        task.status().name(), progress, task.waivedAll(), overdue, taskBlocked,
                        openWorkOrder, task.ownerUserId(), task.plannedStart(), task.plannedEnd(),
                        task.actualStart(), task.actualEnd(), task.cancelled(), task.cancelReason(),
                        store.findParticipantUserIds(actor.tenantId(), task.id()),
                        activeDependencies, blocks, workOrders, itemViews,
                        store.findEvents(actor.tenantId(), planId, "TASK", task.id()).stream()
                                .map(PlanQueryService::toEventView).toList()));
            }
            boolean stageWaived = taskViews.stream()
                    .anyMatch(t -> t.waivedAll() || t.cancelled()
                            || (t.checkItems() != null && t.checkItems().stream()
                                    .anyMatch(CheckItemView::cancelled)));
            stages.add(new StageView(stage.id(), stage.stageNo(), stage.name(), stage.status().name(),
                    stage.cancelled(), stage.cancelReason(), stage.ownerUserId(),
                    stage.plannedStart(), stage.plannedEnd(), stage.actualStart(), stage.actualEnd(),
                    stageProgress(taskViews), stageWaived, taskViews));
        }
        Long planProgress = PlanStatusCalculator.progressPercent(doneCounter, totalCounter, cancelledCounter);
        long pendingSuggestions = store.findPendingSuggestions(actor.tenantId(), 0L).stream()
                .filter(s -> planContains(actor, planId, s.checkItemId())).count();
        List<PlanEventView> events = store.findEvents(actor.tenantId(), planId, "PLAN", planId).stream()
                .map(PlanQueryService::toEventView).toList();
        boolean uncompletable = !plan.cancelled()
                && store.findStages(actor.tenantId(), planId).stream().noneMatch(s -> !s.cancelled());
        return new PlanDetailView(plan, environment.code(), environment.name(), targets, stages,
                planProgress, hasBlocked, hasOverdue, hasWaived, uncompletable, pendingSuggestions,
                store.findStageDependencies(actor.tenantId(), planId), events);
    }

    public record DashboardStage(long id, int stageNo, String name, String status, Long progress,
                                 boolean hasWaived, List<DashboardTask> tasks) {
    }

    public record DashboardTask(long id, String name, String status, Long progress, boolean waivedAll,
                                 boolean overdue, boolean hasBlocked, String targetName) {
    }

    public record DashboardView(long planId, String planNo, String name, String environmentName,
                                PlanStatus status, Long progress, boolean hasBlocked, boolean hasOverdue,
                                boolean hasWaived, List<DashboardStage> stages) {
    }

    public DashboardView dashboard(AuthUser actor, long planId) {
        PlanDetailView detail = detail(actor, planId);
        List<DashboardStage> stages = detail.stages().stream().map(stage -> new DashboardStage(
                stage.id(), stage.stageNo(), stage.name(), stage.status(), stage.progress(),
                stage.hasWaived(), stage.tasks().stream().map(task -> new DashboardTask(task.id(),
                task.name(), task.status(), task.progress(), task.waivedAll(), task.overdue(),
                task.hasBlocked(), task.targetName())).toList())).toList();
        return new DashboardView(detail.plan().id(), detail.plan().planNo(), detail.plan().name(),
                detail.environmentName(), detail.plan().status(), detail.progress(), detail.hasBlocked(),
                detail.hasOverdue(), detail.hasWaived(), stages);
    }

    public record TimelineRow(long id, String name, String type, long parentId, String status,
                              LocalDateTime plannedStart, LocalDateTime plannedEnd,
                              LocalDateTime actualStart, LocalDateTime actualEnd, Long progress,
                              boolean overdue, String targetName) {
    }

    public record TimelineView(long planId, String planNo, String name, List<TimelineRow> rows) {
    }

    public TimelineView timeline(AuthUser actor, long planId) {
        Plan plan = engine.requirePlan(actor, planId);
        List<TimelineRow> rows = new ArrayList<>();
        rows.add(new TimelineRow(plan.id(), plan.name(), "PLAN", 0, plan.status().name(),
                plan.plannedStart(), plan.plannedEnd(), plan.actualStart(), plan.actualEnd(), null,
                false, null));
        for (Stage stage : store.findStages(actor.tenantId(), planId)) {
            rows.add(new TimelineRow(stage.id(), stage.name(), "STAGE", stage.planId(),
                    stage.status().name(), stage.plannedStart(), stage.plannedEnd(),
                    stage.actualStart(), stage.actualEnd(), null, false, null));
            for (Task task : store.findTasks(actor.tenantId(), planId, stage.id())) {
                boolean overdue = !task.cancelled() && task.status() != TaskStatus.COMPLETED
                        && task.plannedEnd() != null
                        && LocalDateTime.now().isAfter(task.plannedEnd());
                rows.add(new TimelineRow(task.id(), task.name(), "TASK", stage.id(),
                        task.status().name(), task.plannedStart(), task.plannedEnd(),
                        task.actualStart(), task.actualEnd(), taskProgress(actor, task), overdue,
                        TaskTargetName.of(task)));
            }
        }
        return new TimelineView(plan.id(), plan.planNo(), plan.name(), rows);
    }

    public List<CancelSuggestion> pendingSuggestions(AuthUser actor, long planId) {
        Map<Long, Long> checkToTask = new HashMap<>();
        for (Task task : store.findTasks(actor.tenantId(), planId, null)) {
            for (CheckItem item : store.findCheckItems(actor.tenantId(), task.id())) {
                checkToTask.put(item.id(), task.id());
            }
        }
        return store.findPendingSuggestions(actor.tenantId(), 0L).stream()
                .filter(suggestion -> checkToTask.containsKey(suggestion.checkItemId())).toList();
    }

    public record ReportView(PlanDetailView detail, DashboardView dashboard, TimelineView timeline) {
    }

    public ReportView report(AuthUser actor, long planId) {
        return new ReportView(detail(actor, planId), dashboard(actor, planId), timeline(actor, planId));
    }

    private Long taskProgress(AuthUser actor, Task task) {
        List<CheckItem> items = store.findCheckItems(actor.tenantId(), task.id());
        int completed = (int) items.stream().filter(i -> !i.cancelled()
                && i.status() == com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED)
                .count();
        int cancelled = (int) items.stream().filter(i -> i.cancelled()).count();
        return PlanStatusCalculator.progressPercent(completed, items.size(), cancelled);
    }

    private Long stageProgress(List<TaskView> tasks) {
        int done = 0;
        int total = 0;
        int cancelled = 0;
        for (TaskView task : tasks) {
            if (task.cancelled()) {
                continue;
            }
            done += (int) task.checkItems().stream()
                    .filter(i -> !i.cancelled() && "COMPLETED".equals(i.status())).count();
            total += task.checkItems().size();
            cancelled += (int) task.checkItems().stream().filter(CheckItemView::cancelled).count();
        }
        return PlanStatusCalculator.progressPercent(done, total, cancelled);
    }

    private boolean planContains(AuthUser actor, long planId, long checkItemId) {
        for (Task task : store.findTasks(actor.tenantId(), planId, null)) {
            if (store.findCheckItems(actor.tenantId(), task.id()).stream()
                    .anyMatch(item -> item.id() == checkItemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean planHasOverdueTask(AuthUser actor, long planId, LocalDateTime now) {
        for (Task task : store.findTasks(actor.tenantId(), planId, null)) {
            if (!task.cancelled() && task.status() != TaskStatus.COMPLETED
                    && task.plannedEnd() != null && now.isAfter(task.plannedEnd())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWaived(AuthUser actor, long planId, PlanListRow row) {
        boolean cancelled = store.findTasks(actor.tenantId(), planId, null).stream()
                .anyMatch(t -> t.cancelled() || t.waivedAll());
        if (cancelled) {
            return true;
        }
        for (Task task : store.findTasks(actor.tenantId(), planId, null)) {
            if (store.findCheckItems(actor.tenantId(), task.id()).stream()
                    .anyMatch(CheckItem::cancelled)) {
                return true;
            }
        }
        return false;
    }

    private String currentName(AuthUser actor, TargetType targetType, long targetId) {
        return store.currentTargetNames(actor.tenantId(), targetType, List.of(targetId))
                .get(targetId);
    }

    private static PlanEventView toEventView(com.ccb.architecture.plan.model.PlanModels.PlanEvent event) {
        return new PlanEventView(event.id(), event.objectType(), event.objectId(),
                event.eventType().name(), event.occurredAt(), event.operatorUserId(), event.reason(),
                event.correctOfEventId());
    }

    /** 任务目标展示名（计划级任务为空）。 */
    static final class TaskTargetName {
        static String of(Task task) {
            return task.targetName();
        }

        private TaskTargetName() {
        }
    }
}
