package com.ccb.architecture.plan.web;

import com.ccb.architecture.plan.model.PlanTemplateModels.CheckItemDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.CreateTemplateCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.Dimension;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplate;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplateDetail;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateStatus;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion;
import com.ccb.architecture.plan.model.PlanTemplateModels.UpdateTemplateCommand;
import com.ccb.architecture.plan.service.PlanTemplateService;
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


/** 搭建计划模板 HTTP 边界（REQ-20260830-056）。 */
@RestController
@RequestMapping("/api/architecture")
public class PlanTemplateController {
    private static final Logger log = LoggerFactory.getLogger(PlanTemplateController.class);
    private static final String VIEW_AUTHORITY = "hasAnyAuthority('architecture:plan-template:view',"
            + "'architecture:plan-template:manage','architecture:view','architecture:manage')";
    private static final String MANAGE_AUTHORITY = "hasAnyAuthority('architecture:plan-template:manage',"
            + "'architecture:manage')";

    private final PlanTemplateService service;
    private final SystemOperationAudit operationAudit;

    public PlanTemplateController(PlanTemplateService service, SystemOperationAudit operationAudit) {
        this.service = service;
        this.operationAudit = operationAudit;
    }

    @GetMapping("/plan-templates")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PageResult<PlanTemplateView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TemplateStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser actor) {
        PageResult<PlanTemplateView> result = new PageResult<>(
                service.list(actor, keyword, status, page, size).stream()
                        .map(PlanTemplateView::from).toList(),
                service.count(actor, keyword, status), page, size);
        return success(result);
    }

    @PostMapping("/plan-templates")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanTemplateView> create(@RequestBody CreateTemplateRequest request,
                                                @AuthenticationPrincipal AuthUser actor) {
        PlanTemplate template = audited(actor, "architecture.plan-template.create", "POST",
                "/api/architecture/plan-templates",
                () -> service.createDraft(actor, new CreateTemplateCommand(
                        request == null ? null : request.name(),
                        request == null ? null : request.description())));
        return success(PlanTemplateView.from(template));
    }

    @GetMapping("/plan-templates/{id}")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PlanTemplateDetailView> detail(@PathVariable long id,
                                                      @AuthenticationPrincipal AuthUser actor) {
        return success(toDetail(service.detail(actor, id)));
    }

    @PutMapping("/plan-templates/{id}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanTemplateView> update(@PathVariable long id,
                                                @RequestBody UpdateTemplateRequest request,
                                                @AuthenticationPrincipal AuthUser actor) {
        PlanTemplate template = audited(actor, "architecture.plan-template.update", "PUT",
                "/api/architecture/plan-templates/" + id,
                () -> service.update(actor, id, new UpdateTemplateCommand(
                        request == null ? null : request.name(),
                        request == null ? null : request.description(),
                        request == null ? null : request.rowVersion())));
        return success(PlanTemplateView.from(template));
    }

    @PostMapping("/plan-templates/{id}/status")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PlanTemplateView> changeStatus(@PathVariable long id,
                                                      @RequestBody ChangeStatusRequest request,
                                                      @AuthenticationPrincipal AuthUser actor) {
        if (request == null || request.status() == null) {
            throw new BusinessException(40000, "目标状态不能为空");
        }
        PlanTemplate template = audited(actor, "architecture.plan-template.status", "POST",
                "/api/architecture/plan-templates/" + id + "/status",
                () -> service.changeStatus(actor, id, request.status()));
        return success(PlanTemplateView.from(template));
    }

    @PostMapping("/plan-templates/{id}/stages")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<StageView> addStage(@PathVariable long id, @RequestBody StageRequest request,
                                           @AuthenticationPrincipal AuthUser actor) {
        StageDraft stage = audited(actor, "architecture.plan-template.stage.add", "POST",
                "/api/architecture/plan-templates/" + id + "/stages",
                () -> service.addStage(actor, id, new StageCommand(
                        request == null ? null : request.name(),
                        request == null ? null : request.sortNo(),
                        request == null ? null : request.startOffsetDays(),
                        request == null ? null : request.durationDays())));
        return success(toStage(stage));
    }

    @PutMapping("/plan-templates/stages/{stageId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<StageView> updateStage(@PathVariable long stageId, @RequestBody StageRequest request,
                                              @AuthenticationPrincipal AuthUser actor) {
        StageDraft stage = audited(actor, "architecture.plan-template.stage.update", "PUT",
                "/api/architecture/plan-templates/stages/" + stageId,
                () -> service.updateStage(actor, stageId, new StageCommand(
                        request == null ? null : request.name(),
                        request == null ? null : request.sortNo(),
                        request == null ? null : request.startOffsetDays(),
                        request == null ? null : request.durationDays())));
        return success(toStage(stage));
    }

    @DeleteMapping("/plan-templates/stages/{stageId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> deleteStage(@PathVariable long stageId,
                                         @AuthenticationPrincipal AuthUser actor) {
        audited(actor, "architecture.plan-template.stage.delete", "DELETE",
                "/api/architecture/plan-templates/stages/" + stageId,
                () -> {
                    service.deleteStage(actor, stageId);
                    return null;
                });
        return success(null);
    }

    @PostMapping("/plan-templates/{id}/task-templates")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskTemplateView> addTaskTemplate(@PathVariable long id,
                                                         @RequestBody TaskTemplateRequest request,
                                                         @AuthenticationPrincipal AuthUser actor) {
        if (request == null || request.stageId() == null) {
            throw new BusinessException(40000, "归属环节不能为空");
        }
        TaskTemplateDraft task = audited(actor, "architecture.plan-template.task.add", "POST",
                "/api/architecture/plan-templates/" + id + "/task-templates",
                () -> service.addTaskTemplate(actor, id, request.stageId(), toCommand(request)));
        return success(toTask(task));
    }

    @PutMapping("/task-templates/{taskId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TaskTemplateView> updateTaskTemplate(@PathVariable long taskId,
                                                            @RequestBody TaskTemplateRequest request,
                                                            @AuthenticationPrincipal AuthUser actor) {
        TaskTemplateDraft task = audited(actor, "architecture.plan-template.task.update", "PUT",
                "/api/architecture/task-templates/" + taskId,
                () -> service.updateTaskTemplate(actor, taskId, toCommand(request)));
        return success(toTask(task));
    }

    @DeleteMapping("/task-templates/{taskId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> deleteTaskTemplate(@PathVariable long taskId,
                                                @AuthenticationPrincipal AuthUser actor) {
        audited(actor, "architecture.plan-template.task.delete", "DELETE",
                "/api/architecture/task-templates/" + taskId,
                () -> {
                    service.deleteTaskTemplate(actor, taskId);
                    return null;
                });
        return success(null);
    }

    @PostMapping("/plan-templates/{id}/publish")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<TemplateVersionView> publish(@PathVariable long id,
                                                    @RequestBody(required = false) PublishRequest request,
                                                    @AuthenticationPrincipal AuthUser actor) {
        TemplateVersion version = audited(actor, "architecture.plan-template.publish", "POST",
                "/api/architecture/plan-templates/" + id + "/publish",
                () -> service.publish(actor, id, request == null ? null : request.note()));
        return success(toVersion(version));
    }

    @PostMapping("/plan-templates/stages/{stageId}/dependencies")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<List<long[]>> setStageDependencies(@PathVariable long stageId,
                                                          @RequestBody StageDependencyRequest request,
                                                          @AuthenticationPrincipal AuthUser actor) {
        List<long[]> result = audited(actor, "architecture.plan-template.stage.dependency", "POST",
                "/api/architecture/plan-templates/stages/" + stageId + "/dependencies",
                () -> service.setStageDependencies(actor, stageId,
                        request == null ? List.of() : request.predecessorStageIds()).stream()
                        .map(pair -> new long[]{pair[0], pair[1]}).toList());
        return success(result);
    }

    @PostMapping("/task-templates/{taskTemplateId}/dependencies")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<List<long[]>> setTaskTemplateDependencies(@PathVariable long taskTemplateId,
                                                                 @RequestBody TaskTemplateDependencyRequest request,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        List<long[]> result = audited(actor, "architecture.plan-template.task.dependency", "POST",
                "/api/architecture/task-templates/" + taskTemplateId + "/dependencies",
                () -> service.setTaskTemplateDependencies(actor, taskTemplateId,
                        request == null ? List.of() : request.predecessorTaskTemplateIds()).stream()
                        .map(pair -> new long[]{pair[0], pair[1]}).toList());
        return success(result);
    }

    @GetMapping("/plan-templates/{id}/versions")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<TemplateVersionView>> versions(@PathVariable long id,
                                                           @AuthenticationPrincipal AuthUser actor) {
        return success(service.detail(actor, id).versions().stream()
                .map(PlanTemplateController::toVersion).toList());
    }

    private TaskTemplateCommand toCommand(TaskTemplateRequest request) {
        return new TaskTemplateCommand(request == null ? null : request.name(),
                request == null ? null : request.dimension(),
                request == null ? null : request.checkItems().stream()
                        .map(item -> new CheckItemDraft(item == null ? null : item.name(),
                                item == null ? 0 : item.sortNo(),
                                item == null ? null : item.guide()))
                        .toList(),
                request == null ? null : request.rowVersion());
    }

    private PlanTemplateDetailView toDetail(PlanTemplateDetail detail) {
        return new PlanTemplateDetailView(PlanTemplateView.from(detail.template()),
                detail.stages().stream().map(PlanTemplateController::toStage).toList(),
                detail.stageDependencies().stream().map(pair -> new long[]{pair[0], pair[1]}).toList(),
                detail.taskDependencies().stream().map(pair -> new long[]{pair[0], pair[1]}).toList(),
                detail.versions().stream().map(PlanTemplateController::toVersion).toList());
    }

    private static StageView toStage(StageDraft stage) {
        return new StageView(stage.id(), stage.name(), stage.sortNo(), stage.startOffsetDays(),
                stage.durationDays(),
                stage.tasks().stream().map(PlanTemplateController::toTask).toList());
    }

    private static TaskTemplateView toTask(TaskTemplateDraft task) {
        return new TaskTemplateView(task.id(), task.name(), task.dimension(),
                task.checkItems().stream().map(item -> new CheckItemView(item.name(), item.sortNo(),
                        item.guide())).toList(),
                task.status(), task.latestVersionNo(), task.rowVersion());
    }

    private static TemplateVersionView toVersion(TemplateVersion version) {
        return new TemplateVersionView(version.id(), version.versionNo(), version.contentJson(),
                version.note(), version.publishedBy(), version.publishedAt());
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
                    failure.getMessage() == null ? "搭建计划模板操作失败" : failure.getMessage(),
                    TraceId.getOrCreate());
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "搭建计划模板操作失败", TraceId.getOrCreate());
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
            log.warn("搭建计划模板审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    public record PlanTemplateView(long id, String name, String description, TemplateStatus status,
                                   int latestVersionNo, long rowVersion) {
        static PlanTemplateView from(PlanTemplate template) {
            return new PlanTemplateView(template.id(), template.name(), template.description(),
                    template.status(), template.latestVersionNo(), template.rowVersion());
        }
    }

    public record PlanTemplateDetailView(PlanTemplateView template, List<StageView> stages,
                                         List<long[]> stageDependencies, List<long[]> taskDependencies,
                                         List<TemplateVersionView> versions) {
    }

    public record StageView(long id, String name, int sortNo, Integer startOffsetDays,
                            Integer durationDays, List<TaskTemplateView> tasks) {
        public StageView(long id, String name, int sortNo, List<TaskTemplateView> tasks) {
            this(id, name, sortNo, null, null, tasks);
        }
    }

    public record TaskTemplateView(long id, String name, Dimension dimension, List<CheckItemView> checkItems,
                                   TemplateStatus status, int latestVersionNo, long rowVersion) {
    }

    public record CheckItemView(String name, int sortNo, String guide) {
        public CheckItemView(String name, int sortNo) {
            this(name, sortNo, null);
        }
    }

    public record TemplateVersionView(long id, int versionNo, String contentJson, String note,
                                      long publishedBy, LocalDateTime publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateTemplateRequest(String name, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateTemplateRequest(String name, String description, Long rowVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangeStatusRequest(TemplateStatus status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageRequest(String name, Integer sortNo, Integer startOffsetDays, Integer durationDays) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskTemplateRequest(Long stageId, String name, Dimension dimension,
                                      List<CheckItemView> checkItems, Long rowVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PublishRequest(String note) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageDependencyRequest(List<Long> predecessorStageIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskTemplateDependencyRequest(List<Long> predecessorTaskTemplateIds) {
    }
}
