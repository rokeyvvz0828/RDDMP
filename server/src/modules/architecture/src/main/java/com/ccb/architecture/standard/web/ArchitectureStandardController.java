package com.ccb.architecture.standard.web;

import com.ccb.architecture.standard.model.StandardModels.StandardCommand;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.model.StandardModels.StandardQuery;
import com.ccb.architecture.standard.model.StandardModels.StandardVersion;
import com.ccb.architecture.standard.service.ArchitectureStandardService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.system.capability.SystemParameterReference;
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

/**
 * 架构规范文档 HTTP 边界。
 *
 * <p>查看需要 {@code architecture:standard:view}；发布与维护需要
 * {@code architecture:standard:manage}。关键写操作统一审计，审计失败不阻断业务结果。</p>
 */
@RestController
@RequestMapping("/api/architecture/standards")
public class ArchitectureStandardController {
    private static final Logger log = LoggerFactory.getLogger(ArchitectureStandardController.class);
    private static final String VIEW_AUTHORITY = "hasAuthority('architecture:standard:view')";
    private static final String MANAGE_AUTHORITY = "hasAuthority('architecture:standard:manage')";

    private final ArchitectureStandardService service;
    private final SystemOperationAudit operationAudit;

    public ArchitectureStandardController(ArchitectureStandardService service,
                                          SystemOperationAudit operationAudit) {
        this.service = service;
        this.operationAudit = operationAudit;
    }

    @GetMapping("/categories")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<CategoryResponse>> categories(@AuthenticationPrincipal AuthUser user) {
        List<SystemParameterReference> options = service.categories(user);
        return success(options.stream()
                .map(option -> new CategoryResponse(option.code(), option.label()))
                .toList());
    }

    @GetMapping
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<PageResult<DocumentSummaryResponse>> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String status) {
        PageResult<StandardDocument> result = service.list(user, new PageQuery(page, size),
                new StandardQuery(title, categoryCode, status));
        return success(new PageResult<>(
                result.records().stream().map(DocumentSummaryResponse::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<DocumentDetailResponse> detail(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable long id) {
        return success(DocumentDetailResponse.from(service.detail(user, id)));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<VersionResponse>> versions(@AuthenticationPrincipal AuthUser user,
                                                       @PathVariable long id) {
        return success(service.versions(user, id).stream().map(VersionResponse::from).toList());
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize(VIEW_AUTHORITY)
    public ApiResponse<List<AttachmentResponse>> attachments(@AuthenticationPrincipal AuthUser user,
                                                             @PathVariable long id) {
        return success(service.attachments(user, id).stream().map(AttachmentResponse::from).toList());
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> bindAttachment(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable long id,
                                            @RequestBody BindAttachmentRequest request) {
        audited(user, "architecture.standard.attachment.bind", "POST",
                "/api/architecture/standards/" + id + "/attachments",
                () -> {
                    service.bindAttachment(user, id, request.attachmentId());
                    return null;
                });
        return success(null);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> deleteAttachment(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable long id,
                                              @PathVariable long attachmentId) {
        audited(user, "architecture.standard.attachment.delete", "DELETE",
                "/api/architecture/standards/" + id + "/attachments/" + attachmentId,
                () -> {
                    service.deleteAttachment(user, id, attachmentId);
                    return null;
                });
        return success(null);
    }

    @PostMapping
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<DocumentDetailResponse> create(@AuthenticationPrincipal AuthUser user,
                                                      @RequestBody CreateDocumentRequest request) {
        return success(DocumentDetailResponse.from(
                audited(user, "architecture.standard.create", "POST", "/api/architecture/standards",
                        () -> service.create(user, request.toCommand()))));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<DocumentDetailResponse> update(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable long id,
                                                      @RequestBody UpdateDocumentRequest request) {
        return success(DocumentDetailResponse.from(
                audited(user, "architecture.standard.update", "PUT",
                        "/api/architecture/standards/" + id,
                        () -> service.update(user, id, request.rowVersion(), request.toCommand()))));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<VersionResponse> publish(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable long id,
                                                @RequestBody PublishDocumentRequest request) {
        return success(VersionResponse.from(
                audited(user, "architecture.standard.publish", "POST",
                        "/api/architecture/standards/" + id + "/publish",
                        () -> service.publish(user, id, request.rowVersion()))));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<DocumentDetailResponse> offline(@AuthenticationPrincipal AuthUser user,
                                                       @PathVariable long id,
                                                       @RequestBody PublishDocumentRequest request) {
        return success(DocumentDetailResponse.from(
                audited(user, "architecture.standard.offline", "POST",
                        "/api/architecture/standards/" + id + "/offline",
                        () -> service.offline(user, id, request.rowVersion()))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE_AUTHORITY)
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable long id,
                                    @RequestParam long rowVersion) {
        audited(user, "architecture.standard.delete", "DELETE", "/api/architecture/standards/" + id,
                () -> {
                    service.delete(user, id, rowVersion);
                    return null;
                });
        return success(null);
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
            recordAudit(actor, operationCode, method, path, "架构规范操作失败");
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
            log.warn("架构规范审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    private static String safeMessage(BusinessException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "架构规范操作失败" : message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateDocumentRequest(String title, String categoryCode, String summary, String content) {
        StandardCommand toCommand() {
            return new StandardCommand(title, categoryCode, summary, content);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateDocumentRequest(long rowVersion, String title, String categoryCode,
                                        String summary, String content) {
        StandardCommand toCommand() {
            return new StandardCommand(title, categoryCode, summary, content);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PublishDocumentRequest(long rowVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BindAttachmentRequest(long attachmentId) {
    }

    public record AttachmentResponse(long id, String fileName, String contentType, long size,
                                     String uploaderName, String createdAt) {
        static AttachmentResponse from(com.ccb.attachment.model.AttachmentItem item) {
            return new AttachmentResponse(item.id(), item.fileName(), item.contentType(), item.size(),
                    item.uploaderName(), item.createdAt());
        }
    }

    public record CategoryResponse(String code, String label) {
    }

    public record DocumentSummaryResponse(
            long id, String title, String categoryCode, String status,
            int currentVersion, LocalDateTime publishedAt, String publishedByName,
            LocalDateTime updatedAt) {
        static DocumentSummaryResponse from(StandardDocument document) {
            return new DocumentSummaryResponse(document.id(), document.title(), document.categoryCode(),
                    document.status().name(), document.currentVersion(), document.publishedAt(),
                    document.publishedByName(), document.updatedAt());
        }
    }

    public record DocumentDetailResponse(
            long id, String title, String categoryCode, String summary, String content,
            String status, int currentVersion, LocalDateTime publishedAt, Long publishedBy,
            String publishedByName, long rowVersion, String createdByName, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        static DocumentDetailResponse from(StandardDocument document) {
            return new DocumentDetailResponse(document.id(), document.title(), document.categoryCode(),
                    document.summary(), document.content(), document.status().name(),
                    document.currentVersion(), document.publishedAt(), document.publishedBy(),
                    document.publishedByName(), document.rowVersion(), document.createdByName(),
                    document.createdAt(), document.updatedAt());
        }
    }

    public record VersionResponse(
            long id, long documentId, int versionNo, String title, String categoryCode,
            String summary, String content, LocalDateTime publishedAt, long publishedBy,
            String publishedByName) {
        static VersionResponse from(StandardVersion version) {
            return new VersionResponse(version.id(), version.documentId(), version.versionNo(),
                    version.title(), version.categoryCode(), version.summary(), version.content(),
                    version.publishedAt(), version.publishedBy(), version.publishedByName());
        }
    }

    private static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

}
