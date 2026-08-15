package com.ccb.architecture.web;

import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.architecture.service.PhysicalSubsystemService;
import com.ccb.architecture.service.PhysicalSubsystemService.PhysicalSubsystemView;
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
@RequestMapping("/api/architecture/physical-subsystems")
public class PhysicalSubsystemController {
    private final PhysicalSubsystemService service;

    public PhysicalSubsystemController(PhysicalSubsystemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('architecture:physical:list')")
    public ApiResponse<PageResult<PhysicalSubsystemView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String shortName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String businessGroupName,
            @RequestParam(required = false) Long responsibleTeamOrgId,
            @RequestParam(required = false) Long logicalSubsystemId,
            @AuthenticationPrincipal AuthUser actor) {
        PhysicalSubsystemQuery query = new PhysicalSubsystemQuery(code, shortName, name, businessGroupName,
                responsibleTeamOrgId, logicalSubsystemId);
        return ApiResponse.success(service.list(actor, new PageQuery(page, size), query), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('architecture:physical:list')")
    public ApiResponse<PhysicalSubsystemView> detail(@PathVariable long id,
                                                      @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.detail(actor, id), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('architecture:physical:create')")
    public ApiResponse<PhysicalSubsystemView> create(@RequestBody PhysicalSubsystemCommand command,
                                                      @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.create(actor, command, traceId), traceId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('architecture:physical:update')")
    public ApiResponse<PhysicalSubsystemView> update(@PathVariable long id,
                                                      @RequestBody PhysicalSubsystemCommand command,
                                                      @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.update(actor, id, command, traceId), traceId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('architecture:physical:delete')")
    public ApiResponse<Void> delete(@PathVariable long id,
                                    @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        service.delete(actor, id, traceId);
        return ApiResponse.success(null, traceId);
    }
}
