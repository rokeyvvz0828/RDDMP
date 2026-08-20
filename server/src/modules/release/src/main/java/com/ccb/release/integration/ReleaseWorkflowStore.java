package com.ccb.release.integration;

import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowStartResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class ReleaseWorkflowStore {
    private final JdbcTemplate jdbc;

    public ReleaseWorkflowStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int nextRoundNo(long tenantId, long applicationId) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(round_no), 0) + 1 FROM rel_application_round "
                        + "WHERE tenant_id = ? AND application_id = ?",
                Integer.class, tenantId, applicationId);
        return value == null ? 1 : value;
    }

    public long insertStartingRound(Application application, int roundNo, String workflowCode, String digest) {
        long id = nextId();
        jdbc.update("INSERT INTO rel_application_round (id, tenant_id, application_id, round_no, workflow_code, "
                        + "round_status, data_digest) VALUES (?, ?, ?, ?, ?, 'STARTING', ?)",
                id, application.tenantId(), application.id(), roundNo, workflowCode, digest);
        return id;
    }

    public boolean completeWorkflowStart(long roundId, long tenantId, WorkflowStartResult result) {
        return jdbc.update("UPDATE rel_application_round SET workflow_definition_id = ?, workflow_definition_version = ?, "
                        + "workflow_instance_id = ?, round_status = 'IN_REVIEW', submitted_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND tenant_id = ? AND round_status = 'STARTING' AND workflow_instance_id IS NULL",
                result.definitionId(), result.definitionVersion(), result.instanceId(), roundId, tenantId) == 1;
    }

    public boolean transitionApplicationToReview(Application application, long expectedVersion,
                                                 VersionType versionType, Characteristic characteristic,
                                                 String workflowCode, long operatorId) {
        return jdbc.update("UPDATE rel_release_application SET version_type = ?, characteristic = ?, workflow_code = ?, "
                        + "application_status = 'IN_REVIEW', updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND application_status = ? AND row_version = ? AND deleted = 0",
                versionType.name(), characteristic.name(), workflowCode, operatorId, application.id(), application.tenantId(),
                application.status().name(), expectedVersion) == 1;
    }

    public void insertAttachment(long tenantId, long applicationId, long attachmentId, String category,
                                 String fileName, long applicationRevision) {
        jdbc.update("INSERT IGNORE INTO rel_application_attachment (id, tenant_id, application_id, attachment_id, "
                        + "attachment_category, file_name_snapshot, application_revision, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                nextId(), tenantId, applicationId, attachmentId, category, fileName, applicationRevision);
    }

    public List<AttachmentSnapshot> findActiveAttachments(long tenantId, long applicationId) {
        return jdbc.query("SELECT attachment_id, attachment_category, file_name_snapshot, application_revision "
                        + "FROM rel_application_attachment WHERE tenant_id = ? AND application_id = ? AND active = 1 "
                        + "ORDER BY created_at, id",
                (rs, rowNum) -> new AttachmentSnapshot(rs.getLong("attachment_id"),
                        rs.getString("attachment_category"), rs.getString("file_name_snapshot"),
                        rs.getLong("application_revision")), tenantId, applicationId);
    }

    public boolean retireAttachment(long tenantId, long applicationId, long attachmentId) {
        return jdbc.update("UPDATE rel_application_attachment SET active = 0 WHERE tenant_id = ? AND application_id = ? "
                        + "AND attachment_id = ? AND active = 1",
                tenantId, applicationId, attachmentId) == 1;
    }

    public boolean bumpEditableApplicationVersion(Application application, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_application SET updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND application_status = ? AND row_version = ? AND deleted = 0",
                operatorId, application.id(), application.tenantId(), application.status().name(), expectedVersion) == 1;
    }

    public Optional<RoundSnapshot> findLatestRound(long tenantId, long applicationId) {
        return first(jdbc.query("SELECT id, tenant_id, application_id, round_no, workflow_code, workflow_definition_id, "
                        + "workflow_definition_version, workflow_instance_id, round_status, data_digest, submitted_at, completed_at "
                        + "FROM rel_application_round WHERE tenant_id = ? AND application_id = ? ORDER BY round_no DESC LIMIT 1",
                ReleaseWorkflowStore::mapRound, tenantId, applicationId));
    }

    public Optional<RoundSnapshot> findLatestRoundForUpdate(long tenantId, long applicationId) {
        return first(jdbc.query("SELECT id, tenant_id, application_id, round_no, workflow_code, workflow_definition_id, "
                        + "workflow_definition_version, workflow_instance_id, round_status, data_digest, submitted_at, completed_at "
                        + "FROM rel_application_round WHERE tenant_id = ? AND application_id = ? "
                        + "ORDER BY round_no DESC LIMIT 1 FOR UPDATE",
                ReleaseWorkflowStore::mapRound, tenantId, applicationId));
    }

    public Optional<RoundSnapshot> findRoundByInstanceForUpdate(long tenantId, long instanceId) {
        return first(jdbc.query("SELECT id, tenant_id, application_id, round_no, workflow_code, workflow_definition_id, "
                        + "workflow_definition_version, workflow_instance_id, round_status, data_digest, submitted_at, completed_at "
                        + "FROM rel_application_round WHERE tenant_id = ? AND workflow_instance_id = ? FOR UPDATE",
                ReleaseWorkflowStore::mapRound, tenantId, instanceId));
    }

    public boolean isLatestRound(long tenantId, long applicationId, int roundNo) {
        Integer latest = jdbc.queryForObject("SELECT MAX(round_no) FROM rel_application_round "
                + "WHERE tenant_id = ? AND application_id = ?", Integer.class, tenantId, applicationId);
        return latest != null && latest == roundNo;
    }

    public boolean markWithdrawalRequested(long tenantId, long roundId) {
        return jdbc.update("UPDATE rel_application_round SET round_status = 'WITHDRAW_REQUESTED' "
                        + "WHERE id = ? AND tenant_id = ? AND round_status = 'IN_REVIEW'",
                roundId, tenantId) == 1;
    }

    public boolean markCancelRequested(long tenantId, long roundId) {
        return jdbc.update("UPDATE rel_application_round SET round_status = 'CANCEL_REQUESTED' "
                        + "WHERE id = ? AND tenant_id = ? AND round_status = 'IN_REVIEW'",
                roundId, tenantId) == 1;
    }

    public boolean completeRound(long tenantId, long roundId, String expectedStatus, String targetStatus,
                                 LocalDateTime completedAt) {
        return jdbc.update("UPDATE rel_application_round SET round_status = ?, completed_at = ? "
                        + "WHERE id = ? AND tenant_id = ? AND round_status = ?",
                targetStatus, Timestamp.valueOf(completedAt), roundId, tenantId, expectedStatus) == 1;
    }

    public boolean markApproved(Application application, long assignedWindowId, LocalDateTime approvedAt, long operatorId) {
        return jdbc.update("UPDATE rel_release_application SET application_status = 'RELEASED', assigned_window_id = ?, "
                        + "approved_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND application_status = 'IN_REVIEW' "
                        + "AND row_version = ? AND deleted = 0",
                assignedWindowId, Timestamp.valueOf(approvedAt), operatorId, application.id(), application.tenantId(),
                application.rowVersion()) == 1;
    }

    public boolean markReturned(Application application, long operatorId) {
        return transitionApplication(application, Status.RETURNED, operatorId);
    }

    public boolean markWithdrawn(Application application, long operatorId) {
        return transitionApplication(application, Status.WITHDRAWN, operatorId);
    }

    public boolean markCancelled(Application application, long operatorId) {
        return transitionApplication(application, Status.CANCELLED, operatorId);
    }

    private boolean transitionApplication(Application application, Status target, long operatorId) {
        return jdbc.update("UPDATE rel_release_application SET application_status = ?, updated_by = ?, "
                        + "row_version = row_version + 1 WHERE id = ? AND tenant_id = ? "
                        + "AND application_status = 'IN_REVIEW' AND row_version = ? AND deleted = 0",
                target.name(), operatorId, application.id(), application.tenantId(), application.rowVersion()) == 1;
    }

    public Optional<Long> findReceivingWindow(long tenantId, String projectId, LocalDateTime at) {
        Long started = jdbc.queryForObject("SELECT COUNT(*) FROM rel_release_window WHERE tenant_id = ? AND project_id = ? "
                        + "AND deleted = 0 AND production_start <= ?",
                Long.class, tenantId, projectId, Timestamp.valueOf(at));
        if (started == null || started == 0) return Optional.empty();
        List<Long> current = jdbc.query("SELECT id FROM rel_release_window WHERE tenant_id = ? AND project_id = ? "
                        + "AND deleted = 0 AND production_start <= ? AND production_end >= ? "
                        + "ORDER BY production_start DESC, id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"), tenantId, projectId, Timestamp.valueOf(at), Timestamp.valueOf(at));
        if (!current.isEmpty()) return Optional.of(current.get(0));
        List<Long> future = jdbc.query("SELECT id FROM rel_release_window WHERE tenant_id = ? AND project_id = ? "
                        + "AND deleted = 0 AND production_start > ? ORDER BY production_start, id LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"), tenantId, projectId, Timestamp.valueOf(at));
        return future.isEmpty() ? Optional.empty() : Optional.of(future.get(0));
    }

    public boolean beginReceipt(WorkflowLifecycleEvent event, long applicationId, String consumerKey) {
        return jdbc.update("INSERT IGNORE INTO rel_workflow_event_receipt (id, tenant_id, workflow_event_id, "
                        + "workflow_instance_id, application_id, round_no, event_type, consumer_key, receipt_status, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?)",
                nextId(), event.tenantId(), event.eventId(), event.instanceId(), applicationId,
                event.context().businessRound(), event.eventType().name(), consumerKey,
                Timestamp.valueOf(event.occurredAt())) == 1;
    }

    public void completeReceipt(long tenantId, String eventId, String consumerKey, String status) {
        jdbc.update("UPDATE rel_workflow_event_receipt SET receipt_status = ? WHERE tenant_id = ? "
                        + "AND workflow_event_id = ? AND consumer_key = ? AND receipt_status = 'PROCESSING'",
                status, tenantId, eventId, consumerKey);
    }

    private static RoundSnapshot mapRound(ResultSet rs, int rowNum) throws SQLException {
        return new RoundSnapshot(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("application_id"),
                rs.getInt("round_no"), rs.getString("workflow_code"), nullableLong(rs, "workflow_definition_id"),
                nullableInteger(rs, "workflow_definition_version"), nullableLong(rs, "workflow_instance_id"),
                rs.getString("round_status"), rs.getString("data_digest"), dateTime(rs, "submitted_at"),
                dateTime(rs, "completed_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    public record RoundSnapshot(long id, long tenantId, long applicationId, int roundNo, String workflowCode,
                                Long workflowDefinitionId, Integer workflowDefinitionVersion, Long workflowInstanceId,
                                String roundStatus, String dataDigest, LocalDateTime submittedAt,
                                LocalDateTime completedAt) {
    }

    public record AttachmentSnapshot(long attachmentId, String category, String fileName, long applicationRevision) {
    }
}
