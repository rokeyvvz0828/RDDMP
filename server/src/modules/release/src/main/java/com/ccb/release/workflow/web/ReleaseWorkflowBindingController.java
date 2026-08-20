package com.ccb.release.workflow.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingHistoryView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.PublishedDefinitionView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.UpdateBindingRequest;
import com.ccb.release.workflow.service.ReleaseWorkflowBindingService;
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
@RequestMapping("/api/release/workflow-bindings")
@PreAuthorize("hasAnyAuthority('release:access','system:admin')")
public class ReleaseWorkflowBindingController {
    private final ReleaseWorkflowBindingService service;
    public ReleaseWorkflowBindingController(ReleaseWorkflowBindingService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('release:workflow-config:view','system:admin')")
    public ApiResponse<List<BindingView>> list(@RequestParam String projectRef, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectRef, user), TraceId.getOrCreate());
    }
    @GetMapping("/published-definitions")
    @PreAuthorize("hasAnyAuthority('release:workflow-config:view','system:admin')")
    public ApiResponse<List<PublishedDefinitionView>> publishedDefinitions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.publishedDefinitions(user), TraceId.getOrCreate());
    }
    @PutMapping("/{sceneCode}")
    @PreAuthorize("hasAnyAuthority('release:workflow-config:update','system:admin')")
    public ApiResponse<BindingView> update(@PathVariable String sceneCode, @RequestBody UpdateBindingRequest request,
                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(scene(sceneCode), request, user), TraceId.getOrCreate());
    }
    @GetMapping("/{sceneCode}/history")
    @PreAuthorize("hasAnyAuthority('release:workflow-config:view','system:admin')")
    public ApiResponse<List<BindingHistoryView>> history(@PathVariable String sceneCode, @RequestParam String projectRef,
                                                         @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.history(projectRef, scene(sceneCode), user), TraceId.getOrCreate());
    }
    private Scene scene(String value) {
        try { return Scene.parse(value); }
        catch (IllegalArgumentException error) { throw new BusinessException(ErrorCode.BAD_REQUEST, error.getMessage()); }
    }
}
