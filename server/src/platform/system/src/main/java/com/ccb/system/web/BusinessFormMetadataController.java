package com.ccb.system.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.formmetadata.BusinessFormMetadataService;
import com.ccb.system.formmetadata.BusinessFormViewService;
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
    private final BusinessFormViewService viewService;

    public BusinessFormMetadataController(BusinessFormMetadataService service, BusinessFormViewService viewService) { this.service = service; this.viewService = viewService; }

    @GetMapping("/modules")
    public ApiResponse<List<Map<String, Object>>> modules(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listModules(user), TraceId.getOrCreate());
    }

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

    @GetMapping("/scopes/{scopeId}/views")
    public ApiResponse<List<Map<String, Object>>> views(@PathVariable long scopeId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.listViews(scopeId, user), TraceId.getOrCreate());
    }

    @GetMapping("/scopes/{scopeId}/views/{viewId}")
    public ApiResponse<Map<String, Object>> viewSchema(@PathVariable long scopeId, @PathVariable long viewId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.schema(scopeId, viewId, user), TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/views")
    public ApiResponse<Map<String, Object>> createView(@PathVariable long scopeId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveView(scopeId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}/views/{viewId}")
    public ApiResponse<Map<String, Object>> updateView(@PathVariable long scopeId, @PathVariable long viewId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveView(scopeId, viewId, input, user), TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/views/{viewId}/fields")
    public ApiResponse<Map<String, Object>> createViewField(@PathVariable long scopeId, @PathVariable long viewId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveViewField(scopeId, viewId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}/views/{viewId}/fields/{viewFieldId}")
    public ApiResponse<Map<String, Object>> updateViewField(@PathVariable long scopeId, @PathVariable long viewId, @PathVariable long viewFieldId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveViewField(scopeId, viewId, viewFieldId, input, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/scopes/{scopeId}/views/{viewId}/fields/{viewFieldId}")
    public ApiResponse<Void> deleteViewField(@PathVariable long scopeId, @PathVariable long viewId, @PathVariable long viewFieldId, @AuthenticationPrincipal AuthUser user) {
        viewService.deleteViewField(scopeId, viewId, viewFieldId, user); return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/views/{viewId}/steps")
    public ApiResponse<Map<String, Object>> createStep(@PathVariable long scopeId, @PathVariable long viewId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveStep(scopeId, viewId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}/views/{viewId}/steps/{stepId}")
    public ApiResponse<Map<String, Object>> updateStep(@PathVariable long scopeId, @PathVariable long viewId, @PathVariable long stepId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveStep(scopeId, viewId, stepId, input, user), TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/views/{viewId}/steps/{stepId}/fields")
    public ApiResponse<Map<String, Object>> createStepField(@PathVariable long scopeId, @PathVariable long viewId, @PathVariable long stepId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveStepField(scopeId, viewId, stepId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/scopes/{scopeId}/views/{viewId}/steps/{stepId}/fields/{stepFieldId}")
    public ApiResponse<Map<String, Object>> updateStepField(@PathVariable long scopeId, @PathVariable long viewId, @PathVariable long stepId, @PathVariable long stepFieldId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveStepField(scopeId, viewId, stepId, stepFieldId, input, user), TraceId.getOrCreate());
    }

    @PostMapping("/scopes/{scopeId}/views/{viewId}/publish")
    public ApiResponse<Map<String, Object>> publishView(@PathVariable long scopeId, @PathVariable long viewId, @RequestBody(required = false) Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.publish(scopeId, viewId, input == null ? null : String.valueOf(input.getOrDefault("change_summary", "")), user), TraceId.getOrCreate());
    }

    @GetMapping("/pages")
    public ApiResponse<List<Map<String, Object>>> pages(@RequestParam(required = false) String moduleKey, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.listPages(moduleKey, user), TraceId.getOrCreate());
    }

    @GetMapping("/pages/{pageId}")
    public ApiResponse<Map<String, Object>> pageSchema(@PathVariable long pageId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.pageSchema(pageId, user), TraceId.getOrCreate());
    }

    @PostMapping("/pages")
    public ApiResponse<Map<String, Object>> createPage(@RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.savePage(null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/pages/{pageId}")
    public ApiResponse<Map<String, Object>> updatePage(@PathVariable long pageId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.savePage(pageId, input, user), TraceId.getOrCreate());
    }

    @PostMapping("/pages/{pageId}/blocks")
    public ApiResponse<Map<String, Object>> createBlock(@PathVariable long pageId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveBlock(pageId, null, input, user), TraceId.getOrCreate());
    }

    @PutMapping("/pages/{pageId}/blocks/{blockId}")
    public ApiResponse<Map<String, Object>> updateBlock(@PathVariable long pageId, @PathVariable long blockId, @RequestBody Map<String, Object> input, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(viewService.saveBlock(pageId, blockId, input, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/pages/{pageId}/blocks/{blockId}")
    public ApiResponse<Void> deleteBlock(@PathVariable long pageId, @PathVariable long blockId, @AuthenticationPrincipal AuthUser user) {
        viewService.deleteBlock(pageId, blockId, user); return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
