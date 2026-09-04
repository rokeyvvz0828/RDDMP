package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalReplacement;
import com.ccb.architecture.change.model.SubsystemChangeModels.PublishedStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.integration.ReferenceCheckRequest;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 工作流批准事件的发布边界。
 *
 * <p>审批命令不携带任何业务字段，所有发布值均只从已提交物理草稿读取。逻辑子系统工单历史
 * 只允许停留在历史数据中，不能再被批准发布。</p>
 */
@Service
public class SubsystemPublicationService {
    private static final AtomicLong GENERATED_IDENTIFIERS = new AtomicLong(System.currentTimeMillis() * 1_000L);

    private final SubsystemChangeStore store;
    private final SubsystemReferenceGuard referenceGuard;
    private final TransactionTemplate transactions;
    private final LongSupplier identifierSupplier;

    @Autowired
    public SubsystemPublicationService(SubsystemChangeStore store,
                                       SubsystemReferenceGuard referenceGuard,
                                       TransactionTemplate transactions) {
        this(store, referenceGuard, transactions, SubsystemPublicationService::nextGeneratedIdentifier);
    }

    /** 仅供同包测试注入稳定标识；生产装配使用上方构造器。 */
    SubsystemPublicationService(SubsystemChangeStore store,
                                SubsystemReferenceGuard referenceGuard,
                                TransactionTemplate transactions,
                                LongSupplier identifierSupplier) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
        this.referenceGuard = Objects.requireNonNull(referenceGuard, "referenceGuard 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "transactions 不能为空");
        this.identifierSupplier = Objects.requireNonNull(identifierSupplier, "identifierSupplier 不能为空");
    }

    /**
     * 在同一真实事务中锁定申请、校验可信工作流上下文、标记 APPROVED，并发布主记录变更。
     */
    public ApprovalResult approve(ApprovalCommand command, AuthUser workflowOperator) {
        Objects.requireNonNull(command, "command 不能为空");
        requireOperator(workflowOperator);
        try {
            ApprovalResult result = transactions.execute(status -> approveInTransaction(command, workflowOperator));
            if (result == null) {
                throw new IllegalStateException("批准事务未返回发布结果");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            // 异常离开 TransactionTemplate 回调后才转换，确保事务已被标记并完成回滚。
            throw conflict("发布记录的唯一编号或名称已被占用");
        }
    }

    private ApprovalResult approveInTransaction(ApprovalCommand command, AuthUser operator) {
        ChangeApplication application = store.lockApplication(operator.tenantId(), command.applicationId())
                .orElseThrow(() -> conflict("变更申请不存在、跨租户或已被删除"));
        verifyApprovalContext(application, command);

        PreparedPublication publication = preparePhysicalPublication(application, operator);

        // CAS 在主记录写入前完成；其后任一步失败都会由整个事务回滚，不会留下 APPROVED 半状态。
        if (!store.compareAndSetApplicationStatus(application.tenantId(), application.id(),
                ApplicationStatus.IN_REVIEW, command.expectedApplicationRowVersion(),
                ApplicationStatus.APPROVED, operator.id())) {
            throw conflict("变更申请状态或版本已变化，请使用最新工作流事件重试");
        }

        publication.writer().run();
        if (application.targetId() != null) {
            store.deleteTargetLock(application.tenantId(), application.targetKind(), application.targetId(),
                    application.id());
        }
        store.deleteValueReservations(application.tenantId(), application.id());
        store.insertHistory(new ChangeHistoryEvent(
                nextIdentifier(), application.tenantId(), application.id(), "APPROVED_PUBLISHED",
                ApplicationStatus.IN_REVIEW, ApplicationStatus.APPROVED, application.currentBusinessRound(),
                safeHistorySummary(application), null, null, operator.id(), LocalDateTime.now()));

        return new ApprovalResult(application.id(), publication.physicalSubsystemIds());
    }

    private PreparedPublication preparePhysicalPublication(ChangeApplication application, AuthUser operator) {
        if (application.targetKind() != TargetKind.PHYSICAL) {
            throw conflict("逻辑子系统工单已退役，不能发布");
        }
        return switch (application.actionType()) {
            case CREATE -> preparePhysicalCreate(application, operator);
            case UPDATE -> preparePhysicalUpdate(application, operator);
            case OFFLINE -> preparePhysicalStatusChange(application, operator, PublishedStatus.ACTIVE,
                    PublishedStatus.OFFLINE, ReferenceCheckRequest.Operation.OFFLINE);
            case REACTIVATE -> preparePhysicalStatusChange(application, operator, PublishedStatus.OFFLINE,
                    PublishedStatus.ACTIVE, null);
            case VOID -> preparePhysicalVoid(application, operator);
            case REPLACE -> preparePhysicalReplace(application, operator);
        };
    }

    private PreparedPublication preparePhysicalCreate(ChangeApplication application, AuthUser operator) {
        requireCreateWithoutPublishedTarget(application);
        PhysicalDraft draft = singleSubmittedPhysicalDraft(application);
        requireCreateDraft(application, draft);
        ensurePermanentUnique(application, draft, null);
        long physicalSubsystemId = nextIdentifier();
        return new PreparedPublication(List.of(physicalSubsystemId), () ->
                store.insertPhysicalPublished(physicalSubsystemId, application.tenantId(), draft,
                        PublishedStatus.ACTIVE, 0L, operator.id()));
    }

    private PreparedPublication preparePhysicalUpdate(ChangeApplication application, AuthUser operator) {
        ExistingPhysicalContext context = lockExistingPhysical(application);
        requireMutableStatus(context.target().status(), "物理子系统");
        requireSameCode(context.draft(), context.target());
        ensurePermanentUnique(application, context.draft(), context.target().id());
        return new PreparedPublication(List.of(context.target().id()), () -> {
            if (!store.updatePhysicalPublishedFields(application.tenantId(), context.target().id(), context.draft(),
                    context.target().rowVersion(), operator.id())) {
                throw conflict("物理子系统源行版本已变化，字段更新失败");
            }
        });
    }

    private PreparedPublication preparePhysicalStatusChange(ChangeApplication application, AuthUser operator,
                                                            PublishedStatus requiredStatus,
                                                            PublishedStatus targetStatus,
                                                            ReferenceCheckRequest.Operation guardOperation) {
        ExistingPhysicalContext context = lockExistingPhysical(application);
        requireExactStatus(context.target().status(), requiredStatus, application.actionType(), "物理子系统");
        requireSameCode(context.draft(), context.target());
        if (guardOperation != null) {
            referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                    ReferenceCheckRequest.SubsystemKind.PHYSICAL, context.target().id(), guardOperation));
        }
        return new PreparedPublication(List.of(context.target().id()), () -> {
            if (!store.updatePhysicalPublishedStatus(application.tenantId(), context.target().id(), targetStatus,
                    context.target().rowVersion(), operator.id())) {
                throw conflict("物理子系统源行版本已变化，状态更新失败");
            }
        });
    }

    private PreparedPublication preparePhysicalVoid(ChangeApplication application, AuthUser operator) {
        ExistingPhysicalContext context = lockExistingPhysical(application);
        requireVoidableStatus(context.target().status(), "物理子系统");
        requireSameCode(context.draft(), context.target());
        referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                ReferenceCheckRequest.SubsystemKind.PHYSICAL, context.target().id(),
                ReferenceCheckRequest.Operation.VOID));
        return new PreparedPublication(List.of(context.target().id()), () -> {
            if (!store.updatePhysicalPublishedStatus(application.tenantId(), context.target().id(),
                    PublishedStatus.VOIDED, context.target().rowVersion(), operator.id())) {
                throw conflict("物理子系统源行版本已变化，作废失败");
            }
        });
    }

    private PreparedPublication preparePhysicalReplace(ChangeApplication application, AuthUser operator) {
        ExistingPhysicalContext context = lockExistingPhysical(application);
        requireExactStatus(context.target().status(), PublishedStatus.ACTIVE, ActionType.REPLACE, "物理子系统");
        referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                ReferenceCheckRequest.SubsystemKind.PHYSICAL, context.target().id(),
                ReferenceCheckRequest.Operation.OFFLINE));
        ensurePermanentUnique(application, context.draft(), null);

        long newPhysicalSubsystemId = nextIdentifier();
        long replacementId = nextIdentifier();
        return new PreparedPublication(List.of(newPhysicalSubsystemId), () -> {
            store.insertPhysicalPublished(newPhysicalSubsystemId, application.tenantId(), context.draft(),
                    PublishedStatus.ACTIVE, 0L, operator.id());
            if (!store.updatePhysicalPublishedStatus(application.tenantId(), context.target().id(),
                    PublishedStatus.OFFLINE, context.target().rowVersion(), operator.id())) {
                throw conflict("被替换物理子系统源行版本已变化，下线失败");
            }
            store.insertPhysicalReplacement(new PhysicalReplacement(replacementId, application.tenantId(),
                    context.target().id(), newPhysicalSubsystemId, application.id(), LocalDateTime.now()));
        });
    }

    private ExistingPhysicalContext lockExistingPhysical(ChangeApplication application) {
        long targetId = requireExistingTarget(application);
        verifyOwnedTargetLock(application, targetId);
        PhysicalDraft draft = singleSubmittedPhysicalDraft(application);
        PhysicalPublishedState target = store.lockPhysical(application.tenantId(), targetId)
                .orElseThrow(() -> conflict("物理子系统源记录不存在或跨租户"));
        if (target.tenantId() != application.tenantId() || target.id() != targetId || target.deleted()) {
            throw conflict("物理子系统源记录租户、编号或删除状态无效");
        }
        if (draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || draft.lineNo() <= 0 || !Objects.equals(draft.sourcePhysicalSubsystemId(), targetId)
                || draft.sourceRowVersion() == null || draft.sourceRowVersion() != target.rowVersion()) {
            throw conflict("物理草稿的申请、租户、源编号或源行版本与发布记录不一致");
        }
        return new ExistingPhysicalContext(draft, target);
    }

    private PhysicalDraft singleSubmittedPhysicalDraft(ChangeApplication application) {
        List<PhysicalDraft> drafts = store.findPhysicalDrafts(application.tenantId(), application.id());
        if (drafts.size() != 1) {
            throw conflict("物理变更申请必须且只能包含一条已提交物理草稿");
        }
        PhysicalDraft draft = drafts.get(0);
        if (isBlank(draft.submittedSnapshotJson())) {
            throw conflict("物理草稿缺少已提交快照");
        }
        return draft;
    }

    private void requireCreateDraft(ChangeApplication application, PhysicalDraft draft) {
        if (draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || draft.lineNo() <= 0 || draft.sourcePhysicalSubsystemId() != null
                || draft.sourceRowVersion() != null || isBlank(draft.code())) {
            throw conflict("物理新增草稿的申请、租户、来源、版本或编号无效");
        }
    }

    private long requireExistingTarget(ChangeApplication application) {
        if (application.targetId() == null || application.targetId() <= 0) {
            throw conflict("非新增申请必须指向有效的已发布物理子系统");
        }
        return application.targetId();
    }

    private void verifyOwnedTargetLock(ChangeApplication application, long targetId) {
        TargetLock targetLock = store.findTargetLock(application.tenantId(), application.targetKind(), targetId)
                .orElseThrow(() -> conflict("变更申请未持有目标排他锁"));
        if (targetLock.tenantId() != application.tenantId() || targetLock.targetKind() != application.targetKind()
                || targetLock.targetId() != targetId || targetLock.applicationId() != application.id()) {
            throw conflict("目标排他锁不属于当前变更申请");
        }
    }

    private void requireMutableStatus(PublishedStatus status, String label) {
        if (status == PublishedStatus.VOIDED) {
            throw conflict(label + "已作废，不允许再修改");
        }
    }

    private void requireExactStatus(PublishedStatus actual, PublishedStatus expected,
                                    ActionType actionType, String label) {
        if (actual != expected) {
            throw conflict(label + "执行 " + actionType + " 时必须处于 " + expected + " 状态");
        }
    }

    private void requireVoidableStatus(PublishedStatus status, String label) {
        if (status != PublishedStatus.ACTIVE && status != PublishedStatus.OFFLINE) {
            throw conflict(label + "只有 ACTIVE 或 OFFLINE 状态可以作废");
        }
    }

    private void requireSameCode(PhysicalDraft draft, PhysicalPublishedState target) {
        if (!Objects.equals(draft.code(), target.code())) {
            throw conflict("普通物理子系统工单不得修改系统编号；如需替换编号请使用 REPLACE");
        }
    }

    private void ensurePermanentUnique(ChangeApplication application, PhysicalDraft draft, Long excludeId) {
        if (store.physicalCodeExists(application.tenantId(), draft.code(), excludeId)) {
            throw conflict("物理子系统编号已存在，删除后的编号也不能复用");
        }
        if (store.physicalNameExists(application.tenantId(), draft.name(), excludeId)) {
            throw conflict("物理子系统名称已存在，删除后的名称也不能复用");
        }
        if (draft.englishName() != null
                && store.physicalEnglishNameExists(application.tenantId(), draft.englishName(), excludeId)) {
            throw conflict("物理子系统英文名称已存在，删除后的英文名称也不能复用");
        }
    }

    private void verifyApprovalContext(ChangeApplication application, ApprovalCommand command) {
        if (application.status() != ApplicationStatus.IN_REVIEW) {
            throw conflict("只有 IN_REVIEW 申请可以批准发布");
        }
        if (application.currentBusinessRound() != command.expectedBusinessRound()
                || application.rowVersion() != command.expectedApplicationRowVersion()
                || !Objects.equals(application.currentWorkflowInstanceId(), command.expectedWorkflowInstanceId())
                || !Objects.equals(application.currentPayloadDigest(), command.expectedPayloadDigest())) {
            throw conflict("工作流轮次、实例、摘要或申请版本与当前申请不一致");
        }
    }

    private void requireCreateWithoutPublishedTarget(ChangeApplication application) {
        if (application.targetId() != null) {
            throw conflict("新增申请不得指向既有发布目标");
        }
    }

    private String safeHistorySummary(ChangeApplication application) {
        return "工作流批准并原子发布 " + application.targetKind() + " " + application.actionType();
    }

    private long nextIdentifier() {
        long identifier = identifierSupplier.getAsLong();
        if (identifier <= 0) {
            throw new IllegalStateException("发布标识生成器返回无效值");
        }
        return identifier;
    }

    private static long nextGeneratedIdentifier() {
        long now = System.currentTimeMillis() * 1_000L;
        return GENERATED_IDENTIFIERS.updateAndGet(previous -> Math.max(previous + 1, now));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireOperator(AuthUser operator) {
        if (operator == null || operator.id() <= 0 || operator.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的工作流操作人和租户");
        }
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    /** 仅含工作流一致性条件，绝不接收审批人可篡改的业务草稿字段。 */
    public record ApprovalCommand(
            long applicationId,
            int expectedBusinessRound,
            long expectedApplicationRowVersion,
            Long expectedWorkflowInstanceId,
            String expectedPayloadDigest) {
        public ApprovalCommand {
            if (applicationId <= 0 || expectedBusinessRound <= 0 || expectedApplicationRowVersion < 0
                    || expectedWorkflowInstanceId == null || expectedWorkflowInstanceId <= 0
                    || expectedPayloadDigest == null || expectedPayloadDigest.isBlank()
                    || expectedPayloadDigest.length() > 64) {
                throw new IllegalArgumentException("批准命令缺少有效的申请、轮次、版本、实例或摘要");
            }
            expectedPayloadDigest = expectedPayloadDigest.trim();
        }
    }

    public record ApprovalResult(long applicationId, List<Long> physicalSubsystemIds) {
        public ApprovalResult {
            physicalSubsystemIds = physicalSubsystemIds == null ? List.of() : List.copyOf(physicalSubsystemIds);
        }
    }

    private record ExistingPhysicalContext(PhysicalDraft draft, PhysicalPublishedState target) {
    }

    private record PreparedPublication(List<Long> physicalSubsystemIds, Runnable writer) {
        private PreparedPublication {
            physicalSubsystemIds = List.copyOf(physicalSubsystemIds);
            writer = Objects.requireNonNull(writer, "writer 不能为空");
        }
    }
}
