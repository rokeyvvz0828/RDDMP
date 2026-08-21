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
            result.put("projects", jdbc.queryForObject("SELECT COUNT(*) FROM dm_project WHERE tenant_id = ? AND deleted = 0", Integer.class, user.tenantId()));
            result.put("components", jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE tenant_id = ? AND deleted = 0", Integer.class, user.tenantId()));
            result.put("assets", jdbc.queryForObject("SELECT COUNT(*) FROM dm_asset WHERE tenant_id = ? AND deleted = 0", Integer.class, user.tenantId()));
        }
        result.put("byType", jdbc.queryForList("SELECT asset_type AS type, COUNT(*) AS total FROM dm_asset WHERE tenant_id = ? AND deleted = 0 GROUP BY asset_type ORDER BY total DESC", user.tenantId()));
        return result;
    }

    private Number snapshotValue(AuthUser user, String metricCode, Long projectId) {
        Object date = jdbc.queryForObject("SELECT MAX(snapshot_date) FROM dm_dashboard_snapshot WHERE tenant_id = ?", Object.class, user.tenantId());
        if (date == null) return 0;
        if (projectId == null) return jdbc.queryForObject("SELECT COALESCE(SUM(metric_value), 0) FROM dm_dashboard_snapshot WHERE tenant_id = ? AND snapshot_date = ? AND metric_code = ? AND project_id IS NULL", Number.class, user.tenantId(), date, metricCode);
        return jdbc.queryForObject("SELECT COALESCE(metric_value, 0) FROM dm_dashboard_snapshot WHERE tenant_id = ? AND snapshot_date = ? AND metric_code = ? AND project_id = ?", Number.class, user.tenantId(), date, metricCode, projectId);
    }
    public List<Map<String,Object>> component(AuthUser user, Long projectId) {
        if (projectId == null) return jdbc.queryForList("SELECT c.id, c.component_code, c.component_name, COUNT(a.id) AS asset_count FROM dm_component c LEFT JOIN dm_asset a ON a.component_id = c.id AND a.tenant_id = c.tenant_id AND a.deleted = 0 WHERE c.tenant_id = ? AND c.deleted = 0 GROUP BY c.id, c.component_code, c.component_name ORDER BY c.component_name", user.tenantId());
        return jdbc.queryForList("SELECT c.id, c.component_code, c.component_name, COUNT(a.id) AS asset_count FROM dm_component c LEFT JOIN dm_asset a ON a.component_id = c.id AND a.tenant_id = c.tenant_id AND a.deleted = 0 WHERE c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0 GROUP BY c.id, c.component_code, c.component_name ORDER BY c.component_name", user.tenantId(), projectId);
    }
}
