package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.ProjectComponentService;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据迁移模块统一“关联系统”下拉入口（方案B）。
 *
 * <p>数据迁移各页面（迁移方案、映射、规则、目标表/中间表、问题清单、会议纪要、专题材料、投产及演练、
 * 迁移程序、组件清单等）的“系统/组件清单”下拉统一在此获取，口径与「系统/组件清单」保持一致：
 * 仅返回当前项目 {@code dm_component} 启用系统。
 *
 * <p>权限沿用数据迁移基础访问权限；页面级功能权限仍由各页面自身接口守卫生效。
 */
@RestController("dataMigrationSystemOptionsController")
@RequestMapping("/api/data-migration/components")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin',"
        + "'data-migration:content:issues','data-migration:content:mappings','data-migration:content:meetings',"
        + "'data-migration:content:topics','data-migration:content:release-drills','data-migration:content:plans',"
        + "'data-migration:content:programs','data-migration:content:validation-rules',"
        + "'data-migration:base:table-fields-target','data-migration:base:table-fields-intermediate',"
        + "'data-migration:components','data-migration:dashboard')")
public class DataMigrationSystemOptionsController {

    private final ProjectComponentService service;

    public DataMigrationSystemOptionsController(ProjectComponentService service) {
        this.service = service;
    }

    /** 统一“关联系统”下拉：{@code projectId} 必填，仅返回该项目启用系统，前端本地随输随筛。 */
    @GetMapping("/options/systems")
    public ApiResponse<List<Map<String, Object>>> systemOptions(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.getSystemOptions(projectId, user), TraceId.getOrCreate());
    }
}
