package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.lifecycle.LifecycleDataSourceService;
import com.ccb.datamigration.lifecycle.model.LifecycleComponentOption;
import com.ccb.datamigration.lifecycle.model.LifecycleMemberOption;
import com.ccb.datamigration.lifecycle.model.LifecycleRoleOption;
import com.ccb.security.model.AuthUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 生命周期平台底座选项接口：成员/组件/角色选择器（服务端 RBAC + 数据范围前置，R9）。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class LifecycleOptionsController {
    private final LifecycleDataSourceService dataSource;

    public LifecycleOptionsController(LifecycleDataSourceService dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/options/members")
    public ApiResponse<PageResult<LifecycleMemberOption>> memberOptions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dataSource.memberOptions(user, new PageQuery(page, size), keyword),
                TraceId.getOrCreate());
    }

    @GetMapping("/options/components")
    public ApiResponse<List<LifecycleComponentOption>> componentOptions(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dataSource.componentOptions(user, projectId, keyword), TraceId.getOrCreate());
    }

    @GetMapping("/options/roles")
    public ApiResponse<List<LifecycleRoleOption>> roleOptions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dataSource.roleOptions(user), TraceId.getOrCreate());
    }
}
