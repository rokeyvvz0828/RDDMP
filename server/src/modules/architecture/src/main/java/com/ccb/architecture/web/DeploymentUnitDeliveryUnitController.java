package com.ccb.architecture.web;

import com.ccb.architecture.service.DeliveryUnitService;
import com.ccb.architecture.service.DeliveryUnitService.RelatedDeliveryUnitView;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部署单元侧的交付单元子资源：关联查询、同物理子系统候选与覆盖式保存。
 *
 * <p>关联无方向，两侧共享同一张关系表；按用户确认的方案 A，本入口的关联编辑不发布部署单元新版本。
 * 关联写入统一要求 {@code architecture:delivery-unit:manage}，与发起侧无关。</p>
 */
@RestController
@RequestMapping("/api/architecture/deployment-units")
public class DeploymentUnitDeliveryUnitController {
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:deployment-unit:manage', "
                    + "'architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                    + "'architecture:view', 'architecture:apply', 'architecture:manage')";
    private static final String MANAGE_PERMISSION = "hasAuthority('architecture:delivery-unit:manage')";

    private final DeliveryUnitService service;
    private final ProjectAccessService projectAccessService;

    public DeploymentUnitDeliveryUnitController(DeliveryUnitService service,
                                                ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/{id}/delivery-units")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<List<RelatedDeliveryUnitView>> relatedDeliveryUnits(
            @PathVariable long id,
            @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor) {
        ProjectAccess project = projectAccessService.requireAccessible(projectRef, actor);
        return ApiResponse.success(service.relatedDeliveryUnits(actor, project, id), TraceId.getOrCreate());
    }

    /** 同物理子系统下启用交付单元候选。 */
    @GetMapping("/{id}/delivery-unit-options")
    @PreAuthorize(VIEW_PERMISSION)
    public ApiResponse<PageResult<RelatedDeliveryUnitView>> deliveryUnitOptions(
            @PathVariable long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor) {
        ProjectAccess project = projectAccessService.requireAccessible(projectRef, actor);
        return ApiResponse.success(service.deliveryUnitOptionsForDeploymentUnit(actor, project, id, keyword,
                new PageQuery(page, size)), TraceId.getOrCreate());
    }

    /** 从部署单元侧覆盖式更新关联集合。 */
    @PutMapping("/{id}/delivery-units")
    @PreAuthorize(MANAGE_PERMISSION)
    public ApiResponse<List<RelatedDeliveryUnitView>> replaceDeliveryUnits(
            @PathVariable long id,
            @RequestBody DeliveryUnitController.DeliveryUnitRelationCommand command,
            @RequestParam String projectRef,
            @AuthenticationPrincipal AuthUser actor) {
        String traceId = TraceId.getOrCreate();
        ProjectAccess project = projectAccessService.requireAccessible(projectRef, actor);
        List<Long> deliveryUnitIds = command == null ? null : command.deploymentUnitIds();
        return ApiResponse.success(service.replaceDeploymentUnitDeliveryUnits(actor, project, id, deliveryUnitIds,
                traceId), traceId);
    }
}
