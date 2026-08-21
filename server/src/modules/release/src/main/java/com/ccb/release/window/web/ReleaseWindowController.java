package com.ccb.release.window.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.release.window.model.ChangeRegularEnabledRequest;
import com.ccb.release.window.model.CreateReleaseWindowRequest;
import com.ccb.release.window.model.ReleaseWindowResponse;
import com.ccb.release.window.model.UpdateReleaseWindowRequest;
import com.ccb.release.window.service.ReleaseWindowService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/release/windows")
@PreAuthorize("hasAnyAuthority('release:access','system:admin')")
public class ReleaseWindowController {
    private final ReleaseWindowService service;
    public ReleaseWindowController(ReleaseWindowService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('release:window:view','release:application:view','release:application:create','release:baseline:view','release:analytics:view','system:admin')")
    public ApiResponse<PageResult<ReleaseWindowResponse>> list(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String projectId, @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(page, size, projectId, keyword, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('release:window:view','system:admin')")
    public ApiResponse<ReleaseWindowResponse> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('release:window:create','system:admin')")
    public ApiResponse<ReleaseWindowResponse> create(@RequestBody CreateReleaseWindowRequest request,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(request, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('release:window:update','system:admin')")
    public ApiResponse<ReleaseWindowResponse> update(@PathVariable long id, @RequestBody UpdateReleaseWindowRequest request,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, request, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id}/regular-enabled")
    @PreAuthorize("hasAnyAuthority('release:window:update','system:admin')")
    public ApiResponse<ReleaseWindowResponse> changeRegularEnabled(@PathVariable long id,
                                                                  @RequestBody ChangeRegularEnabledRequest request,
                                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.changeRegularEnabled(id, request, user), TraceId.getOrCreate());
    }
}
