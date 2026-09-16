package com.ccb.release.application.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.release.application.service.ReleaseMasterDataService;
import com.ccb.release.application.service.ReleaseMasterDataService.DeliveryUnitOption;
import com.ccb.release.application.service.ReleaseMasterDataService.PhysicalSubsystemOption;
import com.ccb.release.application.service.ReleaseMasterDataService.RequirementOption;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/release/master-data")
@PreAuthorize("hasAnyAuthority('release:access','system:admin')")
public class ReleaseMasterDataController {
    private final ReleaseMasterDataService service;

    public ReleaseMasterDataController(ReleaseMasterDataService service) {
        this.service = service;
    }

    @GetMapping("/physical-subsystems")
    @PreAuthorize("hasAnyAuthority('release:application:view','release:application:create',"
            + "'release:application:update','system:admin')")
    public ApiResponse<PageResult<PhysicalSubsystemOption>> physicalSubsystems(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "100") long size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.physicalSubsystems(projectId, page, size, keyword, actor),
                TraceId.getOrCreate());
    }

    @GetMapping("/physical-subsystems/{physicalSubsystemId}/delivery-units")
    @PreAuthorize("hasAnyAuthority('release:application:view','release:application:create',"
            + "'release:application:update','system:admin')")
    public ApiResponse<PageResult<DeliveryUnitOption>> deliveryUnits(
            @PathVariable String physicalSubsystemId,
            @RequestParam String projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "100") long size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.deliveryUnits(projectId, physicalSubsystemId, page, size, keyword, actor),
                TraceId.getOrCreate());
    }

    @GetMapping("/requirements")
    @PreAuthorize("hasAnyAuthority('release:application:view','release:application:create',"
            + "'release:application:update','system:admin')")
    public ApiResponse<PageResult<RequirementOption>> requirements(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "100") long size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.requirements(projectId, page, size, keyword, actor),
                TraceId.getOrCreate());
    }
}
