package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ContentFileAssetService;
import com.ccb.datamigration.service.AttachmentStreamService;
import com.ccb.security.model.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 文件型内容资源控制器（REQ-20260831-050）：为 6 个文件型二级菜单提供各自独立的资源端点，
 * 替代原通用 {@code /assets/{type}}；类型由路径段解析，读写经 {@link ContentFileAssetService} 落各内容表，
 * 附件关系沿用平台公共附件能力；文件内容不执行摘要查重。
 */
@RestController
@RequestMapping("/api/data-migration")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:assets')")
public class ContentAssetController {
    private static final Map<String, String> RESOURCE_TYPES = Map.of(
            "mappings", "MAPPING_DOC",
            "dependencies", "DEPENDENCY",
            "programs", "SCRIPT",
            "topics", "TOPIC",
            "release-drills", "RELEASE_DRILL");
    private static final String PREFIX = "/api/data-migration/";

    private final ContentFileAssetService service;
    private final AttachmentStreamService attachmentStream;

    public ContentAssetController(ContentFileAssetService service, AttachmentStreamService attachmentStream) {
        this.service = service;
        this.attachmentStream = attachmentStream;
    }

    @GetMapping({"/mappings", "/dependencies", "/programs", "/topics", "/release-drills"})
    public ApiResponse<PageResult<Map<String, Object>>> list(HttpServletRequest request,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(type(request), projectId, componentId, keyword, page, size, user), TraceId.getOrCreate());
    }

    @PostMapping({"/mappings/upload", "/dependencies/upload", "/programs/upload", "/topics/upload", "/release-drills/upload"})
    @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> upload(HttpServletRequest request,
            @RequestParam long projectId, @RequestParam(required = false) Long componentId,
            @RequestParam Long attachmentId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(type(request), projectId, componentId, attachmentId, user), TraceId.getOrCreate());
    }

    @PutMapping({"/mappings/{id}/upload", "/dependencies/{id}/upload", "/programs/{id}/upload", "/topics/{id}/upload", "/release-drills/{id}/upload"})
    @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> replace(HttpServletRequest request, @PathVariable long id,
            @RequestParam(required = false) Long componentId,
            @RequestParam Long attachmentId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.replace(type(request), id, componentId, attachmentId, user), TraceId.getOrCreate());
    }

    @PostMapping({"/mappings/delete", "/dependencies/delete", "/programs/delete", "/topics/delete", "/release-drills/delete"})
    @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(HttpServletRequest request, @RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(type(request), ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping({"/mappings/{id}/download", "/dependencies/{id}/download", "/programs/{id}/download", "/topics/{id}/download", "/release-drills/{id}/download"})
    public ResponseEntity<StreamingResponseBody> download(HttpServletRequest request, @PathVariable long id,
                                                          @AuthenticationPrincipal AuthUser user) {
        return attachmentStream.stream(service.downloadAttachmentId(type(request), id, user), user, request);
    }

    private String type(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String rest = uri.startsWith(PREFIX) ? uri.substring(PREFIX.length()) : uri;
        String segment = rest.split("/")[0];
        String assetType = RESOURCE_TYPES.get(segment);
        if (assetType == null) throw new IllegalArgumentException("Unknown content resource: " + segment);
        return assetType;
    }
}
