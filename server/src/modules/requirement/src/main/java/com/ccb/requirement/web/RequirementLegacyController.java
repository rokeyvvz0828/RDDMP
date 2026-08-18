package com.ccb.requirement.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.trace.TraceId;
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

import java.util.List;
import java.util.Map;

/** 需求管理平台：存量项目域与系统清单。 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementLegacyController {
    private final RequirementLegacyService legacyService;
    private final RequirementSystemService systemService;

    public RequirementLegacyController(RequirementLegacyService legacyService,
                                       RequirementSystemService systemService) {
        this.legacyService = legacyService;
        this.systemService = systemService;
    }

    @GetMapping("/legacy")
    @PreAuthorize("hasAnyAuthority('requirement:access','requirement:legacy:read')")
    public ApiResponse<Object> legacyList(@RequestParam(required = false) String businessGroup,
                                          @RequestParam(required = false) String stage,
                                          @RequestParam(required = false) String stageStatus,
                                          @RequestParam(required = false) String keyword,
                                          PageQuery query,
                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.list(businessGroup, stage, stageStatus, keyword, query, user),
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
                                                            @RequestBody Map<String, String> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(legacyService.stageTransition(id, body.get("stage"), body.get("action"),
                body.get("comment"), user), TraceId.getOrCreate());
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
