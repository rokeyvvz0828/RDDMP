package com.ccb.requirement.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.requirement.service.RequirementAttachmentService;
import com.ccb.requirement.service.RequirementBaselineService;
import com.ccb.requirement.service.RequirementDifferenceService;
import com.ccb.requirement.service.RequirementImportService;
import com.ccb.requirement.service.RequirementProjectService;
import com.ccb.requirement.support.RequirementEnums;
import com.ccb.security.model.AuthUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 需求管理平台：新建项目域（项目、差异、评审处理、基线、导入、附件）。 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {
    private final RequirementProjectService projectService;
    private final RequirementDifferenceService differenceService;
    private final RequirementBaselineService baselineService;
    private final RequirementImportService importService;
    private final RequirementAttachmentService attachmentService;

    public RequirementController(RequirementProjectService projectService,
                                 RequirementDifferenceService differenceService,
                                 RequirementBaselineService baselineService,
                                 RequirementImportService importService,
                                 RequirementAttachmentService attachmentService) {
        this.projectService = projectService;
        this.differenceService = differenceService;
        this.baselineService = baselineService;
        this.importService = importService;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/enums")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> enums() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("options", RequirementEnums.OPTIONS);
        result.put("fieldLabels", RequirementEnums.FIELD_LABELS);
        return ApiResponse.success(result, TraceId.getOrCreate());
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<List<Map<String, Object>>> projects(@RequestParam(required = false) String keyword,
                                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(projectService.list(keyword, user), TraceId.getOrCreate());
    }

    @PostMapping("/projects")
    @PreAuthorize("hasAuthority('requirement:project:create')")
    public ApiResponse<Map<String, Object>> createProject(@RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(projectService.create(body, user), TraceId.getOrCreate());
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<Map<String, Object>> project(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(projectService.get(id, user), TraceId.getOrCreate());
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Map<String, Object>> updateProject(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(projectService.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('requirement:project:delete')")
    public ApiResponse<Void> deleteProject(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        projectService.delete(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/projects/{id}/members")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(projectService.members(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/projects/{id}/members")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Map<String, Object>> addMember(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(projectService.addMember(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/project-members/{id}")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Void> removeMember(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        projectService.removeMember(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/differences")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<Object> differences(@RequestParam long projectId,
                                           @RequestParam(required = false) String reviewStatus,
                                           @RequestParam(required = false) String devStatus,
                                           @RequestParam(required = false) String testStatus,
                                           @RequestParam(required = false) String keyword,
                                           PageQuery query,
                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.list(projectId, reviewStatus, devStatus, testStatus,
                keyword, query, user), TraceId.getOrCreate());
    }

    @PostMapping("/differences")
    @PreAuthorize("hasAuthority('requirement:project:create')")
    public ApiResponse<Map<String, Object>> createDifference(@RequestParam long projectId,
                                                             @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.create(projectId, body, user), TraceId.getOrCreate());
    }

    @GetMapping("/differences/{id}")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<Map<String, Object>> difference(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.get(id, user), TraceId.getOrCreate());
    }

    @PutMapping("/differences/{id}")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Map<String, Object>> updateDifference(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/differences/{id}")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Void> deleteDifference(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        differenceService.delete(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/differences/{id}/submit-review")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Map<String, Object>> submitReview(@PathVariable long id,
                                                         @RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) body.get("approverIds");
        List<Long> approverIds = raw == null ? List.of()
                : raw.stream().map(Number::longValue).toList();
        String reportDocName = body.get("reportDocName") == null ? null : String.valueOf(body.get("reportDocName"));
        return ApiResponse.success(differenceService.submitReview(id, approverIds, reportDocName, user),
                TraceId.getOrCreate());
    }

    @PostMapping("/differences/{id}/cancel-review")
    @PreAuthorize("hasAuthority('requirement:project:update')")
    public ApiResponse<Map<String, Object>> cancelReview(@PathVariable long id,
                                                         @RequestBody(required = false) Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        String reason = body == null ? null : (String) body.get("reason");
        return ApiResponse.success(differenceService.cancelReview(id, reason, user), TraceId.getOrCreate());
    }

    @GetMapping("/reviewers")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:update')")
    public ApiResponse<List<Map<String, Object>>> reviewers(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.reviewers(user), TraceId.getOrCreate());
    }

    @GetMapping("/differences/{id}/changes")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:changelog:read')")
    public ApiResponse<List<Map<String, Object>>> differenceChanges(@PathVariable long id,
                                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.changes(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/differences/{id}/approval-logs")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<List<Map<String, Object>>> approvalLogs(@PathVariable long id,
                                                              @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(differenceService.approvalLogs(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/baselines")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<List<Map<String, Object>>> baselines(@RequestParam long projectId,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(baselineService.list(projectId, user), TraceId.getOrCreate());
    }

    @PostMapping("/projects/{id}/baseline")
    @PreAuthorize("hasAuthority('requirement:baseline:create')")
    public ApiResponse<Map<String, Object>> createBaseline(@PathVariable long id,
                                                           @RequestBody(required = false) Map<String, String> body,
                                                           @AuthenticationPrincipal AuthUser user) {
        String remark = body == null ? null : body.get("remark");
        return ApiResponse.success(baselineService.create(id, remark, user), TraceId.getOrCreate());
    }

    @GetMapping("/baselines/{id}/items")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:project:read')")
    public ApiResponse<List<Map<String, Object>>> baselineItems(@PathVariable long id,
                                                                @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(baselineService.items(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/imports")
    @PreAuthorize("hasAuthority('requirement:import:create')")
    public ApiResponse<List<Map<String, Object>>> importBatches(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(importService.listBatches(user), TraceId.getOrCreate());
    }

    @PostMapping("/imports/preview")
    @PreAuthorize("hasAuthority('requirement:import:create')")
    public ApiResponse<Map<String, Object>> previewImport(@RequestParam String bizType,
                                                          @RequestParam(required = false) Long projectId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(importService.preview(bizType, projectId, file, user), TraceId.getOrCreate());
    }

    @PostMapping("/imports/confirm")
    @PreAuthorize("hasAuthority('requirement:import:create')")
    public ApiResponse<Map<String, Object>> confirmImport(@RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal AuthUser user) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        Object projectId = body.get("projectId");
        Long parsedProjectId = projectId == null ? null : Long.parseLong(String.valueOf(projectId));
        return ApiResponse.success(importService.confirm(String.valueOf(body.get("bizType")), parsedProjectId,
                body.get("fileName") == null ? null : String.valueOf(body.get("fileName")), rows, user),
                TraceId.getOrCreate());
    }

    @GetMapping("/imports/templates/{bizType}")
    @PreAuthorize("hasAuthority('requirement:import:create')")
    public ResponseEntity<byte[]> template(@PathVariable String bizType) {
        if (!"DIFF".equals(bizType) && !"LEGACY".equals(bizType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板类型必须为 DIFF 或 LEGACY");
        }
        byte[] content = importService.template(bizType);
        String fileName = "DIFF".equals(bizType) ? "requirement-difference-template.xlsx" : "legacy-requirement-template.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/attachments")
    @PreAuthorize("hasAnyAuthority('requirement:project:read','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> attachments(@RequestParam String bizType,
                                                              @RequestParam long bizId,
                                                              @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(attachmentService.list(bizType, bizId, user), TraceId.getOrCreate());
    }

    @PostMapping("/attachments")
    @PreAuthorize("hasAnyAuthority('requirement:project:update','requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> createAttachment(@RequestParam String bizType,
                                                             @RequestParam long bizId,
                                                             @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(attachmentService.create(bizType, bizId, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAnyAuthority('requirement:project:update','requirement:legacy:update')")
    public ApiResponse<Void> deleteAttachment(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        attachmentService.delete(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
