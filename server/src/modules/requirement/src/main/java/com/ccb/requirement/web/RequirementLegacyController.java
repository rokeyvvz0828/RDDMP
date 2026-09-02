package com.ccb.requirement.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.trace.TraceId;
import com.ccb.requirement.service.RequirementLegacyEnhanceService;
import com.ccb.requirement.service.RequirementLegacyService;
import com.ccb.requirement.service.RequirementSystemService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 需求管理平台：存量项目域与系统清单。 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementLegacyController {
    private final RequirementLegacyService legacyService;
    private final RequirementLegacyEnhanceService enhanceService;
    private final RequirementSystemService systemService;

    public RequirementLegacyController(RequirementLegacyService legacyService,
                                       RequirementLegacyEnhanceService enhanceService,
                                       RequirementSystemService systemService) {
        this.legacyService = legacyService;
        this.enhanceService = enhanceService;
        this.systemService = systemService;
    }

    @GetMapping("/legacy")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<Object> legacyList(@RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) String businessGroup,
                                          @RequestParam(required = false) String stage,
                                          @RequestParam(required = false) String stageStatus,
                                          @RequestParam(required = false) String keyword,
                                          PageQuery query,
                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.list(projectId, businessGroup, stage, stageStatus, keyword, query, user),
                TraceId.getOrCreate());
    }

    @PostMapping("/legacy")
    @PreAuthorize("hasAuthority('requirement:legacy:create')")
    public ApiResponse<Map<String, Object>> createLegacy(@RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.create(body, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<Map<String, Object>> legacy(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.get(id, user), TraceId.getOrCreate());
    }

    @PutMapping("/legacy/{id}")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> updateLegacy(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/legacy/{id}")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Void> deleteLegacy(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        legacyService.delete(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/stage")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> stageTransition(@PathVariable long id,
                                                            @RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        boolean ignoreMissingStageFields = Boolean.TRUE.equals(body.get("ignoreMissingStageFields"));
        return ApiResponse.success(legacyService.stageTransition(id,
                (String) body.get("stage"), (String) body.get("action"),
                (String) body.get("comment"), ignoreMissingStageFields, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/stage-logs")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> stageLogs(@PathVariable long id,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.stageLogs(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/changes")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:changelog:read')")
    public ApiResponse<List<Map<String, Object>>> legacyChanges(@PathVariable long id,
                                                                @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.changes(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/members")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> legacyMembers(@PathVariable long id,
                                                                @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.members(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/members")
    @PreAuthorize("hasAnyAuthority('requirement:legacy:update','requirement:pmo')")
    public ApiResponse<Map<String, Object>> addLegacyMember(@PathVariable long id,
                                                            @RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.addMember(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/legacy-members/{id}")
    @PreAuthorize("hasAnyAuthority('requirement:legacy:update','requirement:pmo')")
    public ApiResponse<Void> removeLegacyMember(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        legacyService.removeMember(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    // ---------------- 存量增强：系统子表 / 流转 / 版本 / 交付件 / 协同事项 / 评审记录 ----------------

    @GetMapping("/legacy/{id}/system-items")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> systemItems(@PathVariable long id,
                                                              @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.systemItems(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/flow-logs")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> flowLogs(@PathVariable long id,
                                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.flowLogs(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/flow")
    @PreAuthorize("hasAnyAuthority('requirement:legacy:update','requirement:pmo')")
    public ApiResponse<Map<String, Object>> sendFlow(@PathVariable long id,
                                                     @RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal AuthUser user) {
        long toUserId = Long.parseLong(String.valueOf(body.get("toUserId")));
        return ApiResponse.success(legacyService.sendFlow(id, toUserId,
                body.get("comment") == null ? null : String.valueOf(body.get("comment")), user),
                TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/flow/return")
    @PreAuthorize("hasAnyAuthority('requirement:legacy:update','requirement:pmo')")
    public ApiResponse<Map<String, Object>> returnFlow(@PathVariable long id,
                                                       @RequestBody(required = false) Map<String, Object> body,
                                                       @AuthenticationPrincipal AuthUser user) {
        String comment = body == null ? null : (body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        return ApiResponse.success(legacyService.returnFlow(id, comment, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/versions")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> versions(@PathVariable long id,
                                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.versions(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/change")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> saveChange(@PathVariable long id,
                                                       @RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.saveChange(id, body, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/deliverables")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> deliverables(@PathVariable long id,
                                                               @RequestParam String type,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(enhanceService.deliverables(id, type, user), TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/deliverables")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> saveDeliverable(@PathVariable long id,
                                                            @RequestParam String type,
                                                            @RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(enhanceService.saveDeliverable(id, type, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/deliverables/{id}")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Void> deleteDeliverable(@PathVariable long id,
                                               @RequestParam String type,
                                               @AuthenticationPrincipal AuthUser user) {
        enhanceService.deleteDeliverable(id, type, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/deliverables/{id}/submit-review")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> submitDeliverableReview(@PathVariable long id,
                                                                    @RequestParam String type,
                                                                    @RequestBody(required = false) Map<String, Object> body,
                                                                    @AuthenticationPrincipal AuthUser user) {
        List<Long> approverIds = new ArrayList<>();
        Object raw = body == null ? null : body.get("approverIds");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number number) {
                    approverIds.add(number.longValue());
                } else if (item != null) {
                    approverIds.add(Long.valueOf(String.valueOf(item)));
                }
            }
        }
        String reportDocName = body == null ? null : (body.get("reportDocName") == null
                ? null : String.valueOf(body.get("reportDocName")));
        return ApiResponse.success(enhanceService.submitDeliverableReview(id, type, approverIds, reportDocName, user),
                TraceId.getOrCreate());
    }

    @PostMapping("/deliverables/{id}/review")
    @PreAuthorize("hasAnyAuthority('requirement:legacy:update','requirement:pmo')")
    public ApiResponse<Map<String, Object>> reviewDeliverable(@PathVariable long id,
                                                              @RequestParam String type,
                                                              @RequestBody Map<String, Object> body,
                                                              @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(enhanceService.reviewDeliverable(id, type, body, user), TraceId.getOrCreate());
    }

    @GetMapping("/legacy/{id}/coordination")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<List<Map<String, Object>>> coordinationItems(@PathVariable long id,
                                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(enhanceService.coordinationItems(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/legacy/{id}/coordination")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Map<String, Object>> saveCoordination(@PathVariable long id,
                                                             @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(enhanceService.saveCoordination(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/coordination/{id}")
    @PreAuthorize("hasAuthority('requirement:legacy:update')")
    public ApiResponse<Void> deleteCoordination(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        enhanceService.deleteCoordination(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/review-records")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read','requirement:project:read')")
    public ApiResponse<List<Map<String, Object>>> reviewRecords(@RequestParam String bizType,
                                                                @RequestParam long bizId,
                                                                @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(enhanceService.reviewRecords(bizType, bizId, user), TraceId.getOrCreate());
    }

    @GetMapping("/systems")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:system:read')")
    public ApiResponse<List<Map<String, Object>>> systems(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(systemService.list(user), TraceId.getOrCreate());
    }

    @PostMapping("/systems")
    @PreAuthorize("hasAuthority('requirement:system:create')")
    public ApiResponse<Map<String, Object>> createSystem(@RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(systemService.create(body, user), TraceId.getOrCreate());
    }

    @PutMapping("/systems/{id}")
    @PreAuthorize("hasAuthority('requirement:system:update')")
    public ApiResponse<Map<String, Object>> updateSystem(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(systemService.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/systems/{id}")
    @PreAuthorize("hasAuthority('requirement:system:update')")
    public ApiResponse<Void> deleteSystem(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        systemService.delete(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
