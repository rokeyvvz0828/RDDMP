package com.ccb.architecture.web;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitQuery;
import com.ccb.architecture.service.DeploymentUnitService;
import com.ccb.architecture.service.DeploymentUnitService.DeploymentUnitView;
import com.ccb.architecture.service.DeploymentUnitService.DeploymentUnitVersionView;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部署单元 API。查询要求查看权限（含既有三级架构权限）；全部写操作仅限
 * {@code architecture:deployment-unit:manage}（技术架构师）。
 */
@RestController
@RequestMapping("/api/architecture/deployment-units")
public class DeploymentUnitController {
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:view', 'architecture:apply', 'architecture:manage')";
    private static final String MANAGE_PERMISSION = "hasAuthority('architecture:deployment-unit:manage')";

    private final DeploymentUnitService service;

    public DeploymentUnitController(DeploymentUnitService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<PageResult<DeploymentUnitView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String shortName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthUser actor) {
        DeploymentUnitQuery query = new DeploymentUnitQuery(code, shortName, name, physicalSubsystemId, kind, status);
        return ApiResponse.success(service.list(actor, new PageQuery(page, size), query), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<DeploymentUnitView> detail(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.detail(actor, id), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<List<DeploymentUnitVersionView>> versions(@PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.versions(actor, id), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeploymentUnitView> create(@RequestBody DeploymentUnitCommand command,
                                                  @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.create(actor, command, traceId), traceId);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeploymentUnitView> update(@PathVariable long id,
                                                  @RequestBody DeploymentUnitCommand command,
                                                  @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.update(actor, id, command, traceId), traceId);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeploymentUnitView> deactivate(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.deactivate(actor, id, traceId), traceId);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeploymentUnitView> reactivate(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.reactivate(actor, id, traceId), traceId);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeploymentUnitView> voidUnit(@PathVariable long id, @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.voidUnit(actor, id, traceId), traceId);
    }
}
