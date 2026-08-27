package com.ccb.release.production.persistence;

import com.ccb.release.production.model.ProductionModels.Entry;
import com.ccb.release.production.model.ProductionModels.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReleaseProductionStore {
    private static final String COLUMNS = "id, tenant_id, window_id, application_id, application_code, approved_at, "
            + "subsystem_id, subsystem_code, subsystem_name, delivery_unit_id, delivery_unit_code, delivery_unit_name, "
            + "artifact_type, artifact_version, item_type, file_path, item_key, version_type, characteristic, production_result, production_at, "
            + "result_reason, active_candidate, row_version, created_at, updated_at";
    private final JdbcTemplate jdbc;

    public ReleaseProductionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Entry> findBaseline(long tenantId, long windowId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM rel_production_entry WHERE tenant_id = ? AND window_id = ? "
                + "AND active_candidate = 1 ORDER BY subsystem_name, delivery_unit_name", MAPPER, tenantId, windowId);
    }

    public Optional<Entry> findActiveForUpdate(long tenantId, long windowId, String subsystemCode, String itemKey) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_production_entry WHERE tenant_id = ? AND window_id = ? "
                        + "AND subsystem_code = ? AND item_key = ? AND active_candidate = 1 FOR UPDATE",
                MAPPER, tenantId, windowId, subsystemCode, itemKey));
    }

    public Optional<Entry> findBySource(long tenantId, long windowId, long applicationId, String itemKey) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_production_entry WHERE tenant_id = ? AND window_id = ? "
                + "AND application_id = ? AND item_key = ?", MAPPER, tenantId, windowId, applicationId, itemKey));
    }

    public Optional<Entry> findByIdForUpdate(long id, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_production_entry WHERE id = ? AND tenant_id = ? FOR UPDATE",
                MAPPER, id, tenantId));
    }

    public Optional<Entry> findById(long id, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_production_entry WHERE id = ? AND tenant_id = ?",
                MAPPER, id, tenantId));
    }

    public void deactivate(long id, long tenantId, long operatorId) {
        jdbc.update("UPDATE rel_production_entry SET active_candidate = 0, updated_by = ? WHERE id = ? AND tenant_id = ?",
                operatorId, id, tenantId);
    }

    public void activate(long id, long tenantId, long operatorId) {
        jdbc.update("UPDATE rel_production_entry SET active_candidate = 1, updated_by = ? WHERE id = ? AND tenant_id = ?",
                operatorId, id, tenantId);
    }

    public void insert(Entry value, long operatorId) {
        jdbc.update("INSERT INTO rel_production_entry (id, tenant_id, window_id, application_id, application_code, approved_at, "
                        + "subsystem_id, subsystem_code, subsystem_name, delivery_unit_id, delivery_unit_code, delivery_unit_name, "
                        + "artifact_type, artifact_version, item_type, file_path, item_key, version_type, characteristic, production_result, production_at, "
                        + "result_reason, active_candidate, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                value.id(), value.tenantId(), value.windowId(), value.applicationId(), value.applicationCode(),
                Timestamp.valueOf(value.approvedAt()), value.subsystemId(), value.subsystemCode(), value.subsystemName(),
                value.deliveryUnitId(), value.deliveryUnitCode(), value.deliveryUnitName(), value.artifactType(),
                value.artifactVersion(), value.itemType(), value.filePath(), value.itemKey(), value.versionType(),
                value.characteristic(), value.productionResult().name(),
                value.productionAt() == null ? null : Timestamp.valueOf(value.productionAt()), value.resultReason(),
                value.activeCandidate(), operatorId, operatorId);
    }

    public boolean updateResult(long id, long tenantId, long version, Result result, LocalDateTime productionAt,
                                String resultReason, long operatorId) {
        return jdbc.update("UPDATE rel_production_entry SET production_result = ?, production_at = ?, result_reason = ?, "
                        + "updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND row_version = ?",
                result.name(), productionAt == null ? null : Timestamp.valueOf(productionAt), resultReason, operatorId,
                id, tenantId, version) == 1;
    }

    public void appendResultLog(long id, long tenantId, Entry before, Result result, LocalDateTime productionAt,
                                String reason, long operatorId, String operatorName) {
        jdbc.update("INSERT INTO rel_production_result_log (id, tenant_id, production_entry_id, from_result, to_result, "
                        + "change_reason, production_at_before, production_at_after, operator_id, operator_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, tenantId, before.id(), before.productionResult().name(),
                result.name(), reason, before.productionAt() == null ? null : Timestamp.valueOf(before.productionAt()),
                productionAt == null ? null : Timestamp.valueOf(productionAt), operatorId, operatorName);
    }

    public List<Entry> findCurrentVersions(long tenantId, String projectId) {
        String projectClause = projectId == null || projectId.isBlank() ? "" : " AND a.project_id = ?";
        String sql = "SELECT " + prefixColumns("p") + ", w.window_name AS window_name FROM rel_production_entry p "
                + "JOIN rel_release_window w ON w.tenant_id = p.tenant_id AND w.id = p.window_id "
                + "JOIN rel_release_application a ON a.tenant_id = p.tenant_id AND a.id = p.application_id WHERE p.tenant_id = ? "
                + "AND p.production_result = 'SUCCEEDED'" + projectClause + " AND NOT EXISTS (SELECT 1 FROM rel_production_entry n "
                + "WHERE n.tenant_id = p.tenant_id AND n.subsystem_code = p.subsystem_code AND n.item_key = p.item_key "
                + "AND n.production_result = 'SUCCEEDED' AND (n.production_at > p.production_at OR "
                + "(n.production_at = p.production_at AND (n.updated_at > p.updated_at OR (n.updated_at = p.updated_at AND n.id > p.id))))) "
                + "ORDER BY p.subsystem_name, p.delivery_unit_name";
        return projectClause.isEmpty() ? jdbc.query(sql, WINDOW_NAME_MAPPER, tenantId)
                : jdbc.query(sql, WINDOW_NAME_MAPPER, tenantId, projectId.trim());
    }

    public List<Entry> findHistory(long tenantId, String subsystemCode, String deliveryCode) {
        return jdbc.query("SELECT " + prefixColumns("p") + ", w.window_name AS window_name FROM rel_production_entry p "
                + "JOIN rel_release_window w ON w.tenant_id = p.tenant_id AND w.id = p.window_id "
                + "WHERE p.tenant_id = ? AND p.subsystem_code = ? AND p.delivery_unit_code = ? "
                + "AND p.production_result = 'SUCCEEDED' "
                + "ORDER BY p.production_at DESC, p.updated_at DESC, p.id DESC", WINDOW_NAME_MAPPER,
                tenantId, subsystemCode, deliveryCode);
    }

    public List<Entry> findHistoryByItemKey(long tenantId, String subsystemCode, String itemKey) {
        return jdbc.query("SELECT " + prefixColumns("p") + ", w.window_name AS window_name FROM rel_production_entry p "
                + "JOIN rel_release_window w ON w.tenant_id = p.tenant_id AND w.id = p.window_id "
                + "WHERE p.tenant_id = ? AND p.subsystem_code = ? AND p.item_key = ? "
                + "AND p.production_result = 'SUCCEEDED' "
                + "ORDER BY p.production_at DESC, p.updated_at DESC, p.id DESC", WINDOW_NAME_MAPPER,
                tenantId, subsystemCode, itemKey);
    }

    private static String prefixColumns(String alias) {
        return java.util.Arrays.stream(COLUMNS.split(", ")).map(value -> alias + "." + value).collect(java.util.stream.Collectors.joining(", "));
    }
    private Optional<Entry> first(List<Entry> rows) { return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0)); }
    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toLocalDateTime();
    }
    private static final RowMapper<Entry> MAPPER = (rs, row) -> new Entry(rs.getLong("id"), rs.getLong("tenant_id"),
            rs.getLong("window_id"), rs.getLong("application_id"), rs.getString("application_code"), time(rs, "approved_at"),
            rs.getString("subsystem_id"), rs.getString("subsystem_code"), rs.getString("subsystem_name"),
            rs.getString("delivery_unit_id"), rs.getString("delivery_unit_code"), rs.getString("delivery_unit_name"),
            rs.getString("artifact_type"), rs.getString("artifact_version"), rs.getString("item_type"),
            rs.getString("file_path"), rs.getString("item_key"), rs.getString("version_type"),
            rs.getString("characteristic"), Result.valueOf(rs.getString("production_result")), time(rs, "production_at"),
            rs.getString("result_reason"), rs.getBoolean("active_candidate"), rs.getLong("row_version"),
            time(rs, "created_at"), time(rs, "updated_at"));
    private static final RowMapper<Entry> WINDOW_NAME_MAPPER = (rs, row) ->
            MAPPER.mapRow(rs, row).withWindowName(rs.getString("window_name"));
}
