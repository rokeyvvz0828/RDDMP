package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final DashboardRepository repository;
    private final DataMigrationPermissionService permissions;
    public DashboardService(DashboardRepository repository, DataMigrationPermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    /**
     * 整体看板（T32 项目隔离）：组件数、活动资产数、资产类型分布均限定在传入的单个可访问项目内。
     *
     * <p>口径变更：不再读租户口径的日快照（{@code PROJECT_TOTAL}/{@code COMPONENT_TOTAL} 仅按租户汇总，
     * 会泄露其他项目的计数），改为项目内实时计数；“项目”卡片含义相应改为“当前调用者可访问的项目数”。
     */
    public Map<String,Object> overall(Long projectId, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("projects", accessibleProjectCount(user));
        result.put("components", repository.activeComponentCount(user.tenantId(), scope));
        // 资产总数与类型分布共用一次分组统计（byType 已排除零计数，类型求和即资产总数）
        List<Map<String, Object>> byType = assetsByType(scope, user);
        long assets = byType.stream().mapToLong(row -> ((Number) row.get("total")).longValue()).sum();
        result.put("assets", assets);
        result.put("byType", byType);
        return result;
    }

    /**
     * 当前调用者可访问的项目数（T32-r1）：直接取 platform/system 的项目成员口径，
     * 本模块不再复制 {@code pm_project} / {@code pm_project_member} 计数 SQL。
     */
    private int accessibleProjectCount(AuthUser user) {
        return permissions.accessibleProjectIds(user).size();
    }

    /** 跨内容表按资产类型分组的活动计数（仅保留非零类型）；T32 按项目统计，资产总数由其一次求和得出。 */
    private List<Map<String, Object>> assetsByType(long projectId, AuthUser user) {
        return repository.assetsByType(user.tenantId(), projectId);
    }

    /**
     * 组件看板（T32）：{@code projectId} 必填，不再返回跨项目组件全集。
     */
    public List<Map<String,Object>> component(AuthUser user, Long projectId) {
        long scope = permissions.requireProject(projectId, user);
        return repository.components(user.tenantId(), scope);
    }
}
