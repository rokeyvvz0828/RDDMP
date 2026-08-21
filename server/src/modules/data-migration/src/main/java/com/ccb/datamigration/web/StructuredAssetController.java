package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.DashboardService;
import com.ccb.datamigration.service.StructuredAssetService;
import com.ccb.datamigration.service.ExcelService;
import com.ccb.security.model.AuthUser;
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
    private final StructuredAssetService structured;
    private final DashboardService dashboard;
    private final ExcelService excel;
    public StructuredAssetController(StructuredAssetService structured, DashboardService dashboard, ExcelService excel) { this.structured = structured; this.dashboard = dashboard; this.excel = excel; }
    @GetMapping("/structured/{type}") public ApiResponse<List<Map<String,Object>>> list(@PathVariable String type, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.list(type, keyword, user), TraceId.getOrCreate()); }
    @PostMapping("/structured/{type}") public ApiResponse<Map<String,Object>> save(@PathVariable String type, @RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.save(type, body, user), TraceId.getOrCreate()); }
    @PutMapping("/structured/{type}/{id}") public ApiResponse<Map<String,Object>> update(@PathVariable String type, @PathVariable long id, @RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(structured.update(type, id, body, user), TraceId.getOrCreate()); }
    @PostMapping("/structured/{type}/delete") public ApiResponse<Void> delete(@PathVariable String type, @RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) { structured.delete(ids, type, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @GetMapping("/dashboard/overall") public ApiResponse<Map<String,Object>> overall(@AuthenticationPrincipal AuthUser user) { return ApiResponse.success(dashboard.overall(user), TraceId.getOrCreate()); }
    @GetMapping({"/dashboard/component", "/dashboard/components"}) public ApiResponse<List<Map<String,Object>>> component(@RequestParam(required=false) Long projectId, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(dashboard.component(user, projectId), TraceId.getOrCreate()); }
    @GetMapping("/structured/{type}/export") public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam(required=false) Long projectId, @RequestParam(required=false) Long componentId, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data-migration-" + type + ".xlsx").contentType(MediaType.APPLICATION_OCTET_STREAM).body(excel.export(type, projectId, componentId, keyword, user)); }
    @PostMapping("/structured/{type}/import") public ApiResponse<Map<String,Object>> importAssets(@PathVariable String type, @RequestPart MultipartFile file, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(excel.importAssets(type, file, user), TraceId.getOrCreate()); }
}
