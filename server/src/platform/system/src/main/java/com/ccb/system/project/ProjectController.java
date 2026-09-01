package com.ccb.system.project;

import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentCategory;
import com.ccb.attachment.model.AttachmentLink;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/project")
@PreAuthorize("hasAuthority('project:access')")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) { this.service = service; }

    @GetMapping("/workbench")
    public ApiResponse<List<Map<String, Object>>> workbench(@AuthenticationPrincipal AuthUser user) { return ok(service.workbench(user)); }

    @GetMapping("/options/users")
    public ApiResponse<List<Map<String, Object>>> userOptions(@RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser user) { return ok(service.userOptions(keyword, user)); }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options(@AuthenticationPrincipal AuthUser user) { return ok(service.options(user)); }

    @GetMapping("/{projectId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.detail(projectId, user)); }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.create(input, user)); }

    @PutMapping("/{projectId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.update(projectId, input, user)); }

    @PutMapping("/{projectId}/settings")
    public ApiResponse<Map<String, Object>> updateSettings(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updateSettings(projectId, input, user)); }

    @GetMapping("/{projectId}/stages")
    public ApiResponse<List<Map<String, Object>>> stages(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.stages(projectId, user)); }
    @PostMapping("/{projectId}/stages")
    public ApiResponse<Map<String, Object>> createStage(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createStage(projectId, input, user)); }
    @PutMapping("/{projectId}/stages/{stageId}")
    public ApiResponse<Map<String, Object>> updateStage(@PathVariable long projectId, @PathVariable long stageId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updateStage(projectId, stageId, input, user)); }
    @DeleteMapping("/{projectId}/stages/{stageId}")
    public ApiResponse<Void> deleteStage(@PathVariable long projectId, @PathVariable long stageId, @AuthenticationPrincipal AuthUser user) { service.deleteStage(projectId, stageId, user); return ok(null); }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { service.delete(projectId, user); return ok(null); }

    @GetMapping("/{projectId}/plans")
    public ApiResponse<List<Map<String, Object>>> plans(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.plans(projectId, user)); }
    @PostMapping("/{projectId}/plans")
    public ApiResponse<Map<String, Object>> createPlan(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createPlan(projectId, input, user)); }
    @PutMapping("/{projectId}/plans/{planId}")
    public ApiResponse<Map<String, Object>> updatePlan(@PathVariable long projectId, @PathVariable long planId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updatePlan(projectId, planId, input, user)); }
    @DeleteMapping("/{projectId}/plans/{planId}")
    public ApiResponse<Void> deletePlan(@PathVariable long projectId, @PathVariable long planId, @AuthenticationPrincipal AuthUser user) { service.deletePlan(projectId, planId, user); return ok(null); }

    @GetMapping("/{projectId}/attachments")
    public ApiResponse<PageResult<AttachmentItem>> attachments(@PathVariable long projectId,
                                                                @RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "20") long size,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) Long categoryId,
                                                                @AuthenticationPrincipal AuthUser user) {
        return ok(service.attachments(projectId, page, size, keyword, categoryId, user));
    }

    @GetMapping("/{projectId}/attachment-categories")
    public ApiResponse<List<AttachmentCategory>> attachmentCategories(@PathVariable long projectId,
                                                                       @AuthenticationPrincipal AuthUser user) {
        return ok(service.attachmentCategories(projectId, user));
    }

    @PostMapping("/{projectId}/attachment-categories")
    public ApiResponse<AttachmentCategory> createAttachmentCategory(@PathVariable long projectId,
                                                                      @RequestBody Map<String, Object> input,
                                                                      @AuthenticationPrincipal AuthUser user) {
        return ok(service.createAttachmentCategory(projectId, input, user));
    }

    @PostMapping("/{projectId}/attachments")
    public ApiResponse<AttachmentItem> uploadAttachment(@PathVariable long projectId,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ok(service.uploadAttachment(projectId, file, categoryId, user));
    }

    @GetMapping("/{projectId}/attachments/{attachmentId}/preview")
    public ApiResponse<AttachmentLink> previewAttachment(@PathVariable long projectId, @PathVariable long attachmentId, @AuthenticationPrincipal AuthUser user) { return ok(service.previewAttachment(projectId, attachmentId, user)); }

    @GetMapping("/{projectId}/attachments/{attachmentId}/download")
    public ApiResponse<AttachmentLink> downloadAttachment(@PathVariable long projectId, @PathVariable long attachmentId, @AuthenticationPrincipal AuthUser user) { return ok(service.downloadAttachment(projectId, attachmentId, user)); }

    @PutMapping("/{projectId}/attachments/{attachmentId}/category")
    public ApiResponse<AttachmentItem> updateAttachmentCategory(@PathVariable long projectId,
                                                                 @PathVariable long attachmentId,
                                                                 @RequestBody Map<String, Object> input,
                                                                 @AuthenticationPrincipal AuthUser user) {
        return ok(service.updateAttachmentCategory(projectId, attachmentId,
                input == null ? null : nullableLong(input.get("categoryId")), user));
    }

    @DeleteMapping("/{projectId}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable long projectId, @PathVariable long attachmentId, @AuthenticationPrincipal AuthUser user) { service.deleteAttachment(projectId, attachmentId, user); return ok(null); }

    @GetMapping("/{projectId}/plan-groups")
    public ApiResponse<List<Map<String, Object>>> planGroups(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.planGroups(projectId, user)); }
    @PostMapping("/{projectId}/plan-groups")
    public ApiResponse<Map<String, Object>> createPlanGroup(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createPlanGroup(projectId, input, user)); }
    @PutMapping("/{projectId}/plan-groups/{groupId}")
    public ApiResponse<Map<String, Object>> updatePlanGroup(@PathVariable long projectId, @PathVariable long groupId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updatePlanGroup(projectId, groupId, input, user)); }
    @DeleteMapping("/{projectId}/plan-groups/{groupId}")
    public ApiResponse<Void> deletePlanGroup(@PathVariable long projectId, @PathVariable long groupId, @AuthenticationPrincipal AuthUser user) { service.deletePlanGroup(projectId, groupId, user); return ok(null); }
    @PutMapping("/{projectId}/plans/{planId}/group")
    public ApiResponse<Void> movePlanToGroup(@PathVariable long projectId, @PathVariable long planId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { service.movePlanToGroup(projectId, planId, input, user); return ok(null); }

    @GetMapping("/{projectId}/risks")
    public ApiResponse<List<Map<String, Object>>> risks(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.risks(projectId, user)); }
    @PostMapping("/{projectId}/risks")
    public ApiResponse<Map<String, Object>> createRisk(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createRisk(projectId, input, user)); }
    @PutMapping("/{projectId}/risks/{riskId}")
    public ApiResponse<Map<String, Object>> updateRisk(@PathVariable long projectId, @PathVariable long riskId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updateRisk(projectId, riskId, input, user)); }
    @DeleteMapping("/{projectId}/risks/{riskId}")
    public ApiResponse<Void> deleteRisk(@PathVariable long projectId, @PathVariable long riskId, @AuthenticationPrincipal AuthUser user) { service.deleteRisk(projectId, riskId, user); return ok(null); }
    @GetMapping("/{projectId}/risks/{riskId}/comments")
    public ApiResponse<List<Map<String, Object>>> riskComments(@PathVariable long projectId, @PathVariable long riskId, @AuthenticationPrincipal AuthUser user) { return ok(service.riskComments(projectId, riskId, user)); }
    @PostMapping("/{projectId}/risks/{riskId}/comments")
    public ApiResponse<Map<String, Object>> createRiskComment(@PathVariable long projectId, @PathVariable long riskId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createRiskComment(projectId, riskId, input, user)); }

    @GetMapping("/{projectId}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.members(projectId, user)); }
    @GetMapping("/{projectId}/organizations")
    public ApiResponse<List<Map<String, Object>>> organizations(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.organizations(projectId, user)); }
    @PostMapping("/{projectId}/organizations")
    public ApiResponse<Map<String, Object>> createOrganization(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createOrganization(projectId, input, user)); }
    @PutMapping("/{projectId}/organizations/{organizationId}")
    public ApiResponse<Map<String, Object>> updateOrganization(@PathVariable long projectId, @PathVariable long organizationId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updateOrganization(projectId, organizationId, input, user)); }
    @DeleteMapping("/{projectId}/organizations/{organizationId}")
    public ApiResponse<Void> deleteOrganization(@PathVariable long projectId, @PathVariable long organizationId, @AuthenticationPrincipal AuthUser user) { service.deleteOrganization(projectId, organizationId, user); return ok(null); }
    @PostMapping("/{projectId}/members")
    public ApiResponse<Map<String, Object>> createMember(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createMember(projectId, input, user)); }
    @PutMapping("/{projectId}/members/{memberId}")
    public ApiResponse<Map<String, Object>> updateMember(@PathVariable long projectId, @PathVariable long memberId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updateMember(projectId, memberId, input, user)); }
    @DeleteMapping("/{projectId}/members/{memberId}")
    public ApiResponse<Void> deleteMember(@PathVariable long projectId, @PathVariable long memberId, @AuthenticationPrincipal AuthUser user) { service.deleteMember(projectId, memberId, user); return ok(null); }

    @GetMapping("/{projectId}/roles")
    public ApiResponse<List<Map<String, Object>>> roles(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.roles(projectId, user)); }
    @PostMapping("/{projectId}/roles")
    public ApiResponse<Map<String, Object>> createRole(@PathVariable long projectId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.createRole(projectId, input, user)); }
    @PutMapping("/{projectId}/roles/{roleId}")
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable long projectId, @PathVariable long roleId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) { return ok(service.updateRole(projectId, roleId, input, user)); }
    @DeleteMapping("/{projectId}/roles/{roleId}")
    public ApiResponse<Void> deleteRole(@PathVariable long projectId, @PathVariable long roleId, @AuthenticationPrincipal AuthUser user) { service.deleteRole(projectId, roleId, user); return ok(null); }

    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success(data, TraceId.getOrCreate()); }

    private static Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类编号无效");
        }
    }
}
