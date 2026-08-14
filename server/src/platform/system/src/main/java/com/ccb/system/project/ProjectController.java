package com.ccb.system.project;

import com.ccb.common.api.ApiResponse;
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

    @GetMapping("/{projectId}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable long projectId, @AuthenticationPrincipal AuthUser user) { return ok(service.members(projectId, user)); }
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
}
