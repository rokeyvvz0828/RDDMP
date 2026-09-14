package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.AttachmentStreamService;
import com.ccb.datamigration.service.MappingService;
import com.ccb.security.model.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 迁移映射 Controller（对标 ProgramController）。
 * 路由前缀 /api/data-migration/mappings。回收站入口收敛到统一 /api/data-migration/recycle-bin（type=MAPPING_DOC）。
 */
@RestController("dataMigrationMappingController")
@RequestMapping("/api/data-migration/mappings")
@PreAuthorize("hasAnyAuthority('data-migration:content:mappings','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class MappingController {

    private final MappingService service;
    private final AttachmentStreamService attachmentStream;

    public MappingController(MappingService service, AttachmentStreamService attachmentStream) {
        this.service = service;
        this.attachmentStream = attachmentStream;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String mappingType,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, mappingType, systemCode, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/options/types")
    public ApiResponse<List<Map<String, Object>>> typeOptions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getTypeOptions(user), TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:mappings:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:mappings:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:mappings:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}/download")
    public ApiResponse<String> download(@PathVariable long id, @RequestParam(required = false) Long attachmentId,
                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.download(id, attachmentId, user), TraceId.getOrCreate());
    }

    @GetMapping("/{id:\\d+}/download-all")
    public ResponseEntity<StreamingResponseBody> downloadAll(@PathVariable long id,
                                                             @AuthenticationPrincipal AuthUser user,
                                                             HttpServletRequest request) {
        Map<String, Object> detail = service.detail(id, user);
        String zipName = String.valueOf(detail.getOrDefault("asset_name", "迁移映射")) + ".zip";
        return attachmentStream.streamZip(service.listAttachments(id, user), zipName, user, request);
    }

    @GetMapping("/{id:\\d+}/attachments")
    public ApiResponse<List<Map<String, Object>>> attachments(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listAttachments(id, user), TraceId.getOrCreate());
    }
}
