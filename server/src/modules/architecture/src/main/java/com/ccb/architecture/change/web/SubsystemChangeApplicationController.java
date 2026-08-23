package com.ccb.architecture.change.web;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.service.ArchitectureSubsystemSubmissionService;
import com.ccb.architecture.change.service.SubsystemChangeService;
import com.ccb.architecture.change.service.SubsystemChangeService.AccessScope;
import com.ccb.architecture.change.service.SubsystemChangeService.ApplicationDetail;
import com.ccb.architecture.change.service.SubsystemChangeService.DraftUpdateCommand;
import com.ccb.architecture.change.service.SubsystemChangeService.LogicalApplicationCommand;
import com.ccb.architecture.change.service.SubsystemChangeService.LogicalDraftInput;
import com.ccb.architecture.change.service.SubsystemChangeService.PhysicalApplicationCommand;
import com.ccb.architecture.change.service.SubsystemChangeService.PhysicalDraftInput;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider.Suggestion;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider.SuggestionRequest;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 架构子系统变更工单的草稿 HTTP 边界。
 *
 * <p>提交和审批中取消通过真实工作流协调器执行；批准、退回、拒绝只由平台工作流任务与
 * 生命周期事件驱动，不能通过这里绕过工作流直接改写已发布主记录。</p>
 */
@RestController
@RequestMapping("/api/architecture/subsystem-change-applications")
public class SubsystemChangeApplicationController {
    private static final Logger log = LoggerFactory.getLogger(SubsystemChangeApplicationController.class);
    private static final String MANAGE_AUTHORITY = "architecture:manage";
    private static final Set<String> RESERVED_SUGGESTION_FIELDS = Set.of("tenantid", "applicantid", "accessscope");

    private final SubsystemChangeService service;
    private final ArchitectureSubsystemSubmissionService workflowService;
    private final SubsystemSuggestionProvider suggestionProvider;
    private final SystemOperationAudit operationAudit;

    public SubsystemChangeApplicationController(SubsystemChangeService service,
                                                ArchitectureSubsystemSubmissionService workflowService,
                                                SubsystemSuggestionProvider suggestionProvider,
                                                SystemOperationAudit operationAudit) {
        this.service = service;
        this.workflowService = workflowService;
        this.suggestionProvider = suggestionProvider;
        this.operationAudit = operationAudit;
    }

    /** 关键写操作统一审计：成功记录成功，业务失败记录失败；审计失败不阻断业务结果。 */
    private <T> T audited(AuthUser actor, String operationCode, String method, String path,
                          Supplier<T> action) {
        try {
            T result = action.get();
            recordAudit(actor, operationCode, method, path, null, TraceId.getOrCreate());
            return result;
        } catch (BusinessException failure) {
            recordAudit(actor, operationCode, method, path, businessAuditMessage(failure), TraceId.getOrCreate());
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "工单操作失败", TraceId.getOrCreate());
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
            log.warn("变更工单审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    private static String businessAuditMessage(BusinessException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "工单操作失败" : message;
    }

    /** view/apply/manage 都只能默认查看本人，manage 由认证权限提升为当前租户全部。 */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('architecture:view','architecture:apply','architecture:manage')")
    public ApiResponse<List<ApplicationSummaryResponse>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor,
            Authentication authentication) {
        List<ApplicationSummaryResponse> applications = service.list(actor, accessScope(authentication), status, limit, offset)
                .stream()
                .map(this::toSummary)
                .toList();
        return success(applications);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:view','architecture:apply','architecture:manage')")
    public ApiResponse<ApplicationDetailResponse> detail(@PathVariable long id,
                                                          @AuthenticationPrincipal AuthUser actor,
                                                          Authentication authentication) {
        return success(toDetail(service.detail(actor, accessScope(authentication), id)));
    }

    /**
     * 创建请求必须显式指定目标类型。租户、申请人和数据范围只从认证主体和权限派生，不能从正文覆盖。
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('architecture:apply','architecture:manage')")
    public ApiResponse<ApplicationDetailResponse> create(@RequestBody CreateApplicationRequest request,
                                                          @AuthenticationPrincipal AuthUser actor) {
        TargetKind targetKind = requiredTargetKind(request == null ? null : request.targetKind());
        ApplicationDetail detail = audited(actor, "architecture.subsystem-change.create", "POST",
                "/api/architecture/subsystem-change-applications", () -> switch (targetKind) {
                    case LOGICAL -> service.createLogical(actor, new LogicalApplicationCommand(
                            request.actionType(), request.targetId(), request.reason(), request.logicalDraft(),
                            request.physicalDrafts()));
                    case PHYSICAL -> service.createPhysical(actor, new PhysicalApplicationCommand(
                            request.actionType(), request.targetId(), request.reason(), request.physicalDraft()));
                });
        return success(toDetail(detail));
    }

    /** 仅 DRAFT/RETURNED 的字段可由 Service 编辑；审批中字段仍受 Service 状态机保护。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:apply','architecture:manage')")
    public ApiResponse<ApplicationDetailResponse> update(@PathVariable long id,
                                                          @RequestBody UpdateApplicationRequest request,
                                                          @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        ApplicationDetail detail = audited(actor, "architecture.subsystem-change.update", "PUT",
                "/api/architecture/subsystem-change-applications/" + id, () ->
                service.update(actor, AccessScope.OWN, id, rowVersion,
                        new DraftUpdateCommand(request.reason(), request.logicalDraft(), request.physicalDrafts())));
        return success(toDetail(detail));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('architecture:apply','architecture:manage')")
    public ApiResponse<ApplicationDetailResponse> cancel(@PathVariable long id,
                                                          @RequestBody CancelApplicationRequest request,
                                                          @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        ApplicationDetail detail = audited(actor, "architecture.subsystem-change.cancel", "POST",
                "/api/architecture/subsystem-change-applications/" + id + "/cancel", () ->
                workflowService.cancel(actor, id, rowVersion));
        return success(toDetail(detail));
    }

    /** 提交入口只接受行版本；业务值全部从当前工单草稿读取并固化快照。 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('architecture:apply','architecture:manage')")
    public ApiResponse<ApplicationDetailResponse> submit(@PathVariable long id,
                                                          @RequestBody SubmitApplicationRequest request,
                                                          @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        ApplicationDetail detail = audited(actor, "architecture.subsystem-change.submit", "POST",
                "/api/architecture/subsystem-change-applications/" + id + "/submit", () ->
                workflowService.submit(actor, id, rowVersion));
        return success(toDetail(detail));
    }

    /**
     * 首期建议接口只委派本地 Provider 生成候选值，不回写草稿，也不调用任何真实 AI 或网络服务。
     */
    @PostMapping("/suggestions")
    @PreAuthorize("hasAnyAuthority('architecture:apply','architecture:manage')")
    public ApiResponse<List<Suggestion>> suggestions(@RequestBody SuggestionPayload request,
                                                     @AuthenticationPrincipal AuthUser actor) {
        requireActor(actor);
        Map<String, String> fieldValues = request == null ? Map.of() : safeSuggestionFields(request.fieldValues());
        List<Suggestion> suggestions = suggestionProvider.suggest(new SuggestionRequest(fieldValues));
        return success(List.copyOf(suggestions == null ? List.of() : suggestions));
    }

    private AccessScope accessScope(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> MANAGE_AUTHORITY.equals(authority.getAuthority()))) {
            return AccessScope.MANAGE;
        }
        return AccessScope.OWN;
    }

    private TargetKind requiredTargetKind(String targetKind) {
        if (targetKind == null || targetKind.isBlank()) {
            throw badRequest("targetKind 不能为空，必须为 LOGICAL 或 PHYSICAL");
        }
        try {
            return TargetKind.valueOf(targetKind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("targetKind 非法，必须为 LOGICAL 或 PHYSICAL");
        }
    }

    private long requiredRowVersion(Long rowVersion) {
        if (rowVersion == null || rowVersion < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        return rowVersion;
    }

    private Map<String, String> safeSuggestionFields(Map<String, String> fieldValues) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safeFields = new LinkedHashMap<>();
        fieldValues.forEach((field, value) -> {
            if (field != null && !RESERVED_SUGGESTION_FIELDS.contains(normalizeFieldName(field))) {
                safeFields.put(field, value);
            }
        });
        return Map.copyOf(safeFields);
    }

    private String normalizeFieldName(String field) {
        return field.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    private ApplicationSummaryResponse toSummary(ChangeApplication application) {
        return new ApplicationSummaryResponse(application.id(), application.targetKind(), application.actionType(),
                application.targetId(), application.applicantId(), application.reason(), application.status(),
                application.currentBusinessRound(), application.currentWorkflowDefinitionId(),
                application.currentWorkflowVersionId(), application.currentWorkflowInstanceId(),
                application.currentPayloadDigest(), application.cancellationRequested(), application.rowVersion(),
                application.createdBy(), application.updatedBy(), application.createdAt(), application.updatedAt());
    }

    private ApplicationDetailResponse toDetail(ApplicationDetail detail) {
        return new ApplicationDetailResponse(toSummary(detail.application()), toLogicalDraft(detail.logicalDraft()),
                detail.physicalDrafts().stream().map(this::toPhysicalDraft).toList(),
                detail.history().stream().map(this::toHistory).toList());
    }

    private LogicalDraftResponse toLogicalDraft(LogicalDraft draft) {
        if (draft == null) {
            return null;
        }
        return new LogicalDraftResponse(draft.sourceLogicalSubsystemId(), draft.shortName(), draft.name(),
                draft.businessOrgId(), draft.deploymentPlatformCode(), draft.systemTypeCode(),
                draft.systemOwnershipCode(), draft.contactUserId(), draft.description(), draft.remark(),
                draft.sortNo(), draft.reservedNumberSequence(), draft.sourceRowVersion(), draft.draftRevision(),
                draft.submittedSnapshotJson(), draft.createdAt(), draft.updatedAt());
    }

    private PhysicalDraftResponse toPhysicalDraft(PhysicalDraft draft) {
        return new PhysicalDraftResponse(draft.lineNo(), draft.sourcePhysicalSubsystemId(),
                draft.targetLogicalSubsystemId(), draft.shortName(), draft.name(), draft.englishName(),
                draft.businessGroupName(), draft.responsibleTeamOrgId(), draft.responsibleTeamNameSnapshot(),
                draft.runtimeCode(), draft.systemLevelCode(), draft.developmentFrameworkCode(), draft.ownerUserId(),
                draft.description(), draft.remark(), draft.reservedNumberSlot(), draft.sourceRowVersion(),
                draft.draftRevision(), draft.submittedSnapshotJson(), draft.createdAt(), draft.updatedAt());
    }

    private ChangeHistoryResponse toHistory(ChangeHistoryEvent event) {
        return new ChangeHistoryResponse(event.id(), event.eventType(), event.fromStatus(), event.toStatus(),
                event.businessRound(), event.summary(), event.snapshotJson(), event.diffJson(), event.operatorId(),
                event.occurredAt());
    }

    /** 输入 DTO 不含 tenantId、applicantId 或 accessScope；这些字段即使出现在 JSON 中也会被忽略。 */
    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record CreateApplicationRequest(String targetKind, ActionType actionType, Long targetId, String reason,
                                           LogicalDraftInput logicalDraft,
                                           List<PhysicalDraftInput> physicalDrafts,
                                           PhysicalDraftInput physicalDraft) {
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record UpdateApplicationRequest(Long rowVersion, String reason, LogicalDraftInput logicalDraft,
                                           List<PhysicalDraftInput> physicalDrafts) {
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record CancelApplicationRequest(Long rowVersion) {
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record SubmitApplicationRequest(Long rowVersion) {
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record SuggestionPayload(Map<String, String> fieldValues) {
    }

    /** 列表返回的工单主数据投影，不暴露服务端租户标识。 */
    public record ApplicationSummaryResponse(long id, TargetKind targetKind, ActionType actionType, Long targetId,
                                             long applicantId, String reason, ApplicationStatus status,
                                             int currentBusinessRound, Long currentWorkflowDefinitionId,
                                             Long currentWorkflowVersionId, Long currentWorkflowInstanceId,
                                             String currentPayloadDigest, boolean cancellationRequested,
                                             long rowVersion, long createdBy, long updatedBy,
                                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 详情聚合草稿和不可变历史，同样不暴露 tenantId。 */
    public record ApplicationDetailResponse(ApplicationSummaryResponse application,
                                            LogicalDraftResponse logicalDraft,
                                            List<PhysicalDraftResponse> physicalDrafts,
                                            List<ChangeHistoryResponse> history) {
        public ApplicationDetailResponse {
            physicalDrafts = List.copyOf(physicalDrafts == null ? List.of() : physicalDrafts);
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    public record LogicalDraftResponse(Long sourceLogicalSubsystemId, String shortName, String name,
                                       long businessOrgId, String deploymentPlatformCode, String systemTypeCode,
                                       String systemOwnershipCode, long contactUserId, String description,
                                       String remark, int sortNo, Integer reservedNumberSequence,
                                       Long sourceRowVersion, int draftRevision, String submittedSnapshotJson,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PhysicalDraftResponse(int lineNo, Long sourcePhysicalSubsystemId,
                                        Long targetLogicalSubsystemId, String shortName, String name,
                                        String englishName, String businessGroupName, long responsibleTeamOrgId,
                                        String responsibleTeamNameSnapshot, String runtimeCode,
                                        String systemLevelCode, String developmentFrameworkCode, Long ownerUserId,
                                        String description, String remark, String reservedNumberSlot,
                                        Long sourceRowVersion, int draftRevision, String submittedSnapshotJson,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record ChangeHistoryResponse(long id, String eventType, ApplicationStatus fromStatus,
                                        ApplicationStatus toStatus, int businessRound, String summary,
                                        String snapshotJson, String diffJson, long operatorId,
                                        LocalDateTime occurredAt) {
    }
}
