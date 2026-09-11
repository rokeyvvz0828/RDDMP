package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.DependencyService;
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
 * 迁移过程依赖关系 Controller（REQ-20260910-068）。
 * 路由前缀 /api/data-migration/dependencies。
 * 回收站入口收敛到统一 /api/data-migration/recycle-bin（type=DEPENDENCY）。
 */
@RestController("dataMigrationDependencyController")
@RequestMapping("/api/data-migration/dependencies")
@PreAuthorize("hasAnyAuthority('data-migration:content:dependencies','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class DependencyController {
    private static final String TEMPLATE_FILE_NAME = "data-migration-dependency-template.xlsx";

    private final DependencyService service;

    public DependencyController(DependencyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String consumerSystemCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, consumerSystemCode, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/options/parameters")
    public ApiResponse<List<Map<String, Object>>> parameterOptions(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listParameterOptions(projectId, keyword, user), TraceId.getOrCreate());
    }

    @GetMapping("/parameters")
    public ApiResponse<PageResult<Map<String, Object>>> parameterPage(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listParameterPage(projectId, systemCode, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + TEMPLATE_FILE_NAME)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.downloadTemplate());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:dependencies:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyAuthority('data-migration:content:dependencies:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> batchCreate(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.batchCreate(body, user), TraceId.getOrCreate());
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('data-migration:content:dependencies:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> importDependencies(
            @RequestParam(required = false) Long projectId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.importDependencies(projectId, file, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:dependencies:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id,
                                                   @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:dependencies:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
