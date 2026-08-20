package com.ccb.release.window.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.model.WindowFieldChange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Repository
public class ReleaseWindowStore {
    private static final String COLUMNS = "id, tenant_id, window_code, window_name, project_id, project_code, project_name, "
            + "declaration_start, declaration_end, production_start, production_end, regular_enabled, description, "
            + "row_version, created_by, updated_by, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public ReleaseWindowStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResult<ReleaseWindow> findPage(long tenantId, String projectId, String keyword, PageQuery page) {
        StringBuilder where = new StringBuilder(" FROM rel_release_window WHERE tenant_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (projectId != null && !projectId.isBlank()) {
            where.append(" AND project_id = ?");
            args.add(projectId.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (window_code LIKE ? OR window_name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(page.size());
        queryArgs.add((page.page() - 1) * page.size());
        List<ReleaseWindow> records = jdbc.query("SELECT " + COLUMNS + where
                        + " ORDER BY production_start DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, queryArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, page.page(), page.size());
    }

    public Optional<ReleaseWindow> findById(long id, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS
                + " FROM rel_release_window WHERE id = ? AND tenant_id = ? AND deleted = 0", ROW_MAPPER, id, tenantId));
    }

    public Optional<ReleaseWindow> findByIdForUpdate(long id, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS
                + " FROM rel_release_window WHERE id = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", ROW_MAPPER, id, tenantId));
    }

    public OptionalLong findTenantId(long id) {
        List<Long> values = jdbc.query("SELECT tenant_id FROM rel_release_window WHERE id = ? AND deleted = 0",
                (rs, rowNum) -> rs.getLong("tenant_id"), id);
        return values.isEmpty() ? OptionalLong.empty() : OptionalLong.of(values.get(0));
    }

    public void lockProjectWindows(long tenantId, String projectId) {
        jdbc.queryForList("SELECT id FROM rel_release_window WHERE tenant_id = ? AND project_id = ? AND deleted = 0 "
                + "ORDER BY declaration_start FOR UPDATE", tenantId, projectId);
    }

    public boolean hasOverlap(long tenantId, String projectId, LocalDateTime start, LocalDateTime end, Long excludedId) {
        String sql = "SELECT COUNT(*) FROM rel_release_window WHERE tenant_id = ? AND project_id = ? AND deleted = 0 "
                + "AND declaration_start < ? AND production_end > ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId, Timestamp.valueOf(end), Timestamp.valueOf(start)));
        if (excludedId != null) {
            sql += " AND id <> ?";
            args.add(excludedId);
        }
        Long count = jdbc.queryForObject(sql, Long.class, args.toArray());
        return count != null && count > 0;
    }

    public int nextMonthlySequence(long tenantId, String monthPrefix) {
        List<String> codes = jdbc.query("SELECT window_code FROM rel_release_window WHERE tenant_id = ? "
                        + "AND window_code LIKE ? ORDER BY window_code DESC LIMIT 1 FOR UPDATE",
                (rs, rowNum) -> rs.getString("window_code"), tenantId, monthPrefix + "%");
        if (codes.isEmpty()) return 1;
        String code = codes.get(0);
        int separator = code.lastIndexOf('-');
        if (separator < 0 || separator == code.length() - 1) return 1;
        try { return Integer.parseInt(code.substring(separator + 1)) + 1; }
        catch (NumberFormatException ignored) { return 1; }
    }

    public void insert(ReleaseWindow window) {
        jdbc.update("INSERT INTO rel_release_window (id, tenant_id, window_code, window_name, project_id, project_code, "
                        + "project_name, declaration_start, declaration_end, production_start, production_end, regular_enabled, "
                        + "description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                window.id(), window.tenantId(), window.windowCode(), window.windowName(), window.projectId(), window.projectCode(),
                window.projectName(), Timestamp.valueOf(window.declarationStart()), Timestamp.valueOf(window.declarationEnd()),
                Timestamp.valueOf(window.productionStart()), Timestamp.valueOf(window.productionEnd()), window.regularEnabled(),
                window.description(), window.createdBy(), window.updatedBy());
    }

    public boolean update(ReleaseWindow window, long expectedVersion) {
        int changed = jdbc.update("UPDATE rel_release_window SET window_name = ?, declaration_start = ?, declaration_end = ?, "
                        + "production_start = ?, production_end = ?, regular_enabled = ?, description = ?, updated_by = ?, "
                        + "row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND deleted = 0 AND row_version = ?",
                window.windowName(), Timestamp.valueOf(window.declarationStart()), Timestamp.valueOf(window.declarationEnd()),
                Timestamp.valueOf(window.productionStart()), Timestamp.valueOf(window.productionEnd()), window.regularEnabled(),
                window.description(), window.updatedBy(), window.id(), window.tenantId(), expectedVersion);
        return changed == 1;
    }

    public void appendChanges(long tenantId, long windowId, List<WindowFieldChange> changes,
                              String reason, long operatorId, long idSeed) {
        long id = idSeed;
        for (WindowFieldChange change : changes) {
            jdbc.update("INSERT INTO rel_window_change_log (id, tenant_id, window_id, field_name, old_value, new_value, "
                            + "change_reason, operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id++, tenantId, windowId, change.fieldName(), change.oldValue(), change.newValue(), reason, operatorId);
        }
    }

    private Optional<ReleaseWindow> first(List<ReleaseWindow> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static final RowMapper<ReleaseWindow> ROW_MAPPER = (rs, rowNum) -> new ReleaseWindow(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("window_code"), rs.getString("window_name"),
            rs.getString("project_id"), rs.getString("project_code"), rs.getString("project_name"),
            dateTime(rs, "declaration_start"), dateTime(rs, "declaration_end"), dateTime(rs, "production_start"),
            dateTime(rs, "production_end"), rs.getBoolean("regular_enabled"), rs.getString("description"),
            rs.getLong("row_version"), rs.getLong("created_by"), rs.getLong("updated_by"),
            dateTime(rs, "created_at"), dateTime(rs, "updated_at"));
}
