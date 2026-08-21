package com.ccb.release.reporting.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.reporting.model.ReleaseAnalyticsModels.Summary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReleaseAnalyticsStore {
    private final JdbcTemplate jdbc;
    public ReleaseAnalyticsStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Summary summary(long tenantId, String projectId, Long windowId) {
        Filter filter = filter(tenantId, projectId, windowId);
        long applications = count("SELECT COUNT(DISTINCT a.id)" + filter.fromWhere(), filter.args());
        long subsystems = count("SELECT COUNT(DISTINCT a.subsystem_code)" + filter.fromWhere(), filter.args());
        long units = count("SELECT COUNT(DISTINCT CONCAT(a.subsystem_code, ':', d.item_key)) "
                + "FROM rel_release_application a JOIN rel_application_delivery d ON d.tenant_id = a.tenant_id "
                + "AND d.application_id = a.id AND d.active = 1 AND d.item_type = 'DELIVERY_UNIT'"
                + filter.whereOnly(), filter.args());
        long fileMedia = count("SELECT COUNT(DISTINCT CONCAT(a.subsystem_code, ':', d.item_key)) "
                + "FROM rel_release_application a JOIN rel_application_delivery d ON d.tenant_id = a.tenant_id "
                + "AND d.application_id = a.id AND d.active = 1 AND d.item_type = 'FILE_MEDIA'"
                + filter.whereOnly(), filter.args());
        long requirements = count("SELECT COUNT(DISTINCT r.requirement_code) FROM rel_release_application a"
                + " JOIN rel_application_requirement r ON r.tenant_id = a.tenant_id AND r.application_id = a.id AND r.active = 1"
                + filter.whereOnly(),
                filter.args());
        long windows = count("SELECT COUNT(DISTINCT a.window_id)" + filter.fromWhere() + " AND a.window_id IS NOT NULL", filter.args());
        Map<String, Long> versionTypes = grouped("SELECT a.version_type AS metric, COUNT(*) AS amount"
                + filter.fromWhere() + " GROUP BY a.version_type", filter.args());
        Map<String, Long> results = grouped("SELECT p.production_result AS metric, COUNT(*) AS amount FROM rel_production_entry p "
                + "JOIN rel_release_application a ON a.tenant_id = p.tenant_id AND a.id = p.application_id"
                + filter.whereOnly() + " GROUP BY p.production_result", filter.args());
        return new Summary(windows, applications, subsystems, units, fileMedia, requirements, versionTypes, results);
    }

    public PageResult<Map<String, Object>> drilldown(long tenantId, String projectId, Long windowId,
                                                     String dimension, String value, PageQuery page) {
        Filter filter = filter(tenantId, projectId, windowId);
        String extra = "";
        List<Object> args = new ArrayList<>(filter.args());
        if (dimension != null && !dimension.isBlank()) {
            String column = switch (dimension) {
                case "versionType" -> "a.version_type";
                case "status" -> "a.application_status";
                case "productionResult" -> "p.production_result";
                default -> throw new IllegalArgumentException("unsupported dimension");
            };
            extra = " AND " + column + " = ?";
            args.add(value);
        }
        String from = " FROM rel_release_application a LEFT JOIN rel_production_entry p ON p.tenant_id = a.tenant_id "
                + "AND p.application_id = a.id AND p.active_candidate = 1" + filter.whereOnly() + extra;
        long total = count("SELECT COUNT(DISTINCT a.id)" + from, args);
        List<Object> queryArgs = new ArrayList<>(args); queryArgs.add(page.size()); queryArgs.add((page.page() - 1) * page.size());
        String fields = "a.application_code, a.project_name, a.subsystem_code, a.subsystem_name, a.version_type, "
                + "a.characteristic, a.application_status, a.approved_at, p.production_result";
        String distinctRows = "SELECT DISTINCT " + fields + ", a.updated_at AS sort_updated_at, a.id AS sort_id" + from;
        List<Map<String, Object>> records = jdbc.queryForList("SELECT application_code, project_name, subsystem_code, "
                + "subsystem_name, version_type, characteristic, application_status, approved_at, production_result FROM ("
                + distinctRows + ") drilldown_rows ORDER BY sort_updated_at DESC, sort_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return new PageResult<>(records, total, page.page(), page.size());
    }

    private Filter filter(long tenantId, String projectId, Long windowId) {
        StringBuilder where = new StringBuilder(" WHERE a.tenant_id = ? AND a.deleted = 0");
        List<Object> args = new ArrayList<>(); args.add(tenantId);
        if (projectId != null && !projectId.isBlank()) { where.append(" AND a.project_id = ?"); args.add(projectId.trim()); }
        if (windowId != null) { where.append(" AND COALESCE(a.assigned_window_id, a.window_id) = ?"); args.add(windowId); }
        return new Filter(where.toString(), args);
    }

    private long count(String sql, List<Object> args) {
        Long value = jdbc.queryForObject(sql, Long.class, args.toArray()); return value == null ? 0 : value;
    }
    private Map<String, Long> grouped(String sql, List<Object> args) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.queryForList(sql, args.toArray()).forEach(row -> result.put(String.valueOf(row.get("metric")),
                ((Number) row.get("amount")).longValue()));
        return result;
    }
    private record Filter(String whereOnly, List<Object> args) {
        String fromWhere() { return " FROM rel_release_application a" + whereOnly; }
    }
}
