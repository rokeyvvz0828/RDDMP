package com.ccb.architecture.web;

import com.ccb.architecture.model.LogicalSubsystemOption;
import com.ccb.architecture.model.OrganizationOption;
import com.ccb.architecture.model.ParameterOption;
import com.ccb.architecture.model.UserOption;
import com.ccb.architecture.service.ArchitectureOptionsService;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/architecture/options")
public class ArchitectureOptionsController {
    private final ArchitectureOptionsService service;

    public ArchitectureOptionsController(ArchitectureOptionsService service) {
        this.service = service;
    }

    @GetMapping("/logical-subsystem/organizations")
    @PreAuthorize("hasAuthority('architecture:logical:list')")
    public ApiResponse<PageResult<OrganizationOption>> logicalOrganizations(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser actor) {
        return success(service.organizations(actor, new PageQuery(page, size), keyword));
    }

    @GetMapping("/physical-subsystem/organizations")
    @PreAuthorize("hasAuthority('architecture:physical:list')")
    public ApiResponse<PageResult<OrganizationOption>> physicalOrganizations(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser actor) {
        return success(service.organizations(actor, new PageQuery(page, size), keyword));
    }

    @GetMapping("/logical-subsystem/users")
    @PreAuthorize("hasAuthority('architecture:logical:list')")
    public ApiResponse<PageResult<UserOption>> logicalUsers(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser actor) {
        return success(service.users(actor, new PageQuery(page, size), keyword));
    }

    @GetMapping("/physical-subsystem/users")
    @PreAuthorize("hasAuthority('architecture:physical:list')")
    public ApiResponse<PageResult<UserOption>> physicalUsers(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword, @AuthenticationPrincipal AuthUser actor) {
        return success(service.users(actor, new PageQuery(page, size), keyword));
    }

    @GetMapping("/logical-subsystem/parameters/{categoryCode}")
    @PreAuthorize("hasAuthority('architecture:logical:list')")
    public ApiResponse<List<ParameterOption>> logicalParameters(
            @PathVariable String categoryCode, @AuthenticationPrincipal AuthUser actor) {
        return success(service.parameters(actor, ArchitectureOptionsService.LOGICAL_RESOURCE, categoryCode));
    }

    @GetMapping("/physical-subsystem/parameters/{categoryCode}")
    @PreAuthorize("hasAuthority('architecture:physical:list')")
    public ApiResponse<List<ParameterOption>> physicalParameters(
            @PathVariable String categoryCode, @AuthenticationPrincipal AuthUser actor) {
        return success(service.parameters(actor, ArchitectureOptionsService.PHYSICAL_RESOURCE, categoryCode));
    }

    @GetMapping("/physical-subsystem/logical-subsystems")
    @PreAuthorize("hasAuthority('architecture:physical:list')")
    public ApiResponse<PageResult<LogicalSubsystemOption>> physicalLogicalSubsystems(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String code, @RequestParam(required = false) String name,
            @AuthenticationPrincipal AuthUser actor) {
        return success(service.logicalSubsystems(actor, new PageQuery(page, size), code, name));
    }

    @GetMapping("/{resource}/organizations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unknownOrganizations(@PathVariable String resource) {
        throw unknownResource(resource);
    }

    @GetMapping("/{resource}/users")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unknownUsers(@PathVariable String resource) {
        throw unknownResource(resource);
    }

    @GetMapping("/{resource}/parameters/{categoryCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unknownParameters(@PathVariable String resource, @PathVariable String categoryCode) {
        throw unknownResource(resource);
    }

    @GetMapping("/{resource}/logical-subsystems")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> unknownLogicalSubsystems(@PathVariable String resource) {
        throw unknownResource(resource);
    }

    private ArchitectureNotFoundException unknownResource(String resource) {
        return new ArchitectureNotFoundException("未知或不支持的选项资源上下文：" + resource);
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }
}
