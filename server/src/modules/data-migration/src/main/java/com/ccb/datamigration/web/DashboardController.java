package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.DashboardService;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-migration/dashboard")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:dashboard')")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/overall")
    public ApiResponse<Map<String, Object>> overall(@RequestParam(required = false) Long projectId,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboard.overall(projectId, user), TraceId.getOrCreate());
    }

    @GetMapping({"/component", "/components"})
    public ApiResponse<List<Map<String, Object>>> component(@RequestParam(required = false) Long projectId,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboard.component(user, projectId), TraceId.getOrCreate());
    }

    @GetMapping("/overall/metrics/{metricCode}")
    public ApiResponse<Map<String, Object>> metric(@PathVariable String metricCode,
                                                   @RequestParam("projectId") Long projectId,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboard.metric(metricCode, projectId, user), TraceId.getOrCreate());
    }

    @GetMapping("/overall/drilldowns/{metricCode}")
    public ApiResponse<PageResult<Map<String, Object>>> drilldown(@PathVariable String metricCode,
                                                                  @RequestParam("projectId") Long projectId,
                                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                                  @RequestParam(value = "size", defaultValue = "20") int size,
                                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboard.drilldown(metricCode, projectId, page, size, user),
                TraceId.getOrCreate());
    }
}
