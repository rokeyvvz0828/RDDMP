package com.ccb.development.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.development.model.DevelopmentStageModels.*;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.service.DevelopmentStageService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/development/tasks/{id}")
public class DevelopmentStageController {
    private final DevelopmentStageService stages;
    public DevelopmentStageController(DevelopmentStageService stages) { this.stages = stages; }

    @GetMapping("/stages")
    @PreAuthorize("hasAnyAuthority('development:task:read','development:admin')")
    public ApiResponse<StageView> get(@AuthenticationPrincipal AuthUser actor, @PathVariable long id) {
        return ok(stages.get(actor, id));
    }
    @PutMapping("/stages")
    @PreAuthorize("hasAnyAuthority('development:task:update','development:admin')")
    public ApiResponse<StageView> save(@AuthenticationPrincipal AuthUser actor, @PathVariable long id, @RequestBody StageWrite request) {
        return ok(stages.save(actor, id, request));
    }
    @PostMapping("/actions")
    @PreAuthorize("hasAnyAuthority('development:task:update','development:task:complete','development:admin')")
    public ApiResponse<TaskView> action(@AuthenticationPrincipal AuthUser actor, @PathVariable long id, @RequestBody TaskAction request) {
        return ok(stages.action(actor, id, request));
    }
    private static <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, TraceId.getOrCreate()); }
}
