package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ParameterService;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 迁移参数 Controller（REQ-20260906-067，对标迁移检核规则）。
 * 路由前缀 /api/data-migration/parameters。回收站入口收敛到统一 /api/data-migration/recycle-bin（type=PARAMETER）。
 */
@RestController("dataMigrationParameterController")
@RequestMapping("/api/data-migration/parameters")
@PreAuthorize("hasAnyAuthority('data-migration:content:parameters','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class ParameterController {
    private static final String EXPORT_FILE_NAME = "data-migration-parameters.xlsx";
    private static final String TEMPLATE_FILE_NAME = "data-migration-parameter-template.xlsx";

    private final ParameterService service;

    public ParameterController(ParameterService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String parameterType,
            @RequestParam(required = false) String parameterScope,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, parameterType, parameterScope, systemCode, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + TEMPLATE_FILE_NAME)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.downloadTemplate());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String parameterType,
            @RequestParam(required = false) String parameterScope,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + EXPORT_FILE_NAME)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.export(projectId, parameterType, parameterScope, systemCode, keyword, user));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> importParameters(
            @RequestParam(required = false) Long projectId,
            @RequestPart MultipartFile file,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.importParameters(projectId, file, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}/fields")
    public ApiResponse<List<Map<String, Object>>> listFields(
            @PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listFields(id, user), TraceId.getOrCreate());
    }

    @PostMapping("/{id:\\d+}/fields")
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<List<Map<String, Object>>> batchAddFields(
            @PathVariable long id, @RequestBody List<Map<String, Object>> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.batchAddFields(id, body, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}/fields/{fieldId:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> updateField(
            @PathVariable long id, @PathVariable long fieldId,
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.updateField(id, fieldId, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/{id:\\d+}/fields/{fieldId:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> deleteField(
            @PathVariable long id, @PathVariable long fieldId, @AuthenticationPrincipal AuthUser user) {
        service.deleteField(id, fieldId, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PostMapping("/{id:\\d+}/fields/batch-delete")
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> batchDeleteFields(
            @PathVariable long id, @RequestBody List<Long> body, @AuthenticationPrincipal AuthUser user) {
        service.deleteFields(id, body, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:parameters:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
