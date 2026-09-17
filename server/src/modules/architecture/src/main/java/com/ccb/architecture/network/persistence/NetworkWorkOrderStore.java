package com.ccb.architecture.network.persistence;

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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Service-facing network-work-order store; SQL ownership is delegated to the MyBatis repository. */
@Repository
public class NetworkWorkOrderStore {
    private final NetworkWorkOrderRepository repository;

    public NetworkWorkOrderStore(NetworkWorkOrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "NetworkWorkOrderRepository 不能为空");
    }

    public void insertWorkOrder(WorkOrder workOrder) {
        requireTransaction();
        repository.insertWorkOrder(Objects.requireNonNull(workOrder, "工单不能为空"));
    }

    public Optional<WorkOrder> findWorkOrder(long tenantId, long workOrderId) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        return repository.findWorkOrder(tenantId, workOrderId);
    }

    public Optional<WorkOrder> lockWorkOrder(long tenantId, long workOrderId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        return repository.lockWorkOrder(tenantId, workOrderId);
    }

    /** applicantId/kind/status 为空时不附加对应筛选，仍始终由 tenantId 隔离。 */
    public List<WorkOrder> listWorkOrders(long tenantId, Long applicantId, Kind kind,
                                          WorkOrderStatus status, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return repository.listWorkOrders(tenantId, applicantId, kind, status, limit, offset);
    }

    /** 草稿编辑：仅 DRAFT/RETURNED 且行版本匹配时更新载荷、附件与原因。 */
    public boolean updateDraft(long tenantId, long workOrderId, WorkOrderStatus expectedStatus,
                               long expectedRowVersion, String reason, String payload,
                               String attachmentIds, long updatedBy) {
        requireTransaction();
        return repository.updateDraft(tenantId, workOrderId, Objects.requireNonNull(expectedStatus, "期望状态不能为空"),
                expectedRowVersion, reason, payload, attachmentIds, updatedBy);
    }

    /** 仅以状态和行版本作为 CAS 条件；允许的状态图由 service 决定。 */
    public boolean compareAndSetStatus(long tenantId, long workOrderId,
                                       WorkOrderStatus expectedStatus, long expectedRowVersion,
                                       WorkOrderStatus nextStatus, long updatedBy) {
        requireTransaction();
        return repository.compareAndSetStatus(tenantId, workOrderId,
                Objects.requireNonNull(expectedStatus, "期望状态不能为空"), expectedRowVersion,
                Objects.requireNonNull(nextStatus, "目标状态不能为空"), updatedBy);
    }

    /** 提交启动成功后原子写入当前轮次和工作流上下文。 */
    public boolean compareAndSetWorkflowContext(long tenantId, long workOrderId,
                                                int expectedCurrentBusinessRound,
                                                long expectedRowVersion, int nextBusinessRound,
                                                long workflowDefinitionId, long workflowVersionId,
                                                long workflowInstanceId, String payloadDigest,
                                                long updatedBy) {
        requireTransaction();
        return repository.compareAndSetWorkflowContext(tenantId, workOrderId, expectedCurrentBusinessRound,
                expectedRowVersion, nextBusinessRound, workflowDefinitionId, workflowVersionId,
                workflowInstanceId, payloadDigest, updatedBy);
    }

    public boolean compareAndSetCancellationRequested(long tenantId, long workOrderId,
                                                      long expectedRowVersion, boolean requested,
                                                      long updatedBy) {
        requireTransaction();
        return repository.compareAndSetCancellationRequested(tenantId, workOrderId, expectedRowVersion, requested, updatedBy);
    }

    /** 办理结果登记：IN_REVIEW 或 COMPLETED 且行版本匹配时写入，不改变工单状态。 */
    public boolean updateHandlingResult(long tenantId, long workOrderId, long expectedRowVersion,
                                        String resultStatus, String resultDescription,
                                        String resultAttachmentIds, long registeredBy) {
        requireTransaction();
        return repository.updateHandlingResult(tenantId, workOrderId, expectedRowVersion, resultStatus,
                resultDescription, resultAttachmentIds, registeredBy);
    }

    public void insertHistory(HistoryEvent event) {
        requireTransaction();
        repository.insertHistory(Objects.requireNonNull(event, "历史事件不能为空"));
    }

    /** occurred_at 相同的事件按 id 升序返回，避免数据库时间精度造成非稳定顺序。 */
    public List<HistoryEvent> listHistory(long tenantId, long workOrderId) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        return repository.listHistory(tenantId, workOrderId);
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
        repository.insertPendingWorkflowRound(round);
    }

    public Optional<WorkflowRound> findWorkflowRound(long tenantId, long workOrderId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return repository.findWorkflowRound(tenantId, workOrderId, roundNo);
    }

    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workflowInstanceId, "工作流实例编号");
        return repository.lockWorkflowRoundByInstance(tenantId, workflowInstanceId);
    }

    public boolean isLatestWorkflowRound(long tenantId, long workOrderId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(workOrderId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return repository.isLatestWorkflowRound(tenantId, workOrderId, roundNo);
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
        return repository.bindWorkflowRoundStarted(tenantId, workOrderId, roundNo, workflowDefinitionId,
                workflowVersionId, workflowInstanceId, payloadDigest, startedAt);
    }

    public boolean completeStartedWorkflowRound(long tenantId, long workOrderId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        requireTransaction();
        return repository.completeStartedWorkflowRound(tenantId, workOrderId, roundNo,
                Objects.requireNonNull(nextStatus, "轮次目标状态不能为空"), endedAt);
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
        return repository.beginReceipt(receipt);
    }

    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        return repository.completeReceipt(tenantId, eventId, subscriberKey,
                Objects.requireNonNull(status, "回执状态不能为空"), detail);
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        return repository.findReceipt(tenantId, eventId, subscriberKey);
    }

    private static void requireTransaction() {
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
}
