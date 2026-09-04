package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.TargetTableService;
import com.ccb.security.model.AuthUser;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data-migration/target-tables")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin','data-migration:base:table-fields-target','data-migration:base:table-fields-intermediate')")
public class TargetTableController {
    private final TargetTableService service;
    public TargetTableController(TargetTableService service) { this.service = service; }

    private String cat(String category) { return category == null ? "TARGET" : category; }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String category,
                                                             @RequestParam(required = false) Long projectId,
                                                             @RequestParam(required = false) String systemCode,
                                                             @RequestParam(required = false) Integer isKeyField,
                                                             @RequestParam(required = false) String dictCode,
                                                             @RequestParam(required = false) String tableKeyword,
                                                             @RequestParam(required = false) String fieldKeyword,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @AuthenticationPrincipal AuthUser user) {
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("systemCode", systemCode);
        params.put("isKeyField", isKeyField);
        params.put("dictCode", dictCode);
        params.put("tableKeyword", tableKeyword);
        params.put("fieldKeyword", fieldKeyword);
        return ApiResponse.success(service.list(cat(category), params, user, new PageQuery(page, size)), TraceId.getOrCreate());
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = service.template();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=target-table-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);
    }

    @PostMapping("/import") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:create','data-migration:base:table-fields-intermediate:create')")
    public ApiResponse<Map<String, Object>> importTables(@RequestParam(required = false) String category,
                                                         @RequestParam(required = false) Long projectId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @AuthenticationPrincipal AuthUser user) {
        try {
            return ApiResponse.success(service.importTables(cat(category), projectId, file.getBytes(), user), TraceId.getOrCreate());
        } catch (IOException ex) {
            return ApiResponse.failure(ErrorCode.BAD_REQUEST, ex.getMessage(), TraceId.getOrCreate());
        }
    }

    @GetMapping("/export") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target','data-migration:base:table-fields-intermediate')")
    public ResponseEntity<byte[]> exportTables(@RequestParam(required = false) String category,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String systemCode,
                                               @RequestParam(required = false) Integer isKeyField,
                                               @RequestParam(required = false) String dictCode,
                                               @RequestParam(required = false) String tableKeyword,
                                               @RequestParam(required = false) String fieldKeyword,
                                               @RequestParam(required = false) List<Long> fieldCodes,
                                               @AuthenticationPrincipal AuthUser user) {
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("systemCode", systemCode);
        params.put("isKeyField", isKeyField);
        params.put("dictCode", dictCode);
        params.put("tableKeyword", tableKeyword);
        params.put("fieldKeyword", fieldKeyword);
        byte[] bytes = service.exportTables(cat(category), params, fieldCodes, user);
        String name = "TARGET".equals(cat(category)) ? "target-tables" : "intermediate-tables";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);
    }

    @GetMapping("/{tableCode}")
    public ApiResponse<Map<String, Object>> get(@PathVariable long tableCode, @RequestParam(required = false) String category, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getDetail(tableCode, cat(category), user), TraceId.getOrCreate());
    }

    @PostMapping @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:create','data-migration:base:table-fields-intermediate:create')")
    public ApiResponse<Map<String, Object>> create(@RequestParam(required = false) String category, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.createTable(cat(category), body, user), TraceId.getOrCreate());
    }

    @PutMapping("/{tableCode}") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:update','data-migration:base:table-fields-intermediate:update')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long tableCode, @RequestParam(required = false) String category, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.updateTable(tableCode, cat(category), body, user), TraceId.getOrCreate());
    }

    @PostMapping("/batch-delete") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:delete','data-migration:base:table-fields-intermediate:delete')")
    public ApiResponse<Void> batchDelete(@RequestParam(required = false) String category, @RequestBody List<Long> tableCodes, @AuthenticationPrincipal AuthUser user) {
        service.deleteTables(tableCodes, cat(category), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @DeleteMapping("/{tableCode}") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:delete','data-migration:base:table-fields-intermediate:delete')")
    public ApiResponse<Void> delete(@PathVariable long tableCode, @RequestParam(required = false) String category, @AuthenticationPrincipal AuthUser user) {
        service.deleteTables(List.of(tableCode), cat(category), user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/{tableCode}/fields")
    public ApiResponse<List<Map<String, Object>>> listFields(@PathVariable long tableCode, @RequestParam(required = false) String category, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.listFields(tableCode, cat(category), user), TraceId.getOrCreate());
    }

    @PostMapping("/{tableCode}/fields") @PreAuthorize("hasAnyAuthority('data-migration:manage','system:admin','data-migration:base:table-fields-target:update','data-migration:base:table-fields-intermediate:update')")
    public ApiResponse<Map<String, Object>> addField(@PathVariable long tableCode, @RequestParam(required = false) String category, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.addField(tableCode, cat(category), body, user), TraceId.getOrCreate());
    }
}
