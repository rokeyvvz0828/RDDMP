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

    public PageResult<PlanRow> list(AuthUser actor, long projectId, PlanFilter filter, long page, long size) {
        long safePage = Math.max(1, page);
        int safeSize = (int) Math.max(1, Math.min(100, size));
        var result = store.searchVisiblePlans(actor.tenantId(), projectId, actor.id(),
                engine.participation().administrator(actor), engine.participation().activeMember(actor, projectId),
                filter.environmentId(), filter.status(), filter.ownerUserId(), filter.hasBlocked(), filter.hasOverdue(),
                filter.hasWaived(), filter.keyword(), filter.targetType(), filter.targetId(), safeSize, (safePage - 1) * safeSize);
        var views = result.rows().stream().map(visible -> {
            var row = visible.row();
            return new PlanRow(row.plan().id(), row.plan().planNo(), row.plan().name(), row.environmentCode(),
                    row.environmentName(), row.plan().status(),
                    PlanStatusCalculator.progressPercent((int) row.completedCheckItems(), (int) row.totalCheckItems(),
                            (int) row.cancelledCheckItems()), row.taskCount(), row.openBlocks() > 0,
                    visible.overdue(), visible.waived(), row.plan().plannedEnd(), row.plan().planOwnerUserId(), null);
        }).toList();
        return new PageResult<>(views, result.total(), safePage, safeSize);
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
                           List<CheckItemView> checkItems, List<PlanEventView> events, boolean canExecute) {
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

    public PlanDetailView detail(AuthUser actor, long projectId, long planId) {
        return detail(actor, projectId, planId, engine.participation().readScope(actor, projectId, planId));
    }

    private PlanDetailView detail(AuthUser actor, long projectId, long planId, PlanParticipationService.ReadScope scope) {
        Plan plan = engine.requirePlan(actor, projectId, planId);
        EnvironmentRef environment = store.envReference(actor.tenantId(), projectId, plan.environmentId())
                .orElse(new EnvironmentRef(0L, "", "", ""));
        List<TargetView> targets = new ArrayList<>();
        Map<Long, String> currentPhysicalNames = new HashMap<>();
        Map<Long, String> currentUnitNames = new HashMap<>();
        var visibleTasks = store.findTasks(actor.tenantId(), projectId, planId, null).stream()
                .filter(t -> (scope.manager() || scope.related(t))).toList();
        if (visibleTasks.isEmpty() && !scope.manager()) {
            throw new com.ccb.architecture.web.ArchitectureNotFoundException("搭建计划不存在或无权访问");
        }
        for (PlanTarget target : store.findTargets(actor.tenantId(), projectId, planId, true)) {
            if (!scope.manager() && visibleTasks.stream()
                    .noneMatch(t -> t.targetType() == target.targetType() && java.util.Objects.equals(t.targetId(), target.targetId()))) continue;
            if (target.targetType() == TargetType.PHYSICAL_SUBSYSTEM) {
                currentPhysicalNames.putIfAbsent(target.targetId(), currentName(actor, projectId,
                        TargetType.PHYSICAL_SUBSYSTEM, target.targetId()));
            } else {
                currentUnitNames.putIfAbsent(target.targetId(), currentName(actor, projectId,
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
        for (Stage stage : store.findStages(actor.tenantId(), projectId, planId)) {
            List<TaskView> taskViews = new ArrayList<>();
            for (Task task : store.findTasks(actor.tenantId(), projectId, planId, stage.id())) {
                if (!(scope.manager() || scope.related(task))) continue;
                List<CheckItem> items = store.findCheckItems(actor.tenantId(), projectId, task.id());
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
                List<Dependency> dependencies = store.findDependencies(actor.tenantId(), projectId,
                        task.id(), true);
                List<Dependency> activeDependencies = store.findDependencies(actor.tenantId(), projectId,
                        task.id(), false);
                List<Block> blocks = store.findBlocks(actor.tenantId(), projectId, task.id());
                boolean taskBlocked = blocks.stream().anyMatch(b -> !b.resolved());
                List<TaskWorkOrder> workOrders = store.findWorkOrders(actor.tenantId(), projectId, task.id());
                boolean openWorkOrder = !engine.openWorkOrderRefs(actor.tenantId(), projectId,
                        workOrders).isEmpty();
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
                        scope.participants(task.id()),
                        activeDependencies.stream().filter(d -> visibleTasks.stream().anyMatch(t -> t.id() == d.predecessorId())).toList(), blocks, workOrders.stream().filter(w -> engine.participation().workOrderVisible(actor, projectId, w.workOrderType(), w.workOrderId())).toList(), itemViews,
                        store.findEvents(actor.tenantId(), projectId, planId, "TASK", task.id()).stream()
                                .map(PlanQueryService::toEventView).toList(), scope.related(task)));
            }
            boolean stageWaived = taskViews.stream()
                    .anyMatch(t -> t.waivedAll() || t.cancelled()
                            || (t.checkItems() != null && t.checkItems().stream()
                                    .anyMatch(CheckItemView::cancelled)));
            if (taskViews.isEmpty() && !scope.manager()) continue;
            stages.add(new StageView(stage.id(), stage.stageNo(), stage.name(), stage.status().name(),
                    stage.cancelled(), stage.cancelReason(), stage.ownerUserId(),
                    stage.plannedStart(), stage.plannedEnd(), stage.actualStart(), stage.actualEnd(),
                    stageProgress(taskViews), stageWaived, taskViews));
        }
        Long planProgress = PlanStatusCalculator.progressPercent(doneCounter, totalCounter, cancelledCounter);
        long pendingSuggestions = !scope.manager() ? 0 : store.findPendingSuggestions(actor.tenantId(), projectId, 0L).stream()
                .filter(s -> planContains(actor, projectId, planId, s.checkItemId())).count();
        List<PlanEventView> events = !scope.manager() ? List.of() : store.findEvents(actor.tenantId(), projectId, planId,
                "PLAN", planId).stream()
                .map(PlanQueryService::toEventView).toList();
        boolean uncompletable = !plan.cancelled()
                && store.findStages(actor.tenantId(), projectId, planId).stream()
                .noneMatch(s -> !s.cancelled());
        return new PlanDetailView(plan, environment.code(), environment.name(), targets, stages,
                planProgress, hasBlocked, hasOverdue, hasWaived, uncompletable, pendingSuggestions,
                store.findStageDependencies(actor.tenantId(), projectId, planId).stream().filter(d -> stages.stream().anyMatch(st -> st.id() == d[0]) && stages.stream().anyMatch(st -> st.id() == d[1])).toList(), events);
    }

    public record DashboardStage(long id, int stageNo, String name, String status, Long progress,
                                 boolean hasWaived, List<DashboardTask> tasks) {
    }

    public record DashboardTask(long id, String name, String status, Long progress, boolean waivedAll,
                                 boolean overdue, boolean hasBlocked, String targetName, long ownerUserId,
                                String ownerName, long completedChecks, long totalChecks, LocalDateTime plannedEnd,
                                boolean assignmentNeedsAttention) {
    }

    public record DashboardView(long planId, String planNo, String name, String environmentName,
                                PlanStatus status, Long progress, boolean hasBlocked, boolean hasOverdue,
                                boolean hasWaived, List<DashboardStage> stages) {
    }

    public DashboardView dashboard(AuthUser actor, long projectId, long planId) {
        return dashboard(actor, projectId, planId, false);
    }

    public DashboardView dashboard(AuthUser actor, long projectId, long planId, boolean all) {
        var scope = engine.participation().readScope(actor, projectId, planId);
        if (all && !scope.manager()) {
            throw new com.ccb.common.exception.BusinessException(com.ccb.common.exception.ErrorCode.FORBIDDEN, "仅计划管理者可查看全部任务");
        }
        PlanDetailView detail = detail(actor, projectId, planId, scope);
        var relatedIds = store.findTasks(actor.tenantId(), projectId, planId, null).stream()
                .filter(t -> all || scope.related(t)).map(Task::id).toList();
        List<DashboardStage> stages = detail.stages().stream().map(stage -> new DashboardStage(
                stage.id(), stage.stageNo(), stage.name(), stage.status(), stage.progress(),
                false, stage.tasks().stream().filter(t -> !t.cancelled() && relatedIds.contains(t.id())).map(task -> new DashboardTask(task.id(),
                task.name(), task.status(), task.progress(), task.waivedAll(), task.overdue(),
                task.hasBlocked(), task.targetName(), task.ownerUserId(),
                scope.name(task.ownerUserId()),
                task.checkItems().stream().filter(c -> !c.cancelled() && "COMPLETED".equals(c.status())).count(),
                task.checkItems().stream().filter(c -> !c.cancelled()).count(), task.plannedEnd(),
                scope.attention(
                    task.targetType() == null ? null : TargetType.valueOf(task.targetType()), task.targetId(),
                    task.ownerUserId(), task.participantUserIds()))).toList())).toList();
        var tasks = stages.stream().flatMap(stage -> stage.tasks().stream()).toList();
        var filteredStages = stages.stream().filter(stage -> !stage.tasks().isEmpty()).map(stage -> new DashboardStage(
                stage.id(), stage.stageNo(), stage.name(), stage.status(),
                (long) stage.tasks().stream().mapToLong(t -> t.progress() == null ? 0 : t.progress()).average().orElse(0),
                stage.tasks().stream().anyMatch(DashboardTask::waivedAll), stage.tasks())).toList();
        return new DashboardView(detail.plan().id(), detail.plan().planNo(), detail.plan().name(),
                detail.environmentName(), detail.plan().status(),
                (long) tasks.stream().mapToLong(t -> t.progress() == null ? 0 : t.progress()).average().orElse(0),
                tasks.stream().anyMatch(t -> t.hasBlocked() || "WAITING_PRECEDING".equals(t.status())),
                tasks.stream().anyMatch(DashboardTask::overdue), tasks.stream().anyMatch(DashboardTask::waivedAll), filteredStages);
    }

    public record TimelineRow(long id, String name, String type, long parentId, String status,
                              LocalDateTime plannedStart, LocalDateTime plannedEnd,
                              LocalDateTime actualStart, LocalDateTime actualEnd, Long progress,
                              boolean overdue, String targetName) {
    }

    public record TimelineView(long planId, String planNo, String name, List<TimelineRow> rows) {
    }

    public TimelineView timeline(AuthUser actor, long projectId, long planId) {
        Plan plan = engine.requirePlan(actor, projectId, planId);
        if (!engine.participation().manager(actor, projectId, planId)
                && store.findTasks(actor.tenantId(), projectId, planId, null).stream()
                    .noneMatch(task -> engine.participation().related(actor, projectId, task))) {
            throw new com.ccb.architecture.web.ArchitectureNotFoundException("搭建计划不存在或无权访问");
        }
        List<TimelineRow> rows = new ArrayList<>();
        rows.add(new TimelineRow(plan.id(), plan.name(), "PLAN", 0, plan.status().name(),
                plan.plannedStart(), plan.plannedEnd(), plan.actualStart(), plan.actualEnd(), null,
                false, null));
        for (Stage stage : store.findStages(actor.tenantId(), projectId, planId)) {
            if (!engine.participation().manager(actor, projectId, planId) && store.findTasks(actor.tenantId(), projectId, planId, stage.id()).stream()
                    .noneMatch(t -> engine.participation().visible(actor, projectId, t))) continue;
            rows.add(new TimelineRow(stage.id(), stage.name(), "STAGE", stage.planId(),
                    stage.status().name(), stage.plannedStart(), stage.plannedEnd(),
                    stage.actualStart(), stage.actualEnd(), null, false, null));
            for (Task task : store.findTasks(actor.tenantId(), projectId, planId, stage.id())) {
                if (!engine.participation().visible(actor, projectId, task)) continue;
                boolean overdue = !task.cancelled() && task.status() != TaskStatus.COMPLETED
                        && task.plannedEnd() != null
                        && LocalDateTime.now().isAfter(task.plannedEnd());
                rows.add(new TimelineRow(task.id(), task.name(), "TASK", stage.id(),
                        task.status().name(), task.plannedStart(), task.plannedEnd(),
                        task.actualStart(), task.actualEnd(), taskProgress(actor, projectId, task), overdue,
                        TaskTargetName.of(task)));
            }
        }
        return new TimelineView(plan.id(), plan.planNo(), plan.name(), rows);
    }

    public List<CancelSuggestion> pendingSuggestions(AuthUser actor, long projectId, long planId) {
        Map<Long, Long> checkToTask = new HashMap<>();
        for (Task task : store.findTasks(actor.tenantId(), projectId, planId, null)) {
            if (!engine.participation().visible(actor, projectId, task)) continue;
            for (CheckItem item : store.findCheckItems(actor.tenantId(), projectId, task.id())) {
                checkToTask.put(item.id(), task.id());
            }
        }
        return store.findPendingSuggestions(actor.tenantId(), projectId, 0L).stream()
                .filter(suggestion -> checkToTask.containsKey(suggestion.checkItemId())).toList();
    }

    public record ReportView(PlanDetailView detail, DashboardView dashboard, TimelineView timeline) {
    }

    public ReportView report(AuthUser actor, long projectId, long planId) {
        return new ReportView(detail(actor, projectId, planId), dashboard(actor, projectId, planId, engine.participation().manager(actor, projectId, planId)),
                timeline(actor, projectId, planId));
    }

    private Long taskProgress(AuthUser actor, long projectId, Task task) {
        List<CheckItem> items = store.findCheckItems(actor.tenantId(), projectId, task.id());
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

    private boolean planContains(AuthUser actor, long projectId, long planId, long checkItemId) {
        for (Task task : store.findTasks(actor.tenantId(), projectId, planId, null)) {
            if (!engine.participation().visible(actor, projectId, task)) continue;
            if (store.findCheckItems(actor.tenantId(), projectId, task.id()).stream()
                    .anyMatch(item -> item.id() == checkItemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean planHasOverdueTask(AuthUser actor, long projectId, long planId, LocalDateTime now) {
        for (Task task : store.findTasks(actor.tenantId(), projectId, planId, null)) {
            if (!engine.participation().visible(actor, projectId, task)) continue;
            if (!task.cancelled() && task.status() != TaskStatus.COMPLETED
                    && task.plannedEnd() != null && now.isAfter(task.plannedEnd())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWaived(AuthUser actor, long projectId, long planId, PlanListRow row) {
        boolean cancelled = store.findTasks(actor.tenantId(), projectId, planId, null).stream()
                .anyMatch(t -> t.cancelled() || t.waivedAll());
        if (cancelled) {
            return true;
        }
        for (Task task : store.findTasks(actor.tenantId(), projectId, planId, null)) {
            if (!engine.participation().visible(actor, projectId, task)) continue;
            if (store.findCheckItems(actor.tenantId(), projectId, task.id()).stream()
                    .anyMatch(CheckItem::cancelled)) {
                return true;
            }
        }
        return false;
    }

    private String currentName(AuthUser actor, long projectId, TargetType targetType, long targetId) {
        return store.currentTargetNames(actor.tenantId(), projectId, targetType, List.of(targetId))
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
