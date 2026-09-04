package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;
    public DashboardService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
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
        result.put("components", jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND project_id = ? AND deleted = 0", Integer.class, user.tenantId(), scope));
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
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < ContentAssetTables.ALL_TABLES.size(); i++) { args.add(user.tenantId()); args.add(projectId); }
        return jdbc.queryForList("SELECT type, total FROM (" + ContentAssetTables.typeCountUnionSql("tenant_id = ? AND project_id = ? AND deleted = 0", ContentAssetTables.ALL_TABLES) + ") x WHERE total > 0 ORDER BY total DESC", args.toArray());
    }

    /**
     * 组件看板（T32）：{@code projectId} 必填，不再返回跨项目组件全集。
     */
    public List<Map<String,Object>> component(AuthUser user, Long projectId) {
        long scope = permissions.requireProject(projectId, user);
        // 组件身份由系统编号承担；系统编号/名称来自物理子系统（LEFT JOIN，缺失时回退编号本身）。
        // T34（9.2 P2「跨表统计线性增长」）：资产数由逐组件 × 逐表的相关子查询改为
        // 一次分组统计——9 张内容表 UNION ALL 后按 component_id 分组计数，再 LEFT JOIN 组件；
        // 仍保留 tenant_id + project_id + deleted=0 的项目过滤。
        StringBuilder union = new StringBuilder();
        for (String table : ContentAssetTables.ALL_TABLES) {
            if (union.length() > 0) union.append(" UNION ALL ");
            union.append("SELECT component_id, tenant_id FROM ").append(table)
                 .append(" WHERE tenant_id = ? AND project_id = ? AND deleted = 0");
        }
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < ContentAssetTables.ALL_TABLES.size(); i++) { args.add(user.tenantId()); args.add(scope); }
        String sql = "SELECT c.id, c.physical_subsystem_code AS system_code, "
                + "COALESCE(s.short_name, s.name, c.physical_subsystem_code) AS system_name, "
                + "COALESCE(agg.cnt, 0) AS asset_count "
                + "FROM dm_component c "
                + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                + "LEFT JOIN (SELECT component_id, tenant_id, COUNT(*) AS cnt FROM (" + union + ") u "
                + "   WHERE component_id IS NOT NULL GROUP BY component_id, tenant_id) agg "
                + "   ON agg.component_id = c.id AND agg.tenant_id = c.tenant_id "
                + "WHERE c.tenant_id = ? AND c.deleted = 0 AND c.project_id = ? "
                + "GROUP BY c.id, c.physical_subsystem_code, s.short_name, s.name ORDER BY system_name";
        args.add(user.tenantId());
        args.add(scope);
        return jdbc.queryForList(sql, args.toArray());
    }
}
