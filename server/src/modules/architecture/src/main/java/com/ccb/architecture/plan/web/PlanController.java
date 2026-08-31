package com.ccb.architecture.plan.web;

import com.ccb.architecture.plan.model.PlanModels.AddCheckItemCommand;
import com.ccb.architecture.plan.model.PlanModels.AddStageCommand;
import com.ccb.architecture.plan.model.PlanModels.AddTargetCommand;
import com.ccb.architecture.plan.model.PlanModels.AddTaskCommand;
import com.ccb.architecture.plan.model.PlanModels.BlockCommand;
import com.ccb.architecture.plan.model.PlanModels.CancelSuggestion;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.CreatePlanCommand;
import com.ccb.architecture.plan.model.PlanModels.DependencyCommand;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.model.PlanModels.ScheduleCommand;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.TargetType;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderCommand;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderType;
import com.ccb.architecture.plan.service.PlanBlockService;
import com.ccb.architecture.plan.service.PlanDependencyService;
import com.ccb.architecture.plan.service.PlanEngine;
import com.ccb.architecture.plan.service.PlanExecutionService;
import com.ccb.architecture.plan.service.PlanGenerationService;
import com.ccb.architecture.plan.service.PlanQueryService;
import com.ccb.architecture.plan.service.PlanTimeService;
import com.ccb.architecture.plan.service.PlanWorkOrderService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/** 搭建计划 HTTP 边界（REQ-20260830-056）。 */
@RestController
@RequestMapping("/api/architecture")
public class PlanController {
    private static final Logger log = LoggerFactory.getLogger(PlanController.class);
    private static final String VIEW_AUTHORITY = "hasAnyAuthority('architecture:plan:view',"
            + "'architecture:plan:manage','architecture:view','architecture:manage')";
    private static final String MANAGE_AUTHORITY = "hasAnyAuthority('architecture:plan:manage',"
            + "'architecture:manage')";
    private static final String ADMIN_AUTHORITY = "architecture:manage";

    private final PlanGenerationService generationService;
    private final PlanExecutionService executionService;
    private final PlanDependencyService dependencyService;
    private final PlanBlockService blockService;
    private final PlanTimeService timeService;
    private final PlanWorkOrderService workOrderService;
    private final PlanQueryService queryService;
    private final PlanEngine engine;
    private final com.ccb.architecture.service.ArchitectureOptionsService optionsService;
    private final SystemOperationAudit operationAudit;

    public PlanController(PlanGenerationService generationService, PlanExecutionService executionService,
                          PlanDependencyService dependencyService, PlanBlockService blockService,
                          PlanTimeService timeService, PlanWorkOrderService workOrderService,
                          PlanQueryService queryService, PlanEngine engine,
                          com.ccb.architecture.service.ArchitectureOptionsService optionsService,
                          SystemOperationAudit operationAudit) {
        this.generationService = generationService;
        this.executionService = executionService;
        this.dependencyService = dependencyService;
        this.blockService = blockService;
        this.timeService = timeService;
        this.workOrderService = workOrderService;
        this.queryService = queryService;
        this.engine = engine;
        this.optionsService = optionsService;
        this.operationAudit = operationAudit;
    }

    // ---------- 计划查询 ----------

    @GetMapping("/plan-options/users")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<com.ccb.common.api.PageResult<com.ccb.architecture.model.UserOption>> planUserOptions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser actor) {
        return success(optionsService.users(actor, new com.ccb.common.api.PageQuery(page, size), keyword));
    }

    @GetMapping("/plans")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PageResult<PlanRowView>> listPlans(
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(defaultValue = "false") boolean blocked,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "false") boolean waived,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser actor) {
        PageResult<PlanQueryService.PlanRow> result = queryService.list(actor,
                new PlanQueryService.PlanFilter(environmentId, status, ownerUserId, blocked, overdue,
                        waived, keyword, targetType, targetId), page, size);
        return success(new PageResult<>(result.records().stream()
                .map(row -> new PlanRowView(row.id(), row.planNo(), row.name(), row.environmentCode(),
                        row.environmentName(), row.status(), row.progress(), row.taskCount(),
                        row.hasBlocked(), row.hasOverdue(), row.hasWaived(), row.plannedEnd(),
                        row.planOwnerUserId()))
                .toList(), result.total(), result.page(), result.size()));
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PlanDetailView> planDetail(@PathVariable long id,
                                                  @AuthenticationPrincipal AuthUser actor) {
        return success(toDetailView(queryService.detail(actor, id)));
    }

    @GetMapping("/plans/{id}/dashboard")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PlanQueryService.DashboardView> dashboard(@PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        return success(queryService.dashboard(actor, id));
    }

    @GetMapping("/plans/{id}/timeline")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PlanQueryService.TimelineView> timeline(@PathVariable long id,
                                                               @AuthenticationPrincipal AuthUser actor) {
        return success(queryService.timeline(actor, id));
    }

    @GetMapping("/plans/{id}/report")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PlanQueryService.ReportView> report(@PathVariable long id,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return success(queryService.report(actor, id));
    }

    // ---------- 创建与调整 ----------

    @PostMapping("/plans")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanView> createPlan(@RequestBody CreatePlanRequest request,
                                            @AuthenticationPrincipal AuthUser actor) {
        Plan plan = audited(actor, "architecture.plan.create", "POST", "/api/architecture/plans",
                () -> generationService.createPlan(actor, new CreatePlanCommand(
                        request.environmentId(), request.templateId(), request.name(),
                        request.planOwnerUserId(), request.physicalSubsystemIds(),
                        request.deploymentUnitIds(), request.participantUserIds(),
                        request.plannedStart(), request.plannedEnd())));
        return success(toPlanView(plan));
    }

    @PostMapping("/plans/{id}/cancel")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanView> cancelPlan(@PathVariable long id,
                                            @RequestBody ReasonRequest request,
                                            @AuthenticationPrincipal AuthUser actor,
                                            Authentication authentication) {
        Plan plan = audited(actor, "architecture.plan.cancel", "POST",
                "/api/architecture/plans/" + id + "/cancel",
                () -> executionService.cancelPlan(actor, id, request == null ? null : request.reason(),
                        isAdmin(authentication)));
        return success(toPlanView(plan));
    }

    @PostMapping("/plans/{id}/restore")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanView> restorePlan(@PathVariable long id,
                                             @RequestBody ReasonRequest request,
                                             @AuthenticationPrincipal AuthUser actor,
                                             Authentication authentication) {
        Plan plan = audited(actor, "architecture.plan.restore", "POST",
                "/api/architecture/plans/" + id + "/restore",
                () -> executionService.restorePlan(actor, id, request == null ? null : request.reason(),
                        isAdmin(authentication)));
        return success(toPlanView(plan));
    }

    @PostMapping("/plans/{id}/targets")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanView> addTargets(@PathVariable long id,
                                            @RequestBody AddTargetRequest request,
                                            @AuthenticationPrincipal AuthUser actor,
                                            Authentication authentication) {
        Plan plan = audited(actor, "architecture.plan.target.add", "POST",
                "/api/architecture/plans/" + id + "/targets",
                () -> generationService.addTargets(actor, id, new AddTargetCommand(
                        request.physicalSubsystemIds(), request.deploymentUnitIds(),
                        request.reason()), isAdmin(authentication)));
        return success(toPlanView(plan));
    }

    @PostMapping("/plans/{id}/targets/{targetId}/remove")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanView> removeTarget(@PathVariable long id, @PathVariable long targetId,
                                              @RequestBody ReasonRequest request,
                                              @AuthenticationPrincipal AuthUser actor,
                                              Authentication authentication) {
        Plan plan = audited(actor, "architecture.plan.target.remove", "POST",
                "/api/architecture/plans/" + id + "/targets/" + targetId + "/remove",
                () -> generationService.removeTarget(actor, id, targetId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toPlanView(plan));
    }

    @PostMapping("/plans/{id}/stages")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<StageView> addStage(@PathVariable long id, @RequestBody AddStageRequest request,
                                           @AuthenticationPrincipal AuthUser actor,
                                           Authentication authentication) {
        Stage stage = audited(actor, "architecture.plan.stage.add", "POST",
                "/api/architecture/plans/" + id + "/stages",
                () -> generationService.addStage(actor, id, new AddStageCommand(request.name(),
                        request.ownerUserId(), request.plannedStart(), request.plannedEnd()),
                        isAdmin(authentication)));
        return success(toStageView(stage));
    }

    @PostMapping("/plans/{id}/tasks")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskView> addTask(@PathVariable long id, @RequestBody AddTaskRequest request,
                                         @AuthenticationPrincipal AuthUser actor,
                                         Authentication authentication) {
        Task task = audited(actor, "architecture.plan.task.add", "POST",
                "/api/architecture/plans/" + id + "/tasks",
                () -> generationService.addTask(actor, id,
                        new AddTaskCommand(request.stageId(), request.name(), request.targetId(),
                                request.ownerUserId(), request.participantUserIds(),
                                request.checkItemNames(), request.plannedStart(), request.plannedEnd()),
                        isAdmin(authentication)));
        return success(toTaskView(task));
    }

    @PostMapping("/tasks/{taskId}/check-items")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<CheckItemView> addCheckItem(@PathVariable long taskId,
                                                   @RequestBody CheckItemRequest request,
                                                   @AuthenticationPrincipal AuthUser actor,
                                                   Authentication authentication) {
        CheckItem item = audited(actor, "architecture.plan.check-item.add", "POST",
                "/api/architecture/tasks/" + taskId + "/check-items",
                () -> generationService.addCheckItem(actor, taskId,
                        new AddCheckItemCommand(request == null ? null : request.name(),
                                request == null ? null : request.guide()),
                        isAdmin(authentication)));
        return success(toCheckItemView(item));
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> deleteTask(@PathVariable long taskId, @RequestBody ReasonRequest request,
                                        @AuthenticationPrincipal AuthUser actor,
                                        Authentication authentication) {
        audited(actor, "architecture.plan.task.delete", "DELETE", "/api/architecture/tasks/" + taskId,
                () -> {
                    generationService.deleteTask(actor, taskId,
                            request == null ? null : request.reason(), isAdmin(authentication));
                    return null;
                });
        return success(null);
    }

    @DeleteMapping("/check-items/{checkItemId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> deleteCheckItem(@PathVariable long checkItemId,
                                             @RequestBody ReasonRequest request,
                                             @AuthenticationPrincipal AuthUser actor,
                                             Authentication authentication) {
        audited(actor, "architecture.plan.check-item.delete", "DELETE",
                "/api/architecture/check-items/" + checkItemId,
                () -> {
                    generationService.deleteCheckItem(actor, checkItemId,
                            request == null ? null : request.reason(), isAdmin(authentication));
                    return null;
                });
        return success(null);
    }

    // ---------- 执行与豁免 ----------

    @PostMapping("/tasks/{taskId}/start")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskView> startTask(@PathVariable long taskId,
                                           @AuthenticationPrincipal AuthUser actor,
                                           Authentication authentication) {
        Task task = audited(actor, "architecture.plan.task.start", "POST",
                "/api/architecture/tasks/" + taskId + "/start",
                () -> executionService.startTask(actor, taskId, isAdmin(authentication)));
        return success(toTaskView(task));
    }

    @PostMapping("/check-items/{checkItemId}/complete")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<CheckItemView> completeCheckItem(@PathVariable long checkItemId,
                                                        @RequestBody(required = false) CheckItemRequest request,
                                                        @AuthenticationPrincipal AuthUser actor,
                                                        Authentication authentication) {
        CheckItem item = audited(actor, "architecture.plan.check-item.complete", "POST",
                "/api/architecture/check-items/" + checkItemId + "/complete",
                () -> executionService.completeCheckItem(actor, checkItemId,
                        request == null ? null : request.remark(), isAdmin(authentication)));
        return success(toCheckItemView(item));
    }

    @PostMapping("/check-items/{checkItemId}/reopen")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<CheckItemView> reopenCheckItem(@PathVariable long checkItemId,
                                                      @RequestBody ReasonRequest request,
                                                      @AuthenticationPrincipal AuthUser actor,
                                                      Authentication authentication) {
        CheckItem item = audited(actor, "architecture.plan.check-item.reopen", "POST",
                "/api/architecture/check-items/" + checkItemId + "/reopen",
                () -> executionService.reopenCheckItem(actor, checkItemId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toCheckItemView(item));
    }

    @PostMapping("/check-items/{checkItemId}/cancel")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<CheckItemView> cancelCheckItem(@PathVariable long checkItemId,
                                                      @RequestBody ReasonRequest request,
                                                      @AuthenticationPrincipal AuthUser actor,
                                                      Authentication authentication) {
        CheckItem item = audited(actor, "architecture.plan.check-item.cancel", "POST",
                "/api/architecture/check-items/" + checkItemId + "/cancel",
                () -> executionService.cancelCheckItem(actor, checkItemId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toCheckItemView(item));
    }

    @PostMapping("/check-items/{checkItemId}/restore")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<CheckItemView> restoreCheckItem(@PathVariable long checkItemId,
                                                       @RequestBody ReasonRequest request,
                                                       @AuthenticationPrincipal AuthUser actor,
                                                       Authentication authentication) {
        CheckItem item = audited(actor, "architecture.plan.check-item.restore", "POST",
                "/api/architecture/check-items/" + checkItemId + "/restore",
                () -> executionService.restoreCheckItem(actor, checkItemId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toCheckItemView(item));
    }

    @PostMapping("/check-items/{checkItemId}/suggest-cancel")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<SuggestionView> suggestCancel(@PathVariable long checkItemId,
                                                     @RequestBody ReasonRequest request,
                                                     @AuthenticationPrincipal AuthUser actor) {
        CancelSuggestion suggestion = audited(actor, "architecture.plan.check-item.suggest-cancel",
                "POST", "/api/architecture/check-items/" + checkItemId + "/suggest-cancel",
                () -> executionService.suggestCancelCheckItem(actor, checkItemId,
                        request == null ? null : request.reason()));
        return success(toSuggestionView(suggestion));
    }

    @PostMapping("/suggestions/{suggestionId}/accept")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<CheckItemView> acceptSuggestion(@PathVariable long suggestionId,
                                                       @RequestBody ReasonRequest request,
                                                       @AuthenticationPrincipal AuthUser actor,
                                                       Authentication authentication) {
        CheckItem item = audited(actor, "architecture.plan.suggestion.accept", "POST",
                "/api/architecture/suggestions/" + suggestionId + "/accept",
                () -> executionService.acceptSuggestion(actor, suggestionId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toCheckItemView(item));
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<SuggestionView> rejectSuggestion(@PathVariable long suggestionId,
                                                        @RequestBody ReasonRequest request,
                                                        @AuthenticationPrincipal AuthUser actor,
                                                        Authentication authentication) {
        CancelSuggestion suggestion = audited(actor, "architecture.plan.suggestion.reject", "POST",
                "/api/architecture/suggestions/" + suggestionId + "/reject",
                () -> executionService.rejectSuggestion(actor, suggestionId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toSuggestionView(suggestion));
    }

    @GetMapping("/plans/{id}/suggestions")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<SuggestionView>> suggestions(@PathVariable long id,
                                                         @AuthenticationPrincipal AuthUser actor) {
        return success(queryService.pendingSuggestions(actor, id).stream()
                .map(PlanController::toSuggestionView).toList());
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskView> cancelTask(@PathVariable long taskId, @RequestBody ReasonRequest request,
                                            @AuthenticationPrincipal AuthUser actor,
                                            Authentication authentication) {
        Task task = audited(actor, "architecture.plan.task.cancel", "POST",
                "/api/architecture/tasks/" + taskId + "/cancel",
                () -> executionService.cancelTask(actor, taskId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toTaskView(task));
    }

    @PostMapping("/tasks/{taskId}/restore")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskView> restoreTask(@PathVariable long taskId, @RequestBody ReasonRequest request,
                                             @AuthenticationPrincipal AuthUser actor,
                                             Authentication authentication) {
        Task task = audited(actor, "architecture.plan.task.restore", "POST",
                "/api/architecture/tasks/" + taskId + "/restore",
                () -> executionService.restoreTask(actor, taskId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toTaskView(task));
    }

    @PostMapping("/stages/{stageId}/cancel")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<StageView> cancelStage(@PathVariable long stageId,
                                              @RequestBody ReasonRequest request,
                                              @AuthenticationPrincipal AuthUser actor,
                                              Authentication authentication) {
        Stage stage = audited(actor, "architecture.plan.stage.cancel", "POST",
                "/api/architecture/stages/" + stageId + "/cancel",
                () -> executionService.cancelStage(actor, stageId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toStageView(stage));
    }

    @PostMapping("/stages/{stageId}/restore")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<StageView> restoreStage(@PathVariable long stageId,
                                               @RequestBody ReasonRequest request,
                                               @AuthenticationPrincipal AuthUser actor,
                                               Authentication authentication) {
        Stage stage = audited(actor, "architecture.plan.stage.restore", "POST",
                "/api/architecture/stages/" + stageId + "/restore",
                () -> executionService.restoreStage(actor, stageId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toStageView(stage));
    }

    // ---------- 依赖 ----------

    @PostMapping("/tasks/{taskId}/dependencies")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<List<DependencyView>> setDependencies(
            @PathVariable long taskId, @RequestBody DependencyRequest request,
            @AuthenticationPrincipal AuthUser actor, Authentication authentication) {
        List<com.ccb.architecture.plan.model.PlanModels.Dependency> dependencies =
                audited(actor, "architecture.plan.dependency.change", "POST",
                        "/api/architecture/tasks/" + taskId + "/dependencies",
                        () -> dependencyService.setDependencies(actor, taskId,
                                request == null ? null : request.predecessorTaskIds(),
                                request == null ? null : request.reason(), isAdmin(authentication)));
        return success(dependencies.stream().map(dep -> new DependencyView(dep.id(), dep.taskId(),
                dep.predecessorId(), dep.removed())).toList());
    }

    @DeleteMapping("/dependencies/{dependencyId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> removeDependency(@PathVariable long dependencyId,
                                              @RequestBody ReasonRequest request,
                                              @AuthenticationPrincipal AuthUser actor,
                                              Authentication authentication) {
        audited(actor, "architecture.plan.dependency.remove", "DELETE",
                "/api/architecture/dependencies/" + dependencyId,
                () -> {
                    dependencyService.removeDependency(actor, dependencyId,
                            request == null ? null : request.reason(), isAdmin(authentication));
                    return null;
                });
        return success(null);
    }

    // ---------- 阻塞 ----------

    @PostMapping("/tasks/{taskId}/blocks")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<BlockView> addBlock(@PathVariable long taskId, @RequestBody BlockRequest request,
                                           @AuthenticationPrincipal AuthUser actor,
                                           Authentication authentication) {
        com.ccb.architecture.plan.model.PlanModels.Block block = audited(actor,
                "architecture.plan.block.add", "POST", "/api/architecture/tasks/" + taskId + "/blocks",
                () -> blockService.addBlock(actor, taskId, new BlockCommand(request.description(),
                        request.impact(), request.ownerUserId(), request.expectedResolveAt()),
                        isAdmin(authentication)));
        return success(toBlockView(block));
    }

    @PutMapping("/blocks/{blockId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<BlockView> updateBlock(@PathVariable long blockId, @RequestBody BlockRequest request,
                                              @AuthenticationPrincipal AuthUser actor,
                                              Authentication authentication) {
        com.ccb.architecture.plan.model.PlanModels.Block block = audited(actor,
                "architecture.plan.block.update", "PUT", "/api/architecture/blocks/" + blockId,
                () -> blockService.updateBlock(actor, blockId, new BlockCommand(request.description(),
                        request.impact(), request.ownerUserId(), request.expectedResolveAt()),
                        isAdmin(authentication)));
        return success(toBlockView(block));
    }

    @PostMapping("/blocks/{blockId}/resolve")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<BlockView> resolveBlock(@PathVariable long blockId,
                                               @RequestBody ReasonRequest request,
                                               @AuthenticationPrincipal AuthUser actor,
                                               Authentication authentication) {
        com.ccb.architecture.plan.model.PlanModels.Block block = audited(actor,
                "architecture.plan.block.resolve", "POST",
                "/api/architecture/blocks/" + blockId + "/resolve",
                () -> blockService.resolveBlock(actor, blockId,
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(toBlockView(block));
    }

    // ---------- 时间 ----------

    @PutMapping("/plans/{id}/schedule")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanView> updatePlanSchedule(@PathVariable long id,
                                                    @RequestBody ScheduleRequest request,
                                                    @AuthenticationPrincipal AuthUser actor,
                                                    Authentication authentication) {
        Plan plan = audited(actor, "architecture.plan.schedule.update", "PUT",
                "/api/architecture/plans/" + id + "/schedule",
                () -> timeService.updatePlanSchedule(actor, id,
                        new ScheduleCommand(request.plannedStart(), request.plannedEnd(),
                                request.reason()), isAdmin(authentication)));
        return success(toPlanView(plan));
    }

    @PutMapping("/stages/{stageId}/schedule")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<StageView> updateStageSchedule(@PathVariable long stageId,
                                                      @RequestBody ScheduleRequest request,
                                                      @AuthenticationPrincipal AuthUser actor,
                                                      Authentication authentication) {
        Stage stage = audited(actor, "architecture.plan.stage.schedule.update", "PUT",
                "/api/architecture/stages/" + stageId + "/schedule",
                () -> timeService.updateStageSchedule(actor, stageId,
                        new ScheduleCommand(request.plannedStart(), request.plannedEnd(),
                                request.reason()), isAdmin(authentication)));
        return success(toStageView(stage));
    }

    @PutMapping("/tasks/{taskId}/schedule")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskView> updateTaskSchedule(@PathVariable long taskId,
                                                    @RequestBody ScheduleRequest request,
                                                    @AuthenticationPrincipal AuthUser actor,
                                                    Authentication authentication) {
        Task task = audited(actor, "architecture.plan.task.schedule.update", "PUT",
                "/api/architecture/tasks/" + taskId + "/schedule",
                () -> timeService.updateTaskSchedule(actor, taskId,
                        new ScheduleCommand(request.plannedStart(), request.plannedEnd(),
                                request.reason()), isAdmin(authentication)));
        return success(toTaskView(task));
    }

    @PostMapping("/events/{eventId}/correct")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> correctEvent(@PathVariable long eventId, @RequestBody CorrectEventRequest request,
                                          @AuthenticationPrincipal AuthUser actor,
                                          Authentication authentication) {
        audited(actor, "architecture.plan.event.correct", "POST",
                "/api/architecture/events/" + eventId + "/correct",
                () -> {
                    timeService.correctEvent(actor, eventId, request.newOccurredAt(),
                            request.reason(), isAdmin(authentication));
                    return null;
                });
        return success(null);
    }

    @GetMapping("/plans/{id}/events")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<EventView>> events(@PathVariable long id,
                                               @AuthenticationPrincipal AuthUser actor) {
        return success(timeService.listPlanEvents(actor, id).stream()
                .map(event -> new EventView(event.id(), event.objectType(), event.objectId(),
                        event.eventType().name(), event.occurredAt(), event.operatorUserId(),
                        event.reason(), event.correctOfEventId()))
                .toList());
    }

    // ---------- 工单关联 ----------

    @PostMapping("/tasks/{taskId}/work-orders")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<List<WorkOrderLinkView>> attachWorkOrders(
            @PathVariable long taskId, @RequestBody WorkOrderRequest request,
            @AuthenticationPrincipal AuthUser actor, Authentication authentication) {
        List<com.ccb.architecture.plan.model.PlanModels.TaskWorkOrder> links = audited(actor,
                "architecture.plan.work-order.attach", "POST",
                "/api/architecture/tasks/" + taskId + "/work-orders",
                () -> workOrderService.attach(actor, taskId, new WorkOrderCommand(
                        request.workOrderType(), request.workOrderIds(), null),
                        request == null ? null : request.reason(), isAdmin(authentication)));
        return success(links.stream().map(link -> new WorkOrderLinkView(link.id(), link.taskId(),
                link.workOrderType(), link.workOrderId(), link.source().name())).toList());
    }

    @DeleteMapping("/work-orders/{workOrderRelationId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> detachWorkOrder(@PathVariable long workOrderRelationId,
                                             @RequestBody ReasonRequest request,
                                             @AuthenticationPrincipal AuthUser actor,
                                             Authentication authentication) {
        audited(actor, "architecture.plan.work-order.detach", "DELETE",
                "/api/architecture/work-orders/" + workOrderRelationId,
                () -> {
                    workOrderService.remove(actor, workOrderRelationId,
                            request == null ? null : request.reason(), isAdmin(authentication));
                    return null;
                });
        return success(null);
    }

    @GetMapping("/tasks/{taskId}/work-orders")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<WorkOrderLinkView>> workOrders(@PathVariable long taskId,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return success(workOrderService.list(actor, taskId).stream()
                .map(link -> new WorkOrderLinkView(link.id(), link.taskId(), link.workOrderType(),
                        link.workOrderId(), link.source().name())).toList());
    }

    // ---------- 视图映射 ----------

    private PlanDetailView toDetailView(PlanQueryService.PlanDetailView detail) {
        return new PlanDetailView(toPlanView(detail.plan()), detail.environmentCode(),
                detail.environmentName(), detail.targets(), detail.stages().stream()
                        .map(stage -> new StageDetailView(stage.id(), stage.stageNo(), stage.name(),
                                stage.status(), stage.cancelled(), stage.cancelReason(),
                                stage.ownerUserId(), stage.plannedStart(), stage.plannedEnd(),
                                stage.actualStart(), stage.actualEnd(), stage.progress(),
                                stage.hasWaived(), stage.tasks().stream()
                                        .map(this::toTaskDetailView).toList()))
                        .toList(), detail.progress(), detail.hasBlocked(), detail.hasOverdue(),
                detail.hasWaived(), detail.uncompletable(), detail.pendingSuggestionCount(),
                detail.stageDependencies().stream().map(pair -> new long[]{pair[0], pair[1]}).toList(),
                detail.events().stream().map(event -> new EventView(event.id(), event.objectType(),
                        event.objectId(), event.eventType(), event.occurredAt(), event.operatorUserId(),
                        event.reason(), event.correctOfEventId())).toList());
    }

    private TaskDetailView toTaskDetailView(PlanQueryService.TaskView task) {
        return new TaskDetailView(task.id(), task.stageId(), task.taskNo(), task.name(),
                task.targetName(), task.targetId(), task.targetType(), task.status(), task.progress(),
                task.waivedAll(), task.overdue(), task.hasBlocked(), task.hasOpenWorkOrder(),
                task.ownerUserId(), task.plannedStart(), task.plannedEnd(), task.actualStart(),
                task.actualEnd(), task.cancelled(), task.cancelReason(), task.participantUserIds(),
                task.dependencies().stream().map(dep -> new DependencyView(dep.id(), dep.taskId(),
                        dep.predecessorId(), dep.removed())).toList(),
                task.blocks().stream().map(PlanController::toBlockView).toList(),
                task.workOrders().stream().map(link -> new WorkOrderLinkView(link.id(), link.taskId(),
                        link.workOrderType(), link.workOrderId(), link.source().name())).toList(),
                task.checkItems().stream()
                        .map(item -> new CheckItemView(item.id(), item.name(), item.guide(), item.status(),
                                item.remark(), item.completedBy(), item.completedAt(), item.cancelled(),
                                item.cancelReason(), item.cancelledBy(), item.cancelledAt()))
                        .toList(),
                task.events().stream().map(event -> new EventView(event.id(), event.objectType(),
                        event.objectId(), event.eventType(), event.occurredAt(), event.operatorUserId(),
                        event.reason(), event.correctOfEventId())).toList());
    }

    private static BlockView toBlockView(com.ccb.architecture.plan.model.PlanModels.Block block) {
        return new BlockView(block.id(), block.taskId(), block.description(), block.impact(),
                block.ownerUserId(), block.expectedResolveAt(), block.resolved(), block.resolvedNote(),
                block.resolvedBy(), block.resolvedAt(), block.createdBy());
    }

    private static PlanView toPlanView(Plan plan) {
        return new PlanView(plan.id(), plan.planNo(), plan.name(), plan.environmentId(),
                plan.status(), plan.templateId(), plan.templateVersionNo(), plan.planOwnerUserId(),
                plan.plannedStart(), plan.plannedEnd(), plan.actualStart(), plan.actualEnd(),
                plan.cancelled(), plan.cancelReason(), plan.rowVersion());
    }

    private static StageView toStageView(Stage stage) {
        return new StageView(stage.id(), stage.planId(), stage.stageNo(), stage.name(),
                stage.status(), stage.cancelled(), stage.cancelReason(), stage.ownerUserId(),
                stage.plannedStart(), stage.plannedEnd(), stage.actualStart(), stage.actualEnd());
    }

    private static TaskView toTaskView(Task task) {
        return new TaskView(task.id(), task.planId(), task.stageId(), task.taskNo(), task.name(),
                task.targetType() == null ? null : task.targetType().name(), task.targetId(),
                task.targetName(), task.status(), task.waivedAll(), task.cancelled(),
                task.cancelReason(), task.ownerUserId(), task.plannedStart(), task.plannedEnd(),
                task.actualStart(), task.actualEnd());
    }

    private static CheckItemView toCheckItemView(CheckItem item) {
        return new CheckItemView(item.id(), item.name(), item.guide(), item.status().name(), item.remark(),
                item.completedBy(), item.completedAt(), item.cancelled(), item.cancelReason(),
                item.cancelledBy(), item.cancelledAt());
    }

    private static SuggestionView toSuggestionView(CancelSuggestion suggestion) {
        return new SuggestionView(suggestion.id(), suggestion.checkItemId(), suggestion.reason(),
                suggestion.submitterUserId(), suggestion.status(), suggestion.handledByUserId(),
                suggestion.handledAt(), suggestion.handlerNote());
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    private static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    private <T> T audited(AuthUser actor, String operationCode, String method, String path,
                          Supplier<T> action) {
        try {
            T result = action.get();
            recordAudit(actor, operationCode, method, path, null, TraceId.getOrCreate());
            return result;
        } catch (BusinessException failure) {
            recordAudit(actor, operationCode, method, path,
                    failure.getMessage() == null ? "搭建计划操作失败" : failure.getMessage(),
                    TraceId.getOrCreate());
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "搭建计划操作失败", TraceId.getOrCreate());
            throw failure;
        }
    }

    private void recordAudit(AuthUser actor, String operationCode, String method, String path,
                             String errorMessage, String traceId) {
        try {
            SystemOperationAuditCommand command = new SystemOperationAuditCommand(
                    actor, operationCode, method, path, errorMessage, traceId);
            if (errorMessage == null) {
                operationAudit.recordSuccess(command);
            } else {
                operationAudit.recordFailure(command);
            }
        } catch (RuntimeException auditFailure) {
            log.warn("搭建计划审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    // ---------- 响应 DTO ----------

    public record PlanView(long id, String planNo, String name, long environmentId, PlanStatus status,
                           long templateId, int templateVersionNo, long planOwnerUserId,
                           LocalDateTime plannedStart, LocalDateTime plannedEnd,
                           LocalDateTime actualStart, LocalDateTime actualEnd, boolean cancelled,
                           String cancelReason, long rowVersion) {
    }

    public record PlanRowView(long id, String planNo, String name, String environmentCode,
                              String environmentName, PlanStatus status, Long progress, long taskCount,
                              boolean hasBlocked, boolean hasOverdue, boolean hasWaived,
                              LocalDateTime plannedEnd, long planOwnerUserId) {
    }

    public record StageView(long id, long planId, int stageNo, String name, PlanStatus status,
                            boolean cancelled, String cancelReason, long ownerUserId,
                            LocalDateTime plannedStart, LocalDateTime plannedEnd,
                            LocalDateTime actualStart, LocalDateTime actualEnd) {
    }

    public record TaskView(long id, long planId, long stageId, int taskNo, String name,
                           String targetType, Long targetId, String targetName, TaskStatus status,
                           boolean waivedAll, boolean cancelled, String cancelReason, long ownerUserId,
                           LocalDateTime plannedStart, LocalDateTime plannedEnd,
                           LocalDateTime actualStart, LocalDateTime actualEnd) {
    }

    public record CheckItemView(long id, String name, String guide, String status, String remark,
                                Long completedBy, LocalDateTime completedAt, boolean cancelled,
                                String cancelReason, Long cancelledBy, LocalDateTime cancelledAt) {
    }

    public record StageDetailView(long id, int stageNo, String name, String status, boolean cancelled,
                                  String cancelReason, long ownerUserId, LocalDateTime plannedStart,
                                  LocalDateTime plannedEnd, LocalDateTime actualStart,
                                  LocalDateTime actualEnd, Long progress, boolean hasWaived,
                                  List<TaskDetailView> tasks) {
    }

    public record TaskDetailView(long id, long stageId, int taskNo, String name, String targetName,
                                 Long targetId, String targetType, String status, Long progress,
                                 boolean waivedAll, boolean overdue, boolean hasBlocked,
                                 boolean hasOpenWorkOrder, long ownerUserId,
                                 LocalDateTime plannedStart, LocalDateTime plannedEnd,
                                 LocalDateTime actualStart, LocalDateTime actualEnd, boolean cancelled,
                                 String cancelReason, List<Long> participantUserIds,
                                 List<DependencyView> dependencies, List<BlockView> blocks,
                                 List<WorkOrderLinkView> workOrders, List<CheckItemView> checkItems,
                                 List<EventView> events) {
    }

    public record PlanDetailView(PlanView plan, String environmentCode, String environmentName,
                                 List<PlanQueryService.TargetView> targets,
                                 List<StageDetailView> stages, Long progress, boolean hasBlocked,
                                 boolean hasOverdue, boolean hasWaived, boolean uncompletable,
                                 long pendingSuggestionCount, List<long[]> stageDependencies,
                                 List<EventView> events) {
    }

    public record DependencyView(long id, long taskId, long predecessorId, boolean removed) {
    }

    public record BlockView(long id, long taskId, String description, String impact, long ownerUserId,
                            LocalDateTime expectedResolveAt, boolean resolved, String resolvedNote,
                            Long resolvedBy, LocalDateTime resolvedAt, long createdBy) {
    }

    public record WorkOrderLinkView(long id, long taskId, WorkOrderType workOrderType, long workOrderId,
                                    String source) {
    }

    public record EventView(long id, String objectType, long objectId, String eventType,
                            LocalDateTime occurredAt, long operatorUserId, String reason,
                            Long correctOfEventId) {
    }

    public record SuggestionView(long id, long checkItemId, String reason, long submitterUserId,
                                 String status, Long handledByUserId, LocalDateTime handledAt,
                                 String handlerNote) {
    }

    // ---------- 请求 DTO ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatePlanRequest(Long environmentId, Long templateId, String name, Long planOwnerUserId,
                                    List<Long> physicalSubsystemIds, List<Long> deploymentUnitIds,
                                    List<Long> participantUserIds, LocalDateTime plannedStart,
                                    LocalDateTime plannedEnd) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReasonRequest(String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddTargetRequest(List<Long> physicalSubsystemIds, List<Long> deploymentUnitIds,
                                   String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddStageRequest(String name, Long ownerUserId, LocalDateTime plannedStart,
                                  LocalDateTime plannedEnd) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddTaskRequest(Long stageId, String name, Long targetId, Long ownerUserId,
                                 List<Long> participantUserIds, List<String> checkItemNames,
                                 LocalDateTime plannedStart, LocalDateTime plannedEnd) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckItemRequest(String name, String guide, String remark) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DependencyRequest(List<Long> predecessorTaskIds, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlockRequest(String description, String impact, Long ownerUserId,
                               LocalDateTime expectedResolveAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleRequest(LocalDateTime plannedStart, LocalDateTime plannedEnd, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorrectEventRequest(LocalDateTime newOccurredAt, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkOrderRequest(WorkOrderType workOrderType, List<Long> workOrderIds, String reason) {
    }
}
