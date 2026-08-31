package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.AddCheckItemCommand;
import com.ccb.architecture.plan.model.PlanModels.AddStageCommand;
import com.ccb.architecture.plan.model.PlanModels.AddTargetCommand;
import com.ccb.architecture.plan.model.PlanModels.AddTaskCommand;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.PlanTarget;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.TargetType;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.CreatePlanCommand;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.model.PlanTemplateModels.CheckItemDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplateDetail;
import com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotStage;
import com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.plan.persistence.PlanStore.EnvironmentRef;
import com.ccb.architecture.plan.persistence.PlanStore.TargetRef;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.notification.NotificationLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 计划创建、任务生成、运行中调整与维度管理查询（REQ-20260830-056）。 */
@Service
public class PlanGenerationService {
    private final PlanStore store;
    private final PlanTemplateService templateService;
    private final PlanEngine engine;
    private final PlanNotificationService notificationService;
    private final SystemReferenceQuery referenceQuery;
    private final ObjectMapper objectMapper;
    private final LongSupplier idSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanGenerationService(PlanStore store, PlanTemplateService templateService, PlanEngine engine,
                                 PlanNotificationService notificationService,
                                 SystemReferenceQuery referenceQuery, ObjectMapper objectMapper) {
        this(store, templateService, engine, notificationService, referenceQuery, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    PlanGenerationService(PlanStore store, PlanTemplateService templateService, PlanEngine engine,
                          PlanNotificationService notificationService,
                          SystemReferenceQuery referenceQuery, ObjectMapper objectMapper,
                          LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "计划存储不能为空");
        this.templateService = Objects.requireNonNull(templateService, "模板服务不能为空");
        this.engine = Objects.requireNonNull(engine, "计算引擎不能为空");
        this.notificationService = Objects.requireNonNull(notificationService, "通知服务不能为空");
        this.referenceQuery = Objects.requireNonNull(referenceQuery, "用户查询不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 能力不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    /** 从已发布模板创建计划：固化模板版本、目标快照与结构快照，按维度生成任务与检查项。 */
    @Transactional
    public Plan createPlan(AuthUser actor, CreatePlanCommand cmd) {
        String name = requireText(cmd == null ? null : cmd.name(), "计划名称", 300);
        EnvironmentRef environment = store.envReference(actor.tenantId(), cmd.environmentId())
                .orElseThrow(() -> new ArchitectureNotFoundException("具体环境不存在"));
        if (!"ACTIVE".equals(environment.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "具体环境已停用，不能创建搭建计划");
        }
        PlanTemplateDetail template = templateService.detailForGeneration(actor.tenantId(), cmd.templateId());
        List<Long> physicalIds = distinctIds(cmd.physicalSubsystemIds());
        List<Long> deploymentUnitIds = distinctIds(cmd.deploymentUnitIds());
        if (physicalIds.isEmpty() && deploymentUnitIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少选择一个目标（物理子系统或部署单元）");
        }
        List<TargetRef> physicals = store.listPhysicalSubsystemRefs(actor.tenantId(), physicalIds);
        List<TargetRef> units = store.listDeploymentUnitRefs(actor.tenantId(), deploymentUnitIds);
        if (physicals.size() != physicalIds.size() || units.size() != deploymentUnitIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的物理子系统或部署单元目标");
        }
        if (physicals.stream().anyMatch(t -> !"ACTIVE".equals(t.status()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在已停用的物理子系统目标");
        }
        if (units.stream().anyMatch(t -> !"ACTIVE".equals(t.status()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在已停用的部署单元目标");
        }
        requireUser(actor, cmd.planOwnerUserId(), "计划责任人");
        List<Long> participants = distinctIds(cmd.participantUserIds());
        for (Long participant : participants) {
            requireUser(actor, participant, "任务参与人");
        }
        long planId = nextId();
        Plan plan = new Plan(planId, "SP" + planId, name, cmd.environmentId(), PlanStatus.NOT_STARTED,
                cmd.templateId(), template.template().latestVersionNo(), cmd.planOwnerUserId(),
                cmd.plannedStart(), cmd.plannedEnd(), null, null, false, null, null, null, 0);
        store.insertPlan(actor.tenantId(), plan);
        // 目标快照
        Map<TargetType, List<TargetRef>> targetsByType = new LinkedHashMap<>();
        targetsByType.put(TargetType.PHYSICAL_SUBSYSTEM, physicals);
        targetsByType.put(TargetType.DEPLOYMENT_UNIT, units);
        for (Map.Entry<TargetType, List<TargetRef>> entry : targetsByType.entrySet()) {
            for (TargetRef ref : entry.getValue()) {
                store.insertTarget(actor.tenantId(), new PlanTarget(nextId(), planId, entry.getKey(),
                        ref.id(), ref.code(), ref.name(), false, null), null);
            }
        }
        // 结构生成
        generateFromSnapshot(actor, plan, template, physicals, units, participants, cmd.plannedStart(),
                cmd.plannedEnd());
        // 生成后立即重算：按依赖/阻塞/检查项推导任务与环节状态（如前置未完成 → WAITING_PRECEDING）
        engine.recompute(actor.tenantId(), planId, LocalDateTime.now());
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "PLAN", planId, "PLAN_CREATED",
                actor.id(), null, toJson(Map.of("environment", environment.name(),
                        "template", template.template().name(),
                        "templateVersion", template.template().latestVersionNo())), null);
        notificationService.notifyTaskAssigned(actor.tenantId(), plan.planNo(), plan.name(),
                List.of(cmd.planOwnerUserId()));
        return refreshPlan(actor, planId);
    }

    private void generateFromSnapshot(AuthUser actor, Plan plan, PlanTemplateDetail template,
                                      List<TargetRef> physicals, List<TargetRef> units,
                                      List<Long> participants, LocalDateTime plannedStart,
                                      LocalDateTime plannedEnd) {
        List<SnapshotStage> snapshot = templateService.parseSnapshot(
                template.versions().get(0).contentJson());
        Map<Long, Long> stageIdByTemplateStageId = new LinkedHashMap<>();
        Map<Long, List<TaskHandle>> tasksByTemplate = new LinkedHashMap<>();
        Map<Long, List<TaskHandle>> tasksByStage = new LinkedHashMap<>();
        int stageNo = 1;
        int snapshotIndex = 0;
        for (SnapshotStage stageSnapshot : snapshot) {
            long stageId = nextId();
            LocalDateTime stageStart = shiftDays(plannedStart, stageSnapshot.startOffsetDays() == null
                    ? 0 : stageSnapshot.startOffsetDays());
            LocalDateTime stageEnd = stageSnapshot.durationDays() == null ? null
                    : stageStart.plusDays(stageSnapshot.durationDays());
            store.insertStage(actor.tenantId(), new Stage(stageId, plan.id(), stageNo++,
                    stageSnapshot.stageName(), stageSnapshot.sortNo(), plan.planOwnerUserId(),
                    stageStart, stageEnd, null, null, PlanStatus.NOT_STARTED, false, null, null,
                    null, null));
            if (stageSnapshot.stageId() != null) {
                stageIdByTemplateStageId.put(stageSnapshot.stageId(), stageId);
            }
            snapshotIndex++;
            int taskNo = 1;
            for (SnapshotTask taskSnapshot : stageSnapshot.tasks()) {
                List<PlanTarget> targets = new ArrayList<>();
                switch (taskSnapshot.dimension()) {
                    case NONE -> targets.add(nullTarget(plan.id()));
                    case PHYSICAL_SUBSYSTEM -> physicals.forEach(ref ->
                            targets.add(new PlanTarget(-1L, plan.id(), TargetType.PHYSICAL_SUBSYSTEM,
                                    ref.id(), ref.code(), ref.name(), false, null)));
                    case DEPLOYMENT_UNIT -> units.forEach(ref ->
                            targets.add(new PlanTarget(-1L, plan.id(), TargetType.DEPLOYMENT_UNIT,
                                    ref.id(), ref.code(), ref.name(), false, null)));
                }
                for (PlanTarget target : targets) {
                    long taskId = nextId();
                    store.insertTask(actor.tenantId(), new Task(taskId, plan.id(), stageId, taskNo++,
                            taskSnapshot.name(),
                            target.targetType() == null ? TargetType.PHYSICAL_SUBSYSTEM
                                    : target.targetType(),
                            target.targetId() < 0 ? null : target.targetId(),
                            target.targetNo() == null ? null : target.targetNo(),
                            target.targetName() == null ? null : target.targetName(),
                            taskSnapshot.taskTemplateId(), taskSnapshot.taskTemplateVersionNo(),
                            taskSnapshot.dimension().name(), toJson(Map.of("name", taskSnapshot.name(),
                                    "checkItems", taskSnapshot.checkItems())),
                            plan.planOwnerUserId(), stageStart, stageEnd, null, null,
                            TaskStatus.NOT_STARTED, false, false, null, null, null, 0));
                    TaskHandle handle = new TaskHandle(taskId,
                            target.targetType() == null ? null : target.targetType(),
                            target.targetId() < 0 ? null : target.targetId());
                    tasksByTemplate.computeIfAbsent(taskSnapshot.taskTemplateId(), k -> new ArrayList<>())
                            .add(handle);
                    tasksByStage.computeIfAbsent(stageId, k -> new ArrayList<>()).add(handle);
                    int checkNo = 1;
                    for (CheckItemDraft checkItem : taskSnapshot.checkItems()) {
                        store.insertCheckItem(actor.tenantId(), new CheckItem(nextId(), taskId,
                                checkNo++, checkItem.name(), checkItem.sortNo(),
                                checkItem.guide(),
                                com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.PENDING,
                                null, null, null, false, null, null, null, 0, actor.id()));
                    }
                    for (Long participant : participants) {
                        store.insertParticipant(actor.tenantId(), nextId(), taskId, participant,
                                actor.id());
                    }
                }
            }
        }
        // 环节依赖（按模板环节 id 对齐生成后的环节）
        for (SnapshotStage stageSnapshot : snapshot) {
            Long stageId = stageSnapshot.stageId() == null ? null
                    : stageIdByTemplateStageId.get(stageSnapshot.stageId());
            if (stageId == null) {
                continue;
            }
            for (Long predecessorTemplateId : stageSnapshot.dependencyStageIds()) {
                Long predecessorId = stageIdByTemplateStageId.get(predecessorTemplateId);
                if (predecessorId != null && stageId != predecessorId) {
                    store.insertStageDependency(actor.tenantId(), nextId(), plan.id(), stageId,
                            predecessorId, actor.id());
                }
            }
        }
        // 任务模板依赖 → 目标对齐的任务依赖
        for (SnapshotStage stageSnapshot : snapshot) {
            for (SnapshotTask taskSnapshot : stageSnapshot.tasks()) {
                for (Long predecessorTemplateId : taskSnapshot.dependsOnTaskTemplateIds()) {
                    List<TaskHandle> successors = tasksByTemplate.getOrDefault(
                            taskSnapshot.taskTemplateId(), List.of());
                    List<TaskHandle> predecessors = tasksByTemplate.getOrDefault(
                            predecessorTemplateId, List.of());
                    for (TaskHandle successor : successors) {
                        for (TaskHandle predecessor : taskDependencyMatch(successor, predecessors)) {
                            store.insertDependency(actor.tenantId(), nextId(), successor.taskId(),
                                    predecessor.taskId(), actor.id());
                        }
                    }
                }
            }
        }
    }

    /** 记录任务生成句柄（目标），用于依赖映射。 */
    private record TaskHandle(long taskId, TargetType targetType, Long targetId) {
    }

    /** 任务模板依赖映射：同维度按目标对应；前置不展开时全部；后置不展开时依赖前置全部。 */
    private static List<TaskHandle> taskDependencyMatch(TaskHandle successor,
                                                        List<TaskHandle> predecessors) {
        if (predecessors.isEmpty()) {
            return List.of();
        }
        if (successor.targetType() == null) {
            return predecessors;
        }
        return predecessors.stream()
                .filter(pre -> pre.targetType() == null
                        || (pre.targetType() == successor.targetType()
                        && Objects.equals(pre.targetId(), successor.targetId())))
                .toList();
    }

    private static LocalDateTime shiftDays(LocalDateTime base, int days) {
        return base == null ? null : base.plusDays(days);
    }

    private PlanTarget nullTarget(long planId) {
        return new PlanTarget(-1L, planId, TargetType.PHYSICAL_SUBSYSTEM, -1L, null, null, false, null);
    }

    /** 增加计划目标（使用计划自身模板版本快照生成任务）。 */
    @Transactional
    public Plan addTargets(AuthUser actor, long planId, AddTargetCommand cmd, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        requireAdjustable(plan);
        String reason = requireText(cmd == null ? null : cmd.reason(), "增加目标原因", 1000);
        List<Long> physicalIds = distinctIds(cmd.physicalSubsystemIds());
        List<Long> deploymentUnitIds = distinctIds(cmd.deploymentUnitIds());
        if (physicalIds.isEmpty() && deploymentUnitIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少选择一个目标");
        }
        List<TargetRef> physicals = store.listPhysicalSubsystemRefs(actor.tenantId(), physicalIds);
        List<TargetRef> units = store.listDeploymentUnitRefs(actor.tenantId(), deploymentUnitIds);
        List<PlanTarget> targets = new ArrayList<>();
        Map<Long, TargetRef> physicalRefById = new LinkedHashMap<>();
        for (TargetRef ref : physicals) {
            if (store.findTarget(actor.tenantId(), planId, TargetType.PHYSICAL_SUBSYSTEM, ref.id())
                    .isEmpty()) {
                long targetId = nextId();
                store.insertTarget(actor.tenantId(), new PlanTarget(targetId, planId,
                        TargetType.PHYSICAL_SUBSYSTEM, ref.id(), ref.code(), ref.name(), false, null),
                        reason);
                targets.add(new PlanTarget(targetId, planId, TargetType.PHYSICAL_SUBSYSTEM, ref.id(),
                        ref.code(), ref.name(), false, null));
                physicalRefById.put(targetId, ref);
            }
        }
        Map<Long, TargetRef> unitRefById = new LinkedHashMap<>();
        for (TargetRef ref : units) {
            if (store.findTarget(actor.tenantId(), planId, TargetType.DEPLOYMENT_UNIT, ref.id())
                    .isEmpty()) {
                long targetId = nextId();
                store.insertTarget(actor.tenantId(), new PlanTarget(targetId, planId,
                        TargetType.DEPLOYMENT_UNIT, ref.id(), ref.code(), ref.name(), false, null),
                        reason);
                targets.add(new PlanTarget(targetId, planId, TargetType.DEPLOYMENT_UNIT, ref.id(),
                        ref.code(), ref.name(), false, null));
                unitRefById.put(targetId, ref);
            }
        }
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选目标均已存在于计划中");
        }
        // 使用计划自身模板版本快照生成新目标任务
        PlanTemplateDetail template = templateService.detailForGeneration(actor.tenantId(),
                plan.templateId());
        List<SnapshotStage> snapshot = templateService.parseSnapshot(
                template.versions().get(0).contentJson());
        List<Stage> stages = store.findStages(actor.tenantId(), planId);
        List<PlanTarget> allPhysical = store.findTargets(actor.tenantId(), planId, false).stream()
                .filter(t -> t.targetType() == TargetType.PHYSICAL_SUBSYSTEM).toList();
        List<PlanTarget> allUnits = store.findTargets(actor.tenantId(), planId, false).stream()
                .filter(t -> t.targetType() == TargetType.DEPLOYMENT_UNIT).toList();
        int stageIndex = 0;
        for (SnapshotStage stageSnapshot : snapshot) {
            if (stageIndex >= stages.size()) {
                break;
            }
            Stage stage = stages.get(stageIndex++);
            for (SnapshotTask taskSnapshot : stageSnapshot.tasks()) {
                List<PlanTarget> newTargets = switch (taskSnapshot.dimension()) {
                    case PHYSICAL_SUBSYSTEM -> targets.stream()
                            .filter(t -> t.targetType() == TargetType.PHYSICAL_SUBSYSTEM).toList();
                    case DEPLOYMENT_UNIT -> targets.stream()
                            .filter(t -> t.targetType() == TargetType.DEPLOYMENT_UNIT).toList();
                    default -> List.of();
                };
                if (taskSnapshot.dimension() == com.ccb.architecture.plan.model.PlanTemplateModels.Dimension.NONE
                        && newTargets.isEmpty()) {
                    continue;
                }
                for (PlanTarget target : newTargets) {
                    int taskNo = store.findTasks(actor.tenantId(), planId, stage.id()).size() + 1;
                    long taskId = nextId();
                    store.insertTask(actor.tenantId(), new Task(taskId, planId, stage.id(), taskNo,
                            taskSnapshot.name(), target.targetType(), target.targetId(), target.targetNo(),
                            target.targetName(), taskSnapshot.taskTemplateId(),
                            taskSnapshot.taskTemplateVersionNo(), taskSnapshot.dimension().name(),
                            toJson(Map.of("name", taskSnapshot.name(), "checkItems",
                                    taskSnapshot.checkItems())),
                            stage.ownerUserId(), stage.plannedStart(), stage.plannedEnd(), null, null,
                            TaskStatus.NOT_STARTED, false, false, null, null, null, 0));
                    int checkNo = 1;
                    for (CheckItemDraft checkItem : taskSnapshot.checkItems()) {
                        store.insertCheckItem(actor.tenantId(), new CheckItem(nextId(), taskId,
                                checkNo++, checkItem.name(), checkItem.sortNo(),
                                checkItem.guide(),
                                com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.PENDING,
                                null, null, null, false, null, null, null, 0, actor.id()));
                    }
                }
            }
        }
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "TARGET", null, "TARGET_ADDED",
                actor.id(), reason, null, toJson(Map.of("count", targets.size())));
        engine.recompute(actor.tenantId(), planId, LocalDateTime.now());
        return refreshPlan(actor, planId);
    }

    /** 移出计划目标：未完成任务自动取消，已完成保留，历史不删除。 */
    @Transactional
    public Plan removeTarget(AuthUser actor, long planId, long targetId, String reason, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        requireAdjustable(plan);
        PlanTarget target = store.findTarget(actor.tenantId(), planId,
                TargetType.PHYSICAL_SUBSYSTEM, targetId)
                .or(() -> store.findTarget(actor.tenantId(), planId, TargetType.DEPLOYMENT_UNIT, targetId))
                .orElseThrow(() -> new ArchitectureNotFoundException("计划目标不存在"));
        if (target.removed()) {
            throw new BusinessException(ErrorCode.CONFLICT, "计划目标已被移出");
        }
        String removeReason = requireText(reason, "移出目标原因", 1000);
        store.removeTarget(actor.tenantId(), target.id(), removeReason, actor.id());
        int cancelledTasks = 0;
        for (Task task : store.findTasks(actor.tenantId(), planId, null)) {
            if (!task.cancelled() && task.targetType() == target.targetType()
                    && Objects.equals(task.targetId(), target.targetId())
                    && task.actualStart() == null && task.status() != TaskStatus.COMPLETED) {
                store.updateTaskCancel(actor.tenantId(), task.id(), true,
                        "目标移出：" + removeReason, actor.id(), LocalDateTime.now());
                cancelledTasks++;
            }
        }
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "TARGET", target.id(),
                "TARGET_REMOVED", actor.id(), removeReason, toJson(Map.of("cancelledTasks", cancelledTasks)),
                null);
        engine.recompute(actor.tenantId(), planId, LocalDateTime.now());
        return refreshPlan(actor, planId);
    }

    /** 新增环节（运行中调整）。 */
    @Transactional
    public Stage addStage(AuthUser actor, long planId, AddStageCommand cmd, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        requireAdjustable(plan);
        String name = requireText(cmd == null ? null : cmd.name(), "环节名称", 200);
        requireUser(actor, cmd.ownerUserId(), "环节责任人");
        List<Stage> stages = store.findStages(actor.tenantId(), planId);
        int stageNo = stages.stream().mapToInt(Stage::stageNo).max().orElse(0) + 1;
        long stageId = nextId();
        store.insertStage(actor.tenantId(), new Stage(stageId, planId, stageNo, name, stageNo,
                cmd.ownerUserId(), cmd.plannedStart(), cmd.plannedEnd(), null, null,
                PlanStatus.NOT_STARTED, false, null, null, null, null));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "STAGE", stageId,
                "STAGE_ADDED", actor.id(), null, null, toJson(Map.of("name", name)));
        engine.recompute(actor.tenantId(), planId, LocalDateTime.now());
        return store.findStage(actor.tenantId(), stageId).orElseThrow();
    }

    /** 新增任务（运行中调整，目标可选）。 */
    @Transactional
    public Task addTask(AuthUser actor, long planId, AddTaskCommand cmd, boolean isAdmin) {
        Plan plan = engine.requirePlan(actor, planId);
        engine.requirePlanOwner(actor, plan, isAdmin);
        requireAdjustable(plan);
        Stage stage = store.findStage(actor.tenantId(), cmd.stageId())
                .orElseThrow(() -> new ArchitectureNotFoundException("环节不存在"));
        if (stage.planId() != planId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "环节不属于该计划");
        }
        String name = requireText(cmd == null ? null : cmd.name(), "任务名称", 300);
        requireUser(actor, cmd.ownerUserId(), "任务责任人");
        List<String> checkItemNames = distinctNames(cmd.checkItemNames());
        if (checkItemNames.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新增任务至少需要一个检查项");
        }
        TargetType targetType = null;
        Long targetId = null;
        String targetNo = null;
        String targetName = null;
        if (cmd.targetId() != null) {
            PlanTarget target = store.findTarget(actor.tenantId(), planId,
                    TargetType.PHYSICAL_SUBSYSTEM, cmd.targetId())
                    .or(() -> store.findTarget(actor.tenantId(), planId, TargetType.DEPLOYMENT_UNIT,
                            cmd.targetId()))
                    .orElseThrow(() -> new ArchitectureNotFoundException("目标任务不在计划目标范围内"));
            if (target.removed()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "目标任务已移出计划");
            }
            targetType = target.targetType();
            targetId = target.targetId();
            targetNo = target.targetNo();
            targetName = target.targetName();
        }
        if (targetType == null) {
            targetType = TargetType.PHYSICAL_SUBSYSTEM;
        }
        int taskNo = store.findTasks(actor.tenantId(), planId, stage.id()).size() + 1;
        long taskId = nextId();
        store.insertTask(actor.tenantId(), new Task(taskId, planId, stage.id(), taskNo, name,
                targetType, targetId, targetNo, targetName, null, null, null, null,
                cmd.ownerUserId(), cmd.plannedStart(), cmd.plannedEnd(), null, null,
                TaskStatus.NOT_STARTED, false, false, null, null, null, 0));
        int checkNo = 1;
        for (String checkItemName : checkItemNames) {
            store.insertCheckItem(actor.tenantId(), new CheckItem(nextId(), taskId, checkNo++,
                    checkItemName, checkNo, null,
                    com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.PENDING,
                    null, null, null, false, null, null, null, 0, actor.id()));
        }
        for (Long participant : distinctIds(cmd.participantUserIds())) {
            requireUser(actor, participant, "任务参与人");
            store.insertParticipant(actor.tenantId(), nextId(), taskId, participant, actor.id());
        }
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", planId, "TASK", taskId,
                "TASK_ADDED", actor.id(), null, null, toJson(Map.of("name", name, "stage", stage.name())));
        notificationService.notifyTaskAssigned(actor.tenantId(), plan.planNo(), name,
                List.of(cmd.ownerUserId()));
        engine.recompute(actor.tenantId(), planId, LocalDateTime.now());
        return requireFreshTask(actor, taskId);
    }

    /** 新增检查项（运行中调整，需要对应层级责任人）。 */
    @Transactional
    public CheckItem addCheckItem(AuthUser actor, long taskId, AddCheckItemCommand cmd, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        Plan plan = engine.requirePlan(actor, task.planId());
        Stage stage = store.findStage(actor.tenantId(), task.stageId()).orElseThrow();
        boolean allowed = isAdmin || actor.id() == plan.planOwnerUserId()
                || actor.id() == stage.ownerUserId() || actor.id() == task.ownerUserId();
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅计划、环节或任务责任人可以新增检查项");
        }
        if (plan.cancelled() || task.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划或任务已取消，不能新增检查项");
        }
        String name = requireText(cmd == null ? null : cmd.name(), "检查项名称", 500);
        int checkNo = store.findCheckItems(actor.tenantId(), taskId).size() + 1;
        long itemId = nextId();
        store.insertCheckItem(actor.tenantId(), new CheckItem(itemId, taskId, checkNo, name, checkNo,
                cmd == null ? null : cmd.guide(),
                com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.PENDING, null, null, null,
                false, null, null, null, 0, actor.id()));
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "CHECK_ITEM", itemId,
                "CHECK_ITEM_ADDED", actor.id(), null, null, toJson(Map.of("taskId", taskId, "name", name)));
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
        return store.findCheckItem(actor.tenantId(), itemId).orElseThrow();
    }

    /** 删除未执行的错误任务（附原因留痕）。 */
    @Transactional
    public void deleteTask(AuthUser actor, long taskId, String reason, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String deleteReason = requireText(reason, "删除原因", 1000);
        if (task.actualStart() != null || task.status() == TaskStatus.COMPLETED
                || task.status() == TaskStatus.CANCELLED
                || store.findCheckItems(actor.tenantId(), taskId).stream()
                        .anyMatch(c -> c.status() == com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "任务已开始、已完成、已取消或存在已完成检查项，不能删除；可改为取消任务");
        }
        store.deleteParticipants(actor.tenantId(), taskId);
        store.deleteDependenciesByTask(actor.tenantId(), taskId);
        store.deleteBlocksByTask(actor.tenantId(), taskId);
        for (CheckItem item : store.findCheckItems(actor.tenantId(), taskId)) {
            store.deleteCheckItem(actor.tenantId(), item.id());
        }
        store.deleteTask(actor.tenantId(), taskId);
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "TASK", taskId,
                "TASK_DELETED", actor.id(), deleteReason,
                toJson(Map.of("name", task.name())), null);
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
    }

    /** 删除未执行错误检查项（附原因留痕）。 */
    @Transactional
    public void deleteCheckItem(AuthUser actor, long checkItemId, String reason, boolean isAdmin) {
        CheckItem item = engine.requireCheckItem(actor, checkItemId);
        Task task = engine.requireTask(actor, item.taskId());
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String deleteReason = requireText(reason, "删除原因", 1000);
        if (task.actualStart() != null || item.cancelled()
                || item.status() != com.ccb.architecture.plan.model.PlanModels.CheckItemStatus.PENDING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "任务已开始或检查项已处理（完成/取消），不能物理删除；可改为取消检查项");
        }
        store.deleteCheckItem(actor.tenantId(), checkItemId);
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "CHECK_ITEM",
                checkItemId, "CHECK_ITEM_DELETED", actor.id(), deleteReason,
                toJson(Map.of("name", item.name())), null);
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
    }

    public Plan refreshPlan(AuthUser actor, long planId) {
        return engine.requirePlan(actor, planId);
    }

    private Task requireFreshTask(AuthUser actor, long taskId) {
        return store.findTask(actor.tenantId(), taskId).orElseThrow();
    }

    private void requireAdjustable(Plan plan) {
        if (plan.cancelled() || plan.status() == PlanStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划已取消或已完成，不能调整");
        }
    }

    private void requireUser(AuthUser actor, Long userId, String role) {
        if (userId == null || referenceQuery.findUser(actor, userId, true).isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, role + "（用户 " + userId + "）不存在或已停用");
        }
    }

    private static List<Long> distinctIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<String> distinctNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String name : names) {
            String trimmed = name == null ? null : name.trim();
            if (trimmed != null && !trimmed.isBlank() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String requireText(String value, String field, int maxLength) {
        String text = value == null || value.isBlank() ? null : value.trim();
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "长度不能超过 " + maxLength);
        }
        return text;
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
            throw new IllegalStateException("计划标识生成器返回无效值");
        }
        return value;
    }
}
