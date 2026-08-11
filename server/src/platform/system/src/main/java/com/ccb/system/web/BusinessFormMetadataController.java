package com.ccb.system.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.formmetadata.BusinessFormMetadataService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/form-metadata")
@PreAuthorize("hasAuthority('system:access')")
public class BusinessFormMetadataController {
    private final BusinessFormMetadataService service;

    public BusinessFormMetadataController(BusinessFormMetadataService service) { this.service = service; }

    @GetMapping("/scopes")
    public ApiResponse<List<Map<String, Object>>> scopes(@RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listScopes(keyword, user), TraceId.getOrCreate());
    }

    @GetMapping("/scopes/{scopeId}")
    public ApiResponse<Map<String, Object>> schema(@PathVariable long scopeId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.schema(scopeId, user), TraceId.getOrCreate());
    }

    @PostMapping("/scopes")
    public ApiResponse<Map<String, Object>> createScope(@RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createScope(input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}")
    public ApiResponse<Map<String, Object>> updateScope(@PathVariable long scopeId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.updateScope(scopeId, input, user), TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/sections")
    public ApiResponse<Map<String, Object>> createSection(@PathVariable long scopeId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.saveSection(scopeId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}/sections/{sectionId}")
    public ApiResponse<Map<String, Object>> updateSection(@PathVariable long scopeId, @PathVariable long sectionId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.saveSection(scopeId, sectionId, input, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/scopes/{scopeId}/sections/{sectionId}")
    public ApiResponse<Void> deleteSection(@PathVariable long scopeId, @PathVariable long sectionId, @AuthenticationPrincipal AuthUser user) {
        service.deleteSection(scopeId, sectionId, user); return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/fields")
    public ApiResponse<Map<String, Object>> createField(@PathVariable long scopeId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.saveField(scopeId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}/fields/{fieldId}")
    public ApiResponse<Map<String, Object>> updateField(@PathVariable long scopeId, @PathVariable long fieldId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.saveField(scopeId, fieldId, input, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/scopes/{scopeId}/fields/{fieldId}")
    public ApiResponse<Void> deleteField(@PathVariable long scopeId, @PathVariable long fieldId, @AuthenticationPrincipal AuthUser user) {
        service.deleteField(scopeId, fieldId, user); return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable long scopeId, @RequestBody(required = false) Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.publish(scopeId, input == null ? null : String.valueOf(input.getOrDefault("change_summary", "")), user), TraceId.getOrCreate());
    }
}
