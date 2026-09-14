package com.ccb.development.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.model.DevelopmentStageModels.*;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.repository.DevelopmentChangeRepository;
import com.ccb.development.repository.DevelopmentStageRepository;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentStageService {
    private static final String BUSINESS_TYPE = "development-task";
    private final DevelopmentTaskRepository tasks;
    private final DevelopmentStageRepository stages;
    private final DevelopmentAccessPolicy access;
    private final DevelopmentTaskService taskService;
    private final DevelopmentSourceResolver sources;
    private final DevelopmentCalendarService calendar;
    private final DevelopmentChangeRepository changes;
    private final AttachmentGateway attachments;

    public DevelopmentStageService(DevelopmentTaskRepository tasks, DevelopmentStageRepository stages,
                                   DevelopmentAccessPolicy access, DevelopmentTaskService taskService,
                                   DevelopmentSourceResolver sources, DevelopmentCalendarService calendar,
                                   DevelopmentChangeRepository changes, AttachmentGateway attachments) {
        this.tasks = tasks;
        this.stages = stages;
        this.access = access;
        this.taskService = taskService;
        this.sources = sources;
        this.calendar = calendar;
        this.changes = changes;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public StageView get(AuthUser actor, long taskId) {
        var task = tasks.require(actor.tenantId(), taskId, false);
        access.read(actor, task);
        return view(actor, task);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StageView save(AuthUser actor, long taskId, StageWrite input) {
        var task = tasks.require(actor.tenantId(), taskId, true);
        access.manage(actor, task, "development:task:update");
        access.editable(task);
        var request = normalize(input);
        var previous = stages.find(actor.tenantId(), taskId);
        var old = previous.orElseGet(() -> stages.empty(taskId));
        if (old.rowVersion() != request.rowVersion()) throw conflict("阶段资料已被修改，请刷新后重试");
        task = refreshSource(actor, task);
        boolean pureTest = !requiresCodeWalk(task.sourceMode(), task.sourceRoles());
        if (!pureTest && (request.notApplicableDesign() || request.notApplicableImplementation())) {
            throw bad("只有纯测试任务可以将设计和实施标为不适用");
        }
        var before = view(actor, task);
        for (long id : Stream.of(request.designAttachmentIds(), request.codeWalkAttachmentIds(), request.testReportAttachmentIds())
                .flatMap(List::stream).distinct().sorted().toList()) {
            attachments.bind(new AttachmentBindingCommand(id, BUSINESS_TYPE, Long.toString(taskId), task.projectRef()), actor);
            bound(actor, task, id);
        }
        boolean designChanged = !Objects.equals(old.designPlanStart(), request.designPlanStart())
                || !Objects.equals(old.designPlanEnd(), request.designPlanEnd())
                || !old.designDocumentPath().equals(request.designDocumentPath())
                || old.notApplicableDesign() != request.notApplicableDesign()
                || !ids(actor, taskId, old.rowVersion(), AttachmentKind.DESIGN).equals(request.designAttachmentIds());
        boolean testChanged = !ids(actor, taskId, old.rowVersion(), AttachmentKind.CODE_WALK).equals(request.codeWalkAttachmentIds())
                || !ids(actor, taskId, old.rowVersion(), AttachmentKind.TEST_REPORT).equals(request.testReportAttachmentIds());
        stages.save(actor, taskId, request, previous.isPresent(), designChanged, testChanged);
        tasks.status(actor, task, task.status());
        var after = view(actor, tasks.require(actor.tenantId(), taskId, false));
        changes.record(actor, taskId, "STAGE", taskId, "UPDATE", before, after);
        return after;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView action(AuthUser actor, long taskId, TaskAction request) {
        var task = tasks.require(actor.tenantId(), taskId, true);
        String action = required(request.action(), 32, "动作");
        access.manage(actor, task, "COMPLETE".equals(action) ? "development:task:complete" : "development:task:update");
        if (request.rowVersion() != task.rowVersion()) throw conflict("任务或阶段已被修改，请刷新后重试");
        var before = taskService.view(actor, task);
        TaskStatus target;
        switch (action) {
            case "START" -> {
                if (task.status() != TaskStatus.NOT_STARTED) throw conflict("只有待开始任务可以开始");
                target = TaskStatus.IN_PROGRESS;
            }
            case "COMPLETE" -> {
                access.editable(task);
                task = refreshSource(actor, task);
                requireCompletion(actor, task);
                calendar.freeze(actor, taskId, "TASK", taskId, task.rowVersion() + 1);
                target = TaskStatus.COMPLETED;
            }
            case "CANCEL" -> {
                access.editable(task);
                required(request.reason(), 1000, "取消原因");
                target = TaskStatus.CANCELLED;
            }
            case "REOPEN" -> {
                if (task.status() != TaskStatus.COMPLETED) throw conflict("只有已完成任务可以重开");
                required(request.reason(), 1000, "重开原因");
                target = TaskStatus.IN_PROGRESS;
            }
            case "RESTORE" -> {
                if (task.status() != TaskStatus.CANCELLED) throw conflict("只有已取消任务可以恢复");
                required(request.reason(), 1000, "恢复原因");
                target = tasks.statusBeforeCancel(actor.tenantId(), taskId);
            }
            default -> throw bad("任务动作无效");
        }
        tasks.status(actor, task, target);
        var after = taskService.view(actor, tasks.require(actor.tenantId(), taskId, false));
        changes.record(actor, taskId, "TASK", taskId, action, before,
                new ActionResult(after, optional(request.reason(), 1000, "操作原因")));
        return after;
    }

    public static boolean requiresCodeWalk(SourceMode sourceMode, List<String> roles) {
        return sourceMode != SourceMode.LINKED || !Set.copyOf(roles).equals(Set.of("TEST"));
    }

    private void requireCompletion(AuthUser actor, TaskEntity task) {
        if (tasks.workItemCount(actor.tenantId(), task.id(), false) != tasks.workItemCount(actor.tenantId(), task.id(), true)) {
            throw conflict("尚有未验收完成的工作项");
        }
        var stage = stages.find(actor.tenantId(), task.id()).orElseGet(() -> stages.empty(task.id()));
        if (requiresCodeWalk(task.sourceMode(), task.sourceRoles())) {
            if (stage.notApplicableDesign() || stage.notApplicableImplementation()) throw conflict("来源角色已变化，请重新确认设计和实施适用性");
            var codeWalk = ids(actor, task.id(), stage.rowVersion(), AttachmentKind.CODE_WALK);
            if (codeWalk.isEmpty()) throw conflict("开发类任务完成前必须登记代码走查附件");
            for (long attachmentId : codeWalk) bound(actor, task, attachmentId);
        }
    }

    private TaskEntity refreshSource(AuthUser actor, TaskEntity task) {
        if (task.sourceMode() == SourceMode.STANDALONE) return task;
        var current = sources.current(actor, task.projectRef(), task.sourceRequirementId(), task.systemId());
        tasks.requireStableBinding(actor.tenantId(), current.id(), task.systemId(), current.systemCodes());
        if (!current.revision().equals(task.sourceRevision()) || !current.roles().equals(task.sourceRoles())
                || !current.systemCodes().equals(task.sourceSystemCodes())) {
            var added = current.systemCodes().stream().filter(code -> !task.sourceSystemCodes().contains(code)).toList();
            tasks.bindSource(actor.tenantId(), current.id(), task.systemId(), task.id(), added);
            tasks.refreshSource(actor, task, current.revision(), current.roles(), current.systemCodes());
            changes.record(actor, task.id(), "SOURCE", current.id(), "REFRESH",
                    new SourceView(task.sourceRequirementId().toString(), task.sourceNumber(), task.sourceRevision(), task.sourceRoles(), task.sourceSystemCodes()),
                    new SourceView(Long.toString(current.id()), current.number(), current.revision(), current.roles(), current.systemCodes()));
            return tasks.require(actor.tenantId(), task.id(), false);
        }
        return task;
    }

    private StageView view(AuthUser actor, TaskEntity task) {
        var stage = stages.find(actor.tenantId(), task.id()).orElseGet(() -> stages.empty(task.id()));
        var duration = DevelopmentCalendarService.measure(task.developmentPlanStart(), task.developmentPlanEnd(),
                stage.implementationActualStart(), stage.implementationActualEnd(), calendar.forTask(actor, task));
        return new StageView(Long.toString(task.id()), stage.designPlanStart(), stage.designPlanEnd(), stage.designDocumentPath(),
                files(actor, task, stage, AttachmentKind.DESIGN), stage.implementationActualStart(), stage.implementationActualEnd(),
                files(actor, task, stage, AttachmentKind.CODE_WALK), files(actor, task, stage, AttachmentKind.TEST_REPORT),
                stage.notApplicableDesign(), stage.notApplicableImplementation(), !requiresCodeWalk(task.sourceMode(), task.sourceRoles()),
                stage.rowVersion(), task.rowVersion(), stage.designRegisteredAt(), stage.testRegisteredAt(), duration,
                !access.manages(actor, task) || !access.has(actor, "development:task:update")
                        || task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED);
    }

    private List<AttachmentView> files(AuthUser actor, TaskEntity task, StageEntity stage, AttachmentKind kind) {
        return ids(actor, task.id(), stage.rowVersion(), kind).stream().map(id -> {
            var item = bound(actor, task, id);
            return new AttachmentView(Long.toString(item.id()), item.fileName(), item.fileSize(), item.contentType());
        }).toList();
    }

    private List<Long> ids(AuthUser actor, long taskId, long version, AttachmentKind kind) {
        return stages.attachments(actor.tenantId(), taskId, version, kind);
    }

    private AttachmentItem bound(AuthUser actor, TaskEntity task, long id) {
        var item = attachments.get(id, actor);
        if (!"BOUND".equals(item.status()) || !BUSINESS_TYPE.equals(item.businessType())
                || !Long.toString(task.id()).equals(item.businessKey()) || !task.projectRef().equals(item.projectRef()) || item.fileSize() <= 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件不属于当前任务的有效绑定资料");
        }
        return item;
    }

    private static StageWrite normalize(StageWrite r) {
        dates(r.designPlanStart(), r.designPlanEnd());
        dates(r.implementationActualStart(), r.implementationActualEnd());
        return new StageWrite(r.designPlanStart(), r.designPlanEnd(), optional(r.designDocumentPath(), 2000, "文档路径"),
                normalizeIds(r.designAttachmentIds()), r.implementationActualStart(), r.implementationActualEnd(), normalizeIds(r.codeWalkAttachmentIds()),
                normalizeIds(r.testReportAttachmentIds()), r.notApplicableDesign(), r.notApplicableImplementation(), r.rowVersion());
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        if (ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(ids).size() != ids.size()) {
            throw bad("附件标识无效、重复或超过数量限制");
        }
        return ids.stream().sorted().toList();
    }
    private record ActionResult(TaskView task, String reason) {}
}
