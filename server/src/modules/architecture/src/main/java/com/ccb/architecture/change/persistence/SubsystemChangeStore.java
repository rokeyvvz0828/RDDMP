package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemChangeModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** V82 发布主记录的生命周期读取和加锁边界。 */
@Repository
public class SubsystemChangeStore {
    private static final RowMapper<ChangeApplication> APPLICATION_MAPPER = (rs, rowNum) ->
            new ChangeApplication(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    TargetKind.fromDatabase(rs.getString("target_kind")),
                    ActionType.fromDatabase(rs.getString("action_type")),
                    nullableLong(rs, "target_id"),
                    rs.getLong("applicant_id"),
                    rs.getString("reason"),
                    ApplicationStatus.fromDatabase(rs.getString("status")),
                    rs.getInt("current_business_round"),
                    nullableLong(rs, "current_workflow_definition_id"),
                    nullableLong(rs, "current_workflow_version_id"),
                    nullableLong(rs, "current_workflow_instance_id"),
                    rs.getString("current_payload_digest"),
                    rs.getBoolean("cancellation_requested"),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<LogicalDraft> LOGICAL_DRAFT_MAPPER = (rs, rowNum) ->
            new LogicalDraft(
                    rs.getLong("application_id"),
                    rs.getLong("tenant_id"),
                    nullableLong(rs, "source_logical_subsystem_id"),
                    rs.getString("short_name"),
                    rs.getString("name"),
                    rs.getLong("business_org_id"),
                    rs.getString("deployment_platform_code"),
                    rs.getString("system_type_code"),
                    rs.getString("system_ownership_code"),
                    rs.getLong("contact_user_id"),
                    rs.getString("description"),
                    rs.getString("remark"),
                    rs.getInt("sort_no"),
                    nullableInteger(rs.getObject("reserved_number_sequence")),
                    nullableLong(rs, "source_row_version"),
                    rs.getInt("draft_revision"),
                    rs.getString("submitted_snapshot_json"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<PhysicalDraft> PHYSICAL_DRAFT_MAPPER = (rs, rowNum) ->
            new PhysicalDraft(
                    rs.getLong("application_id"),
                    rs.getInt("line_no"),
                    rs.getLong("tenant_id"),
                    nullableLong(rs, "source_physical_subsystem_id"),
                    nullableLong(rs, "target_logical_subsystem_id"),
                    rs.getString("short_name"),
                    rs.getString("name"),
                    rs.getString("english_name"),
                    rs.getString("business_group_name"),
                    rs.getString("business_continuity_level"),
                    rs.getString("collected_system_level"),
                    rs.getString("deployment_platform"),
                    rs.getString("disaster_recovery_mode"),
                    rs.getLong("responsible_team_org_id"),
                    rs.getString("responsible_team_name_snapshot"),
                    rs.getString("runtime_code"),
                    rs.getString("system_level_code"),
                    rs.getString("development_framework_code"),
                    nullableLong(rs, "owner_user_id"),
                    rs.getString("description"),
                    rs.getString("remark"),
                    rs.getString("reserved_number_slot"),
                    nullableLong(rs, "source_row_version"),
                    rs.getInt("draft_revision"),
                    rs.getString("submitted_snapshot_json"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<ChangeHistoryEvent> HISTORY_MAPPER = (rs, rowNum) ->
            new ChangeHistoryEvent(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("application_id"),
                    rs.getString("event_type"),
                    nullableApplicationStatus(rs, "from_status"),
                    nullableApplicationStatus(rs, "to_status"),
                    rs.getInt("business_round"),
                    rs.getString("summary"),
                    rs.getString("snapshot_json"),
                    rs.getString("diff_json"),
                    rs.getLong("operator_id"),
                    localDateTime(rs.getTimestamp("occurred_at")));

    private static final RowMapper<WorkflowRound> WORKFLOW_ROUND_MAPPER = (rs, rowNum) ->
            new WorkflowRound(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("application_id"),
                    rs.getInt("round_no"),
                    nullableLong(rs, "workflow_definition_id"),
                    nullableLong(rs, "workflow_version_id"),
                    nullableLong(rs, "workflow_instance_id"),
                    rs.getString("payload_digest"),
                    WorkflowRoundStatus.fromDatabase(rs.getString("status")),
                    localDateTime(rs.getTimestamp("started_at")),
                    localDateTime(rs.getTimestamp("ended_at")),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<WorkflowReceipt> WORKFLOW_RECEIPT_MAPPER = (rs, rowNum) ->
            new WorkflowReceipt(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("event_id"),
                    rs.getString("subscriber_key"),
                    nullableLong(rs, "application_id"),
                    nullableInteger(rs.getObject("round_no")),
                    nullableLong(rs, "workflow_instance_id"),
                    rs.getString("event_type"),
                    WorkflowReceiptStatus.fromDatabase(rs.getString("processing_status")),
                    rs.getString("detail"),
                    localDateTime(rs.getTimestamp("received_at")),
                    localDateTime(rs.getTimestamp("processed_at")));

    private static final RowMapper<TargetLock> TARGET_LOCK_MAPPER = (rs, rowNum) ->
            new TargetLock(
                    rs.getLong("tenant_id"),
                    TargetKind.fromDatabase(rs.getString("target_kind")),
                    rs.getLong("target_id"),
                    rs.getLong("application_id"),
                    localDateTime(rs.getTimestamp("acquired_at")));

    private static final RowMapper<ValueReservation> VALUE_RESERVATION_MAPPER = (rs, rowNum) ->
            new ValueReservation(
                    rs.getLong("tenant_id"),
                    rs.getString("reservation_scope"),
                    rs.getString("normalized_value"),
                    rs.getLong("application_id"),
                    rs.getInt("line_no"),
                    localDateTime(rs.getTimestamp("reserved_at")));

    private static final RowMapper<PhysicalReplacement> REPLACEMENT_MAPPER = (rs, rowNum) ->
            new PhysicalReplacement(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("old_physical_subsystem_id"),
                    rs.getLong("new_physical_subsystem_id"),
                    rs.getLong("application_id"),
                    localDateTime(rs.getTimestamp("approved_at")));

    private static final RowMapper<LogicalPublishedState> LOGICAL_MAPPER = (rs, rowNum) ->
            new LogicalPublishedState(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("code"),
                    nullableInteger(rs.getObject("number_sequence")),
                    PublishedStatus.fromDatabase(rs.getString("status")),
                    rs.getInt("sort_no"),
                    rs.getLong("row_version"),
                    rs.getBoolean("deleted"));

    private static final RowMapper<PhysicalPublishedState> PHYSICAL_MAPPER = (rs, rowNum) ->
            new PhysicalPublishedState(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("code"),
                    rs.getString("number_slot"),
                    rs.getLong("logical_subsystem_id"),
                    rs.getString("english_name"),
                    PublishedStatus.fromDatabase(rs.getString("status")),
                    rs.getLong("row_version"),
                    rs.getBoolean("deleted"));

    private static final String LOGICAL_COLUMNS = """
            id, tenant_id, code, number_sequence, status, sort_no, row_version, deleted
            """;
    private static final String PHYSICAL_COLUMNS = """
            id, tenant_id, code, number_slot, logical_subsystem_id, english_name,
            status, row_version, deleted
            """;
    private static final String APPLICATION_COLUMNS = """
            id, tenant_id, target_kind, action_type, target_id, applicant_id, reason, status,
            current_business_round, current_workflow_definition_id, current_workflow_version_id,
            current_workflow_instance_id, current_payload_digest, cancellation_requested, row_version,
            created_by, updated_by, created_at, updated_at
            """;
    private static final String WORKFLOW_ROUND_COLUMNS = """
            id, tenant_id, application_id, round_no, workflow_definition_id, workflow_version_id,
            workflow_instance_id, payload_digest, status, started_at, ended_at, created_at, updated_at
            """;
    private static final String WORKFLOW_RECEIPT_COLUMNS = """
            id, tenant_id, event_id, subscriber_key, application_id, round_no, workflow_instance_id,
            event_type, processing_status, detail, received_at, processed_at
            """;
    private static final String LOGICAL_DRAFT_COLUMNS = """
            application_id, tenant_id, source_logical_subsystem_id, short_name, name, business_org_id,
            deployment_platform_code, system_type_code, system_ownership_code, contact_user_id,
            description, remark, sort_no, reserved_number_sequence, source_row_version, draft_revision,
            submitted_snapshot_json, created_at, updated_at
            """;
    private static final String PHYSICAL_DRAFT_COLUMNS = """
            application_id, line_no, tenant_id, source_physical_subsystem_id, target_logical_subsystem_id,
            short_name, name, english_name, business_group_name, responsible_team_org_id,
            business_continuity_level, collected_system_level, deployment_platform, disaster_recovery_mode,
            responsible_team_name_snapshot, runtime_code, system_level_code, development_framework_code,
            owner_user_id, description, remark, reserved_number_slot, source_row_version, draft_revision,
            submitted_snapshot_json, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    public SubsystemChangeStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc 不能为空");
    }

    /** 创建工单时按调用方提供的 V82 状态字段写入，不解释状态迁移。 */
    public void insertApplication(ChangeApplication application) {
        requireTransaction();
        Objects.requireNonNull(application, "application 不能为空");
        jdbc.update("""
                        INSERT INTO arch_subsystem_change_application
                            (id, tenant_id, target_kind, action_type, target_id, applicant_id, reason, status,
                             current_business_round, current_workflow_definition_id, current_workflow_version_id,
                             current_workflow_instance_id, current_payload_digest, cancellation_requested,
                             row_version, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                application.id(), application.tenantId(), application.targetKind().name(), application.actionType().name(),
                application.targetId(), application.applicantId(), application.reason(), application.status().name(),
                application.currentBusinessRound(), application.currentWorkflowDefinitionId(),
                application.currentWorkflowVersionId(), application.currentWorkflowInstanceId(),
                application.currentPayloadDigest(), application.cancellationRequested(), application.rowVersion(),
                application.createdBy(), application.updatedBy());
    }

    public Optional<ChangeApplication> findApplication(long tenantId, long applicationId) {
        return jdbc.query("SELECT " + APPLICATION_COLUMNS + " FROM arch_subsystem_change_application "
                        + "WHERE tenant_id = ? AND id = ?",
                APPLICATION_MAPPER, tenantId, applicationId).stream().findFirst();
    }

    /** applicantId/status 为空时不附加对应筛选，仍始终由 tenantId 隔离。 */
    public List<ChangeApplication> listApplications(long tenantId, Long applicantId,
                                                    ApplicationStatus status, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(APPLICATION_COLUMNS)
                .append(" FROM arch_subsystem_change_application WHERE tenant_id = ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        if (applicantId != null) {
            sql.append(" AND applicant_id = ?");
            arguments.add(applicantId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            arguments.add(status.name());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?");
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query(sql.toString(), APPLICATION_MAPPER, arguments.toArray());
    }

    public Optional<ChangeApplication> lockApplication(long tenantId, long applicationId) {
        requireTransaction();
        return jdbc.query("SELECT " + APPLICATION_COLUMNS + " FROM arch_subsystem_change_application "
                        + "WHERE tenant_id = ? AND id = ? FOR UPDATE",
                APPLICATION_MAPPER, tenantId, applicationId).stream().findFirst();
    }

    /** 仅以状态和行版本作为 CAS 条件；允许的状态图由 service 决定。 */
    public boolean compareAndSetApplicationStatus(long tenantId, long applicationId,
                                                  ApplicationStatus expectedStatus, long expectedRowVersion,
                                                  ApplicationStatus nextStatus, long updatedBy) {
        requireTransaction();
        Objects.requireNonNull(expectedStatus, "expectedStatus 不能为空");
        Objects.requireNonNull(nextStatus, "nextStatus 不能为空");
        return jdbc.update("""
                        UPDATE arch_subsystem_change_application
                        SET status = ?, row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                        """, nextStatus.name(), updatedBy, tenantId, applicationId,
                expectedStatus.name(), expectedRowVersion) == 1;
    }

    /**
     * 在状态不变的前提下更新申请级可编辑元数据；草稿业务明细仍由对应 draft 表维护。
     * 调用方只能在 DRAFT/RETURNED 等可编辑状态传入相同的 expectedStatus，并以行版本防止覆盖并发编辑。
     */
    public boolean compareAndSetApplicationReason(long tenantId, long applicationId,
                                                  ApplicationStatus expectedStatus, long expectedRowVersion,
                                                  String reason, long updatedBy) {
        requireTransaction();
        Objects.requireNonNull(expectedStatus, "expectedStatus 不能为空");
        return jdbc.update("""
                        UPDATE arch_subsystem_change_application
                        SET reason = ?, row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                """, reason, updatedBy, tenantId, applicationId,
                expectedStatus.name(), expectedRowVersion) == 1;
    }

    /**
     * 在提交流程启动成功后，原子写入当前轮次和工作流上下文。
     * 旧轮次、行版本与 IN_REVIEW 状态均不匹配时不得覆盖较新的提交。
     */
    public boolean compareAndSetApplicationWorkflowContext(long tenantId, long applicationId,
                                                           int expectedCurrentBusinessRound,
                                                           long expectedRowVersion, int nextBusinessRound,
                                                           long workflowDefinitionId, long workflowVersionId,
                                                           long workflowInstanceId, String payloadDigest,
                                                           long updatedBy) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedCurrentBusinessRound, "旧业务轮次");
        requireNonNegative(expectedRowVersion, "旧工单行版本");
        if (nextBusinessRound != Math.addExact(expectedCurrentBusinessRound, 1)) {
            throw new IllegalArgumentException("新业务轮次必须恰好递增一轮");
        }
        requirePositive(workflowDefinitionId, "工作流定义编号");
        requirePositive(workflowVersionId, "工作流版本编号");
        requirePositive(workflowInstanceId, "工作流实例编号");
        requireNonBlank(payloadDigest, "提交摘要");
        requirePositive(updatedBy, "更新人编号");
        return jdbc.update("""
                        UPDATE arch_subsystem_change_application
                        SET current_business_round = ?, current_workflow_definition_id = ?,
                            current_workflow_version_id = ?, current_workflow_instance_id = ?,
                            current_payload_digest = ?, cancellation_requested = 0,
                            row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND status = 'IN_REVIEW'
                          AND current_business_round = ? AND row_version = ?
                        """, nextBusinessRound, workflowDefinitionId, workflowVersionId, workflowInstanceId,
                payloadDigest, updatedBy, tenantId, applicationId, expectedCurrentBusinessRound,
                expectedRowVersion) == 1;
    }

    /** 审批中取消只登记请求，需等待匹配实例的 TERMINATED 生命周期事件确认。 */
    public boolean compareAndSetCancellationRequested(long tenantId, long applicationId,
                                                      long expectedRowVersion, long expectedInstanceId,
                                                      long actorId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(expectedInstanceId, "工作流实例编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requirePositive(actorId, "操作人编号");
        return jdbc.update("""
                        UPDATE arch_subsystem_change_application
                        SET cancellation_requested = 1, row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND status = 'IN_REVIEW'
                          AND current_workflow_instance_id = ? AND row_version = ?
                          AND cancellation_requested = 0
                        """, actorId, tenantId, applicationId, expectedInstanceId, expectedRowVersion) == 1;
    }

    /** 以整行替换逻辑草稿，提交快照和草稿版本由调用方维护。 */
    public void replaceLogicalDraft(LogicalDraft draft) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        jdbc.update("""
                        INSERT INTO arch_subsystem_logical_draft
                            (application_id, tenant_id, source_logical_subsystem_id, short_name, name, business_org_id,
                             deployment_platform_code, system_type_code, system_ownership_code, contact_user_id,
                             description, remark, sort_no, reserved_number_sequence, source_row_version,
                             draft_revision, submitted_snapshot_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            source_logical_subsystem_id = VALUES(source_logical_subsystem_id),
                            short_name = VALUES(short_name), name = VALUES(name),
                            business_org_id = VALUES(business_org_id),
                            deployment_platform_code = VALUES(deployment_platform_code),
                            system_type_code = VALUES(system_type_code),
                            system_ownership_code = VALUES(system_ownership_code),
                            contact_user_id = VALUES(contact_user_id), description = VALUES(description),
                            remark = VALUES(remark), sort_no = VALUES(sort_no),
                            reserved_number_sequence = VALUES(reserved_number_sequence),
                            source_row_version = VALUES(source_row_version),
                            draft_revision = VALUES(draft_revision),
                            submitted_snapshot_json = VALUES(submitted_snapshot_json)
                        """,
                draft.applicationId(), draft.tenantId(), draft.sourceLogicalSubsystemId(), draft.shortName(),
                draft.name(), draft.businessOrgId(), draft.deploymentPlatformCode(), draft.systemTypeCode(),
                draft.systemOwnershipCode(), draft.contactUserId(), draft.description(), draft.remark(),
                draft.sortNo(), draft.reservedNumberSequence(), draft.sourceRowVersion(), draft.draftRevision(),
                draft.submittedSnapshotJson());
    }

    public Optional<LogicalDraft> findLogicalDraft(long tenantId, long applicationId) {
        return jdbc.query("SELECT " + LOGICAL_DRAFT_COLUMNS + " FROM arch_subsystem_logical_draft "
                        + "WHERE tenant_id = ? AND application_id = ?",
                LOGICAL_DRAFT_MAPPER, tenantId, applicationId).stream().findFirst();
    }

    /** 整批替换物理草稿；空集合表示该逻辑新增申请没有物理草稿。 */
    public void replacePhysicalDrafts(long tenantId, long applicationId, List<PhysicalDraft> drafts) {
        requireTransaction();
        Objects.requireNonNull(drafts, "drafts 不能为空");
        for (PhysicalDraft draft : drafts) {
            if (draft == null || draft.tenantId() != tenantId || draft.applicationId() != applicationId) {
                throw new IllegalArgumentException("物理草稿与目标租户或申请不一致");
            }
        }
        jdbc.update("DELETE FROM arch_subsystem_physical_draft WHERE tenant_id = ? AND application_id = ?",
                tenantId, applicationId);
        for (PhysicalDraft draft : drafts) {
            insertPhysicalDraft(draft);
        }
    }

    public List<PhysicalDraft> findPhysicalDrafts(long tenantId, long applicationId) {
        return jdbc.query("SELECT " + PHYSICAL_DRAFT_COLUMNS + " FROM arch_subsystem_physical_draft "
                        + "WHERE tenant_id = ? AND application_id = ? ORDER BY line_no ASC",
                PHYSICAL_DRAFT_MAPPER, tenantId, applicationId);
    }

    public void insertHistory(ChangeHistoryEvent event) {
        requireTransaction();
        Objects.requireNonNull(event, "event 不能为空");
        jdbc.update("""
                        INSERT INTO arch_subsystem_change_history
                            (id, tenant_id, application_id, event_type, from_status, to_status, business_round,
                             summary, snapshot_json, diff_json, operator_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, event.id(), event.tenantId(), event.applicationId(), event.eventType(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(), event.businessRound(), event.summary(),
                event.snapshotJson(), event.diffJson(), event.operatorId(), timestamp(event.occurredAt()));
    }

    /** occurred_at 相同的事件按 id 升序返回，避免数据库时间精度造成非稳定顺序。 */
    public List<ChangeHistoryEvent> listHistory(long tenantId, long applicationId) {
        return jdbc.query("""
                        SELECT id, tenant_id, application_id, event_type, from_status, to_status, business_round,
                               summary, snapshot_json, diff_json, operator_id, occurred_at
                        FROM arch_subsystem_change_history
                        WHERE tenant_id = ? AND application_id = ?
                        ORDER BY occurred_at ASC, id ASC
                        """, HISTORY_MAPPER, tenantId, applicationId);
    }

    /**
     * 在调用平台启动接口前写入待绑定轮次。PENDING 轮次不得预先伪造平台 definition/version/instance 或摘要。
     */
    public void insertPendingWorkflowRound(WorkflowRound round) {
        requireTransaction();
        Objects.requireNonNull(round, "工作流轮次不能为空");
        requirePositive(round.id(), "工作流轮次编号");
        requirePositive(round.tenantId(), "租户编号");
        requirePositive(round.applicationId(), "工单编号");
        requirePositive(round.roundNo(), "工作流轮次");
        if (round.status() != WorkflowRoundStatus.PENDING
                || round.workflowDefinitionId() != null || round.workflowVersionId() != null
                || round.workflowInstanceId() != null || round.payloadDigest() != null
                || round.startedAt() != null || round.endedAt() != null) {
            throw new IllegalArgumentException("PENDING 工作流轮次不得预先绑定平台上下文");
        }
        jdbc.update("""
                        INSERT INTO arch_subsystem_workflow_round
                            (id, tenant_id, application_id, round_no, status)
                        VALUES (?, ?, ?, ?, ?)
                        """, round.id(), round.tenantId(), round.applicationId(), round.roundNo(),
                WorkflowRoundStatus.PENDING.name());
    }

    public Optional<WorkflowRound> findWorkflowRound(long tenantId, long applicationId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return jdbc.query("SELECT " + WORKFLOW_ROUND_COLUMNS + " FROM arch_subsystem_workflow_round "
                        + "WHERE tenant_id = ? AND application_id = ? AND round_no = ?",
                WORKFLOW_ROUND_MAPPER, tenantId, applicationId, roundNo).stream().findFirst();
    }

    /** 生命周期消费者按业务键和轮次加锁，避免同一轮次并发完成。 */
    public Optional<WorkflowRound> lockWorkflowRound(long tenantId, long applicationId, int roundNo) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return jdbc.query("SELECT " + WORKFLOW_ROUND_COLUMNS + " FROM arch_subsystem_workflow_round "
                        + "WHERE tenant_id = ? AND application_id = ? AND round_no = ? FOR UPDATE",
                WORKFLOW_ROUND_MAPPER, tenantId, applicationId, roundNo).stream().findFirst();
    }

    /** 生命周期消费者按平台实例加锁，tenantId 始终是查询条件的一部分。 */
    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workflowInstanceId, "工作流实例编号");
        return jdbc.query("SELECT " + WORKFLOW_ROUND_COLUMNS + " FROM arch_subsystem_workflow_round "
                        + "WHERE tenant_id = ? AND workflow_instance_id = ? FOR UPDATE",
                WORKFLOW_ROUND_MAPPER, tenantId, workflowInstanceId).stream().findFirst();
    }

    /** 轮次号存在且没有更高轮次时返回 true；该只读判断由调用方的 application/round 锁配合使用。 */
    public boolean isLatestWorkflowRound(long tenantId, long applicationId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM arch_subsystem_workflow_round current_round
                        WHERE current_round.tenant_id = ?
                          AND current_round.application_id = ?
                          AND current_round.round_no = ?
                          AND NOT EXISTS (
                              SELECT 1
                              FROM arch_subsystem_workflow_round newer_round
                              WHERE newer_round.tenant_id = current_round.tenant_id
                                AND newer_round.application_id = current_round.application_id
                                AND newer_round.round_no > current_round.round_no
                          )
                        """, Long.class, tenantId, applicationId, roundNo);
        return count != null && count == 1;
    }

    /** PENDING -> STARTED，绑定平台定义、版本、实例和本次提交摘要。 */
    public boolean bindWorkflowRoundStarted(long tenantId, long applicationId, int roundNo,
                                            long workflowDefinitionId, long workflowVersionId,
                                            long workflowInstanceId, String payloadDigest,
                                            LocalDateTime startedAt) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        requirePositive(workflowDefinitionId, "工作流定义编号");
        requirePositive(workflowVersionId, "工作流版本编号");
        requirePositive(workflowInstanceId, "工作流实例编号");
        requireNonBlank(payloadDigest, "提交摘要");
        Objects.requireNonNull(startedAt, "启动时间不能为空");
        return jdbc.update("""
                        UPDATE arch_subsystem_workflow_round
                        SET workflow_definition_id = ?, workflow_version_id = ?, workflow_instance_id = ?,
                            payload_digest = ?, status = 'STARTED', started_at = ?, ended_at = NULL
                        WHERE tenant_id = ? AND application_id = ? AND round_no = ? AND status = 'PENDING'
                        """, workflowDefinitionId, workflowVersionId, workflowInstanceId, payloadDigest,
                timestamp(startedAt), tenantId, applicationId, roundNo) == 1;
    }

    /** STARTED 轮次只允许进入业务约定的四种终态。 */
    public boolean completeStartedWorkflowRound(long tenantId, long applicationId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        Objects.requireNonNull(nextStatus, "下一轮次状态不能为空");
        if (!nextStatus.isTerminalOutcome()) {
            throw new IllegalArgumentException("STARTED 工作流轮次只能进入 RETURNED、APPROVED、REJECTED 或 TERMINATED");
        }
        Objects.requireNonNull(endedAt, "结束时间不能为空");
        return jdbc.update("""
                        UPDATE arch_subsystem_workflow_round
                        SET status = ?, ended_at = ?
                        WHERE tenant_id = ? AND application_id = ? AND round_no = ? AND status = 'STARTED'
                        """, nextStatus.name(), timestamp(endedAt), tenantId, applicationId, roundNo) == 1;
    }

    /**
     * 以 tenant + eventId + subscriberKey 去重。V82 没有处理中状态，未完成事务内暂用 FAILED 占位；
     * 若事务回滚，该占位回执也会回滚，调用方必须在提交前以 completeReceipt 写入最终状态。
     */
    public boolean beginReceipt(WorkflowReceiptStart receipt) {
        requireTransaction();
        Objects.requireNonNull(receipt, "工作流回执不能为空");
        requirePositive(receipt.id(), "工作流回执编号");
        requirePositive(receipt.tenantId(), "租户编号");
        requireNonBlank(receipt.eventId(), "事件编号");
        requireNonBlank(receipt.subscriberKey(), "订阅方标识");
        requireNonBlank(receipt.eventType(), "事件类型");
        requireOptionalPositive(receipt.applicationId(), "工单编号");
        requireOptionalPositive(receipt.roundNo(), "工作流轮次");
        requireOptionalPositive(receipt.workflowInstanceId(), "工作流实例编号");
        return jdbc.update("""
                        INSERT IGNORE INTO arch_subsystem_workflow_receipt
                            (id, tenant_id, event_id, subscriber_key, application_id, round_no,
                             workflow_instance_id, event_type, processing_status, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, receipt.id(), receipt.tenantId(), receipt.eventId(), receipt.subscriberKey(),
                receipt.applicationId(), receipt.roundNo(), receipt.workflowInstanceId(), receipt.eventType(),
                WorkflowReceiptStatus.FAILED.name(), "事务内事件尚未完成") == 1;
    }

    /** 仅当前事务创建的 FAILED 占位回执可以写入最终处理结论。 */
    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        Objects.requireNonNull(status, "回执状态不能为空");
        return jdbc.update("""
                        UPDATE arch_subsystem_workflow_receipt
                        SET processing_status = ?, detail = ?, processed_at = CURRENT_TIMESTAMP
                        WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ?
                          AND processing_status = 'FAILED'
                        """, status.name(), detail, tenantId, eventId, subscriberKey) == 1;
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        return jdbc.query("SELECT " + WORKFLOW_RECEIPT_COLUMNS + " FROM arch_subsystem_workflow_receipt "
                        + "WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ?",
                WORKFLOW_RECEIPT_MAPPER, tenantId, eventId, subscriberKey).stream().findFirst();
    }

    public void insertTargetLock(TargetLock lock) {
        requireTransaction();
        Objects.requireNonNull(lock, "lock 不能为空");
        jdbc.update("""
                        INSERT INTO arch_subsystem_change_lock
                            (tenant_id, target_kind, target_id, application_id)
                        VALUES (?, ?, ?, ?)
                        """, lock.tenantId(), lock.targetKind().name(), lock.targetId(), lock.applicationId());
    }

    public Optional<TargetLock> findTargetLock(long tenantId, TargetKind targetKind, long targetId) {
        Objects.requireNonNull(targetKind, "targetKind 不能为空");
        return jdbc.query("""
                        SELECT tenant_id, target_kind, target_id, application_id, acquired_at
                        FROM arch_subsystem_change_lock
                        WHERE tenant_id = ? AND target_kind = ? AND target_id = ?
                        """, TARGET_LOCK_MAPPER, tenantId, targetKind.name(), targetId).stream().findFirst();
    }

    public void deleteTargetLock(long tenantId, TargetKind targetKind, long targetId, long applicationId) {
        requireTransaction();
        Objects.requireNonNull(targetKind, "targetKind 不能为空");
        jdbc.update("""
                        DELETE FROM arch_subsystem_change_lock
                        WHERE tenant_id = ? AND target_kind = ? AND target_id = ? AND application_id = ?
                        """, tenantId, targetKind.name(), targetId, applicationId);
    }

    public void insertValueReservation(ValueReservation reservation) {
        requireTransaction();
        Objects.requireNonNull(reservation, "reservation 不能为空");
        jdbc.update("""
                        INSERT INTO arch_subsystem_value_reservation
                            (tenant_id, reservation_scope, normalized_value, application_id, line_no)
                        VALUES (?, ?, ?, ?, ?)
                        """, reservation.tenantId(), reservation.reservationScope(), reservation.normalizedValue(),
                reservation.applicationId(), reservation.lineNo());
    }

    public Optional<ValueReservation> findValueReservation(long tenantId, String reservationScope,
                                                           String normalizedValue) {
        return jdbc.query("""
                        SELECT tenant_id, reservation_scope, normalized_value, application_id, line_no, reserved_at
                        FROM arch_subsystem_value_reservation
                        WHERE tenant_id = ? AND reservation_scope = ? AND normalized_value = ?
                        """, VALUE_RESERVATION_MAPPER, tenantId, reservationScope, normalizedValue)
                .stream().findFirst();
    }

    public void deleteValueReservations(long tenantId, long applicationId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_subsystem_value_reservation WHERE tenant_id = ? AND application_id = ?",
                tenantId, applicationId);
    }

    public void insertPhysicalReplacement(PhysicalReplacement replacement) {
        requireTransaction();
        Objects.requireNonNull(replacement, "replacement 不能为空");
        jdbc.update("""
                        INSERT INTO arch_subsystem_replacement
                            (id, tenant_id, old_physical_subsystem_id, new_physical_subsystem_id, application_id)
                        VALUES (?, ?, ?, ?, ?)
                        """, replacement.id(), replacement.tenantId(), replacement.oldPhysicalSubsystemId(),
                replacement.newPhysicalSubsystemId(), replacement.applicationId());
    }

    public Optional<PhysicalReplacement> findPhysicalReplacementByApplication(long tenantId, long applicationId) {
        return jdbc.query("""
                        SELECT id, tenant_id, old_physical_subsystem_id, new_physical_subsystem_id, application_id,
                               approved_at
                        FROM arch_subsystem_replacement
                        WHERE tenant_id = ? AND application_id = ?
                        """, REPLACEMENT_MAPPER, tenantId, applicationId).stream().findFirst();
    }

    public Optional<LogicalPublishedState> findLogical(long tenantId, long id) {
        return logical(tenantId, id, false);
    }

    public Optional<LogicalPublishedState> lockLogical(long tenantId, long id) {
        requireTransaction();
        return logical(tenantId, id, true);
    }

    public Optional<PhysicalPublishedState> findPhysical(long tenantId, long id) {
        return physical(tenantId, id, false);
    }

    public Optional<PhysicalPublishedState> lockPhysical(long tenantId, long id) {
        requireTransaction();
        return physical(tenantId, id, true);
    }

    /** 从逻辑草稿发布新主记录；编号与状态是否合法由调用方在同一事务中决定。 */
    public void insertLogicalPublished(long id, long tenantId, String code, Integer numberSequence,
                                       LogicalDraft draft, PublishedStatus status, long rowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        jdbc.update("""
                        INSERT INTO arch_logical_subsystem
                            (id, tenant_id, code, number_sequence, short_name, name, business_org_id,
                             deployment_platform_code, system_type_code, system_ownership_code, contact_user_id,
                             description, remark, status, sort_no, row_version, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, tenantId, code, numberSequence, draft.shortName(), draft.name(),
                draft.businessOrgId(), draft.deploymentPlatformCode(), draft.systemTypeCode(),
                draft.systemOwnershipCode(), draft.contactUserId(), draft.description(), draft.remark(),
                status.name(), draft.sortNo(), rowVersion, actorId, actorId);
    }

    /** 不修改逻辑编号、发布状态或行版本以外的字段，CAS 只由 rowVersion 保护。 */
    public boolean updateLogicalPublishedFields(long tenantId, long id, LogicalDraft draft,
                                                long expectedRowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        return jdbc.update("""
                        UPDATE arch_logical_subsystem
                        SET short_name = ?, name = ?, business_org_id = ?, deployment_platform_code = ?,
                            system_type_code = ?, system_ownership_code = ?, contact_user_id = ?,
                            description = ?, remark = ?, sort_no = ?, updated_by = ?,
                            row_version = row_version + 1
                        WHERE tenant_id = ? AND id = ? AND row_version = ?
                        """, draft.shortName(), draft.name(), draft.businessOrgId(), draft.deploymentPlatformCode(),
                draft.systemTypeCode(), draft.systemOwnershipCode(), draft.contactUserId(), draft.description(),
                draft.remark(), draft.sortNo(), actorId, tenantId, id, expectedRowVersion) == 1;
    }

    public boolean updateLogicalPublishedStatus(long tenantId, long id, PublishedStatus status,
                                                long expectedRowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(status, "status 不能为空");
        return jdbc.update("""
                        UPDATE arch_logical_subsystem
                        SET status = ?, updated_by = ?, row_version = row_version + 1
                        WHERE tenant_id = ? AND id = ? AND row_version = ?
                        """, status.name(), actorId, tenantId, id, expectedRowVersion) == 1;
    }

    /** 从物理草稿发布新主记录；所属逻辑与槽位由调用方显式给出。 */
    public void insertPhysicalPublished(long id, long tenantId, String code, String numberSlot,
                                        long logicalSubsystemId, PhysicalDraft draft, PublishedStatus status,
                                        long rowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        jdbc.update("""
                        INSERT INTO arch_physical_subsystem
                            (id, tenant_id, code, number_slot, short_name, name, logical_subsystem_id,
                             english_name, business_group_name, business_continuity_level, collected_system_level,
                             deployment_platform, disaster_recovery_mode, responsible_team_org_id,
                             responsible_team_name_snapshot, runtime_code, system_level_code,
                             development_framework_code, owner_user_id, description, remark, status, row_version,
                             created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, tenantId, code, numberSlot, draft.shortName(), draft.name(), logicalSubsystemId,
                draft.englishName(), draft.businessGroupName(), draft.businessContinuityLevel(),
                draft.collectedSystemLevel(), draft.deploymentPlatform(), draft.disasterRecoveryMode(),
                draft.responsibleTeamOrgId(),
                draft.responsibleTeamNameSnapshot(), draft.runtimeCode(), draft.systemLevelCode(),
                draft.developmentFrameworkCode(), draft.ownerUserId(), draft.description(), draft.remark(),
                status.name(), rowVersion, actorId, actorId);
    }

    /** 普通字段更新不触及物理记录所属逻辑、编号、状态和行版本。 */
    public boolean updatePhysicalPublishedFields(long tenantId, long id, PhysicalDraft draft,
                                                 long expectedRowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        return jdbc.update("""
                        UPDATE arch_physical_subsystem
                        SET short_name = ?, name = ?, english_name = ?, business_group_name = ?,
                            business_continuity_level = ?, collected_system_level = ?, deployment_platform = ?,
                            disaster_recovery_mode = ?, responsible_team_org_id = ?,
                            responsible_team_name_snapshot = ?, runtime_code = ?, system_level_code = ?,
                            development_framework_code = ?, owner_user_id = ?, description = ?, remark = ?,
                            updated_by = ?, row_version = row_version + 1
                        WHERE tenant_id = ? AND id = ? AND row_version = ?
                        """, draft.shortName(), draft.name(), draft.englishName(), draft.businessGroupName(),
                draft.businessContinuityLevel(), draft.collectedSystemLevel(), draft.deploymentPlatform(),
                draft.disasterRecoveryMode(), draft.responsibleTeamOrgId(), draft.responsibleTeamNameSnapshot(),
                draft.runtimeCode(), draft.systemLevelCode(), draft.developmentFrameworkCode(), draft.ownerUserId(),
                draft.description(), draft.remark(), actorId, tenantId, id, expectedRowVersion) == 1;
    }

    public boolean updatePhysicalPublishedStatus(long tenantId, long id, PublishedStatus status,
                                                 long expectedRowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(status, "status 不能为空");
        return jdbc.update("""
                        UPDATE arch_physical_subsystem
                        SET status = ?, updated_by = ?, row_version = row_version + 1
                        WHERE tenant_id = ? AND id = ? AND row_version = ?
                        """, status.name(), actorId, tenantId, id, expectedRowVersion) == 1;
    }

    private Optional<LogicalPublishedState> logical(long tenantId, long id, boolean forUpdate) {
        List<LogicalPublishedState> rows = jdbc.query(
                "SELECT " + LOGICAL_COLUMNS + " FROM arch_logical_subsystem WHERE tenant_id = ? AND id = ?"
                        + (forUpdate ? " FOR UPDATE" : ""),
                LOGICAL_MAPPER, tenantId, id);
        return rows.stream().findFirst();
    }

    private Optional<PhysicalPublishedState> physical(long tenantId, long id, boolean forUpdate) {
        List<PhysicalPublishedState> rows = jdbc.query(
                "SELECT " + PHYSICAL_COLUMNS + " FROM arch_physical_subsystem WHERE tenant_id = ? AND id = ?"
                        + (forUpdate ? " FOR UPDATE" : ""),
                PHYSICAL_MAPPER, tenantId, id);
        return rows.stream().findFirst();
    }

    private void insertPhysicalDraft(PhysicalDraft draft) {
        jdbc.update("""
                        INSERT INTO arch_subsystem_physical_draft
                            (application_id, line_no, tenant_id, source_physical_subsystem_id,
                             target_logical_subsystem_id, short_name, name, english_name, business_group_name,
                             business_continuity_level, collected_system_level, deployment_platform,
                             disaster_recovery_mode, responsible_team_org_id, responsible_team_name_snapshot,
                             runtime_code, system_level_code, development_framework_code, owner_user_id, description, remark,
                             reserved_number_slot, source_row_version, draft_revision, submitted_snapshot_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, draft.applicationId(), draft.lineNo(), draft.tenantId(),
                draft.sourcePhysicalSubsystemId(), draft.targetLogicalSubsystemId(), draft.shortName(), draft.name(),
                draft.englishName(), draft.businessGroupName(), draft.businessContinuityLevel(),
                draft.collectedSystemLevel(), draft.deploymentPlatform(), draft.disasterRecoveryMode(),
                draft.responsibleTeamOrgId(),
                draft.responsibleTeamNameSnapshot(), draft.runtimeCode(), draft.systemLevelCode(),
                draft.developmentFrameworkCode(), draft.ownerUserId(), draft.description(), draft.remark(),
                draft.reservedNumberSlot(), draft.sourceRowVersion(), draft.draftRevision(),
                draft.submittedSnapshotJson());
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static ApplicationStatus nullableApplicationStatus(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : ApplicationStatus.fromDatabase(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static void requirePositive(long value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + "必须为正数");
        }
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
    }

    private static void requireOptionalPositive(Number value, String label) {
        if (value != null && value.longValue() <= 0) {
            throw new IllegalArgumentException(label + "必须为正数");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("变更持久化写入或加锁必须在真实数据库事务中执行");
        }
    }
}
