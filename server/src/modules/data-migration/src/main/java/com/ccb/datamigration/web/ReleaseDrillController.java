package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ReleaseDrillService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 投产及演练 Controller（对标 TopicController / PlanController）。
 * 路由前缀 /api/data-migration/release-drills。回收站入口收敛到统一 /api/data-migration/recycle-bin（type=RELEASE_DRILL）。
 */
@RestController("dataMigrationReleaseDrillController")
@RequestMapping("/api/data-migration/release-drills")
@PreAuthorize("hasAnyAuthority('data-migration:content:release-drills','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class ReleaseDrillController {
    private final ReleaseDrillService service;

    public ReleaseDrillController(ReleaseDrillService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String materialTypeCode,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, granularity, materialTypeCode, systemCode, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/options/types")
    public ApiResponse<List<Map<String, Object>>> typeOptions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getTypeOptions(user), TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:release-drills:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:release-drills:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:release-drills:delete','data-migration:write','data-migration:manage','system:admin')")
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
