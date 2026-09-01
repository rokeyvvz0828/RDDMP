package com.ccb.architecture.network.persistence;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HistoryEvent;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceipt;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStart;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRoundStatus;
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

/** V89 网络专项工单的持久化边界；行版本与状态机守卫集中在 SQL 条件中。 */
@Repository
public class NetworkWorkOrderStore {
    private static final String WORK_ORDER_COLUMNS = """
            id, tenant_id, kind, action_type, subject, applicant_id, reason, status, business_payload,
            attachment_ids, result_status, result_description, result_attachment_ids, result_registered_by,
            result_registered_at, current_business_round, current_workflow_definition_id,
            current_workflow_version_id, current_workflow_instance_id, current_payload_digest,
            cancellation_requested, row_version, created_by, updated_by, created_at, updated_at""";

    private static final RowMapper<WorkOrder> WORK_ORDER_MAPPER = (rs, rowNum) ->
            new WorkOrder(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    Kind.fromDatabase(rs.getString("kind")),
                    ActionType.fromDatabase(rs.getString("action_type")),
                    rs.getString("subject"),
                    rs.getLong("applicant_id"),
                    rs.getString("reason"),
                    WorkOrderStatus.fromDatabase(rs.getString("status")),
                    rs.getString("business_payload"),
                    rs.getString("attachment_ids"),
                    nullableResultStatus(rs, "result_status"),
                    rs.getString("result_description"),
                    rs.getString("result_attachment_ids"),
                    nullableLong(rs, "result_registered_by"),
                    localDateTime(rs.getTimestamp("result_registered_at")),
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

    private static final RowMapper<HistoryEvent> HISTORY_MAPPER = (rs, rowNum) ->
            new HistoryEvent(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("work_order_id"),
                    rs.getString("event_type"),
                    nullableStatus(rs, "from_status"),
                    nullableStatus(rs, "to_status"),
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
                    rs.getLong("work_order_id"),
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
                    nullableLong(rs, "work_order_id"),
                    nullableInteger(rs, "round_no"),
                    nullableLong(rs, "workflow_instance_id"),
                    rs.getString("event_type"),
                    WorkflowReceiptStatus.fromDatabase(rs.getString("processing_status")),
                    rs.getString("detail"),
                    localDateTime(rs.getTimestamp("received_at")),
                    localDateTime(rs.getTimestamp("processed_at")));

    private final JdbcTemplate jdbc;

    public NetworkWorkOrderStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate 不能为空");
    }

    public void insertWorkOrder(WorkOrder workOrder) {
        requireTransaction();
        Objects.requireNonNull(workOrder, "工单不能为空");
        jdbc.update("""
                        INSERT INTO arch_network_work_order
                            (id, tenant_id, kind, action_type, subject, applicant_id, reason, status,
                             business_payload, attachment_ids, result_status, result_description,
                             result_attachment_ids, result_registered_by, result_registered_at,
                             current_business_round, current_workflow_definition_id,
                             current_workflow_version_id, current_workflow_instance_id,
                             current_payload_digest, cancellation_requested, row_version, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                workOrder.id(), workOrder.tenantId(), workOrder.kind().name(), workOrder.actionType().name(),
                workOrder.subject(), workOrder.applicantId(), workOrder.reason(), workOrder.status().name(),
                workOrder.payload(), workOrder.attachmentIds(),
                workOrder.resultStatus() == null ? null : workOrder.resultStatus().name(),
                workOrder.resultDescription(), workOrder.resultAttachmentIds(), workOrder.resultRegisteredBy(),
                timestamp(workOrder.resultRegisteredAt()), workOrder.currentBusinessRound(),
                workOrder.currentWorkflowDefinitionId(), workOrder.currentWorkflowVersionId(),
                workOrder.currentWorkflowInstanceId(), workOrder.currentPayloadDigest(),
                workOrder.cancellationRequested(), workOrder.rowVersion(), workOrder.createdBy(),
                workOrder.updatedBy());
    }

    public Optional<WorkOrder> findWorkOrder(long tenantId, long workOrderId) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        return jdbc.query("SELECT " + WORK_ORDER_COLUMNS + " FROM arch_network_work_order "
                        + "WHERE tenant_id = ? AND id = ?",
                WORK_ORDER_MAPPER, tenantId, workOrderId).stream().findFirst();
    }

    public Optional<WorkOrder> lockWorkOrder(long tenantId, long workOrderId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        return jdbc.query("SELECT " + WORK_ORDER_COLUMNS + " FROM arch_network_work_order "
                        + "WHERE tenant_id = ? AND id = ? FOR UPDATE",
                WORK_ORDER_MAPPER, tenantId, workOrderId).stream().findFirst();
    }

    /** applicantId/kind/status 为空时不附加对应筛选，仍始终由 tenantId 隔离。 */
    public List<WorkOrder> listWorkOrders(long tenantId, Long applicantId, Kind kind,
                                          WorkOrderStatus status, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(WORK_ORDER_COLUMNS)
                .append(" FROM arch_network_work_order WHERE tenant_id = ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        if (applicantId != null) {
            sql.append(" AND applicant_id = ?");
            arguments.add(applicantId);
        }
        if (kind != null) {
            sql.append(" AND kind = ?");
            arguments.add(kind.name());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            arguments.add(status.name());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?");
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query(sql.toString(), WORK_ORDER_MAPPER, arguments.toArray());
    }

    /** 草稿编辑：仅 DRAFT/RETURNED 且行版本匹配时更新载荷、附件与原因。 */
    public boolean updateDraft(long tenantId, long workOrderId, WorkOrderStatus expectedStatus,
                               long expectedRowVersion, String reason, String payload,
                               String attachmentIds, long updatedBy) {
        requireTransaction();
        Objects.requireNonNull(expectedStatus, "期望状态不能为空");
        return jdbc.update("""
                        UPDATE arch_network_work_order
                        SET reason = ?, business_payload = ?, attachment_ids = ?,
                            row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                        """, reason, payload, attachmentIds, updatedBy, tenantId, workOrderId,
                expectedStatus.name(), expectedRowVersion) == 1;
    }

    /** 仅以状态和行版本作为 CAS 条件；允许的状态图由 service 决定。 */
    public boolean compareAndSetStatus(long tenantId, long workOrderId,
                                       WorkOrderStatus expectedStatus, long expectedRowVersion,
                                       WorkOrderStatus nextStatus, long updatedBy) {
        requireTransaction();
        Objects.requireNonNull(expectedStatus, "期望状态不能为空");
        Objects.requireNonNull(nextStatus, "目标状态不能为空");
        return jdbc.update("""
                        UPDATE arch_network_work_order
                        SET status = ?, row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                        """, nextStatus.name(), updatedBy, tenantId, workOrderId,
                expectedStatus.name(), expectedRowVersion) == 1;
    }

    /** 提交启动成功后原子写入当前轮次和工作流上下文。 */
    public boolean compareAndSetWorkflowContext(long tenantId, long workOrderId,
                                                int expectedCurrentBusinessRound,
                                                long expectedRowVersion, int nextBusinessRound,
                                                long workflowDefinitionId, long workflowVersionId,
                                                long workflowInstanceId, String payloadDigest,
                                                long updatedBy) {
        requireTransaction();
        return jdbc.update("""
                        UPDATE arch_network_work_order
                        SET current_business_round = ?, current_workflow_definition_id = ?,
                            current_workflow_version_id = ?, current_workflow_instance_id = ?,
                            current_payload_digest = ?, row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND current_business_round = ? AND row_version = ?
                          AND status = 'IN_REVIEW'
                        """, nextBusinessRound, workflowDefinitionId, workflowVersionId, workflowInstanceId,
                payloadDigest, updatedBy, tenantId, workOrderId, expectedCurrentBusinessRound,
                expectedRowVersion) == 1;
    }

    public boolean compareAndSetCancellationRequested(long tenantId, long workOrderId,
                                                      long expectedRowVersion, boolean requested,
                                                      long updatedBy) {
        requireTransaction();
        return jdbc.update("""
                        UPDATE arch_network_work_order
                        SET cancellation_requested = ?, row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND row_version = ? AND status = 'IN_REVIEW'
                        """, requested, updatedBy, tenantId, workOrderId, expectedRowVersion) == 1;
    }

    /** 办理结果登记：IN_REVIEW 或 COMPLETED 且行版本匹配时写入，不改变工单状态。 */
    public boolean updateHandlingResult(long tenantId, long workOrderId, long expectedRowVersion,
                                        String resultStatus, String resultDescription,
                                        String resultAttachmentIds, long registeredBy) {
        requireTransaction();
        return jdbc.update("""
                        UPDATE arch_network_work_order
                        SET result_status = ?, result_description = ?, result_attachment_ids = ?,
                            result_registered_by = ?, result_registered_at = CURRENT_TIMESTAMP,
                            row_version = row_version + 1, updated_by = ?
                        WHERE tenant_id = ? AND id = ? AND row_version = ?
                          AND status IN ('IN_REVIEW', 'COMPLETED')
                        """, resultStatus, resultDescription, resultAttachmentIds, registeredBy, registeredBy,
                tenantId, workOrderId, expectedRowVersion) == 1;
    }

    public void insertHistory(HistoryEvent event) {
        requireTransaction();
        Objects.requireNonNull(event, "历史事件不能为空");
        jdbc.update("""
                        INSERT INTO arch_network_work_order_history
                            (id, tenant_id, work_order_id, event_type, from_status, to_status, business_round,
                             summary, snapshot_json, diff_json, operator_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, event.id(), event.tenantId(), event.workOrderId(), event.eventType(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(), event.businessRound(), event.summary(),
                event.snapshotJson(), event.diffJson(), event.operatorId(), timestamp(event.occurredAt()));
    }

    /** occurred_at 相同的事件按 id 升序返回，避免数据库时间精度造成非稳定顺序。 */
    public List<HistoryEvent> listHistory(long tenantId, long workOrderId) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        return jdbc.query("""
                        SELECT id, tenant_id, work_order_id, event_type, from_status, to_status, business_round,
                               summary, snapshot_json, diff_json, operator_id, occurred_at
                        FROM arch_network_work_order_history
                        WHERE tenant_id = ? AND work_order_id = ?
                        ORDER BY occurred_at ASC, id ASC
                        """, HISTORY_MAPPER, tenantId, workOrderId);
    }

    /** PENDING 轮次不得预先伪造平台 definition/version/instance 或摘要。 */
    public void insertPendingWorkflowRound(WorkflowRound round) {
        requireTransaction();
        Objects.requireNonNull(round, "工作流轮次不能为空");
        requirePositive(round.id(), "工作流轮次编号");
        requirePositive(round.tenantId(), "租户编号");
        requirePositive(round.workOrderId(), "工单编号");
        requirePositive(round.roundNo(), "工作流轮次");
        if (round.status() != WorkflowRoundStatus.PENDING
                || round.workflowDefinitionId() != null || round.workflowVersionId() != null
                || round.workflowInstanceId() != null || round.payloadDigest() != null
                || round.startedAt() != null || round.endedAt() != null) {
            throw new IllegalArgumentException("PENDING 工作流轮次不得预先绑定平台上下文");
        }
        jdbc.update("""
                        INSERT INTO arch_network_workflow_round
                            (id, tenant_id, work_order_id, round_no, workflow_definition_id,
                             workflow_version_id, workflow_instance_id, payload_digest, status,
                             started_at, ended_at)
                        VALUES (?, ?, ?, ?, NULL, NULL, NULL, NULL, 'PENDING', NULL, NULL)
                        """, round.id(), round.tenantId(), round.workOrderId(), round.roundNo());
    }

    public Optional<WorkflowRound> findWorkflowRound(long tenantId, long workOrderId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return jdbc.query("""
                        SELECT id, tenant_id, work_order_id, round_no, workflow_definition_id,
                               workflow_version_id, workflow_instance_id, payload_digest, status,
                               started_at, ended_at, created_at, updated_at
                        FROM arch_network_workflow_round
                        WHERE tenant_id = ? AND work_order_id = ? AND round_no = ?
                        """, WORKFLOW_ROUND_MAPPER, tenantId, workOrderId, roundNo).stream().findFirst();
    }

    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workflowInstanceId, "工作流实例编号");
        return jdbc.query("""
                        SELECT id, tenant_id, work_order_id, round_no, workflow_definition_id,
                               workflow_version_id, workflow_instance_id, payload_digest, status,
                               started_at, ended_at, created_at, updated_at
                        FROM arch_network_workflow_round
                        WHERE tenant_id = ? AND workflow_instance_id = ? FOR UPDATE
                        """, WORKFLOW_ROUND_MAPPER, tenantId, workflowInstanceId).stream().findFirst();
    }

    public boolean isLatestWorkflowRound(long tenantId, long workOrderId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        Integer latest = jdbc.queryForObject("""
                        SELECT MAX(round_no) FROM arch_network_workflow_round
                        WHERE tenant_id = ? AND work_order_id = ?
                        """, Integer.class, tenantId, workOrderId);
        return latest != null && latest == roundNo;
    }

    public boolean bindWorkflowRoundStarted(long tenantId, long workOrderId, int roundNo,
                                            long workflowDefinitionId, long workflowVersionId,
                                            long workflowInstanceId, String payloadDigest,
                                            LocalDateTime startedAt) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        requireNonBlank(payloadDigest, "载荷摘要");
        requirePositive(workflowDefinitionId, "流程定义编号");
        requirePositive(workflowVersionId, "流程版本编号");
        requirePositive(workflowInstanceId, "流程实例编号");
        return jdbc.update("""
                        UPDATE arch_network_workflow_round
                        SET workflow_definition_id = ?, workflow_version_id = ?, workflow_instance_id = ?,
                            payload_digest = ?, status = 'STARTED', started_at = ?
                        WHERE tenant_id = ? AND work_order_id = ? AND round_no = ? AND status = 'PENDING'
                        """, workflowDefinitionId, workflowVersionId, workflowInstanceId, payloadDigest,
                timestamp(startedAt), tenantId, workOrderId, roundNo) == 1;
    }

    public boolean completeStartedWorkflowRound(long tenantId, long workOrderId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        requireTransaction();
        Objects.requireNonNull(nextStatus, "轮次目标状态不能为空");
        return jdbc.update("""
                        UPDATE arch_network_workflow_round
                        SET status = ?, ended_at = ?
                        WHERE tenant_id = ? AND work_order_id = ? AND round_no = ? AND status = 'STARTED'
                        """, nextStatus.name(), timestamp(endedAt), tenantId, workOrderId, roundNo) == 1;
    }

    /** 占位回执以 FAILED 写入；仅当前事务创建的占位回执可以写入最终处理结论。 */
    public boolean beginReceipt(WorkflowReceiptStart receipt) {
        requireTransaction();
        Objects.requireNonNull(receipt, "工作流回执不能为空");
        requirePositive(receipt.id(), "工作流回执编号");
        requirePositive(receipt.tenantId(), "租户编号");
        requireNonBlank(receipt.eventId(), "事件编号");
        requireNonBlank(receipt.subscriberKey(), "订阅方标识");
        requireNonBlank(receipt.eventType(), "事件类型");
        requirePositive(receipt.workOrderId(), "工单编号");
        requirePositive(receipt.roundNo(), "工作流轮次");
        requirePositive(receipt.workflowInstanceId(), "工作流实例编号");
        return jdbc.update("""
                        INSERT IGNORE INTO arch_network_workflow_receipt
                            (id, tenant_id, event_id, subscriber_key, work_order_id, round_no,
                             workflow_instance_id, event_type, processing_status, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, receipt.id(), receipt.tenantId(), receipt.eventId(), receipt.subscriberKey(),
                receipt.workOrderId(), receipt.roundNo(), receipt.workflowInstanceId(), receipt.eventType(),
                WorkflowReceiptStatus.FAILED.name(), "事务内事件尚未完成") == 1;
    }

    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        Objects.requireNonNull(status, "回执状态不能为空");
        return jdbc.update("""
                        UPDATE arch_network_workflow_receipt
                        SET processing_status = ?, detail = ?, processed_at = CURRENT_TIMESTAMP
                        WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ?
                          AND processing_status = 'FAILED'
                        """, status.name(), detail, tenantId, eventId, subscriberKey) == 1;
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        return jdbc.query("""
                        SELECT id, tenant_id, event_id, subscriber_key, work_order_id, round_no,
                               workflow_instance_id, event_type, processing_status, detail,
                               received_at, processed_at
                        FROM arch_network_workflow_receipt
                        WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ?
                        """, WORKFLOW_RECEIPT_MAPPER, tenantId, eventId, subscriberKey).stream().findFirst();
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("网络工单持久化必须在事务内执行");
        }
    }

    private static void requirePositive(long value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " 必须为正数");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static WorkOrderStatus nullableStatus(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : WorkOrderStatus.fromDatabase(value);
    }

    private static HandlingResultStatus nullableResultStatus(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : HandlingResultStatus.fromDatabase(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
