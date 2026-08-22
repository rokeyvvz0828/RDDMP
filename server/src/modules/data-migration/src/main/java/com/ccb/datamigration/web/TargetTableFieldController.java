package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.TargetTableService;
import com.ccb.security.model.AuthUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/data-migration/target-table-fields")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:base:table-fields-target','data-migration:base:table-fields-intermediate')")
public class TargetTableFieldController {
    private final TargetTableService service;
    public TargetTableFieldController(TargetTableService service) { this.service = service; }

    private String cat(String category) { return category == null ? "TARGET" : category; }

    @PutMapping("/{fieldId}") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:update','data-migration:base:table-fields-intermediate:update')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long fieldId, @RequestParam(required = false) String category, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.updateField(fieldId, cat(category), body, user), TraceId.getOrCreate());
    }

    @DeleteMapping("/{fieldId}") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:delete','data-migration:base:table-fields-intermediate:delete')")
    public ApiResponse<Void> delete(@PathVariable long fieldId, @RequestParam(required = false) String category, @AuthenticationPrincipal AuthUser user) {
        service.deleteField(fieldId, cat(category), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
