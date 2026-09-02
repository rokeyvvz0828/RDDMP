package com.ccb.release.reporting.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.release.reporting.model.ReleaseAnalyticsModels.Summary;
import com.ccb.release.reporting.service.ReleaseAnalyticsService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/release/analytics")
@PreAuthorize("hasAnyAuthority('release:analytics:view','system:admin')")
public class ReleaseAnalyticsController {
    private final ReleaseAnalyticsService service;
    public ReleaseAnalyticsController(ReleaseAnalyticsService service) { this.service = service; }
    @GetMapping("/summary")
    public ApiResponse<Summary> summary(@RequestParam String projectId,
                                        @RequestParam(required = false) Long windowId,
                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.summary(projectId, windowId, user), TraceId.getOrCreate());
    }
    @GetMapping("/drilldown")
    public ApiResponse<PageResult<Map<String, Object>>> drilldown(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam String projectId, @RequestParam(required = false) Long windowId,
            @RequestParam(required = false) String dimension, @RequestParam(required = false) String value,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.drilldown(page, size, projectId, windowId, dimension, value, user), TraceId.getOrCreate());
    }
}
