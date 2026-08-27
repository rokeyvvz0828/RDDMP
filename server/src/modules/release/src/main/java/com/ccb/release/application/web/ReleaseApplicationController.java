package com.ccb.release.application.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictActionResult;
import com.ccb.release.application.model.ReleaseApplicationModels.ConflictReport;
import com.ccb.release.application.model.ReleaseApplicationModels.CreateRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.RelatedHistoryView;
import com.ccb.release.application.model.ReleaseApplicationModels.Response;
import com.ccb.release.application.model.ReleaseApplicationModels.StateActionRequest;
import com.ccb.release.application.model.ReleaseApplicationModels.UpdateRequest;
import com.ccb.release.application.service.ReleaseApplicationService;
import com.ccb.release.application.service.ReleaseSubmissionService;
import com.ccb.release.application.service.ReleaseSubmissionService.AttachmentDeleteResult;
import com.ccb.release.application.service.ReleaseSubmissionService.AttachmentView;
import com.ccb.release.application.service.ReleaseSubmissionService.RoundView;
import com.ccb.release.application.service.ReleaseSubmissionService.SubmitRequest;
import com.ccb.release.application.service.ReleaseSubmissionService.SubmitResult;
import com.ccb.release.application.service.ReleaseSubmissionService.WorkflowActionResult;
import com.ccb.security.model.AuthUser;
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

import java.util.List;

@RestController
@RequestMapping("/api/release/applications")
@PreAuthorize("hasAnyAuthority('release:access','system:admin')")
public class ReleaseApplicationController {
    private final ReleaseApplicationService service;
    private final ReleaseSubmissionService submissionService;

    public ReleaseApplicationController(ReleaseApplicationService service, ReleaseSubmissionService submissionService) {
        this.service = service;
        this.submissionService = submissionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('release:application:view','system:admin')")
    public ApiResponse<PageResult<Response>> list(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String projectId, @RequestParam(required = false) Long windowId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean mineOnly,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(page, size, projectId, windowId, keyword, status, mineOnly, user),
                TraceId.getOrCreate());
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('release:application:view','system:admin')")
    public ApiResponse<Response> detail(@PathVariable String code, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(code, user), TraceId.getOrCreate());
    }

    @GetMapping("/{code}/related-history")
    @PreAuthorize("hasAnyAuthority('release:application:view','system:admin')")
    public ApiResponse<List<RelatedHistoryView>> relatedHistory(@PathVariable String code,
                                                                 @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.relatedHistory(code, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('release:application:create','system:admin')")
    public ApiResponse<Response> create(@RequestBody CreateRequest request, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(request, user), TraceId.getOrCreate());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('release:application:update','system:admin')")
    public ApiResponse<Response> update(@PathVariable String code, @RequestBody UpdateRequest request,
                                        @AuthenticationPrincipal AuthUser user, Authentication authentication) {
        return ApiResponse.success(service.update(code, request, user, elevated(authentication)), TraceId.getOrCreate());
    }

    @PostMapping("/conflicts/preview")
    @PreAuthorize("hasAnyAuthority('release:application:create','system:admin')")
    public ApiResponse<ConflictReport> previewCreate(@RequestBody CreateRequest request,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.preview(request, user), TraceId.getOrCreate());
    }

    @PostMapping("/{code}/conflicts/preview")
    @PreAuthorize("hasAnyAuthority('release:application:update','system:admin')")
    public ApiResponse<ConflictReport> previewUpdate(@PathVariable String code, @RequestBody UpdateRequest request,
                                                     @AuthenticationPrincipal AuthUser user,
                                                     Authentication authentication) {
        return ApiResponse.success(service.preview(code, request, user, elevated(authentication)), TraceId.getOrCreate());
    }

    @GetMapping("/{code}/conflicts")
    @PreAuthorize("hasAnyAuthority('release:application:view','system:admin')")
    public ApiResponse<ConflictReport> conflicts(@PathVariable String code, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.conflicts(code, user), TraceId.getOrCreate());
    }

    @PostMapping("/{code}/conflicts")
    @PreAuthorize("hasAnyAuthority('release:application:update','system:admin')")
    public ApiResponse<ConflictActionResult> resolveConflict(@PathVariable String code,
                                                             @RequestBody ConflictActionRequest request,
                                                             @AuthenticationPrincipal AuthUser user,
                                                             Authentication authentication) {
        return ApiResponse.success(service.resolveConflict(code, request, user, elevated(authentication)), TraceId.getOrCreate());
    }

    @PostMapping("/{code}/submit")
    @PreAuthorize("hasAnyAuthority('release:application:submit','system:admin')")
    public ApiResponse<SubmitResult> submit(@PathVariable String code, @RequestBody SubmitRequest request,
                                            @AuthenticationPrincipal AuthUser user, Authentication authentication) {
        return ApiResponse.success(submissionService.submit(code, request, user, elevated(authentication)),
                TraceId.getOrCreate());
    }

    @PostMapping("/{code}/withdraw")
    @PreAuthorize("hasAnyAuthority('release:application:withdraw','system:admin')")
    public ApiResponse<WorkflowActionResult> withdraw(@PathVariable String code, @RequestBody StateActionRequest request,
                                                       @AuthenticationPrincipal AuthUser user,
                                                       Authentication authentication) {
        return ApiResponse.success(submissionService.withdraw(code, request, user, elevated(authentication)),
                TraceId.getOrCreate());
    }

    @PostMapping("/{code}/conflict-cancel")
    @PreAuthorize("hasAnyAuthority('release:application:withdraw','system:admin')")
    public ApiResponse<WorkflowActionResult> conflictCancel(@PathVariable String code,
                                                             @RequestBody StateActionRequest request,
                                                             @AuthenticationPrincipal AuthUser user,
                                                             Authentication authentication) {
        return ApiResponse.success(submissionService.conflictCancel(code, request, user, elevated(authentication)),
                TraceId.getOrCreate());
    }

    @PostMapping("/{code}/cancel")
    @PreAuthorize("hasAnyAuthority('release:application:cancel','system:admin')")
    public ApiResponse<Response> cancel(@PathVariable String code, @RequestBody StateActionRequest request,
                                        @AuthenticationPrincipal AuthUser user, Authentication authentication) {
        return ApiResponse.success(service.cancel(code, request, user, elevated(authentication)), TraceId.getOrCreate());
    }

    @GetMapping("/{code}/workflow")
    @PreAuthorize("hasAnyAuthority('release:application:view','system:admin')")
    public ApiResponse<RoundView> currentRound(@PathVariable String code, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(submissionService.currentRound(code, user), TraceId.getOrCreate());
    }

    @GetMapping("/{code}/attachments")
    @PreAuthorize("hasAnyAuthority('release:application:view','system:admin')")
    public ApiResponse<List<AttachmentView>> attachments(@PathVariable String code,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(submissionService.attachments(code, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/{code}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('release:application:update','system:admin')")
    public ApiResponse<AttachmentDeleteResult> deleteAttachment(@PathVariable String code,
                                                                 @PathVariable long attachmentId,
                                                                 @RequestBody StateActionRequest request,
                                                                 @AuthenticationPrincipal AuthUser user,
                                                                 Authentication authentication) {
        return ApiResponse.success(submissionService.deleteAttachment(code, attachmentId, request, user,
                elevated(authentication)), TraceId.getOrCreate());
    }

    private boolean elevated(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "system:admin".equals(authority.getAuthority()));
    }
}
