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
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed network-work-order persistence boundary backed exclusively by MyBatis XML. */
@Repository
public class NetworkWorkOrderRepository {
    private final NetworkWorkOrderMapper mapper;

    public NetworkWorkOrderRepository(NetworkWorkOrderMapper mapper) {
        this.mapper = mapper;
    }

    void insertWorkOrder(WorkOrder value) { mapper.insertWorkOrder(workOrderParams(value)); }
    Optional<WorkOrder> findWorkOrder(long tenantId, long workOrderId) { return Optional.ofNullable(mapper.findWorkOrder(p("tenantId", tenantId, "workOrderId", workOrderId))).map(this::workOrder); }
    Optional<WorkOrder> lockWorkOrder(long tenantId, long workOrderId) { return Optional.ofNullable(mapper.lockWorkOrder(p("tenantId", tenantId, "workOrderId", workOrderId))).map(this::workOrder); }
    List<WorkOrder> listWorkOrders(long tenantId, Long applicantId, Kind kind, WorkOrderStatus status, int limit, int offset) {
        return mapper.listWorkOrders(p("tenantId", tenantId, "applicantId", applicantId,
                "kind", kind == null ? null : kind.name(), "status", status == null ? null : status.name(),
                "limit", limit, "offset", offset)).stream().map(this::workOrder).toList();
    }
    boolean updateDraft(long tenantId, long workOrderId, WorkOrderStatus expectedStatus, long expectedRowVersion,
                        String reason, String payload, String attachmentIds, long updatedBy) {
        return mapper.updateDraft(p("tenantId", tenantId, "workOrderId", workOrderId, "expectedStatus", expectedStatus.name(),
                "expectedRowVersion", expectedRowVersion, "reason", reason, "payload", payload,
                "attachmentIds", attachmentIds, "updatedBy", updatedBy)) == 1;
    }
    boolean compareAndSetStatus(long tenantId, long workOrderId, WorkOrderStatus expectedStatus,
                                long expectedRowVersion, WorkOrderStatus nextStatus, long updatedBy) {
        return mapper.compareAndSetStatus(p("tenantId", tenantId, "workOrderId", workOrderId,
                "expectedStatus", expectedStatus.name(), "expectedRowVersion", expectedRowVersion,
                "nextStatus", nextStatus.name(), "updatedBy", updatedBy)) == 1;
    }
    boolean compareAndSetWorkflowContext(long tenantId, long workOrderId, int expectedCurrentBusinessRound,
                                         long expectedRowVersion, int nextBusinessRound, long workflowDefinitionId,
                                         long workflowVersionId, long workflowInstanceId, String payloadDigest, long updatedBy) {
        return mapper.compareAndSetWorkflowContext(p("tenantId", tenantId, "workOrderId", workOrderId,
                "expectedCurrentBusinessRound", expectedCurrentBusinessRound, "expectedRowVersion", expectedRowVersion,
                "nextBusinessRound", nextBusinessRound, "workflowDefinitionId", workflowDefinitionId,
                "workflowVersionId", workflowVersionId, "workflowInstanceId", workflowInstanceId,
                "payloadDigest", payloadDigest, "updatedBy", updatedBy)) == 1;
    }
    boolean compareAndSetCancellationRequested(long tenantId, long workOrderId, long expectedRowVersion,
                                                boolean requested, long updatedBy) {
        return mapper.compareAndSetCancellationRequested(p("tenantId", tenantId, "workOrderId", workOrderId,
                "expectedRowVersion", expectedRowVersion, "requested", requested, "updatedBy", updatedBy)) == 1;
    }
    boolean updateHandlingResult(long tenantId, long workOrderId, long expectedRowVersion, String resultStatus,
                                 String resultDescription, String resultAttachmentIds, long registeredBy) {
        return mapper.updateHandlingResult(p("tenantId", tenantId, "workOrderId", workOrderId,
                "expectedRowVersion", expectedRowVersion, "resultStatus", resultStatus,
                "resultDescription", resultDescription, "resultAttachmentIds", resultAttachmentIds,
                "registeredBy", registeredBy)) == 1;
    }
    void insertHistory(HistoryEvent value) {
        mapper.insertHistory(p("id", value.id(), "tenantId", value.tenantId(), "workOrderId", value.workOrderId(),
                "eventType", value.eventType(), "fromStatus", value.fromStatus() == null ? null : value.fromStatus().name(),
                "toStatus", value.toStatus() == null ? null : value.toStatus().name(), "businessRound", value.businessRound(),
                "summary", value.summary(), "snapshotJson", value.snapshotJson(), "diffJson", value.diffJson(),
                "operatorId", value.operatorId(), "occurredAt", value.occurredAt()));
    }
    List<HistoryEvent> listHistory(long tenantId, long workOrderId) { return mapper.listHistory(p("tenantId", tenantId, "workOrderId", workOrderId)).stream().map(this::history).toList(); }
    void insertPendingWorkflowRound(WorkflowRound value) { mapper.insertPendingWorkflowRound(p("id", value.id(), "tenantId", value.tenantId(), "workOrderId", value.workOrderId(), "roundNo", value.roundNo())); }
    Optional<WorkflowRound> findWorkflowRound(long tenantId, long workOrderId, int roundNo) { return Optional.ofNullable(mapper.findWorkflowRound(p("tenantId", tenantId, "workOrderId", workOrderId, "roundNo", roundNo))).map(this::workflowRound); }
    Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) { return Optional.ofNullable(mapper.lockWorkflowRoundByInstance(p("tenantId", tenantId, "workflowInstanceId", workflowInstanceId))).map(this::workflowRound); }
    boolean isLatestWorkflowRound(long tenantId, long workOrderId, int roundNo) { Integer latest = mapper.maxWorkflowRoundNo(p("tenantId", tenantId, "workOrderId", workOrderId)); return latest != null && latest == roundNo; }
    boolean bindWorkflowRoundStarted(long tenantId, long workOrderId, int roundNo, long workflowDefinitionId,
                                     long workflowVersionId, long workflowInstanceId, String payloadDigest, LocalDateTime startedAt) {
        return mapper.bindWorkflowRoundStarted(p("tenantId", tenantId, "workOrderId", workOrderId, "roundNo", roundNo,
                "workflowDefinitionId", workflowDefinitionId, "workflowVersionId", workflowVersionId,
                "workflowInstanceId", workflowInstanceId, "payloadDigest", payloadDigest, "startedAt", startedAt)) == 1;
    }
    boolean completeStartedWorkflowRound(long tenantId, long workOrderId, int roundNo,
                                         WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        return mapper.completeStartedWorkflowRound(p("tenantId", tenantId, "workOrderId", workOrderId,
                "roundNo", roundNo, "nextStatus", nextStatus.name(), "endedAt", endedAt)) == 1;
    }
    boolean beginReceipt(WorkflowReceiptStart value) {
        return mapper.beginReceipt(p("id", value.id(), "tenantId", value.tenantId(), "eventId", value.eventId(),
                "subscriberKey", value.subscriberKey(), "workOrderId", value.workOrderId(), "roundNo", value.roundNo(),
                "workflowInstanceId", value.workflowInstanceId(), "eventType", value.eventType())) == 1;
    }
    boolean completeReceipt(long tenantId, String eventId, String subscriberKey, WorkflowReceiptStatus status, String detail) {
        return mapper.completeReceipt(p("tenantId", tenantId, "eventId", eventId, "subscriberKey", subscriberKey,
                "status", status.name(), "detail", detail)) == 1;
    }
    Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) { return Optional.ofNullable(mapper.findReceipt(p("tenantId", tenantId, "eventId", eventId, "subscriberKey", subscriberKey))).map(this::receipt); }

    private Map<String, Object> workOrderParams(WorkOrder value) {
        return p("id", value.id(), "tenantId", value.tenantId(), "kind", value.kind().name(), "actionType", value.actionType().name(),
                "subject", value.subject(), "applicantId", value.applicantId(), "reason", value.reason(), "status", value.status().name(),
                "payload", value.payload(), "attachmentIds", value.attachmentIds(),
                "resultStatus", value.resultStatus() == null ? null : value.resultStatus().name(),
                "resultDescription", value.resultDescription(), "resultAttachmentIds", value.resultAttachmentIds(),
                "resultRegisteredBy", value.resultRegisteredBy(), "resultRegisteredAt", value.resultRegisteredAt(),
                "currentBusinessRound", value.currentBusinessRound(), "currentWorkflowDefinitionId", value.currentWorkflowDefinitionId(),
                "currentWorkflowVersionId", value.currentWorkflowVersionId(), "currentWorkflowInstanceId", value.currentWorkflowInstanceId(),
                "currentPayloadDigest", value.currentPayloadDigest(), "cancellationRequested", value.cancellationRequested(),
                "rowVersion", value.rowVersion(), "createdBy", value.createdBy(), "updatedBy", value.updatedBy());
    }
    private WorkOrder workOrder(Map<String, Object> row) {
        return new WorkOrder(longValue(row, "id"), longValue(row, "tenant_id"), Kind.fromDatabase(string(row, "kind")),
                ActionType.fromDatabase(string(row, "action_type")), string(row, "subject"), longValue(row, "applicant_id"),
                string(row, "reason"), WorkOrderStatus.fromDatabase(string(row, "status")), string(row, "business_payload"),
                string(row, "attachment_ids"), nullableEnum(row, "result_status", HandlingResultStatus.class),
                string(row, "result_description"), string(row, "result_attachment_ids"), nullableLong(row, "result_registered_by"),
                dateTime(value(row, "result_registered_at")), integer(row, "current_business_round"),
                nullableLong(row, "current_workflow_definition_id"), nullableLong(row, "current_workflow_version_id"),
                nullableLong(row, "current_workflow_instance_id"), string(row, "current_payload_digest"), booleanValue(row, "cancellation_requested"),
                longValue(row, "row_version"), longValue(row, "created_by"), longValue(row, "updated_by"),
                dateTime(value(row, "created_at")), dateTime(value(row, "updated_at")));
    }
    private HistoryEvent history(Map<String, Object> row) {
        return new HistoryEvent(longValue(row, "id"), longValue(row, "tenant_id"), longValue(row, "work_order_id"),
                string(row, "event_type"), nullableEnum(row, "from_status", WorkOrderStatus.class),
                nullableEnum(row, "to_status", WorkOrderStatus.class), integer(row, "business_round"), string(row, "summary"),
                string(row, "snapshot_json"), string(row, "diff_json"), longValue(row, "operator_id"), dateTime(value(row, "occurred_at")));
    }
    private WorkflowRound workflowRound(Map<String, Object> row) {
        return new WorkflowRound(longValue(row, "id"), longValue(row, "tenant_id"), longValue(row, "work_order_id"),
                integer(row, "round_no"), nullableLong(row, "workflow_definition_id"), nullableLong(row, "workflow_version_id"),
                nullableLong(row, "workflow_instance_id"), string(row, "payload_digest"),
                WorkflowRoundStatus.fromDatabase(string(row, "status")), dateTime(value(row, "started_at")),
                dateTime(value(row, "ended_at")), dateTime(value(row, "created_at")), dateTime(value(row, "updated_at")));
    }
    private WorkflowReceipt receipt(Map<String, Object> row) {
        return new WorkflowReceipt(longValue(row, "id"), longValue(row, "tenant_id"), string(row, "event_id"),
                string(row, "subscriber_key"), nullableLong(row, "work_order_id"), nullableInteger(row, "round_no"),
                nullableLong(row, "workflow_instance_id"), string(row, "event_type"),
                WorkflowReceiptStatus.fromDatabase(string(row, "processing_status")), string(row, "detail"),
                dateTime(value(row, "received_at")), dateTime(value(row, "processed_at")));
    }
    private static Map<String, Object> p(Object... values) { Map<String, Object> result = new HashMap<>(); for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result; }
    private static Object value(Map<String, Object> row, String name) { Object result = row.get(name); return result == null ? row.get(toCamel(name)) : result; }
    private static String string(Map<String, Object> row, String name) { Object result = value(row, name); return result == null ? null : String.valueOf(result); }
    private static long longValue(Map<String, Object> row, String name) { return ((Number) value(row, name)).longValue(); }
    private static int integer(Map<String, Object> row, String name) { return ((Number) value(row, name)).intValue(); }
    private static Long nullableLong(Map<String, Object> row, String name) { Object result = value(row, name); return result == null ? null : ((Number) result).longValue(); }
    private static Integer nullableInteger(Map<String, Object> row, String name) { Object result = value(row, name); return result == null ? null : ((Number) result).intValue(); }
    private static boolean booleanValue(Map<String, Object> row, String name) { Object result = value(row, name); return result instanceof Boolean bool ? bool : ((Number) result).intValue() != 0; }
    private static LocalDateTime dateTime(Object result) { if (result == null) return null; if (result instanceof LocalDateTime local) return local; if (result instanceof Timestamp timestamp) return timestamp.toLocalDateTime(); throw new IllegalStateException("日期时间列格式错误"); }
    private static <E extends Enum<E>> E nullableEnum(Map<String, Object> row, String name, Class<E> type) { String result = string(row, name); return result == null ? null : Enum.valueOf(type, result); }
    private static String toCamel(String value) { StringBuilder result = new StringBuilder(); boolean upper = false; for (char character : value.toCharArray()) { if (character == '_') upper = true; else { result.append(upper ? Character.toUpperCase(character) : character); upper = false; } } return result.toString(); }
}
