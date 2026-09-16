package com.ccb.architecture.web;

import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitCommand;
import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitQuery;
import com.ccb.architecture.service.DeliveryUnitService;
import com.ccb.architecture.service.DeliveryUnitService.DeliveryUnitView;
import com.ccb.architecture.service.DeploymentUnitService.RelatedDeploymentUnitView;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
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

import java.util.List;

/**
 * 交付单元 API。查询要求查看权限（含既有三级架构权限）；全部写操作仅限
 * {@code architecture:delivery-unit:manage}（技术架构师）。
 */
@RestController
@RequestMapping("/api/architecture/delivery-units")
public class DeliveryUnitController {
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                    + "'architecture:view', 'architecture:apply', 'architecture:manage')";
    private static final String MANAGE_PERMISSION = "hasAuthority('architecture:delivery-unit:manage')";

    private final DeliveryUnitService service;
    private final ProjectAccessService projectAccessService;

    public DeliveryUnitController(DeliveryUnitService service, ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<PageResult<DeliveryUnitView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long physicalSubsystemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String artifactTypeCode,
            @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor) {
        DeliveryUnitQuery query = new DeliveryUnitQuery(name, physicalSubsystemId, status, artifactTypeCode);
        return ApiResponse.success(service.list(actor, project(projectRef, actor), new PageQuery(page, size), query),
                TraceId.getOrCreate());
    }

    /** 关联选择用的部署单元候选：限定同一物理子系统下的启用部署单元。 */
    @GetMapping("/deployment-unit-options")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<PageResult<RelatedDeploymentUnitView>> deploymentUnitOptions(
            @RequestParam Long physicalSubsystemId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long excludeId,
            @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.deploymentUnitOptions(actor, project(projectRef, actor),
                physicalSubsystemId, keyword, excludeId, new PageQuery(page, size)), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<DeliveryUnitView> detail(@PathVariable long id,
                                                @RequestParam String projectRef,
                                                @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.success(service.detail(actor, project(projectRef, actor), id), TraceId.getOrCreate());
    }

    @PostMapping
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeliveryUnitView> create(@RequestBody DeliveryUnitCommand command,
                                                @RequestParam String projectRef,
                                                @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.create(actor, project(projectRef, actor), command, traceId), traceId);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeliveryUnitView> update(@PathVariable long id,
                                                @RequestBody DeliveryUnitCommand command,
                                                @RequestParam String projectRef,
                                                @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.update(actor, project(projectRef, actor), id, command, traceId), traceId);
    }

    /** 覆盖式更新交付单元与部署单元的关联集合。 */
    @PutMapping("/{id}/deployment-units")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeliveryUnitView> replaceDeploymentUnits(
            @PathVariable long id,
            @RequestBody DeliveryUnitRelationCommand command,
            @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        List<Long> deploymentUnitIds = command == null ? null : command.deploymentUnitIds();
        return ApiResponse.success(service.replaceDeploymentUnits(actor, project(projectRef, actor), id,
                deploymentUnitIds, traceId), traceId);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeliveryUnitView> deactivate(@PathVariable long id,
                                                    @RequestParam String projectRef,
                                                    @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.deactivate(actor, project(projectRef, actor), id, traceId), traceId);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<DeliveryUnitView> reactivate(@PathVariable long id,
                                                    @RequestParam String projectRef,
                                                    @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        return ApiResponse.success(service.reactivate(actor, project(projectRef, actor), id, traceId), traceId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<Void> delete(@PathVariable long id,
                                    @RequestParam String projectRef,
                                    @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        service.delete(actor, project(projectRef, actor), id, traceId);
        return ApiResponse.success(null, traceId);
    }

    private ProjectAccess project(String projectRef, AuthUser actor) {
        return projectAccessService.requireAccessible(projectRef, actor);
    }

    /** 关联覆盖式更新请求体。 */
    public record DeliveryUnitRelationCommand(List<Long> deploymentUnitIds) {
    }
}
