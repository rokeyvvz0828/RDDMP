package com.ccb.system.audit;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/audit")
@PreAuthorize("hasAuthority('system:audit:list')")
public class OperationAuditController {
    private final OperationAuditService service;

    public OperationAuditController(OperationAuditService service) {
        this.service = service;
    }

    @GetMapping("/operations")
    public ApiResponse<SystemPage<OperationAuditRecord>> operations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.operations(new OperationAuditQuery(startDate, endDate, moduleCode,
                operationType, success, projectId, keyword, page, size), actor), TraceId.getOrCreate());
    }

    @GetMapping("/logins")
    public ApiResponse<SystemPage<LoginAuditRecord>> logins(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.logins(new LoginAuditQuery(startDate, endDate, success, keyword, page, size),
                actor), TraceId.getOrCreate());
    }

    @GetMapping("/projects")
    public ApiResponse<List<AuditProjectOption>> projects(@AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.projects(actor), TraceId.getOrCreate());
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Boolean>> capabilities(@AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(Map.of("loginAudit", service.superAdmin(actor)), TraceId.getOrCreate());
    }
}
