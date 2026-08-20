package com.ccb.release.application.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryItemType;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
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
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class ReleaseApplicationStore {
    private static final String COLUMNS = "id, tenant_id, application_code, project_id, project_code, project_name, emergency, "
            + "window_id, assigned_window_id, subsystem_id, subsystem_code, subsystem_name, version_type, characteristic, "
            + "workflow_code, application_status, requester_id, requester_name, requester_department, emergency_description, "
            + "urgent_reason, description, approved_at, row_version, created_by, updated_by, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public ReleaseApplicationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResult<Application> findPage(long tenantId, String projectId, String keyword, String status,
                                            boolean mineOnly, long requesterId, PageQuery page) {
        StringBuilder where = new StringBuilder(" FROM rel_release_application WHERE tenant_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (projectId != null && !projectId.isBlank()) { where.append(" AND project_id = ?"); args.add(projectId.trim()); }
        if (status != null && !status.isBlank()) { where.append(" AND application_status = ?"); args.add(status.trim()); }
        if (mineOnly) { where.append(" AND requester_id = ?"); args.add(requesterId); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (application_code LIKE ? OR subsystem_code LIKE ? OR subsystem_name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value); args.add(value);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(page.size()); queryArgs.add((page.page() - 1) * page.size());
        List<Application> records = jdbc.query("SELECT " + COLUMNS + where
                + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", BASE_MAPPER, queryArgs.toArray())
                .stream().map(this::withChildren).toList();
        return new PageResult<>(records, total == null ? 0 : total, page.page(), page.size());
    }

    public Optional<Application> findByCode(String code, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_release_application "
                + "WHERE application_code = ? AND tenant_id = ? AND deleted = 0", BASE_MAPPER, code, tenantId));
    }

    public Optional<Application> findByCodeForUpdate(String code, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_release_application "
                + "WHERE application_code = ? AND tenant_id = ? AND deleted = 0 FOR UPDATE", BASE_MAPPER, code, tenantId));
    }

    public Optional<Application> findById(long id, long tenantId) {
        return first(jdbc.query("SELECT " + COLUMNS + " FROM rel_release_application "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", BASE_MAPPER, id, tenantId));
    }

    public OptionalLong findTenantId(String code) {
        List<Long> values = jdbc.query("SELECT tenant_id FROM rel_release_application WHERE application_code = ? AND deleted = 0",
                (rs, rowNum) -> rs.getLong("tenant_id"), code);
        return values.isEmpty() ? OptionalLong.empty() : OptionalLong.of(values.get(0));
    }

    public int nextMonthlySequence(long tenantId, String prefix) {
        List<String> codes = jdbc.query("SELECT application_code FROM rel_release_application WHERE tenant_id = ? "
                        + "AND application_code LIKE ? ORDER BY application_code DESC LIMIT 1 FOR UPDATE",
                (rs, rowNum) -> rs.getString("application_code"), tenantId, prefix + "%");
        if (codes.isEmpty()) return 1;
        String code = codes.get(0);
        try { return Integer.parseInt(code.substring(code.lastIndexOf('-') + 1)) + 1; }
        catch (RuntimeException ignored) { return 1; }
    }

    public void insert(Application value) {
        jdbc.update("INSERT INTO rel_release_application (id, tenant_id, application_code, project_id, project_code, project_name, "
                        + "emergency, window_id, assigned_window_id, subsystem_id, subsystem_code, subsystem_name, version_type, "
                        + "characteristic, workflow_code, application_status, requester_id, requester_name, requester_department, "
                        + "emergency_description, urgent_reason, description, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                value.id(), value.tenantId(), value.applicationCode(), value.projectId(), value.projectCode(), value.projectName(),
                value.emergency(), value.windowId(), value.assignedWindowId(), value.subsystemId(), value.subsystemCode(),
                value.subsystemName(), value.versionType().name(), value.characteristic().name(), value.workflowCode(),
                value.status().name(), value.requesterId(), value.requesterName(), value.requesterDepartment(),
                value.emergencyDescription(), value.urgentReason(), value.description(), value.createdBy(), value.updatedBy());
        insertChildren(value);
    }

    public boolean update(Application value, long expectedVersion) {
        int changed = jdbc.update("UPDATE rel_release_application SET emergency = ?, window_id = ?, subsystem_id = ?, "
                        + "subsystem_code = ?, subsystem_name = ?, version_type = ?, characteristic = ?, workflow_code = ?, "
                        + "emergency_description = ?, urgent_reason = ?, description = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND deleted = 0 AND row_version = ?",
                value.emergency(), value.windowId(), value.subsystemId(), value.subsystemCode(), value.subsystemName(),
                value.versionType().name(), value.characteristic().name(), value.workflowCode(), value.emergencyDescription(),
                value.urgentReason(), value.description(), value.updatedBy(), value.id(), value.tenantId(), expectedVersion);
        if (changed != 1) return false;
        jdbc.update("UPDATE rel_application_delivery SET active = 0 WHERE tenant_id = ? AND application_id = ? AND active = 1",
                value.tenantId(), value.id());
        jdbc.update("UPDATE rel_application_requirement SET active = 0 WHERE tenant_id = ? AND application_id = ? AND active = 1",
                value.tenantId(), value.id());
        insertChildren(value);
        return true;
    }

    public boolean transition(long id, long tenantId, Status from, Status to, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_application SET application_status = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND application_status = ? AND row_version = ? AND deleted = 0",
                to.name(), operatorId, id, tenantId, from.name(), expectedVersion) == 1;
    }

    public void appendEvent(long id, long tenantId, long applicationId, String type, Status from, Status to,
                            String reason, String payloadJson, long operatorId, String operatorName) {
        jdbc.update("INSERT INTO rel_application_event (id, tenant_id, application_id, event_type, from_status, to_status, "
                        + "event_reason, payload_json, operator_id, operator_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, applicationId, type, from == null ? null : from.name(), to == null ? null : to.name(),
                reason, payloadJson, operatorId, operatorName);
    }

    public List<Long> findConflictIds(long tenantId, long windowId, List<String> itemKeys, Long excludedId) {
        if (itemKeys.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(itemKeys.size(), "?"));
        String sql = "SELECT DISTINCT a.id FROM rel_release_application a JOIN rel_application_delivery d "
                + "ON d.tenant_id = a.tenant_id AND d.application_id = a.id WHERE a.tenant_id = ? AND a.window_id = ? "
                + "AND a.deleted = 0 AND a.application_status <> 'CANCELLED' AND d.active = 1 "
                + "AND d.item_key IN (" + placeholders + ")";
        List<Object> args = new ArrayList<>(); args.add(tenantId); args.add(windowId); args.addAll(itemKeys);
        if (excludedId != null) { sql += " AND a.id <> ?"; args.add(excludedId); }
        sql += " ORDER BY a.id";
        return jdbc.query(sql, (rs, rowNum) -> rs.getLong("id"), args.toArray());
    }

    public List<Long> findRelatedApplicationIds(long tenantId, long applicationId) {
        String sql = "SELECT DISTINCT r.related_application_id FROM rel_application_relation r "
                + "JOIN rel_application_delivery current_item ON current_item.tenant_id = r.tenant_id "
                + "AND current_item.application_id = r.application_id AND current_item.active = 1 "
                + "AND current_item.item_key = r.item_key "
                + "JOIN rel_release_application related ON related.tenant_id = r.tenant_id "
                + "AND related.id = r.related_application_id AND related.deleted = 0 "
                + "WHERE r.tenant_id = ? AND r.application_id = ? AND r.relation_type = 'ADDITIONAL' "
                + "AND r.related_application_id <> r.application_id ORDER BY r.related_application_id";
        return jdbc.query(sql, (rs, rowNum) -> rs.getLong("related_application_id"), tenantId, applicationId);
    }

    public void insertRelation(long id, long tenantId, long applicationId, long relatedId, String deliveryCode,
                               String type, String previousVersion, String currentVersion, String reason, long operatorId) {
        insertRelation(id, tenantId, applicationId, relatedId, deliveryCode, DeliveryItemType.DELIVERY_UNIT,
                "UNIT:" + deliveryCode, null, type, previousVersion, currentVersion, reason, operatorId);
    }

    public void insertRelation(long id, long tenantId, long applicationId, long relatedId, String deliveryCode,
                               DeliveryItemType itemType, String itemKey, String filePath, String type,
                               String previousVersion, String currentVersion, String reason, long operatorId) {
        jdbc.update("INSERT IGNORE INTO rel_application_relation (id, tenant_id, application_id, related_application_id, "
                        + "delivery_unit_code, item_type, item_key, file_path, relation_type, previous_version, current_version, "
                        + "relation_reason, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, applicationId, relatedId, deliveryCode, itemType.name(), itemKey, filePath, type,
                previousVersion, currentVersion, reason, operatorId);
    }

    private void insertChildren(Application value) {
        for (DeliverySnapshot delivery : value.deliveries()) {
            jdbc.update("INSERT INTO rel_application_delivery (id, tenant_id, application_id, delivery_unit_id, delivery_unit_code, "
                            + "delivery_unit_name, item_type, file_path, item_key, artifact_type, artifact_version, "
                            + "application_revision, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                    delivery.id(), value.tenantId(), value.id(), delivery.deliveryUnitId(), delivery.deliveryUnitCode(),
                    delivery.deliveryUnitName(), delivery.itemType().name(), delivery.filePath(), delivery.itemKey(),
                    delivery.artifactType().name(), delivery.artifactVersion(), value.rowVersion());
        }
        for (String requirement : value.requirementCodes()) {
            jdbc.update("INSERT INTO rel_application_requirement (id, tenant_id, application_id, requirement_code, "
                            + "application_revision, active) VALUES (?, ?, ?, ?, ?, 1)",
                    nextId(), value.tenantId(), value.id(), requirement, value.rowVersion());
        }
    }

    private Application withChildren(Application base) {
        List<DeliverySnapshot> deliveries = jdbc.query("SELECT id, delivery_unit_id, delivery_unit_code, delivery_unit_name, "
                        + "artifact_type, artifact_version, item_type, file_path, item_key FROM rel_application_delivery "
                        + "WHERE tenant_id = ? AND application_id = ? "
                        + "AND active = 1 ORDER BY id",
                (rs, rowNum) -> new DeliverySnapshot(rs.getLong("id"), rs.getString("delivery_unit_id"),
                        rs.getString("delivery_unit_code"), rs.getString("delivery_unit_name"),
                        ArtifactType.valueOf(rs.getString("artifact_type")), rs.getString("artifact_version"),
                        DeliveryItemType.valueOf(rs.getString("item_type")), rs.getString("file_path"),
                        rs.getString("item_key")),
                base.tenantId(), base.id());
        List<String> requirements = jdbc.query("SELECT requirement_code FROM rel_application_requirement "
                        + "WHERE tenant_id = ? AND application_id = ? AND active = 1 ORDER BY id",
                (rs, rowNum) -> rs.getString("requirement_code"), base.tenantId(), base.id());
        return new Application(base.id(), base.tenantId(), base.applicationCode(), base.projectId(), base.projectCode(),
                base.projectName(), base.emergency(), base.windowId(), base.assignedWindowId(), base.subsystemId(),
                base.subsystemCode(), base.subsystemName(), base.versionType(), base.characteristic(), base.workflowCode(),
                base.status(), base.requesterId(), base.requesterName(), base.requesterDepartment(), base.emergencyDescription(),
                base.urgentReason(), base.description(), base.approvedAt(), base.rowVersion(), base.createdBy(), base.updatedBy(),
                base.createdAt(), base.updatedAt(), deliveries, requirements);
    }

    private Optional<Application> first(List<Application> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(withChildren(rows.get(0)));
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toLocalDateTime();
    }

    private static final RowMapper<Application> BASE_MAPPER = (rs, rowNum) -> new Application(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("application_code"), rs.getString("project_id"),
            rs.getString("project_code"), rs.getString("project_name"), rs.getBoolean("emergency"), nullableLong(rs, "window_id"),
            nullableLong(rs, "assigned_window_id"), rs.getString("subsystem_id"), rs.getString("subsystem_code"),
            rs.getString("subsystem_name"), VersionType.valueOf(rs.getString("version_type")),
            Characteristic.valueOf(rs.getString("characteristic")), rs.getString("workflow_code"),
            Status.valueOf(rs.getString("application_status")), rs.getLong("requester_id"), rs.getString("requester_name"),
            rs.getString("requester_department"), rs.getString("emergency_description"), rs.getString("urgent_reason"),
            rs.getString("description"), dateTime(rs, "approved_at"), rs.getLong("row_version"), rs.getLong("created_by"),
            rs.getLong("updated_by"), dateTime(rs, "created_at"), dateTime(rs, "updated_at"), List.of(), List.of());

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
