package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.DashboardService;
import com.ccb.datamigration.service.StructuredAssetService;
import com.ccb.datamigration.service.ExcelService;
import com.ccb.security.model.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/data-migration")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:dashboard','data-migration:structured')")
public class StructuredAssetController {
    private static final Map<String, String> CONTENT_RESOURCES = Map.of("rules", "RULE", "parameters", "PARAMETER");
    private static final String PREFIX = "/api/data-migration/";
    private final StructuredAssetService structured;
    private final DashboardService dashboard;
    private final ExcelService excel;
    public StructuredAssetController(StructuredAssetService structured, DashboardService dashboard, ExcelService excel) { this.structured = structured; this.dashboard = dashboard; this.excel = excel; }
    @GetMapping("/structured/{type}") public ApiResponse<List<Map<String,Object>>> list(@PathVariable String type, @RequestParam(required=false) Long projectId, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.list(type, projectId, keyword, user), TraceId.getOrCreate()); }
    @PostMapping("/structured/{type}") @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> save(@PathVariable String type, @RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.save(type, body, user), TraceId.getOrCreate()); }
    @PutMapping("/structured/{type}/{id}") @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> update(@PathVariable String type, @PathVariable long id, @RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.update(type, id, body, user), TraceId.getOrCreate()); }
    @PostMapping("/structured/{type}/delete") @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Void> delete(@PathVariable String type, @RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) { structured.delete(ids, type, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @GetMapping("/dashboard/overall") public ApiResponse<Map<String,Object>> overall(@RequestParam(required=false) Long projectId, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(dashboard.overall(projectId, user), TraceId.getOrCreate()); }
    @GetMapping({"/dashboard/component", "/dashboard/components"}) public ApiResponse<List<Map<String,Object>>> component(@RequestParam(required=false) Long projectId, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(dashboard.component(user, projectId), TraceId.getOrCreate()); }
    @GetMapping("/structured/{type}/export") public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam(required=false) Long projectId, @RequestParam(required=false) Long componentId, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data-migration-" + type + ".xlsx").contentType(MediaType.APPLICATION_OCTET_STREAM).body(excel.export(type, projectId, componentId, keyword, user)); }
    @PostMapping("/structured/{type}/import") @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> importAssets(@PathVariable String type, @RequestParam(required=false) Long projectId, @RequestPart MultipartFile file, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(excel.importAssets(type, projectId, file, user), TraceId.getOrCreate()); }

    // ==== 内容结构化菜单专用端点（REQ-20260831-050）：仅保留 /rules、/parameters。中间表统一走目标表接口。 ====
    @GetMapping({"/rules", "/parameters"}) public ApiResponse<List<Map<String,Object>>> contentList(HttpServletRequest request, @RequestParam(required=false) Long projectId, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.list(contentType(request), projectId, keyword, user), TraceId.getOrCreate()); }
    @PostMapping({"/rules", "/parameters"}) @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> contentSave(HttpServletRequest request, @RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.save(contentType(request), body, user), TraceId.getOrCreate()); }
    @PutMapping({"/rules/{id}", "/parameters/{id}"}) @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> contentUpdate(HttpServletRequest request, @PathVariable long id, @RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.update(contentType(request), id, body, user), TraceId.getOrCreate()); }
    @PostMapping({"/rules/delete", "/parameters/delete"}) @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Void> contentDelete(HttpServletRequest request, @RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) { structured.delete(ids, contentType(request), user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @GetMapping({"/rules/export", "/parameters/export"}) public ResponseEntity<byte[]> contentExport(HttpServletRequest request, @RequestParam(required=false) Long projectId, @RequestParam(required=false) Long componentId, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { String type = contentType(request); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data-migration-" + type + ".xlsx").contentType(MediaType.APPLICATION_OCTET_STREAM).body(excel.export(type, projectId, componentId, keyword, user)); }
    @PostMapping({"/rules/import", "/parameters/import"}) @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> contentImport(HttpServletRequest request, @RequestParam(required=false) Long projectId, @RequestPart MultipartFile file, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(excel.importAssets(contentType(request), projectId, file, user), TraceId.getOrCreate()); }

    private static String contentType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String rest = uri.startsWith(PREFIX) ? uri.substring(PREFIX.length()) : uri;
        String segment = rest.split("/")[0];
        String type = CONTENT_RESOURCES.get(segment);
        if (type == null) throw new com.ccb.common.exception.BusinessException(com.ccb.common.exception.ErrorCode.BAD_REQUEST, "Unknown structured resource: " + segment);
        return type;
    }
}
