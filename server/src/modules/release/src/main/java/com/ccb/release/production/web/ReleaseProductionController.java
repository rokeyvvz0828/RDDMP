package com.ccb.release.production.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.release.production.model.ProductionModels.BatchUpdateResultRequest;
import com.ccb.release.production.model.ProductionModels.Entry;
import com.ccb.release.production.model.ProductionModels.UpdateResultRequest;
import com.ccb.release.production.service.ReleaseProductionService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/release")
@PreAuthorize("hasAnyAuthority('release:access','system:admin')")
public class ReleaseProductionController {
    private final ReleaseProductionService service;
    public ReleaseProductionController(ReleaseProductionService service) { this.service = service; }

    @GetMapping("/production-baselines")
    @PreAuthorize("hasAnyAuthority('release:baseline:view','system:admin')")
    public ApiResponse<List<Entry>> baseline(@RequestParam long windowId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.baseline(windowId, user), TraceId.getOrCreate());
    }

    @PutMapping("/production-baselines/entries/{entryId}/result")
    @PreAuthorize("hasAnyAuthority('release:baseline:update','system:admin')")
    public ApiResponse<Entry> updateResult(@PathVariable long entryId, @RequestBody UpdateResultRequest request,
                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.updateResult(entryId, request, user), TraceId.getOrCreate());
    }

    @PutMapping("/production-baselines/results/batch")
    @PreAuthorize("hasAnyAuthority('release:baseline:update','system:admin')")
    public ApiResponse<List<Entry>> updateResults(@RequestBody BatchUpdateResultRequest request,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.updateResults(request, user), TraceId.getOrCreate());
    }

    @GetMapping("/production-versions")
    @PreAuthorize("hasAnyAuthority('release:production-version:view','release:application:view','system:admin')")
    public ApiResponse<List<Entry>> currentVersions(@RequestParam String projectId,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.currentVersions(projectId, user), TraceId.getOrCreate());
    }

    @GetMapping("/production-versions/{subsystemCode}/{deliveryUnitCode}/history")
    @PreAuthorize("hasAnyAuthority('release:production-version:view','system:admin')")
    public ApiResponse<List<Entry>> history(@PathVariable String subsystemCode, @PathVariable String deliveryUnitCode,
                                            @RequestParam String projectId,
                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.history(projectId, subsystemCode, deliveryUnitCode, user), TraceId.getOrCreate());
    }

    @GetMapping("/production-versions/entries/{entryId}/history")
    @PreAuthorize("hasAnyAuthority('release:production-version:view','system:admin')")
    public ApiResponse<List<Entry>> historyByEntry(@PathVariable long entryId,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.historyByEntry(entryId, user), TraceId.getOrCreate());
    }
}
