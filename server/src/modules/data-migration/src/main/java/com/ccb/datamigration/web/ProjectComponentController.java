package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ProjectComponentService;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-migration")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:dashboard','data-migration:projects','data-migration:components')")
public class ProjectComponentController {
    private final ProjectComponentService service;
    public ProjectComponentController(ProjectComponentService service) { this.service = service; }

    @GetMapping("/options") public ApiResponse<Map<String, Object>> options(@AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.options(user), TraceId.getOrCreate()); }
    @GetMapping("/projects") public ApiResponse<List<Map<String, Object>>> projects(@RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.projects(user, keyword), TraceId.getOrCreate()); }
    @PostMapping("/projects") public ApiResponse<Map<String, Object>> createProject(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.createProject(body, user), TraceId.getOrCreate()); }
    @PutMapping("/projects/{id}") public ApiResponse<Map<String, Object>> updateProject(@PathVariable long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.updateProject(id, body, user), TraceId.getOrCreate()); }
    @DeleteMapping("/projects/{id}") public ApiResponse<Void> deleteProject(@PathVariable long id, @AuthenticationPrincipal AuthUser user) { service.deleteProject(id, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @GetMapping("/components") public ApiResponse<List<Map<String, Object>>> components(@RequestParam(required = false) Long projectId, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.components(user, projectId), TraceId.getOrCreate()); }
    @PostMapping("/components") public ApiResponse<Map<String, Object>> createComponent(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.createComponent(body, user), TraceId.getOrCreate()); }
    @PutMapping("/components/{id}") public ApiResponse<Map<String, Object>> updateComponent(@PathVariable long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.updateComponent(id, body, user), TraceId.getOrCreate()); }
    @DeleteMapping("/components/{id}") public ApiResponse<Void> deleteComponent(@PathVariable long id, @AuthenticationPrincipal AuthUser user) { service.deleteComponent(id, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
}
