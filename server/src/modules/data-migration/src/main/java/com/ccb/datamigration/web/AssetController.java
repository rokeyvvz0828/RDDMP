package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.AssetService;
import com.ccb.security.model.AuthUser;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data-migration")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:dashboard','data-migration:assets','data-migration:recycle-bin')")
public class AssetController {
    private final AssetService service;
    public AssetController(AssetService service) { this.service = service; }
    @GetMapping("/assets") public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) String type, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.list(type, keyword, user, false), TraceId.getOrCreate()); }
    @GetMapping("/recycle-bin") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')") public ApiResponse<List<Map<String,Object>>> recycle(@RequestParam(required=false) String type, @RequestParam(required=false) String keyword, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.list(type, keyword, user, true), TraceId.getOrCreate()); }
    @PostMapping("/assets/{type}/upload") @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Map<String,Object>> upload(@PathVariable String type, @RequestParam long projectId, @RequestParam(required=false) Long componentId, @RequestParam String assetCode, @RequestPart MultipartFile file, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.upload(type, projectId, componentId, assetCode, file, user), TraceId.getOrCreate()); }
    @PostMapping("/assets/delete") @PreAuthorize("hasAnyAuthority('data-migration:write','data-migration:manage','system:admin')") public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) { service.delete(ids, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @PostMapping("/recycle-bin/restore") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')") public ApiResponse<Void> restore(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) { service.restore(ids, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @PostMapping("/recycle-bin/purge") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')") public ApiResponse<Void> purge(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) { service.purge(ids, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
    @GetMapping("/assets/{id}/download") public ApiResponse<String> download(@PathVariable long id, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.download(id, user), TraceId.getOrCreate()); }
}
