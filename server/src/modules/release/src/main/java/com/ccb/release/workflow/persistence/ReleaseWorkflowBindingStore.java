package com.ccb.release.workflow.persistence;

import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Binding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingHistoryView;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Scene;
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
public class ReleaseWorkflowBindingStore {
    private static final String COLUMNS = "id, tenant_id, project_ref, project_name, scene_code, workflow_definition_id, "
            + "workflow_code, workflow_name, workflow_version, row_version, created_by, updated_by, created_at, updated_at";
    private final JdbcTemplate jdbc;

    public ReleaseWorkflowBindingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Binding> findProject(long tenantId, String projectRef) {
        return jdbc.query("SELECT " + COLUMNS + " FROM rel_workflow_binding WHERE tenant_id = ? AND project_ref = ? ORDER BY scene_code",
                BINDING_MAPPER, tenantId, projectRef);
    }
    public Optional<Binding> find(long tenantId, String projectRef, Scene scene, boolean forUpdate) {
        return jdbc.query("SELECT " + COLUMNS + " FROM rel_workflow_binding WHERE tenant_id = ? AND project_ref = ? AND scene_code = ?"
                        + (forUpdate ? " FOR UPDATE" : ""), BINDING_MAPPER, tenantId, projectRef, scene.name()).stream().findFirst();
    }
    public void insert(Binding binding) {
        jdbc.update("INSERT INTO rel_workflow_binding (id, tenant_id, project_ref, project_name, scene_code, workflow_definition_id, workflow_code, workflow_name, workflow_version, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                binding.id(), binding.tenantId(), binding.projectRef(), binding.projectName(), binding.scene().name(),
                binding.workflowDefinitionId(), binding.workflowCode(), binding.workflowName(), binding.workflowVersion(),
                binding.rowVersion(), binding.createdBy(), binding.updatedBy());
    }
    public boolean update(Binding binding, long expectedVersion) {
        return jdbc.update("UPDATE rel_workflow_binding SET project_name = ?, workflow_definition_id = ?, workflow_code = ?, workflow_name = ?, workflow_version = ?, row_version = row_version + 1, updated_by = ? WHERE id = ? AND tenant_id = ? AND row_version = ?",
                binding.projectName(), binding.workflowDefinitionId(), binding.workflowCode(), binding.workflowName(),
                binding.workflowVersion(), binding.updatedBy(), binding.id(), binding.tenantId(), expectedVersion) == 1;
    }
    public void appendHistory(long historyId, Binding before, Binding after, String reason, long operatorId, String operatorName) {
        jdbc.update("INSERT INTO rel_workflow_binding_history (id, tenant_id, binding_id, project_ref, project_name, scene_code, before_definition_id, before_workflow_code, before_workflow_name, before_workflow_version, after_definition_id, after_workflow_code, after_workflow_name, after_workflow_version, change_reason, operator_id, operator_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                historyId, after.tenantId(), after.id(), after.projectRef(), after.projectName(), after.scene().name(),
                before == null ? null : before.workflowDefinitionId(), before == null ? null : before.workflowCode(),
                before == null ? null : before.workflowName(), before == null ? null : before.workflowVersion(),
                after.workflowDefinitionId(), after.workflowCode(), after.workflowName(), after.workflowVersion(), reason,
                operatorId, operatorName);
    }
    public List<BindingHistoryView> history(long tenantId, String projectRef, Scene scene) {
        return jdbc.query("SELECT id, scene_code, before_definition_id, before_workflow_code, before_workflow_name, before_workflow_version, after_definition_id, after_workflow_code, after_workflow_name, after_workflow_version, change_reason, operator_id, operator_name, occurred_at FROM rel_workflow_binding_history WHERE tenant_id = ? AND project_ref = ? AND scene_code = ? ORDER BY occurred_at DESC, id DESC",
                (rs, rowNum) -> new BindingHistoryView(rs.getLong("id"), rs.getString("scene_code"),
                        nullableLong(rs, "before_definition_id"), rs.getString("before_workflow_code"), rs.getString("before_workflow_name"), nullableInt(rs, "before_workflow_version"),
                        nullableLong(rs, "after_definition_id"), rs.getString("after_workflow_code"), rs.getString("after_workflow_name"), nullableInt(rs, "after_workflow_version"),
                        rs.getString("change_reason"), rs.getLong("operator_id"), rs.getString("operator_name"), time(rs.getTimestamp("occurred_at"))),
                tenantId, projectRef, scene.name());
    }
    public List<Binding> references(long tenantId, long definitionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM rel_workflow_binding WHERE tenant_id = ? AND workflow_definition_id = ? ORDER BY project_ref, scene_code",
                BINDING_MAPPER, tenantId, definitionId);
    }

    private static final RowMapper<Binding> BINDING_MAPPER = (rs, rowNum) -> new Binding(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("project_ref"), rs.getString("project_name"), Scene.valueOf(rs.getString("scene_code")),
            nullableLong(rs, "workflow_definition_id"), rs.getString("workflow_code"), rs.getString("workflow_name"), nullableInt(rs, "workflow_version"),
            rs.getLong("row_version"), rs.getLong("created_by"), rs.getLong("updated_by"), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at")));
    private static Long nullableLong(ResultSet rs, String column) throws SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value; }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
