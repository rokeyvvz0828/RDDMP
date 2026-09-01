package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> overall(AuthUser user) {
        Map<String,Object> result = new LinkedHashMap<>();
        Object snapshotDate = jdbc.queryForObject("SELECT MAX(snapshot_date) FROM dm_dashboard_snapshot WHERE tenant_id = ?", Object.class, user.tenantId());
        if (snapshotDate != null) {
            result.put("projects", snapshotValue(user, "PROJECT_TOTAL", null));
            result.put("components", snapshotValue(user, "COMPONENT_TOTAL", null));
            result.put("assets", jdbc.queryForObject("SELECT COALESCE(SUM(metric_value), 0) FROM dm_dashboard_snapshot WHERE tenant_id = ? AND snapshot_date = ? AND metric_code = 'ASSET_TOTAL'", Number.class, user.tenantId(), snapshotDate));
            result.put("snapshotDate", snapshotDate);
        } else {
            result.put("projects", jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE tenant_id = ? AND deleted = 0", Integer.class, user.tenantId()));
            result.put("components", jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND deleted = 0", Integer.class, user.tenantId()));
            result.put("assets", totalActiveAssets(user));
        }
        result.put("byType", assetsByType(user));
        return result;
    }

    /** 跨全部内容与结构化表统计活动资产总数。 */
    private Number totalActiveAssets(AuthUser user) {
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < ContentAssetTables.ALL_TABLES.size(); i++) args.add(user.tenantId());
        return jdbc.queryForObject("SELECT COALESCE(SUM(cnt), 0) FROM (" + ContentAssetTables.activeCountUnionSql("tenant_id = ? AND deleted = 0", ContentAssetTables.ALL_TABLES) + ") x", Number.class, args.toArray());
    }

    /** 跨内容表按资产类型分组的活动计数（仅保留非零类型）。 */
    private List<Map<String, Object>> assetsByType(AuthUser user) {
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < ContentAssetTables.ALL_TABLES.size(); i++) args.add(user.tenantId());
        return jdbc.queryForList("SELECT type, total FROM (" + ContentAssetTables.typeCountUnionSql("tenant_id = ? AND deleted = 0", ContentAssetTables.ALL_TABLES) + ") x WHERE total > 0 ORDER BY total DESC", args.toArray());
    }

    private Number snapshotValue(AuthUser user, String metricCode, Long projectId) {
        Object date = jdbc.queryForObject("SELECT MAX(snapshot_date) FROM dm_dashboard_snapshot WHERE tenant_id = ?", Object.class, user.tenantId());
        if (date == null) return 0;
        if (projectId == null) return jdbc.queryForObject("SELECT COALESCE(SUM(metric_value), 0) FROM dm_dashboard_snapshot WHERE tenant_id = ? AND snapshot_date = ? AND metric_code = ? AND project_id IS NULL", Number.class, user.tenantId(), date, metricCode);
        return jdbc.queryForObject("SELECT COALESCE(metric_value, 0) FROM dm_dashboard_snapshot WHERE tenant_id = ? AND snapshot_date = ? AND metric_code = ? AND project_id = ?", Number.class, user.tenantId(), date, metricCode, projectId);
    }
    public List<Map<String,Object>> component(AuthUser user, Long projectId) {
        // 组件身份由系统编号承担；系统编号/名称来自物理子系统（LEFT JOIN，缺失时回退编号本身）。
        // 资产数跨全部内容与结构化表按 component_id 相关子查询求和。
        String assetCount = "(" + ContentAssetTables.correlatedCountSumSql("c", "component_id",
                "ca.tenant_id = c.tenant_id AND ca.deleted = 0", ContentAssetTables.ALL_TABLES) + ")";
        String base = "SELECT c.id, c.physical_subsystem_code AS system_code, "
                + "COALESCE(s.short_name, s.name, c.physical_subsystem_code) AS system_name, "
                + assetCount + " AS asset_count "
                + "FROM dm_component c "
                + "LEFT JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 "
                + "WHERE c.tenant_id = ? AND c.deleted = 0";
        String group = " GROUP BY c.id, c.physical_subsystem_code, s.short_name, s.name ORDER BY system_name";
        if (projectId == null) return jdbc.queryForList(base + group, user.tenantId());
        return jdbc.queryForList(base + " AND c.project_id = ?" + group, user.tenantId(), projectId);
    }
}
