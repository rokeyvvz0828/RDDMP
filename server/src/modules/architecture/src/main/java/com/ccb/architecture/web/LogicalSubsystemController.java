package com.ccb.architecture.web;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemCommand;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.service.LogicalSubsystemService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/architecture/logical-subsystems")
public class LogicalSubsystemController {
    private final LogicalSubsystemService service;

    public LogicalSubsystemController(LogicalSubsystemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('architecture:logical:list', 'architecture:view', 'architecture:apply', 'architecture:manage')")
    public ApiResponse<PageResult<LogicalSubsystem>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String shortName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long businessOrgId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.list(actor, new PageQuery(page, size),
                new LogicalSubsystemQuery(code, shortName, name, businessOrgId, status)), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:logical:list', 'architecture:view', 'architecture:apply', 'architecture:manage')")
    public ApiResponse<LogicalSubsystem> detail(@PathVariable long id,
                                                 @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.detail(actor, id), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('architecture:logical:create', 'architecture:apply', 'architecture:manage')")
    public ApiResponse<LogicalSubsystem> create(@RequestBody LogicalSubsystemCommand command,
                                                 @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.create(actor, command, traceId), traceId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:logical:update', 'architecture:apply', 'architecture:manage')")
    public ApiResponse<LogicalSubsystem> update(@PathVariable long id,
                                                 @RequestBody LogicalSubsystemCommand command,
                                                 @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.update(actor, id, command, traceId), traceId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:logical:delete', 'architecture:apply', 'architecture:manage')")
    public ApiResponse<Void> delete(@PathVariable long id,
                                    @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        service.delete(actor, id, traceId);
        return ApiResponse.success(null, traceId);
    }
}
