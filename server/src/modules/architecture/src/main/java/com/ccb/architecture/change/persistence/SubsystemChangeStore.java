package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemChangeModels.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** V82 发布主记录的生命周期读取和加锁边界。 */
@Repository
public class SubsystemChangeStore {
        private final SubsystemChangeMapper mapper;

    public SubsystemChangeStore(SubsystemChangeMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper 不能为空");
    }

    /** 创建工单时按调用方提供的 V82 状态字段写入，不解释状态迁移。 */
    public void insertApplication(ChangeApplication application) {
        requireTransaction();
        Objects.requireNonNull(application, "application 不能为空");
        mapper.insertApplication(params("a", application));
    }

    public Optional<ChangeApplication> findApplication(long tenantId, long applicationId) {
        return Optional.ofNullable(mapper.findApplication(params("tenantId", tenantId, "applicationId", applicationId)));
    }

    /** applicantId/status 为空时不附加对应筛选，仍始终由 tenantId 隔离。 */
    public List<ChangeApplication> listApplications(long tenantId, Long applicantId,
                                                    ApplicationStatus status, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return mapper.listApplications(params("tenantId", tenantId, "applicantId", applicantId,
                "status", status == null ? null : status.name(), "limit", limit, "offset", offset));
    }

    public Optional<ChangeApplication> lockApplication(long tenantId, long applicationId) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockApplication(params("tenantId", tenantId, "applicationId", applicationId)));
    }

    /** 仅以状态和行版本作为 CAS 条件；允许的状态图由 service 决定。 */
    public boolean compareAndSetApplicationStatus(long tenantId, long applicationId,
                                                  ApplicationStatus expectedStatus, long expectedRowVersion,
                                                  ApplicationStatus nextStatus, long updatedBy) {
        requireTransaction();
        Objects.requireNonNull(expectedStatus, "expectedStatus 不能为空");
        Objects.requireNonNull(nextStatus, "nextStatus 不能为空");
        return mapper.compareAndSetApplicationStatus(params("tenantId", tenantId, "applicationId", applicationId,
                "expectedStatus", expectedStatus.name(), "expectedRowVersion", expectedRowVersion,
                "nextStatus", nextStatus.name(), "updatedBy", updatedBy)) == 1;
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
        return mapper.compareAndSetApplicationReason(params("tenantId", tenantId, "applicationId", applicationId,
                "expectedStatus", expectedStatus.name(), "expectedRowVersion", expectedRowVersion,
                "reason", reason, "updatedBy", updatedBy)) == 1;
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
        return mapper.compareAndSetApplicationWorkflowContext(params("tenantId", tenantId, "applicationId", applicationId,
                "expectedCurrentBusinessRound", expectedCurrentBusinessRound, "expectedRowVersion", expectedRowVersion,
                "nextBusinessRound", nextBusinessRound, "workflowDefinitionId", workflowDefinitionId,
                "workflowVersionId", workflowVersionId, "workflowInstanceId", workflowInstanceId,
                "payloadDigest", payloadDigest, "updatedBy", updatedBy)) == 1;
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
        return mapper.compareAndSetCancellationRequested(params("tenantId", tenantId, "applicationId", applicationId,
                "expectedRowVersion", expectedRowVersion, "expectedInstanceId", expectedInstanceId, "actorId", actorId)) == 1;
    }

    /** 整批替换物理草稿；子系统变更申请退役逻辑草稿后始终以物理草稿为业务明细。 */
    public void replacePhysicalDrafts(long tenantId, long applicationId, List<PhysicalDraft> drafts) {
        requireTransaction();
        Objects.requireNonNull(drafts, "drafts 不能为空");
        for (PhysicalDraft draft : drafts) {
            if (draft == null || draft.tenantId() != tenantId || draft.applicationId() != applicationId) {
                throw new IllegalArgumentException("物理草稿与目标租户或申请不一致");
            }
        }
        mapper.deletePhysicalDrafts(params("tenantId", tenantId, "applicationId", applicationId));
        for (PhysicalDraft draft : drafts) {
            insertPhysicalDraft(draft);
        }
    }

    public List<PhysicalDraft> findPhysicalDrafts(long tenantId, long applicationId) {
        return mapper.findPhysicalDrafts(params("tenantId", tenantId, "applicationId", applicationId));
    }

    public void insertHistory(ChangeHistoryEvent event) {
        requireTransaction();
        Objects.requireNonNull(event, "event 不能为空");
        mapper.insertHistory(params("e", event));
    }

    /** occurred_at 相同的事件按 id 升序返回，避免数据库时间精度造成非稳定顺序。 */
    public List<ChangeHistoryEvent> listHistory(long tenantId, long applicationId) {
        return mapper.listHistory(params("tenantId", tenantId, "applicationId", applicationId));
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
        mapper.insertPendingWorkflowRound(params("r", round));
    }

    public Optional<WorkflowRound> findWorkflowRound(long tenantId, long applicationId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return Optional.ofNullable(mapper.findWorkflowRound(params("tenantId", tenantId, "applicationId", applicationId, "roundNo", roundNo)));
    }

    /** 生命周期消费者按业务键和轮次加锁，避免同一轮次并发完成。 */
    public Optional<WorkflowRound> lockWorkflowRound(long tenantId, long applicationId, int roundNo) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        return Optional.ofNullable(mapper.lockWorkflowRound(params("tenantId", tenantId, "applicationId", applicationId, "roundNo", roundNo)));
    }

    /** 生命周期消费者按平台实例加锁，tenantId 始终是查询条件的一部分。 */
    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(workflowInstanceId, "工作流实例编号");
        return Optional.ofNullable(mapper.lockWorkflowRoundByInstance(params("tenantId", tenantId, "workflowInstanceId", workflowInstanceId)));
    }

    /** 轮次号存在且没有更高轮次时返回 true；该只读判断由调用方的 application/round 锁配合使用。 */
    public boolean isLatestWorkflowRound(long tenantId, long applicationId, int roundNo) {
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requirePositive(roundNo, "工作流轮次");
        Long count = mapper.countLatestWorkflowRound(params("tenantId", tenantId, "applicationId", applicationId, "roundNo", roundNo));
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
        return mapper.bindWorkflowRoundStarted(params("tenantId", tenantId, "applicationId", applicationId, "roundNo", roundNo,
                "workflowDefinitionId", workflowDefinitionId, "workflowVersionId", workflowVersionId,
                "workflowInstanceId", workflowInstanceId, "payloadDigest", payloadDigest, "startedAt", startedAt)) == 1;
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
        return mapper.completeStartedWorkflowRound(params("tenantId", tenantId, "applicationId", applicationId, "roundNo", roundNo,
                "status", nextStatus.name(), "endedAt", endedAt)) == 1;
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
        return mapper.beginReceipt(params("r", receipt)) == 1;
    }

    /** 仅当前事务创建的 FAILED 占位回执可以写入最终处理结论。 */
    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        Objects.requireNonNull(status, "回执状态不能为空");
        return mapper.completeReceipt(params("tenantId", tenantId, "eventId", eventId, "subscriberKey", subscriberKey,
                "status", status.name(), "detail", detail)) == 1;
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        requirePositive(tenantId, "租户编号");
        requireNonBlank(eventId, "事件编号");
        requireNonBlank(subscriberKey, "订阅方标识");
        return Optional.ofNullable(mapper.findReceipt(params("tenantId", tenantId, "eventId", eventId, "subscriberKey", subscriberKey)));
    }

    public void insertTargetLock(TargetLock lock) {
        requireTransaction();
        Objects.requireNonNull(lock, "lock 不能为空");
        mapper.insertTargetLock(params("l", lock));
    }

    public Optional<TargetLock> findTargetLock(long tenantId, TargetKind targetKind, long targetId) {
        Objects.requireNonNull(targetKind, "targetKind 不能为空");
        return Optional.ofNullable(mapper.findTargetLock(params("tenantId", tenantId, "targetKind", targetKind.name(), "targetId", targetId)));
    }

    public void deleteTargetLock(long tenantId, TargetKind targetKind, long targetId, long applicationId) {
        requireTransaction();
        Objects.requireNonNull(targetKind, "targetKind 不能为空");
        mapper.deleteTargetLock(params("tenantId", tenantId, "targetKind", targetKind.name(), "targetId", targetId, "applicationId", applicationId));
    }

    public void insertValueReservation(ValueReservation reservation) {
        requireTransaction();
        Objects.requireNonNull(reservation, "reservation 不能为空");
        mapper.insertValueReservation(params("r", reservation));
    }

    public Optional<ValueReservation> findValueReservation(long tenantId, String reservationScope,
                                                           String normalizedValue) {
        return Optional.ofNullable(mapper.findValueReservation(params("tenantId", tenantId, "reservationScope", reservationScope, "normalizedValue", normalizedValue)));
    }

    public void deleteValueReservations(long tenantId, long applicationId) {
        requireTransaction();
        mapper.deleteValueReservations(params("tenantId", tenantId, "applicationId", applicationId));
    }

    public void insertPhysicalReplacement(PhysicalReplacement replacement) {
        requireTransaction();
        Objects.requireNonNull(replacement, "replacement 不能为空");
        mapper.insertPhysicalReplacement(params("r", replacement));
    }

    public Optional<PhysicalReplacement> findPhysicalReplacementByApplication(long tenantId, long applicationId) {
        return Optional.ofNullable(mapper.findPhysicalReplacementByApplication(params("tenantId", tenantId, "applicationId", applicationId)));
    }

    public boolean physicalCodeExists(long tenantId, String code, Long excludeId) {
        return exists("code", tenantId, code, excludeId);
    }

    public boolean physicalNameExists(long tenantId, String name, Long excludeId) {
        return exists("name", tenantId, name, excludeId);
    }

    public boolean physicalEnglishNameExists(long tenantId, String englishName, Long excludeId) {
        return exists("english_name", tenantId, englishName, excludeId);
    }

    public Optional<PhysicalPublishedState> findPhysical(long tenantId, long id) {
        return physical(tenantId, id, false);
    }

    public Optional<PhysicalPublishedState> lockPhysical(long tenantId, long id) {
        requireTransaction();
        return physical(tenantId, id, true);
    }

    /** 从物理草稿发布新主记录；编号由申请人填写并写入草稿。 */
    public void insertPhysicalPublished(long id, long tenantId, PhysicalDraft draft, PublishedStatus status,
                                        long rowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        mapper.insertPhysicalPublished(params("id", id, "tenantId", tenantId, "d", draft,
                "status", status.name(), "rowVersion", rowVersion, "actorId", actorId));
    }

    /** 普通字段更新不触及物理记录编号、状态和行版本。 */
    public boolean updatePhysicalPublishedFields(long tenantId, long id, PhysicalDraft draft,
                                                 long expectedRowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(draft, "draft 不能为空");
        return mapper.updatePhysicalPublishedFields(params("tenantId", tenantId, "id", id, "d", draft,
                "expectedRowVersion", expectedRowVersion, "actorId", actorId)) == 1;
    }

    public boolean updatePhysicalPublishedStatus(long tenantId, long id, PublishedStatus status,
                                                 long expectedRowVersion, long actorId) {
        requireTransaction();
        Objects.requireNonNull(status, "status 不能为空");
        return mapper.updatePhysicalPublishedStatus(params("tenantId", tenantId, "id", id,
                "status", status.name(), "expectedRowVersion", expectedRowVersion, "actorId", actorId)) == 1;
    }

    private Optional<PhysicalPublishedState> physical(long tenantId, long id, boolean forUpdate) {
        return Optional.ofNullable(forUpdate
                ? mapper.lockPhysical(params("tenantId", tenantId, "id", id))
                : mapper.findPhysical(params("tenantId", tenantId, "id", id)));
    }

    private void insertPhysicalDraft(PhysicalDraft draft) {
        mapper.insertPhysicalDraft(params("d", draft));
    }

    private boolean exists(String column, long tenantId, String value, Long excludeId) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Map<String, Object> p = params("tenantId", tenantId, "value", value, "excludeId", excludeId);
        Long count = switch (column) {
            case "code" -> mapper.countPhysicalByCode(p);
            case "name" -> mapper.countPhysicalByName(p);
            case "english_name" -> mapper.countPhysicalByEnglishName(p);
            default -> throw new IllegalArgumentException("不支持的物理子系统字段: " + column);
        };
        return count != null && count > 0;
    }

    private static Map<String, Object> params(Object... entries) {
        Map<String, Object> result = new java.util.HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
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
