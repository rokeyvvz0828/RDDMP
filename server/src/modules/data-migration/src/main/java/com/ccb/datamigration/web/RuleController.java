package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.RuleService;
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
 * 迁移检核规则 Controller（REQ-20260820-031 增量，对标迁移映射/迁移方案）。
 * 路由前缀 /api/data-migration/rules。回收站入口收敛到统一 /api/data-migration/recycle-bin（type=RULE）。
 */
@RestController("dataMigrationRuleController")
@RequestMapping("/api/data-migration/rules")
@PreAuthorize("hasAnyAuthority('data-migration:content:validation-rules','data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class RuleController {
    private static final String EXPORT_FILE_NAME = "data-migration-rules.xlsx";

    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String checkTargetType,
            @RequestParam(required = false) String ruleCategory,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String ruleKeyword,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.list(projectId, checkTargetType, ruleCategory, systemCode,
                ruleKeyword, keyword, page, size, user), TraceId.getOrCreate());
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data-migration-rule-template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.downloadTemplate());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String checkTargetType,
            @RequestParam(required = false) String ruleCategory,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String ruleKeyword,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + EXPORT_FILE_NAME)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.export(projectId, checkTargetType, ruleCategory, systemCode, ruleKeyword, keyword, user));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.detail(id, user), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:validation-rules:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.create(body, user), TraceId.getOrCreate());
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('data-migration:content:validation-rules:create','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> importRules(
            @RequestParam(required = false) Long projectId,
            @RequestPart MultipartFile file,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.importRules(projectId, file, user), TraceId.getOrCreate());
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('data-migration:content:validation-rules:update','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.update(id, body, user), TraceId.getOrCreate());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('data-migration:content:validation-rules:delete','data-migration:write','data-migration:manage','system:admin')")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids, @AuthenticationPrincipal AuthUser user) {
        service.delete(ids, user);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
