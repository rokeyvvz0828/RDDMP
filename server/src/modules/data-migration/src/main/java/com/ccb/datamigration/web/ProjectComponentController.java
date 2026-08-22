package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ProjectComponentService;
import com.ccb.security.model.AuthUser;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-migration")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:dashboard','data-migration:components')")
public class ProjectComponentController {
    private final ProjectComponentService service;
    public ProjectComponentController(ProjectComponentService service) { this.service = service; }

    @GetMapping("/components")
    public ApiResponse<PageResult<Map<String, Object>>> components(@RequestParam(required = false) Long projectId,
                                                                   @RequestParam(required = false) String businessGroupName,
                                                                   @RequestParam(required = false) String systemCode,
                                                                   @RequestParam(required = false) String responsibleTeam,
                                                                   @RequestParam(required = false) String systemKeyword,
                                                                   @RequestParam(required = false) Integer totalCheck,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size,
                                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.components(user, projectId, businessGroupName, systemCode,
                responsibleTeam, systemKeyword, totalCheck, keyword, new PageQuery(page, size)), TraceId.getOrCreate());
    }
    @GetMapping("/components/export")
    public ResponseEntity<byte[]> exportComponents(@RequestParam(required = false) Long projectId,
                                                   @RequestParam(required = false) String businessGroupName,
                                                   @RequestParam(required = false) String systemCode,
                                                   @RequestParam(required = false) String responsibleTeam,
                                                   @RequestParam(required = false) String systemKeyword,
                                                   @RequestParam(required = false) Integer totalCheck,
                                                   @RequestParam(required = false) String keyword,
                                                   @AuthenticationPrincipal AuthUser user) {
        byte[] bytes = service.exportComponents(user, projectId, businessGroupName, systemCode,
                responsibleTeam, systemKeyword, totalCheck, keyword);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=system-components.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
    @PostMapping("/components") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')") public ApiResponse<Map<String, Object>> createComponent(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.createComponent(body, user), TraceId.getOrCreate()); }
    @PutMapping("/components/{id}") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')") public ApiResponse<Map<String, Object>> updateComponent(@PathVariable long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) { return ApiResponse.success(service.updateComponent(id, body, user), TraceId.getOrCreate()); }
    @DeleteMapping("/components/{id}") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin')") public ApiResponse<Void> deleteComponent(@PathVariable long id, @AuthenticationPrincipal AuthUser user) { service.deleteComponent(id, user); return ApiResponse.success(null, TraceId.getOrCreate()); }
}
