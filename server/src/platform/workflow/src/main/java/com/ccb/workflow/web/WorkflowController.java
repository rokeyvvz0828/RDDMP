package com.ccb.workflow.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
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
    public ApiResponse<PageResult<Map<String, Object>>> definitions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String projectRef,
            @RequestParam(required = false) String scopeType,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.definitions(new PageQuery(page, size), projectRef, scopeType, user), TraceId.getOrCreate());
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

    @PostMapping("/definitions/{id}/archive")
    @PreAuthorize("hasAnyAuthority('system:admin','workflow:access:delete')")
    public ApiResponse<Void> archive(@PathVariable long id, @RequestBody Map<String, String> body,
                                     @AuthenticationPrincipal AuthUser user) {
        service.archiveDefinition(id, body.get("reason"), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/definitions/{id}/restore")
    @PreAuthorize("hasAnyAuthority('system:admin','workflow:access:delete')")
    public ApiResponse<Void> restore(@PathVariable long id, @RequestBody Map<String, String> body,
                                     @AuthenticationPrincipal AuthUser user) {
        service.restoreDefinition(id, body.get("reason"), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/definitions/{id}/versions")
    public ApiResponse<List<Map<String, Object>>> definitionVersions(@PathVariable long id,
                                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.definitionVersions(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/definitions/{id}/versions/{versionNo}")
    public ApiResponse<Map<String, Object>> definitionVersion(@PathVariable long id, @PathVariable int versionNo,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.definitionVersion(id, versionNo, user), TraceId.getOrCreate());
    }

    @GetMapping("/definitions/{id}/events")
    public ApiResponse<List<Map<String, Object>>> definitionEvents(@PathVariable long id,
                                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.definitionEvents(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/definitions")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createDefinition(body.get("code"), body.get("name"),
                body.getOrDefault("definitionJson", "{}"), body.getOrDefault("scopeType", "TEMPLATE"),
                body.get("projectRef"), user), TraceId.getOrCreate());
    }

    @GetMapping("/project-options")
    public ApiResponse<Map<String, Object>> projectOptions(@RequestParam String projectRef,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.projectOptions(projectRef, user), TraceId.getOrCreate());
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
    public ApiResponse<PageResult<Map<String, Object>>> instances(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String definitionKeyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String starterKeyword,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String projectRef,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.instances(new PageQuery(page, size), businessKey, definitionKeyword, status,
                starterKeyword, createdFrom, createdTo, projectRef, user), TraceId.getOrCreate());
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
    public ApiResponse<PageResult<Map<String, Object>>> inbox(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.inbox(new PageQuery(page, size), user), TraceId.getOrCreate());
    }

    @GetMapping("/done")
    public ApiResponse<PageResult<Map<String, Object>>> done(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.done(new PageQuery(page, size), user), TraceId.getOrCreate());
    }

    @GetMapping("/submitted")
    public ApiResponse<PageResult<Map<String, Object>>> submitted(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.submitted(new PageQuery(page, size), user), TraceId.getOrCreate());
    }

    @GetMapping("/tasks/{id}/context")
    public ApiResponse<Map<String, Object>> taskContext(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.taskContext(id, user), TraceId.getOrCreate());
    }

    @GetMapping("/tasks/current-context")
    public ApiResponse<Map<String, Object>> currentTaskContext(
            @RequestParam String businessType,
            @RequestParam String businessKey,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.currentTaskContext(businessType, businessKey, user), TraceId.getOrCreate());
    }

    @PostMapping("/tasks/{id}/decision")
    public ApiResponse<Void> decide(@PathVariable long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        service.decide(id, String.valueOf(body.get("action")), body.get("comment") == null ? null : String.valueOf(body.get("comment")), longValue(body.get("targetUserId")), longList(body.get("ccUserIds")), Boolean.TRUE.equals(body.get("signatureConfirmed")), user);
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
