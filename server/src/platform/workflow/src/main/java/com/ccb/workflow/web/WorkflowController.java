package com.ccb.workflow.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.service.WorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
@PreAuthorize("hasAnyAuthority('system:access','workflow:access')")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping("/definitions")
    public ApiResponse<List<Map<String, Object>>> definitions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.definitions(user), TraceId.getOrCreate());
    }

    @GetMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> definition(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.definition(id, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/definitions/{id}")
    @PreAuthorize("hasAnyAuthority('system:admin','workflow:access:delete')")
    public ApiResponse<Void> delete(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.deleteDefinition(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/definitions")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createDefinition(body.get("code"), body.get("name"), body.getOrDefault("definitionJson", "{}"), user), TraceId.getOrCreate());
    }

    @PutMapping("/definitions/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal AuthUser user) {
        service.updateDefinition(id, body.get("code"), body.get("name"), body.getOrDefault("definitionJson", "{}"), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/definitions/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.publish(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/definitions/{id}/unpublish")
    public ApiResponse<Void> unpublish(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.unpublish(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/instances")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.start(Long.parseLong(String.valueOf(body.get("definitionId"))), String.valueOf(body.get("businessKey")), body, user), TraceId.getOrCreate());
    }

    @GetMapping("/instances")
    public ApiResponse<List<Map<String, Object>>> instances(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.instances(user), TraceId.getOrCreate());
    }

    @GetMapping("/instances/{id}/timeline")
    public ApiResponse<List<Map<String, Object>>> timeline(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.timeline(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/instances/{id}/detail")
    public ApiResponse<Map<String, Object>> instanceDetail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.instanceDetail(id, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/instances/{id}")
    @PreAuthorize("hasAnyAuthority('system:admin','workflow:access:delete')")
    public ApiResponse<Void> deleteInstance(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        service.deleteInstance(id, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/instances/{id}/terminate")
    @PreAuthorize("hasAnyAuthority('system:admin','workflow:access:delete')")
    public ApiResponse<Void> terminate(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        service.terminate(id, body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason")), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/inbox")
    public ApiResponse<List<Map<String, Object>>> inbox(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.inbox(user), TraceId.getOrCreate());
    }

    @GetMapping("/done")
    public ApiResponse<List<Map<String, Object>>> done(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.done(user), TraceId.getOrCreate());
    }

    @PostMapping("/tasks/{id}/decision")
    public ApiResponse<Void> decide(@PathVariable long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        service.decide(id, String.valueOf(body.get("action")), body.get("comment") == null ? null : String.valueOf(body.get("comment")), longValue(body.get("targetUserId")), longList(body.get("ccUserIds")), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<Long> result = new ArrayList<>();
        for (Object item : values) {
            Long parsed = longValue(item);
            if (parsed != null && parsed > 0) result.add(parsed);
        }
        return result;
    }
}
