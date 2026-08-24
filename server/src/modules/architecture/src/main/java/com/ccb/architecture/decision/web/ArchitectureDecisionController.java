package com.ccb.architecture.decision.web;

import com.ccb.architecture.decision.model.DecisionModels.ActionItem;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.FirstHandlingOutcome;
import com.ccb.architecture.decision.model.DecisionModels.MaterialCommand;
import com.ccb.architecture.decision.model.DecisionModels.MaterialRecord;
import com.ccb.architecture.decision.model.DecisionModels.MatterCommand;
import com.ccb.architecture.decision.model.DecisionModels.MatterQuery;
import com.ccb.architecture.decision.model.DecisionModels.PublicationIntent;
import com.ccb.architecture.decision.model.DecisionModels.ReviewCommand;
import com.ccb.architecture.decision.model.DecisionModels.ReviewMethod;
import com.ccb.architecture.decision.model.DecisionModels.ReviewRecord;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionTarget;
import com.ccb.architecture.decision.service.ArchitectureDecisionService;
import com.ccb.architecture.decision.service.ArchitectureDecisionService.AccessLevel;
import com.ccb.architecture.decision.service.ArchitectureDecisionService.ChainLink;
import com.ccb.architecture.decision.service.ArchitectureDecisionService.ConclusionView;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * 架构决策事项 HTTP 边界。
 *
 * <p>权限分级：view 查询；propose 提交和维护本人事项；review 首次处理与评审；
 * manage 发布结论。结论发布只能经工作流 APPROVED 事件落库，本控制器不提供
 * 已发布结论的任何修改或删除路径。</p>
 */
@RestController
@RequestMapping("/api/architecture/decisions")
public class ArchitectureDecisionController {
    private static final Logger log = LoggerFactory.getLogger(ArchitectureDecisionController.class);
    private static final String VIEW_AUTHORITY = "hasAnyAuthority('architecture:decision:view','architecture:decision:propose','architecture:decision:review','architecture:decision:manage')";
    private static final String PROPOSE_AUTHORITY = "hasAnyAuthority('architecture:decision:propose','architecture:decision:review','architecture:decision:manage')";
    private static final String REVIEW_AUTHORITY = "hasAnyAuthority('architecture:decision:review','architecture:decision:manage')";
    private static final String MANAGE_AUTHORITY = "hasAuthority('architecture:decision:manage')";
    private static final String MANAGE_AUTHORITY_NAME = "architecture:decision:manage";

    private final ArchitectureDecisionService service;
    private final SystemReferenceQuery referenceQuery;
    private final SystemOperationAudit operationAudit;

    public ArchitectureDecisionController(ArchitectureDecisionService service,
                                          SystemReferenceQuery referenceQuery,
                                          SystemOperationAudit operationAudit) {
        this.service = service;
        this.referenceQuery = referenceQuery;
        this.operationAudit = operationAudit;
    }

    // ---------- 查询 ----------

    @GetMapping("/options/types")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<TypeResponse>> types(@AuthenticationPrincipal AuthUser user) {
        return success(service.types(user).stream()
                .map(option -> new TypeResponse(option.code(), option.label())).toList());
    }

    @GetMapping("/options/users")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<UserReferenceResponse>> users(@AuthenticationPrincipal AuthUser user,
                                                          @RequestParam(required = false) String keyword) {
        PageResult<SystemUserReference> result = referenceQuery.searchActiveUsers(user,
                new PageQuery(1, 50), keyword == null ? null : keyword.trim());
        return success(result.records().stream()
                .map(item -> new UserReferenceResponse(item.id(), item.displayName(), item.username()))
                .toList());
    }

    @GetMapping
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PageResult<MatterSummaryResponse>> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean firstHandlingOverdue) {
        PageResult<DecisionMatter> result = service.list(user, new PageQuery(page, size),
                new MatterQuery(keyword, typeCode, status, firstHandlingOverdue, null));
        return success(new PageResult<>(
                result.records().stream().map(MatterSummaryResponse::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<MatterDetailResponse> detail(@AuthenticationPrincipal AuthUser user,
                                                    @PathVariable long id) {
        return success(MatterDetailResponse.from(service.detail(user, id)));
    }

    @GetMapping("/{id}/materials")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<MaterialResponse>> materials(@AuthenticationPrincipal AuthUser user,
                                                         @PathVariable long id) {
        return success(service.materials(user, id).stream().map(MaterialResponse::from).toList());
    }

    @GetMapping("/{id}/reviews")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<ReviewResponse>> reviews(@AuthenticationPrincipal AuthUser user,
                                                     @PathVariable long id) {
        return success(service.reviews(user, id).stream().map(ReviewResponse::from).toList());
    }

    @GetMapping("/{id}/reviews/{reviewId}/participants")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<ParticipantResponse>> reviewParticipants(@AuthenticationPrincipal AuthUser user,
                                                                     @PathVariable long id,
                                                                     @PathVariable long reviewId) {
        return success(service.reviewParticipants(user, id, reviewId).stream()
                .map(row -> new ParticipantResponse(((Number) row.get("user_id")).longValue(),
                        row.get("user_name") == null ? null : String.valueOf(row.get("user_name"))))
                .toList());
    }

    @GetMapping("/{id}/reviews/{reviewId}/action-items")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<ActionItemResponse>> reviewActionItems(@AuthenticationPrincipal AuthUser user,
                                                                   @PathVariable long id,
                                                                   @PathVariable long reviewId) {
        return success(service.reviewActionItems(user, id, reviewId).stream()
                .map(ActionItemResponse::from).toList());
    }

    @GetMapping("/conclusions")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PageResult<ConclusionView>> conclusions(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String effectiveStatus) {
        List<ConclusionView> views = service.conclusions(user, new PageQuery(page, size), effectiveStatus);
        return success(new PageResult<>(views, views.size(), page, size));
    }

    @GetMapping("/conclusions/{conclusionId}/chain")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<ConclusionView> conclusionChain(@AuthenticationPrincipal AuthUser user,
                                                       @PathVariable long conclusionId) {
        return success(service.conclusionChain(user, conclusionId));
    }

    // ---------- 提交与协作 ----------

    @PostMapping
    @PreAuthorize(PROPOSE_AUTHORITY)
    public ApiResponse<MatterDetailResponse> create(@AuthenticationPrincipal AuthUser user,
                                                    @RequestBody MatterRequest request) {
        return success(MatterDetailResponse.from(
                audited(user, "architecture.decision.create", "POST", "/api/architecture/decisions",
                        () -> service.create(user, request.toCommand()))));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PROPOSE_AUTHORITY)
    public ApiResponse<MatterDetailResponse> update(@AuthenticationPrincipal AuthUser user,
                                                    Authentication authentication,
                                                    @PathVariable long id,
                                                    @RequestBody MatterUpdateRequest request) {
        return success(MatterDetailResponse.from(
                audited(user, "architecture.decision.update", "PUT", "/api/architecture/decisions/" + id,
                        () -> service.update(user, accessLevel(authentication), id,
                                request.rowVersion(), request.toCommand()))));
    }

    @PostMapping("/{id}/materials")
    @PreAuthorize(PROPOSE_AUTHORITY)
    public ApiResponse<MaterialResponse> addMaterial(@AuthenticationPrincipal AuthUser user,
                                                     Authentication authentication,
                                                     @PathVariable long id,
                                                     @RequestBody MaterialRequest request) {
        return success(MaterialResponse.from(
                audited(user, "architecture.decision.material.add", "POST",
                        "/api/architecture/decisions/" + id + "/materials",
                        () -> service.addMaterial(user, accessLevel(authentication), id, request.toCommand()))));
    }

    @PostMapping("/{id}/type")
    @PreAuthorize(REVIEW_AUTHORITY)
    public ApiResponse<MatterDetailResponse> setType(@AuthenticationPrincipal AuthUser user,
                                                     Authentication authentication,
                                                     @PathVariable long id,
                                                     @RequestBody SetTypeRequest request) {
        return success(MatterDetailResponse.from(
                audited(user, "architecture.decision.type.set", "POST",
                        "/api/architecture/decisions/" + id + "/type",
                        () -> {
                            service.setMatterType(user, accessLevel(authentication), id,
                                    request.rowVersion(), request.typeCode());
                            return service.detail(user, id);
                        })));
    }

    // ---------- 首次处理与评审 ----------

    @PostMapping("/{id}/first-handling")
    @PreAuthorize(REVIEW_AUTHORITY)
    public ApiResponse<MatterDetailResponse> firstHandling(@AuthenticationPrincipal AuthUser user,
                                                           @PathVariable long id,
                                                           @RequestBody FirstHandlingRequest request) {
        return success(MatterDetailResponse.from(
                audited(user, "architecture.decision.first-handling", "POST",
                        "/api/architecture/decisions/" + id + "/first-handling",
                        () -> service.firstHandling(user, AccessLevel.REVIEW, id,
                                request.rowVersion(), request.outcome(), request.comment(),
                                request.reviewMode()))));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize(PROPOSE_AUTHORITY)
    public ApiResponse<MatterDetailResponse> resubmit(@AuthenticationPrincipal AuthUser user,
                                                      Authentication authentication,
                                                      @PathVariable long id,
                                                      @RequestBody RowVersionRequest request) {
        return success(MatterDetailResponse.from(
                audited(user, "architecture.decision.resubmit", "POST",
                        "/api/architecture/decisions/" + id + "/resubmit",
                        () -> service.resubmit(user, accessLevel(authentication), id,
                                request.rowVersion()))));
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize(REVIEW_AUTHORITY)
    public ApiResponse<ReviewResponse> recordReview(@AuthenticationPrincipal AuthUser user,
                                                    @PathVariable long id,
                                                    @RequestBody ReviewRequest request) {
        return success(ReviewResponse.from(
                audited(user, "architecture.decision.review.record", "POST",
                        "/api/architecture/decisions/" + id + "/reviews",
                        () -> service.recordReview(user, AccessLevel.REVIEW, id, request.toCommand()))));
    }

    @PutMapping("/{id}/reviews/{reviewId}")
    @PreAuthorize(REVIEW_AUTHORITY)
    public ApiResponse<ReviewResponse> updateReview(@AuthenticationPrincipal AuthUser user,
                                                    @PathVariable long id,
                                                    @PathVariable long reviewId,
                                                    @RequestBody ReviewRequest request) {
        return success(ReviewResponse.from(
                audited(user, "architecture.decision.review.update", "PUT",
                        "/api/architecture/decisions/" + id + "/reviews/" + reviewId,
                        () -> service.updateReview(user, AccessLevel.REVIEW, id, reviewId,
                                request.toCommand()))));
    }

    @PostMapping("/{id}/reviews/{reviewId}/action-items/{actionItemId}/complete")
    @PreAuthorize(REVIEW_AUTHORITY)
    public ApiResponse<ActionItemResponse> completeActionItem(@AuthenticationPrincipal AuthUser user,
                                                              @PathVariable long id,
                                                              @PathVariable long reviewId,
                                                              @PathVariable long actionItemId) {
        return success(ActionItemResponse.from(
                audited(user, "architecture.decision.action-item.complete", "POST",
                        "/api/architecture/decisions/" + id + "/reviews/" + reviewId
                                + "/action-items/" + actionItemId + "/complete",
                        () -> service.completeActionItem(user, AccessLevel.REVIEW, id, reviewId,
                                actionItemId))));
    }

    // ---------- 结论发布 ----------

    @PostMapping("/{id}/publication/prepare")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<PublicationIntentResponse> preparePublication(@AuthenticationPrincipal AuthUser user,
                                                                     @PathVariable long id,
                                                                     @RequestBody PreparePublicationRequest request) {
        return success(PublicationIntentResponse.from(
                audited(user, "architecture.decision.publication.prepare", "POST",
                        "/api/architecture/decisions/" + id + "/publication/prepare",
                        () -> service.preparePublication(user, id, request.rowVersion(),
                                request.reviewId(), request.toTargets()))));
    }

    @PostMapping("/{id}/publication/start")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<MatterDetailResponse> startPublication(@AuthenticationPrincipal AuthUser user,
                                                              @PathVariable long id,
                                                              @RequestBody RowVersionRequest request) {
        return success(MatterDetailResponse.from(
                audited(user, "architecture.decision.publication.start", "POST",
                        "/api/architecture/decisions/" + id + "/publication/start",
                        () -> service.startPublication(user, id, request.rowVersion()))));
    }

    // ---------- 附件 ----------

    @GetMapping("/{id}/attachments")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<AttachmentResponse>> attachments(@AuthenticationPrincipal AuthUser user,
                                                             @PathVariable long id) {
        return success(service.attachments(user, id).stream()
                .map(AttachmentResponse::from).toList());
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(PROPOSE_AUTHORITY)
    public ApiResponse<Void> bindAttachment(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable long id,
                                            @RequestBody BindAttachmentRequest request) {
        audited(user, "architecture.decision.attachment.bind", "POST",
                "/api/architecture/decisions/" + id + "/attachments",
                () -> {
                    service.bindAttachment(user, id, request.attachmentId());
                    return null;
                });
        return success(null);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(PROPOSE_AUTHORITY)
    public ApiResponse<Void> deleteAttachment(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable long id,
                                              @PathVariable long attachmentId) {
        audited(user, "architecture.decision.attachment.delete", "DELETE",
                "/api/architecture/decisions/" + id + "/attachments/" + attachmentId,
                () -> {
                    service.deleteAttachment(user, id, attachmentId);
                    return null;
                });
        return success(null);
    }

    // ---------- 工具 ----------

    private AccessLevel accessLevel(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> MANAGE_AUTHORITY_NAME.equals(authority.getAuthority()))) {
            return AccessLevel.MANAGE;
        }
        if (authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "architecture:decision:review".equals(authority.getAuthority()))) {
            return AccessLevel.REVIEW;
        }
        return AccessLevel.PROPOSE;
    }

    private <T> T audited(AuthUser actor, String operationCode, String method, String path,
                          Supplier<T> action) {
        try {
            T result = action.get();
            recordAudit(actor, operationCode, method, path, null);
            return result;
        } catch (BusinessException failure) {
            recordAudit(actor, operationCode, method, path, safeMessage(failure));
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "架构决策操作失败");
            throw failure;
        }
    }

    private void recordAudit(AuthUser actor, String operationCode, String method, String path,
                             String errorMessage) {
        try {
            SystemOperationAuditCommand command = new SystemOperationAuditCommand(
                    actor, operationCode, method, path, errorMessage, TraceId.getOrCreate());
            if (errorMessage == null) {
                operationAudit.recordSuccess(command);
            } else {
                operationAudit.recordFailure(command);
            }
        } catch (RuntimeException auditFailure) {
            log.warn("架构决策审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    private static String safeMessage(BusinessException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "架构决策操作失败" : message;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(com.ccb.common.exception.ErrorCode.BAD_REQUEST, message);
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(com.ccb.common.exception.ErrorCode.BAD_REQUEST, message);
        }
    }

    // ---------- 请求与响应 ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatterRequest(String title, String problem) {
        MatterCommand toCommand() {
            return new MatterCommand(title, problem);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatterUpdateRequest(long rowVersion, String title, String problem) {
        MatterCommand toCommand() {
            return new MatterCommand(title, problem);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MaterialRequest(String kind, String content) {
        MaterialCommand toCommand() {
            return new MaterialCommand(
                    parseEnum(kind, com.ccb.architecture.decision.model.DecisionModels.MaterialKind.class,
                            "材料类别仅支持 SOLUTION、IMPACT、DISPUTE 或 OTHER"),
                    content);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SetTypeRequest(long rowVersion, String typeCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FirstHandlingRequest(long rowVersion, FirstHandlingOutcome outcome,
                                       ReviewMethod reviewMode, String comment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RowVersionRequest(long rowVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewRequest(String method, LocalDateTime reviewedAt, String processMaterialSummary,
                                String keyOpinion, String conclusionContent, String conclusionRationale,
                                List<Long> participantUserIds, List<ActionItemRequest> actionItems) {
        ReviewCommand toCommand() {
            return new ReviewCommand(
                    parseEnum(method, ReviewMethod.class, "评审方式仅支持 ASYNC 或 MEETING"),
                    reviewedAt, processMaterialSummary, keyOpinion, conclusionContent, conclusionRationale,
                    participantUserIds, actionItems == null ? List.of() : actionItems.stream()
                    .map(item -> new com.ccb.architecture.decision.model.DecisionModels.ActionItemInput(
                            item.id(), item.content(), item.ownerUserId(), item.ownerName()))
                    .toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionItemRequest(Long id, String content, Long ownerUserId, String ownerName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreparePublicationRequest(long rowVersion, long reviewId,
                                            List<TargetRequest> targets) {
        List<SupersessionTarget> toTargets() {
            if (targets == null) {
                return List.of();
            }
            return targets.stream()
                    .map(target -> new SupersessionTarget(target.conclusionId(),
                            parseEnum(target.kind(),
                                    com.ccb.architecture.decision.model.DecisionModels.SupersessionKind.class,
                                    "替代关系仅支持 SUPERSEDE 或 PARTIALLY_REVISE")))
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TargetRequest(long conclusionId, String kind) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BindAttachmentRequest(long attachmentId) {
    }

    public record TypeResponse(String code, String label) {
    }

    public record UserReferenceResponse(long id, String displayName, String username) {
    }

    public record MatterSummaryResponse(
            long id, String matterNo, String title, String typeCode, String status,
            LocalDateTime receivedAt, LocalDate firstHandlingDeadline, Boolean firstHandlingOverdue,
            String firstHandlingOutcome, String reviewMode, String proposerName,
            LocalDateTime updatedAt) {
        static MatterSummaryResponse from(DecisionMatter matter) {
            return new MatterSummaryResponse(matter.id(), matter.matterNo(), matter.title(),
                    matter.typeCode(), matter.status().name(), matter.receivedAt(),
                    matter.firstHandlingDeadline(), firstHandlingOverdue(matter),
                    matter.firstHandlingOutcome() == null ? null : matter.firstHandlingOutcome().name(),
                    matter.reviewMode() == null ? null : matter.reviewMode().name(),
                    matter.proposerName(), matter.updatedAt());
        }

        private static Boolean firstHandlingOverdue(DecisionMatter matter) {
            if (matter.status() != com.ccb.architecture.decision.model.DecisionModels.MatterStatus.SUBMITTED
                    && matter.status() != com.ccb.architecture.decision.model.DecisionModels.MatterStatus.RETURNED_FOR_INFO) {
                return false;
            }
            return matter.firstHandlingDeadline() != null
                    && matter.firstHandlingDeadline().isBefore(LocalDate.now());
        }
    }

    public record MatterDetailResponse(
            long id, String matterNo, String title, String problem, String typeCode, String status,
            LocalDateTime receivedAt, LocalDate firstHandlingDeadline, Boolean firstHandlingOverdue,
            String firstHandlingOutcome, String firstHandlingComment, LocalDateTime firstHandledAt,
            Long firstHandlerId, String firstHandlerName, String reviewMode,
            long proposerId, String proposerName, long submitterId, String submitterName,
            LocalDateTime publicationPreparedAt, Long publicationPreparedBy,
            int currentBusinessRound, Long currentWorkflowInstanceId, long rowVersion,
            String createdByName, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static MatterDetailResponse from(DecisionMatter matter) {
            return new MatterDetailResponse(matter.id(), matter.matterNo(), matter.title(),
                    matter.problem(), matter.typeCode(), matter.status().name(), matter.receivedAt(),
                    matter.firstHandlingDeadline(), firstHandlingOverdue(matter),
                    matter.firstHandlingOutcome() == null ? null : matter.firstHandlingOutcome().name(),
                    matter.firstHandlingComment(), matter.firstHandledAt(),
                    matter.firstHandlerId(), matter.firstHandlerName(),
                    matter.reviewMode() == null ? null : matter.reviewMode().name(),
                    matter.proposerId(), matter.proposerName(), matter.submitterId(), matter.submitterName(),
                    matter.publicationPreparedAt(), matter.publicationPreparedBy(),
                    matter.currentBusinessRound(), matter.currentWorkflowInstanceId(),
                    matter.rowVersion(), matter.createdByName(), matter.createdAt(), matter.updatedAt());
        }

        private static Boolean firstHandlingOverdue(DecisionMatter matter) {
            if (matter.status() != com.ccb.architecture.decision.model.DecisionModels.MatterStatus.SUBMITTED
                    && matter.status() != com.ccb.architecture.decision.model.DecisionModels.MatterStatus.RETURNED_FOR_INFO) {
                return false;
            }
            return matter.firstHandlingDeadline() != null
                    && matter.firstHandlingDeadline().isBefore(LocalDate.now());
        }
    }

    public record ParticipantResponse(long userId, String displayName) {
    }

    public record MaterialResponse(long id, long matterId, String kind, String content,
                                   String createdByName, LocalDateTime createdAt) {
        static MaterialResponse from(MaterialRecord record) {
            return new MaterialResponse(record.id(), record.matterId(), record.kind().name(),
                    record.content(), record.createdByName(), record.createdAt());
        }
    }

    public record ReviewResponse(
            long id, long matterId, int reviewNo, String method, LocalDateTime reviewedAt,
            String processMaterialSummary, String keyOpinion, String conclusionContent,
            String conclusionRationale, String createdByName, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        static ReviewResponse from(ReviewRecord review) {
            return new ReviewResponse(review.id(), review.matterId(), review.reviewNo(),
                    review.method().name(), review.reviewedAt(), review.processMaterialSummary(),
                    review.keyOpinion(), review.conclusionContent(), review.conclusionRationale(),
                    review.createdByName(), review.createdAt(), review.updatedAt());
        }
    }

    public record ActionItemResponse(long id, long reviewId, String content, Long ownerUserId,
                                     String ownerName, String status, LocalDateTime createdAt) {
        static ActionItemResponse from(ActionItem item) {
            return new ActionItemResponse(item.id(), item.reviewId(), item.content(),
                    item.ownerUserId(), item.ownerName(), item.status().name(), item.createdAt());
        }
    }

    public record PublicationIntentResponse(long matterId, long reviewId, List<TargetResponse> targets,
                                            String payloadDigest, String preparedByName,
                                            LocalDateTime preparedAt) {
        static PublicationIntentResponse from(PublicationIntent intent) {
            return new PublicationIntentResponse(intent.matterId(), intent.reviewId(),
                    intent.targets().stream()
                            .map(target -> new TargetResponse(target.conclusionId(), target.kind().name()))
                            .toList(),
                    intent.payloadDigest(), intent.preparedByName(), intent.preparedAt());
        }
    }

    public record TargetResponse(long conclusionId, String kind) {
    }

    public record AttachmentResponse(long id, String fileName, String contentType, long size,
                                     String uploaderName, String createdAt) {
        static AttachmentResponse from(com.ccb.attachment.model.AttachmentItem item) {
            return new AttachmentResponse(item.id(), item.fileName(), item.contentType(), item.size(),
                    item.uploaderName(), item.createdAt());
        }
    }

    private static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

}
