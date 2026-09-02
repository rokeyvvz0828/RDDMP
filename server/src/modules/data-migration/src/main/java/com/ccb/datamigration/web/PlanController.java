package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.PlanService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 迁移方案 Controller（REQ-20260820-031 增量，对标 MeetingController / ReportController）。
 * 路由前缀 /api/data-migration/plans。回收站入口收敛到统一 {@code /api/data-migration/recycle-bin}（type=PLAN）。
 */
@RestController("dataMigrationPlanController")
@RequestMapping("/api/data-migration/plans")
@PreAuthorize("hasAnyAuthority('data-migration:content:plans','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class PlanController {
    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, granularity, planType, systemId, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/options/systems")
    public ApiResponse<List<Map<String, Object>>> systemOptions(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getSystemOptions(projectId, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:plans:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:plans:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:plans:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}/download")
    public ApiResponse<String> download(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.download(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}/attachments")
    public ApiResponse<List<Map<String, Object>>> attachments(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listAttachments(id, user), TraceId.getOrCreate());
    }
}
