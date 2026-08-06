package com.ccb.ai.web;

import com.ccb.ai.service.AiModelService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyAuthority('system:access','ai:access')")
public class AiModelController {
    private final AiModelService service;

    public AiModelController(AiModelService service) { this.service = service; }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.providers(user), TraceId.getOrCreate());
    }

    @PostMapping("/providers")
    public ApiResponse<Map<String, Object>> createProvider(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createProvider(body, user), TraceId.getOrCreate());
    }

    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> models(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.models(user), TraceId.getOrCreate());
    }

    @GetMapping("/routes")
    public ApiResponse<List<Map<String, Object>>> routes(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.routes(user), TraceId.getOrCreate());
    }

    @PostMapping("/models")
    public ApiResponse<Map<String, Object>> createModel(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createModel(body, user), TraceId.getOrCreate());
    }

    @PostMapping("/routes")
    public ApiResponse<Map<String, Object>> createRoute(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createRoute(body, user), TraceId.getOrCreate());
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, String> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.execute(body.get("capability"), body.get("input"), user), TraceId.getOrCreate());
    }
}