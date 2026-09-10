package com.ccb.architecture.web;

import com.ccb.architecture.service.DeliveryUnitService;
import com.ccb.architecture.service.DeliveryUnitService.RelatedDeliveryUnitView;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部署单元侧只读子资源：某部署单元关联的交付单元。
 *
 * <p>关联无方向，本接口只提供查询，编辑入口在交付单元详情。</p>
 */
@RestController
@RequestMapping("/api/architecture/deployment-units")
public class DeploymentUnitDeliveryUnitController {
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:deployment-unit:manage', "
                    + "'architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                    + "'architecture:view', 'architecture:apply', 'architecture:manage')";

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
}
