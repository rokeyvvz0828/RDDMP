package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanTemplateModels.CheckItemDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.CreateTemplateCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.Dimension;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplate;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplateDetail;
import com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotStage;
import com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateStatus;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion;
import com.ccb.architecture.plan.model.PlanTemplateModels.UpdateTemplateCommand;
import com.ccb.architecture.plan.persistence.PlanTemplateStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 计划模板与任务模板的草稿维护、版本化发布与停用（REQ-20260830-056）。 */
@Service
public class PlanTemplateService {
    private static final int MAX_CHECK_ITEMS = 100;

    /** 任务模板已发布版本的元信息。 */
    public record TaskTemplateVersionMeta(String name, Dimension dimension, String checkItemsJson) {
    }

    /** 环节引用（含所属模板与时间范围配置）。 */
    public record StageRef(long id, long templateId, String name, int sortNo,
                           Integer startOffsetDays, Integer durationDays) {
    }

    private final PlanTemplateStore store;
    private final ObjectMapper objectMapper;
    private final LongSupplier idSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanTemplateService(PlanTemplateStore store, ObjectMapper objectMapper) {
        this(store, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    PlanTemplateService(PlanTemplateStore store, ObjectMapper objectMapper, LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "模板存储不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 能力不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Transactional
    public PlanTemplate createDraft(AuthUser actor, CreateTemplateCommand cmd) {
        String name = requireText(cmd == null ? null : cmd.name(), "模板名称", 200);
        String description = trimToNull(cmd == null ? null : cmd.description());
        long id = nextId();
        store.insertTemplate(actor.tenantId(), new PlanTemplate(id, name, description,
                TemplateStatus.DRAFT, 0, 0, actor.id(), actor.id()));
        store.insertActivity(actor.tenantId(), nextId(), "TEMPLATE", id, "TEMPLATE", id,
                "TEMPLATE_CREATED", actor.id(), null, null, toJson(Map.of("name", name)));
        return requireTemplate(actor.tenantId(), id);
    }

    @Transactional
    public PlanTemplate update(AuthUser actor, long id, UpdateTemplateCommand cmd) {
        PlanTemplate template = requireTemplate(actor.tenantId(), id);
        String name = requireText(cmd == null ? null : cmd.name(), "模板名称", 200);
        String description = trimToNull(cmd == null ? null : cmd.description());
        try {
            store.updateTemplate(actor.tenantId(), id, name, description, cmd.rowVersion(), actor.id());
        } catch (IllegalStateException conflict) {
            throw new BusinessException(ErrorCode.CONFLICT, "模板已被并发修改，请刷新后重试");
        }
        store.insertActivity(actor.tenantId(), nextId(), "TEMPLATE", id, "TEMPLATE", id,
                "TEMPLATE_UPDATED", actor.id(), null,
                toJson(Map.of("name", template.name(), "description", template.description())),
                toJson(Map.of("name", name, "description", description)));
        return requireTemplate(actor.tenantId(), id);
    }

    @Transactional
    public PlanTemplate changeStatus(AuthUser actor, long id, TemplateStatus target) {
        if (target != TemplateStatus.ACTIVE && target != TemplateStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板状态仅支持启用或停用");
        }
        PlanTemplate template = requireTemplate(actor.tenantId(), id);
        if (template.latestVersionNo() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板尚未发布任何版本，不能启用或停用");
        }
        if (template.status() == target) {
            return template;
        }
        store.updateTemplateStatus(actor.tenantId(), id, target, actor.id());
        store.insertActivity(actor.tenantId(), nextId(), "TEMPLATE", id, "TEMPLATE", id,
                "TEMPLATE_STATUS_CHANGED", actor.id(), null,
                toJson(Map.of("from", template.status().name(), "to", target.name())), null);
        return requireTemplate(actor.tenantId(), id);
    }

    @Transactional
    public StageDraft addStage(AuthUser actor, long templateId, StageCommand cmd) {
        requireTemplate(actor.tenantId(), templateId);
        String name = requireText(cmd == null ? null : cmd.name(), "环节名称", 200);
        int sortNo = cmd != null && cmd.sortNo() != null && cmd.sortNo() >= 0
                ? cmd.sortNo() : store.findStages(actor.tenantId(), templateId).size();
        Integer offset = requireNonNegative(cmd == null ? null : cmd.startOffsetDays(), "环节偏移天数");
        Integer duration = requirePositiveOrNull(cmd == null ? null : cmd.durationDays(), "环节持续天数");
        long stageId = nextId();
        store.insertStage(actor.tenantId(), stageId, templateId, name, sortNo, offset, duration,
                actor.id());
        Map<String, Object> change = new java.util.HashMap<>();
        change.put("name", name);
        change.put("sortNo", sortNo);
        change.put("startOffsetDays", offset);
        change.put("durationDays", duration);
        recordStageActivity(actor, templateId, "STAGE_ADDED", stageId, null,
                toJson(change));
        return findStage(actor, templateId, stageId);
    }

    @Transactional
    public StageDraft updateStage(AuthUser actor, long stageId, StageCommand cmd) {
        StageRef stage = requireStageRef(actor.tenantId(), stageId);
        String name = requireText(cmd == null ? null : cmd.name(), "环节名称", 200);
        int sortNo = cmd != null && cmd.sortNo() != null && cmd.sortNo() >= 0
                ? cmd.sortNo() : stage.sortNo();
        Integer offset = cmd != null && cmd.startOffsetDays() != null
                ? requireNonNegative(cmd.startOffsetDays(), "环节偏移天数") : stage.startOffsetDays();
        Integer duration = cmd != null && cmd.durationDays() != null
                ? requirePositiveOrNull(cmd.durationDays(), "环节持续天数") : stage.durationDays();
        store.updateStage(actor.tenantId(), stageId, name, sortNo, offset, duration, actor.id());
        Map<String, Object> change = new java.util.HashMap<>();
        change.put("name", name);
        change.put("sortNo", sortNo);
        change.put("startOffsetDays", offset);
        change.put("durationDays", duration);
        recordStageActivity(actor, stage.templateId(), "STAGE_UPDATED", stageId, null,
                toJson(change));
        return findStage(actor, stage.templateId(), stageId);
    }

    @Transactional
    public void deleteStage(AuthUser actor, long stageId) {
        StageRef stage = requireStageRef(actor.tenantId(), stageId);
        for (TaskTemplateDraft task : store.findTaskTemplates(actor.tenantId(), null, stageId)) {
            store.deleteTaskTemplate(actor.tenantId(), task.id());
        }
        store.deleteStage(actor.tenantId(), stageId);
        recordStageActivity(actor, stage.templateId(), "STAGE_REMOVED", stageId,
                toJson(Map.of("deleted", true)), null);
    }

    @Transactional
    public TaskTemplateDraft addTaskTemplate(AuthUser actor, long templateId, long stageId,
                                             TaskTemplateCommand cmd) {
        requireTemplate(actor.tenantId(), templateId);
        findStage(actor, templateId, stageId);
        String name = requireText(cmd == null ? null : cmd.name(), "任务模板名称", 200);
        Dimension dimension = cmd == null || cmd.dimension() == null ? Dimension.NONE : cmd.dimension();
        List<CheckItemDraft> checkItems = validateCheckItems(cmd == null ? null : cmd.checkItems());
        long taskId = nextId();
        store.insertTaskTemplate(actor.tenantId(), new TaskTemplateDraft(taskId, templateId, name,
                dimension, checkItems, TemplateStatus.DRAFT, 0, 0), templateId, stageId,
                toJson(checkItems), actor.id());
        recordStageActivity(actor, templateId, "TASK_TEMPLATE_ADDED", taskId, null, toJson(Map.of(
                "stageId", stageId, "name", name, "dimension", dimension.name())));
        return requireTaskTemplate(actor.tenantId(), taskId);
    }

    @Transactional
    public TaskTemplateDraft updateTaskTemplate(AuthUser actor, long taskId, TaskTemplateCommand cmd) {
        TaskTemplateDraft current = requireTaskTemplate(actor.tenantId(), taskId);
        String name = requireText(cmd == null ? null : cmd.name(), "任务模板名称", 200);
        Dimension dimension = cmd == null || cmd.dimension() == null ? Dimension.NONE : cmd.dimension();
        List<CheckItemDraft> checkItems = validateCheckItems(cmd == null ? null : cmd.checkItems());
        try {
            store.updateTaskTemplate(actor.tenantId(), new TaskTemplateDraft(taskId, current.templateId(),
                    name, dimension, checkItems, current.status(), current.latestVersionNo(),
                    cmd.rowVersion() == null ? current.rowVersion() : cmd.rowVersion()),
                    toJson(checkItems), actor.id());
        } catch (IllegalStateException conflict) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务模板已被并发修改，请刷新后重试");
        }
        recordStageActivity(actor, current.templateId(), "TASK_TEMPLATE_UPDATED", taskId, null,
                toJson(Map.of("name", name, "dimension", dimension.name(),
                        "checkItems", checkItems.size())));
        return requireTaskTemplate(actor.tenantId(), taskId);
    }

    @Transactional
    public void deleteTaskTemplate(AuthUser actor, long taskId) {
        TaskTemplateDraft current = requireTaskTemplate(actor.tenantId(), taskId);
        store.deleteTaskTemplate(actor.tenantId(), taskId);
        recordStageActivity(actor, current.templateId(), "TASK_TEMPLATE_REMOVED", taskId,
                toJson(Map.of("deleted", true)), null);
    }

    /** 发布新版本：校验结构完整性，生成不可变版本快照；任务模板内容变化时追加任务模板版本。 */
    @Transactional
    public TemplateVersion publish(AuthUser actor, long templateId, String note) {
        PlanTemplate template = store.lockTemplate(actor.tenantId(), templateId)
                .orElseThrow(() -> new ArchitectureNotFoundException("计划模板不存在"));
        List<StageDraft> stages = store.findStages(actor.tenantId(), templateId);
        if (stages.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板至少需要一个环节才能发布");
        }
        Map<Long, List<TaskTemplateDraft>> tasksByStage = new java.util.LinkedHashMap<>();
        for (StageDraft stage : stages) {
            List<TaskTemplateDraft> tasks = store.findTaskTemplates(actor.tenantId(), null, stage.id());
            tasksByStage.put(stage.id(), tasks);
            if (tasks.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "环节「%s」至少需要一个任务模板才能发布".formatted(stage.name()));
            }
        }
        int versionNo = template.latestVersionNo() + 1;
        List<SnapshotStage> snapshot = new ArrayList<>();
        Map<Long, List<Long>> stageDeps = new java.util.HashMap<>();
        for (Long[] pair : store.findStageDependencies(actor.tenantId(), templateId)) {
            stageDeps.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]);
        }
        Map<Long, List<Long>> taskDeps = new java.util.HashMap<>();
        for (Long[] pair : store.findTaskTemplateDependencies(actor.tenantId(), templateId, null)) {
            taskDeps.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]);
        }
        for (StageDraft stage : stages) {
            List<SnapshotTask> snapshotTasks = new ArrayList<>();
            for (TaskTemplateDraft task : tasksByStage.get(stage.id())) {
                int taskVersionNo = publishTaskTemplateIfChanged(actor, task);
                snapshotTasks.add(new SnapshotTask(task.id(), taskVersionNo, task.name(),
                        task.dimension(), task.checkItems(),
                        taskDeps.getOrDefault(task.id(), List.of())));
            }
            snapshot.add(new SnapshotStage(stage.id(), stage.name(), stage.sortNo(),
                    stage.startOffsetDays(), stage.durationDays(), snapshotTasks,
                    stageDeps.getOrDefault(stage.id(), List.of())));
        }
        String contentJson = toJson(snapshot);
        long versionId = nextId();
        store.insertTemplateVersion(actor.tenantId(), versionId, templateId, versionNo, contentJson,
                trimToNull(note), actor.id());
        store.updateTemplateLatestVersion(actor.tenantId(), templateId, versionNo, actor.id());
        if (template.status() == TemplateStatus.DRAFT) {
            store.updateTemplateStatus(actor.tenantId(), templateId, TemplateStatus.ACTIVE, actor.id());
        }
        store.insertActivity(actor.tenantId(), nextId(), "TEMPLATE", templateId, "TEMPLATE_VERSION",
                versionId, "TEMPLATE_PUBLISHED", actor.id(), trimToNull(note),
                toJson(Map.of("from", template.latestVersionNo(), "to", versionNo)), null);
        return store.findTemplateVersion(actor.tenantId(), templateId, versionNo)
                .orElseThrow(() -> new ArchitectureNotFoundException("模板版本不存在"));
    }

    /** 设置环节模板依赖（全量替换，同模板校验 + 循环校验）。 */
    @Transactional
    public List<Long[]> setStageDependencies(AuthUser actor, long stageId, List<Long> predecessorStageIds) {
        StageRef stage = requireStageRef(actor.tenantId(), stageId);
        List<Long> requested = predecessorStageIds == null ? List.of()
                : predecessorStageIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long predecessorId : requested) {
            if (predecessorId == stageId) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "环节不能依赖自身");
            }
            StageRef predecessor = requireStageRef(actor.tenantId(), predecessorId);
            if (predecessor.templateId() != stage.templateId()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "前置环节必须属于同一模板");
            }
        }
        store.deleteStageDependencies(actor.tenantId(), stageId);
        for (Long predecessorId : requested) {
            store.insertStageDependency(actor.tenantId(), nextId(), stage.templateId(), stageId,
                    predecessorId, actor.id());
        }
        recordStageActivity(actor, stage.templateId(), "STAGE_DEPENDENCY_CHANGED", stageId, null,
                toJson(Map.of("predecessors", requested)));
        return store.findStageDependencies(actor.tenantId(), stage.templateId());
    }

    /** 设置任务模板依赖（同环节校验、维度兼容校验、循环校验）。 */
    @Transactional
    public List<Long[]> setTaskTemplateDependencies(AuthUser actor, long taskTemplateId,
                                                    List<Long> predecessorTaskTemplateIds) {
        TaskTemplateDraft task = requireTaskTemplate(actor.tenantId(), taskTemplateId);
        List<Long> requested = predecessorTaskTemplateIds == null ? List.of()
                : predecessorTaskTemplateIds.stream().filter(Objects::nonNull).distinct().toList();
        Long stageId = taskIdStageId(actor, taskTemplateId);
        Map<Long, TaskTemplateDraft> byId = new java.util.LinkedHashMap<>();
        for (TaskTemplateDraft candidate : store.findTaskTemplates(actor.tenantId(), null, stageId)) {
            byId.put(candidate.id(), candidate);
        }
        for (Long predecessorId : requested) {
            if (predecessorId == taskTemplateId) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "任务模板不能依赖自身");
            }
            TaskTemplateDraft predecessor = byId.get(predecessorId);
            if (predecessor == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "前置任务模板必须属于同一环节");
            }
            if (!dimensionCompatible(task.dimension(), predecessor.dimension())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "任务模板依赖要求同生成维度，或其一为不展开");
            }
        }
        store.deleteTaskTemplateDependencies(actor.tenantId(), null, stageId, taskTemplateId);
        for (Long predecessorId : requested) {
            store.insertTaskTemplateDependency(actor.tenantId(), nextId(),
                    task.templateId(), stageId, taskTemplateId, predecessorId, actor.id());
        }
        recordStageActivity(actor, task.templateId(), "TASK_TEMPLATE_DEPENDENCY_CHANGED",
                taskTemplateId, null, toJson(Map.of("predecessors", requested)));
        return store.findTaskTemplateDependencies(actor.tenantId(), null, stageId);
    }

    private Long taskIdStageId(AuthUser actor, long taskTemplateId) {
        return store.stageIdOfTaskTemplate(actor.tenantId(), taskTemplateId);
    }

    private static boolean dimensionCompatible(Dimension a, Dimension b) {
        return a == b || a == Dimension.NONE || b == Dimension.NONE;
    }

    public PlanTemplateDetail detail(AuthUser actor, long templateId) {
        PlanTemplate template = requireTemplate(actor.tenantId(), templateId);
        List<StageDraft> stages = new ArrayList<>();
        for (StageDraft stage : store.findStages(actor.tenantId(), templateId)) {
            List<TaskTemplateDraft> tasks = store.findTaskTemplates(actor.tenantId(), null, stage.id());
            stages.add(new StageDraft(stage.id(), stage.name(), stage.sortNo(), tasks));
        }
        return new PlanTemplateDetail(template, stages,
                store.findStageDependencies(actor.tenantId(), templateId),
                store.findTaskTemplateDependencies(actor.tenantId(), templateId, null),
                store.findTemplateVersions(actor.tenantId(), templateId));
    }

    /** 生成计划使用的模板视图：仅允许已启用模板，返回已发布版本快照结构。 */
    public PlanTemplateDetail detailForGeneration(long tenantId, long templateId) {
        PlanTemplate template = store.findTemplate(tenantId, templateId)
                .orElseThrow(() -> new ArchitectureNotFoundException("计划模板不存在"));
        if (template.status() != TemplateStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板未启用，不能用于创建计划");
        }
        TemplateVersion version = store.findTemplateVersion(tenantId, templateId, template.latestVersionNo())
                .orElseThrow(() -> new ArchitectureNotFoundException("模板没有已发布版本"));
        List<StageDraft> stages = new ArrayList<>();
        for (StageDraft stage : store.findStages(tenantId, templateId)) {
            List<TaskTemplateDraft> tasks = store.findTaskTemplates(tenantId, null, stage.id());
            stages.add(new StageDraft(stage.id(), stage.name(), stage.sortNo(), tasks));
        }
        return new PlanTemplateDetail(template, stages,
                store.findStageDependencies(tenantId, templateId),
                store.findTaskTemplateDependencies(tenantId, templateId, null),
                List.of(version));
    }

    public List<PlanTemplate> list(AuthUser actor, String keyword, TemplateStatus status,
                                   long page, long size) {
        return store.searchTemplates(actor.tenantId(), keyword, status, (int) size, (int) ((page - 1) * size));
    }

    public long count(AuthUser actor, String keyword, TemplateStatus status) {
        return store.countTemplates(actor.tenantId(), keyword, status);
    }

    public List<SnapshotStage> parseSnapshot(String contentJson) {
        try {
            return objectMapper.readValue(contentJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SnapshotStage.class));
        } catch (Exception e) {
            throw new IllegalStateException("模板版本快照解析失败", e);
        }
    }

    private int publishTaskTemplateIfChanged(AuthUser actor, TaskTemplateDraft task) {
        int currentLatest = store.latestTaskTemplateVersion(actor.tenantId(), task.id());
        if (currentLatest > 0) {
            TaskTemplateVersionMeta meta = store.taskTemplateVersionMeta(actor.tenantId(),
                    task.id(), currentLatest).orElse(null);
            if (meta != null && meta.name().equals(task.name())
                    && meta.dimension() == task.dimension()
                    && task.checkItems().equals(readCheckItems(meta.checkItemsJson()))) {
                return currentLatest;
            }
        }
        int versionNo = currentLatest + 1;
        store.insertTaskTemplateVersion(actor.tenantId(), nextId(), task.id(), versionNo, task.name(),
                task.dimension(), toJson(task.checkItems()), "随计划模板发布", actor.id());
        store.updateTaskTemplateLatestVersion(actor.tenantId(), task.id(), versionNo, actor.id());
        store.updateTaskTemplateStatus(actor.tenantId(), task.id(), TemplateStatus.ACTIVE, actor.id());
        return versionNo;
    }

    private List<CheckItemDraft> readCheckItems(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CheckItemDraft.class));
        } catch (Exception e) {
            throw new IllegalStateException("检查项快照解析失败", e);
        }
    }

    private List<CheckItemDraft> validateCheckItems(List<CheckItemDraft> checkItems) {
        if (checkItems == null || checkItems.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务模板至少需要一个标准检查项");
        }
        if (checkItems.size() > MAX_CHECK_ITEMS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检查项数量不能超过 " + MAX_CHECK_ITEMS);
        }
        List<CheckItemDraft> result = new ArrayList<>();
        int sortNo = 1;
        for (CheckItemDraft item : checkItems) {
            String name = requireText(item == null ? null : item.name(), "检查项名称", 500);
            String guide = item == null ? null : trimToNull(item.guide());
            if (guide != null && guide.length() > 2000) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "检查指标内容长度不能超过 2000");
            }
            result.add(new CheckItemDraft(name, sortNo++, guide));
        }
        if (result.stream().map(CheckItemDraft::name).distinct().count() != result.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检查项名称不能重复");
        }
        return result;
    }

    private PlanTemplate requireTemplate(long tenantId, long id) {
        return store.findTemplate(tenantId, id)
                .orElseThrow(() -> new ArchitectureNotFoundException("计划模板不存在"));
    }

    private StageDraft findStage(AuthUser actor, long templateId, long stageId) {
        return store.findStages(actor.tenantId(), templateId).stream()
                .filter(stage -> stage.id() == stageId)
                .findFirst()
                .orElseThrow(() -> new ArchitectureNotFoundException("模板环节不存在"));
    }

    private StageRef requireStageRef(long tenantId, long stageId) {
        return store.findStageRef(tenantId, stageId)
                .orElseThrow(() -> new ArchitectureNotFoundException("模板环节不存在"));
    }

    private TaskTemplateDraft requireTaskTemplate(long tenantId, long taskId) {
        return store.findTaskTemplate(tenantId, taskId)
                .orElseThrow(() -> new ArchitectureNotFoundException("任务模板不存在"));
    }

    private void recordStageActivity(AuthUser actor, long templateId, String action, Long objectId,
                                     String before, String after) {
        store.insertActivity(actor.tenantId(), nextId(), "TEMPLATE", templateId, "STAGE", objectId,
                action, actor.id(), null, before, after);
    }

    private static Integer requireNonNegative(Integer value, String field) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为负数");
        }
        return value;
    }

    private static Integer requirePositiveOrNull(Integer value, String field) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "必须大于 0");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "长度不能超过 " + maxLength);
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
            throw new IllegalStateException("模板标识生成器返回无效值");
        }
        return value;
    }
}
